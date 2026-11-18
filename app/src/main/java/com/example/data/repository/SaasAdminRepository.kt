package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AdminUserDoc
import com.example.data.model.AuditLogDoc
import com.example.data.model.BusinessAccountDoc
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentRequestDoc
import com.example.data.model.PaymentRequestStatus
import com.example.data.model.SubscriptionPackageDoc
import com.example.data.model.SubscriptionTier
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

const val BOOTSTRAPPED_SUPER_ADMIN_EMAIL = "abdulmunafcox101@gmail.com"

class SaasAdminRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUid(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Authentication required")

    fun isBootstrappedSuperAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        return user.email.equals(BOOTSTRAPPED_SUPER_ADMIN_EMAIL, ignoreCase = true) && user.isEmailVerified
    }

    suspend fun getMyAdminProfile(): AdminUserDoc? {
        val user = auth.currentUser ?: return null
        if (isBootstrappedSuperAdmin()) {
            return AdminUserDoc(
                id = user.uid,
                uid = user.uid,
                email = user.email.orEmpty(),
                name = user.displayName ?: "Munaf Super Admin",
                role = "SUPER_ADMIN",
                canVerifyPayments = true,
                canManageUsers = true,
                canViewLimitedReports = true,
                active = true
            )
        }
        return try {
            val snap = db.collection("admins").document(user.uid).get().await()
            if (snap.exists()) {
                snap.toObject(AdminUserDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?.takeIf { it.active }
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "admins/${user.uid}")
            null
        }
    }

    fun observeSubscriptionPackages(): Flow<List<SubscriptionPackageDoc>> =
        db.collection("subscription_packages")
            .snapshots()
            .map { snap ->
                val list = snap.toObjects(
                    SubscriptionPackageDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (list.isEmpty()) defaultPackages() else list.sortedBy {
                    SubscriptionTier.fromName(it.tier).level
                }
            }

    fun defaultPackages(): List<SubscriptionPackageDoc> = SubscriptionTier.entries.map { tier ->
        SubscriptionPackageDoc(
            id = "pkg_${tier.name.lowercase()}",
            tier = tier.name,
            nameBn = tier.nameBn,
            nameEn = tier.nameEn,
            monthlyPriceBdt = tier.defaultPriceBdt,
            maxBranches = tier.defaultMaxBranches.toLong(),
            maxEmployees = tier.defaultMaxEmployees.toLong(),
            aiEnabled = tier.aiIncluded,
            exportEnabled = tier.exportIncluded
        )
    }

    suspend fun seedDefaultPackagesIfAdmin(): Result<Unit> =
        try {
            if (!isBootstrappedSuperAdmin()) return Result.success(Unit)
            defaultPackages().forEach { pkg ->
                val ref = db.collection("subscription_packages").document(pkg.id)
                if (!ref.get().await().exists()) {
                    ref.set(
                        mapOf(
                            "tier" to pkg.tier,
                            "nameBn" to pkg.nameBn,
                            "nameEn" to pkg.nameEn,
                            "monthlyPriceBdt" to pkg.monthlyPriceBdt,
                            "maxBranches" to pkg.maxBranches,
                            "maxEmployees" to pkg.maxEmployees,
                            "aiEnabled" to pkg.aiEnabled,
                            "exportEnabled" to pkg.exportEnabled,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "subscription_packages")
            Result.failure(e)
        }

    fun observeMyPaymentRequests(userId: String): Flow<List<PaymentRequestDoc>> =
        db.collection("payment_requests")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    PaymentRequestDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    suspend fun submitManualPaymentRequest(
        shopName: String,
        method: PaymentMethod,
        senderNumber: String,
        transactionId: String,
        amountBdt: Double,
        packageTier: SubscriptionTier,
        durationMonths: Int,
        screenshotUri: String?
    ): Result<Unit> =
        try {
            val uid = requireUid()
            val email = auth.currentUser?.email.orEmpty()
            val cleanTrx = transactionId.trim().uppercase()
            require(cleanTrx.length >= 4) { "Invalid Transaction ID" }
            require(senderNumber.trim().length >= 6) { "Invalid Sender Number" }
            require(amountBdt > 0) { "Amount must be greater than zero" }

            // Duplicate Transaction ID check within user's existing requests
            val existingMine = db.collection("payment_requests")
                .whereEqualTo("userId", uid)
                .get()
                .await()
            val duplicate = existingMine.documents.any {
                it.getString("transactionId").equals(cleanTrx, ignoreCase = true)
            }
            if (duplicate) {
                return Result.failure(IllegalArgumentException("এই Transaction ID ইতোমধ্যে জমা দেওয়া হয়েছে (Duplicate TrxID)।"))
            }

            val reqId = "pay_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val doc = PaymentRequestDoc(
                userId = uid,
                userEmail = email,
                shopName = shopName.ifBlank { "My Shop" },
                method = method.name,
                senderNumber = senderNumber.trim(),
                transactionId = cleanTrx,
                amountBdt = amountBdt,
                packageTier = packageTier.name,
                durationMonths = durationMonths.toLong(),
                screenshotUri = screenshotUri
            )
            db.collection("payment_requests").document(reqId).set(doc.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "payment_requests")
            Result.failure(e)
        }

    // --- Admin Queries & Actions ---

    fun observeAllBusinessAccounts(): Flow<List<BusinessAccountDoc>> =
        db.collection("business_accounts")
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    BusinessAccountDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    fun observeAllPaymentRequests(): Flow<List<PaymentRequestDoc>> =
        db.collection("payment_requests")
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    PaymentRequestDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    fun observeSubAdmins(): Flow<List<AdminUserDoc>> =
        db.collection("admins")
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    AdminUserDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    fun observeAuditLogs(): Flow<List<AuditLogDoc>> =
        db.collection("audit_logs")
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    AuditLogDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    suspend fun reviewPaymentRequest(
        request: PaymentRequestDoc,
        approve: Boolean,
        adminNote: String
    ): Result<Unit> =
        try {
            val actor = auth.currentUser ?: error("Admin must sign in")
            val newStatus = if (approve) PaymentRequestStatus.APPROVED.name else PaymentRequestStatus.REJECTED.name

            db.collection("payment_requests").document(request.id).update(
                mapOf(
                    "status" to newStatus,
                    "adminNote" to adminNote.take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (approve) {
                val expiryMillis = System.currentTimeMillis() + (request.durationMonths * 30L * 24L * 3600L * 1000L)
                db.collection("business_accounts").document(request.userId).update(
                    mapOf(
                        "subscriptionTier" to request.packageTier,
                        "subscriptionMonths" to request.durationMonths,
                        "subscriptionExpiresAtMillis" to expiryMillis,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Send notification to business user
            val notifId = "nt_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            db.collection("users").document(request.userId).collection("notifications").document(notifId)
                .set(
                    mapOf(
                        "userId" to request.userId,
                        "title" to if (approve) "প্যাকেজ সক্রিয় হয়েছে / Subscription Activated" else "পেমেন্ট বাতিল / Payment Rejected",
                        "body" to "${request.packageTier} (${request.durationMonths}m) - ${request.transactionId} ${if (adminNote.isNotBlank()) "($adminNote)" else ""}",
                        "category" to "PAYMENT",
                        "readFlag" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()

            writeAuditLog(
                actorUid = actor.uid,
                actorEmail = actor.email.orEmpty(),
                action = if (approve) "APPROVE_PAYMENT" else "REJECT_PAYMENT",
                targetId = request.id,
                details = "${request.shopName} | ${request.packageTier} ${request.durationMonths}m | Trx:${request.transactionId}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "payment_requests/${request.id}")
            Result.failure(e)
        }

    suspend fun toggleUserBlock(account: BusinessAccountDoc, blocked: Boolean): Result<Unit> =
        try {
            val actor = auth.currentUser ?: error("Admin must sign in")
            db.collection("business_accounts").document(account.userId).update(
                mapOf(
                    "blocked" to blocked,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLog(
                actorUid = actor.uid,
                actorEmail = actor.email.orEmpty(),
                action = if (blocked) "BLOCK_USER" else "UNBLOCK_USER",
                targetId = account.userId,
                details = "${account.shopName} (${account.ownerEmail})"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "business_accounts/${account.userId}")
            Result.failure(e)
        }

    suspend fun updatePackageConfig(pkg: SubscriptionPackageDoc): Result<Unit> =
        try {
            val actor = auth.currentUser ?: error("Super Admin must sign in")
            val docId = pkg.id.ifBlank { "pkg_${pkg.tier.lowercase()}" }
            val ref = db.collection("subscription_packages").document(docId)
            if (ref.get().await().exists()) {
                ref.update(
                    mapOf(
                        "nameBn" to pkg.nameBn.take(60),
                        "nameEn" to pkg.nameEn.take(60),
                        "monthlyPriceBdt" to pkg.monthlyPriceBdt,
                        "maxBranches" to pkg.maxBranches,
                        "maxEmployees" to pkg.maxEmployees,
                        "aiEnabled" to pkg.aiEnabled,
                        "exportEnabled" to pkg.exportEnabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.set(
                    mapOf(
                        "tier" to pkg.tier,
                        "nameBn" to pkg.nameBn.take(60),
                        "nameEn" to pkg.nameEn.take(60),
                        "monthlyPriceBdt" to pkg.monthlyPriceBdt,
                        "maxBranches" to pkg.maxBranches,
                        "maxEmployees" to pkg.maxEmployees,
                        "aiEnabled" to pkg.aiEnabled,
                        "exportEnabled" to pkg.exportEnabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            writeAuditLog(
                actorUid = actor.uid,
                actorEmail = actor.email.orEmpty(),
                action = "UPDATE_PACKAGE",
                targetId = docId,
                details = "${pkg.tier}: ৳${pkg.monthlyPriceBdt}, Branches=${pkg.maxBranches}, Emp=${pkg.maxEmployees}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "subscription_packages/${pkg.id}")
            Result.failure(e)
        }

    suspend fun saveSubAdmin(admin: AdminUserDoc): Result<Unit> =
        try {
            val actor = auth.currentUser ?: error("Super Admin must sign in")
            val ref = db.collection("admins").document(admin.uid)
            if (ref.get().await().exists()) {
                ref.update(
                    mapOf(
                        "email" to admin.email.take(200),
                        "name" to admin.name.take(120),
                        "role" to admin.role,
                        "canVerifyPayments" to admin.canVerifyPayments,
                        "canManageUsers" to admin.canManageUsers,
                        "canViewLimitedReports" to admin.canViewLimitedReports,
                        "active" to admin.active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.set(
                    mapOf(
                        "uid" to admin.uid,
                        "email" to admin.email.take(200),
                        "name" to admin.name.take(120),
                        "role" to admin.role,
                        "canVerifyPayments" to admin.canVerifyPayments,
                        "canManageUsers" to admin.canManageUsers,
                        "canViewLimitedReports" to admin.canViewLimitedReports,
                        "active" to admin.active,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            writeAuditLog(
                actorUid = actor.uid,
                actorEmail = actor.email.orEmpty(),
                action = "SAVE_SUB_ADMIN",
                targetId = admin.uid,
                details = "${admin.email} (${admin.role})"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "admins/${admin.uid}")
            Result.failure(e)
        }

    private suspend fun writeAuditLog(
        actorUid: String,
        actorEmail: String,
        action: String,
        targetId: String,
        details: String
    ) {
        val logId = "aud_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        try {
            db.collection("audit_logs").document(logId).set(
                mapOf(
                    "actorUid" to actorUid,
                    "actorEmail" to actorEmail.take(200),
                    "action" to action.take(100),
                    "targetId" to targetId.take(128),
                    "details" to details.take(500),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "audit_logs/$logId")
        }
    }
}
