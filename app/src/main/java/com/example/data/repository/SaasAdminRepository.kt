package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.AccountModerationStatus
import com.example.data.model.AdminUserDoc
import com.example.data.model.AuditLogDoc
import com.example.data.model.BusinessAccountDoc
import com.example.data.model.ComplaintReplyDoc
import com.example.data.model.ComplaintStatus
import com.example.data.model.ComplaintTicketDoc
import com.example.data.model.EmailOutboxDoc
import com.example.data.model.EmailOutboxStatus
import com.example.data.model.ModerationHistoryDoc
import com.example.data.model.PackagePriceHistoryDoc
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentRequestDoc
import com.example.data.model.PaymentRequestStatus
import com.example.data.model.PaymentSettingsDoc
import com.example.data.model.SubscriptionEngine
import com.example.data.model.SubscriptionPackageDoc
import com.example.data.model.SubscriptionRenewalDoc
import com.example.data.model.SubscriptionTier
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

private const val TAG = "SaasAdminRepo"
private const val SUPER_ADMIN_EMAIL = "abdulmunafcox101@gmail.com"

data class ServerClockSyncResult(
    val serverNowMillis: Long,
    val offsetMillis: Long
)

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
        auth.currentUser?.uid ?: throw IllegalStateException("Authentication required.")

    /**
     * Synchronizes authoritative Firestore server time using FieldValue.serverTimestamp().
     * The resulting offset makes subscription expiry checks immune to phone date rollback.
     */
    suspend fun syncServerClock(userId: String): Result<ServerClockSyncResult> =
        try {
            val uid = requireUid()
            val ref = db.collection("users").document(userId).collection("clock_sync").document("latest")
            ref.set(
                mapOf(
                    "userId" to userId,
                    "serverTime" to FieldValue.serverTimestamp()
                )
            ).await()
            val snap = ref.get().await()
            val serverMs = snap.getTimestamp("serverTime")?.toDate()?.time
                ?: throw IllegalStateException("Server timestamp unavailable (uid=$uid)")
            val deviceMs = System.currentTimeMillis()
            Result.success(
                ServerClockSyncResult(
                    serverNowMillis = serverMs,
                    offsetMillis = serverMs - deviceMs
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Server clock sync skipped/offline: ${e.message}")
            Result.failure(e)
        }

    suspend fun getMyAdminProfile(): AdminUserDoc? {
        val user = auth.currentUser ?: return null
        val email = user.email.orEmpty()
        if (email.equals(SUPER_ADMIN_EMAIL, ignoreCase = true) && user.isEmailVerified) {
            return AdminUserDoc(
                id = user.uid,
                uid = user.uid,
                email = email,
                name = user.displayName ?: "Super Admin Owner",
                role = "SUPER_ADMIN",
                canVerifyPayments = true,
                canManageUsers = true,
                canReplyComplaints = true,
                canViewLimitedReports = true,
                active = true
            )
        }
        return try {
            val snap = db.collection("admins").document(user.uid).get().await()
            if (snap.exists()) {
                val doc = snap.toObject(AdminUserDoc::class.java)
                if (doc?.active == true) doc else null
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Admin profile lookup skipped or denied: ${e.message}")
            null
        }
    }

    fun observePaymentSettings(): Flow<PaymentSettingsDoc> = callbackFlow {
        val reg = db.collection("app_config").document("payment_settings")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    trySend(PaymentSettingsDoc())
                    return@addSnapshotListener
                }
                val doc = snap?.toObject(PaymentSettingsDoc::class.java)
                trySend(doc ?: PaymentSettingsDoc())
            }
        awaitClose { reg.remove() }
    }

    suspend fun updatePaymentSettings(settings: PaymentSettingsDoc): Result<Unit> =
        try {
            val uid = requireUid()
            val adminEmail = auth.currentUser?.email.orEmpty()
            val payload = mapOf(
                "bkashAccountName" to settings.bkashAccountName.trim().ifBlank { "MUNAF ONLINE SHOP" }.take(120),
                "bkashNumber" to settings.bkashNumber.trim().take(30),
                "bkashEnabled" to settings.bkashEnabled,
                "nagadAccountName" to settings.nagadAccountName.trim().ifBlank { "MUNAF ONLINE SHOP" }.take(120),
                "nagadNumber" to settings.nagadNumber.trim().take(30),
                "nagadEnabled" to settings.nagadEnabled,
                "instructionsBn" to settings.instructionsBn.trim().take(500),
                "instructionsEn" to settings.instructionsEn.trim().take(500),
                "updatedByEmail" to adminEmail.take(200),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("app_config").document("payment_settings").set(payload).await()
            logAudit(
                actorUid = uid,
                actorEmail = adminEmail,
                action = "UPDATE_PAYMENT_SETTINGS",
                targetId = "payment_settings",
                details = "bKash(${settings.bkashEnabled}): ${settings.bkashNumber} | Nagad(${settings.nagadEnabled}): ${settings.nagadNumber}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "app_config/payment_settings")
            Result.failure(e)
        }

    fun observeSubscriptionPackages(): Flow<List<SubscriptionPackageDoc>> = callbackFlow {
        val reg = db.collection("subscription_packages")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val docs = snap?.toObjects(SubscriptionPackageDoc::class.java).orEmpty()
                val sorted = if (docs.isEmpty()) defaultPackages() else docs.sortedBy {
                    SubscriptionTier.fromName(it.tier).level
                }
                trySend(sorted)
            }
        awaitClose { reg.remove() }
    }

    fun defaultPackages(): List<SubscriptionPackageDoc> = SubscriptionTier.entries.map { t ->
        val p1 = t.defaultPriceBdt
        SubscriptionPackageDoc(
            id = "pkg_${t.name.lowercase()}",
            tier = t.name,
            nameBn = t.nameBn,
            nameEn = t.nameEn,
            monthlyPriceBdt = p1,
            price1mBdt = p1,
            price3mBdt = kotlin.math.round(p1 * 3 * 0.90),
            price6mBdt = kotlin.math.round(p1 * 6 * 0.85),
            price12mBdt = kotlin.math.round(p1 * 12 * 0.75),
            maxBranches = t.defaultMaxBranches.toLong(),
            maxEmployees = t.defaultMaxEmployees.toLong(),
            aiEnabled = t.aiIncluded,
            exportEnabled = t.exportIncluded,
            enabled = true,
            featuresSummaryBn = defaultSummaryBn(t),
            featuresSummaryEn = defaultSummaryEn(t)
        )
    }

    private fun defaultSummaryBn(tier: SubscriptionTier): String = when (tier) {
        SubscriptionTier.FREE -> "১ শাখা • ১ কর্মচারী • দৈনিক হিসাব ও খাতা"
        SubscriptionTier.BASIC -> "২ শাখা • ৩ কর্মচারী • AI সহায়ক ও PDF রিপোর্ট"
        SubscriptionTier.PREMIUM -> "৫ শাখা • ১০ কর্মচারী • AI + অগ্রাধিকার সাপোর্ট"
        SubscriptionTier.BUSINESS -> "৫০ শাখা • ১০০ কর্মচারী • এন্টারপ্রাইজ ক্লাউড স্যুইট"
    }

    private fun defaultSummaryEn(tier: SubscriptionTier): String = when (tier) {
        SubscriptionTier.FREE -> "1 Branch • 1 Staff • Daily Khata & Sales"
        SubscriptionTier.BASIC -> "2 Branches • 3 Staff • AI Assistant & PDF Reports"
        SubscriptionTier.PREMIUM -> "5 Branches • 10 Staff • Priority AI & Exports"
        SubscriptionTier.BUSINESS -> "50 Branches • 100 Staff • Full Enterprise Suite"
    }

    suspend fun seedDefaultPackagesIfAdmin() {
        try {
            val existing = db.collection("subscription_packages").get().await()
            if (existing.isEmpty) {
                defaultPackages().forEach { pkg ->
                    db.collection("subscription_packages").document(pkg.id).set(
                        mapOf(
                            "tier" to pkg.tier,
                            "nameBn" to pkg.nameBn,
                            "nameEn" to pkg.nameEn,
                            "monthlyPriceBdt" to pkg.monthlyPriceBdt,
                            "price1mBdt" to pkg.price1mBdt,
                            "price3mBdt" to pkg.price3mBdt,
                            "price6mBdt" to pkg.price6mBdt,
                            "price12mBdt" to pkg.price12mBdt,
                            "maxBranches" to pkg.maxBranches,
                            "maxEmployees" to pkg.maxEmployees,
                            "aiEnabled" to pkg.aiEnabled,
                            "exportEnabled" to pkg.exportEnabled,
                            "enabled" to pkg.enabled,
                            "featuresSummaryBn" to pkg.featuresSummaryBn,
                            "featuresSummaryEn" to pkg.featuresSummaryEn,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            val paySettingsRef = db.collection("app_config").document("payment_settings")
            if (!paySettingsRef.get().await().exists()) {
                val def = PaymentSettingsDoc()
                paySettingsRef.set(
                    mapOf(
                        "bkashAccountName" to def.bkashAccountName,
                        "bkashNumber" to def.bkashNumber,
                        "bkashEnabled" to def.bkashEnabled,
                        "nagadAccountName" to def.nagadAccountName,
                        "nagadNumber" to def.nagadNumber,
                        "nagadEnabled" to def.nagadEnabled,
                        "instructionsBn" to def.instructionsBn,
                        "instructionsEn" to def.instructionsEn,
                        "updatedByEmail" to (auth.currentUser?.email ?: SUPER_ADMIN_EMAIL),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Seed skipped: ${e.message}")
        }
    }

    suspend fun updatePackageConfig(
        pkg: SubscriptionPackageDoc,
        previousMonthlyPriceBdt: Double
    ): Result<Unit> =
        try {
            val uid = requireUid()
            val adminEmail = auth.currentUser?.email.orEmpty()
            val docId = pkg.id.ifBlank { "pkg_${pkg.tier.lowercase()}" }
            val batch = db.batch()
            val pkgRef = db.collection("subscription_packages").document(docId)
            batch.set(
                pkgRef,
                mapOf(
                    "tier" to pkg.tier,
                    "nameBn" to pkg.nameBn.trim().take(60),
                    "nameEn" to pkg.nameEn.trim().take(60),
                    "monthlyPriceBdt" to pkg.monthlyPriceBdt.coerceAtLeast(0.0),
                    "price1mBdt" to pkg.price1mBdt.coerceAtLeast(0.0),
                    "price3mBdt" to pkg.price3mBdt.coerceAtLeast(0.0),
                    "price6mBdt" to pkg.price6mBdt.coerceAtLeast(0.0),
                    "price12mBdt" to pkg.price12mBdt.coerceAtLeast(0.0),
                    "maxBranches" to pkg.maxBranches.coerceAtLeast(1L),
                    "maxEmployees" to pkg.maxEmployees.coerceAtLeast(0L),
                    "aiEnabled" to pkg.aiEnabled,
                    "exportEnabled" to pkg.exportEnabled,
                    "enabled" to pkg.enabled,
                    "featuresSummaryBn" to pkg.featuresSummaryBn.trim().take(400),
                    "featuresSummaryEn" to pkg.featuresSummaryEn.trim().take(400),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val histId = "pkh_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val summary = "Tier ${pkg.tier}: 1m=৳${pkg.priceForMonths(1).toInt()}, 3m=৳${pkg.priceForMonths(3).toInt()}, " +
                "6m=৳${pkg.priceForMonths(6).toInt()}, 12m=৳${pkg.priceForMonths(12).toInt()} | " +
                "Branches=${pkg.maxBranches}, Staff=${pkg.maxEmployees}, Enabled=${pkg.enabled}"
            batch.set(
                db.collection("package_price_history").document(histId),
                mapOf(
                    "tier" to pkg.tier,
                    "actorUid" to uid,
                    "actorEmail" to adminEmail.take(200),
                    "previousMonthlyBdt" to previousMonthlyPriceBdt,
                    "newMonthlyBdt" to pkg.monthlyPriceBdt,
                    "price1mBdt" to pkg.price1mBdt,
                    "price3mBdt" to pkg.price3mBdt,
                    "price6mBdt" to pkg.price6mBdt,
                    "price12mBdt" to pkg.price12mBdt,
                    "maxBranches" to pkg.maxBranches,
                    "maxEmployees" to pkg.maxEmployees,
                    "packageEnabled" to pkg.enabled,
                    "summary" to summary.take(400),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            logAudit(uid, adminEmail, "UPDATE_PACKAGE", pkg.tier, summary)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "subscription_packages/${pkg.tier}")
            Result.failure(e)
        }

    fun observePackagePriceHistory(): Flow<List<PackagePriceHistoryDoc>> = callbackFlow {
        val reg = db.collection("package_price_history")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snap?.toObjects(PackagePriceHistoryDoc::class.java).orEmpty())
            }
        awaitClose { reg.remove() }
    }

    fun observeMyPaymentRequests(userId: String): Flow<List<PaymentRequestDoc>> = callbackFlow {
        val reg = db.collection("payment_requests")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snap?.toObjects(PaymentRequestDoc::class.java).orEmpty()
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeMyRenewals(userId: String): Flow<List<SubscriptionRenewalDoc>> = callbackFlow {
        val reg = db.collection("subscription_renewals")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snap?.toObjects(SubscriptionRenewalDoc::class.java).orEmpty()
                    .sortedByDescending { it.startedAtMillis }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeAllRenewals(): Flow<List<SubscriptionRenewalDoc>> = callbackFlow {
        val reg = db.collection("subscription_renewals")
            .orderBy("startedAtMillis", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snap?.toObjects(SubscriptionRenewalDoc::class.java).orEmpty())
            }
        awaitClose { reg.remove() }
    }

    suspend fun submitManualPaymentRequest(
        shopName: String,
        method: PaymentMethod,
        senderNumber: String,
        transactionId: String,
        amountBdt: Double,
        lockedUnitMonthlyBdt: Double,
        packageTier: SubscriptionTier,
        durationMonths: Int,
        screenshotUri: String?
    ): Result<Unit> {
        return try {
            val uid = requireUid()
            val email = auth.currentUser?.email.orEmpty()
            val normalizedTrx = SubscriptionEngine.normalizeTrxId(transactionId)
            if (normalizedTrx.length < 4) {
                return Result.failure(
                    IllegalArgumentException("সঠিক Transaction ID (কমপক্ষে ৪ অক্ষর) দিন। / Enter a valid Transaction ID (min 4 chars).")
                )
            }
            if (senderNumber.trim().length < 6) {
                return Result.failure(
                    IllegalArgumentException("সঠিক Sender মোবাইল নম্বর দিন। / Enter a valid Sender number.")
                )
            }

            // Local fast duplicate guard for own previous requests
            val myDup = db.collection("payment_requests")
                .whereEqualTo("userId", uid)
                .whereEqualTo("transactionId", normalizedTrx)
                .get().await()
            if (!myDup.isEmpty) {
                return Result.failure(
                    IllegalStateException("এই Transaction ID ($normalizedTrx) ইতিমধ্যেই ব্যবহার করা হয়েছে! ডুপ্লিকেট অনুমোদিত নয়।")
                )
            }

            val reqId = "pay_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val doc = PaymentRequestDoc(
                userId = uid,
                userEmail = email,
                shopName = shopName.ifBlank { "My Business" },
                method = method.name,
                senderNumber = senderNumber.trim(),
                transactionId = normalizedTrx,
                amountBdt = amountBdt,
                lockedUnitMonthlyBdt = lockedUnitMonthlyBdt,
                packageTier = packageTier.name,
                durationMonths = durationMonths.toLong(),
                screenshotUri = screenshotUri
            )

            // Atomic batch: claims global trx_registry/{normalizedTrx} + writes payment_requests/{reqId} + queues email_outbox
            val batch = db.batch()
            batch.set(
                db.collection("trx_registry").document(normalizedTrx),
                mapOf(
                    "userId" to uid,
                    "trxKey" to normalizedTrx,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(db.collection("payment_requests").document(reqId), doc.toCreateMap())
            val mailId = "mail_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("email_outbox").document(mailId),
                mapOf(
                    "recipientEmail" to SUPER_ADMIN_EMAIL,
                    "emailType" to "ORDER_ALERT_OWNER",
                    "subject" to "নতুন অর্ডার: ${packageTier.name} ($durationMonths মাস) - ৳${amountBdt.toInt()}",
                    "bodyPreview" to "Shop: ${doc.shopName} | User: $email | Method: ${method.name} | Sender: $senderNumber | TrxID: $normalizedTrx | Amount: ৳${amountBdt.toInt()}",
                    "relatedUserId" to uid,
                    "relatedOrderId" to reqId,
                    "status" to EmailOutboxStatus.PENDING_BACKEND_CONFIG.name,
                    "statusNote" to "Queued in Firestore email_outbox. Backend SMTP/Cloud Function required to dispatch.",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val rawMsg = e.localizedMessage.orEmpty()
            if (rawMsg.contains("PERMISSION_DENIED", true) || rawMsg.contains("ALREADY_EXISTS", true)) {
                return Result.failure(
                    IllegalStateException("এই Transaction ID ইতিমধ্যেই সিস্টেমে ব্যবহৃত হয়েছে! ডুপ্লিকেট TrxID গ্রহণযোগ্য নয়। / Duplicate Transaction ID blocked.")
                )
            }
            handleFirestoreError(e, OperationType.CREATE, "payment_requests")
            Result.failure(e)
        }
    }

    fun observeAllBusinessAccounts(): Flow<List<BusinessAccountDoc>> = callbackFlow {
        val reg = db.collection("business_accounts")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snap?.toObjects(BusinessAccountDoc::class.java).orEmpty()
                    .sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeAllPaymentRequests(): Flow<List<PaymentRequestDoc>> = callbackFlow {
        val reg = db.collection("payment_requests")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snap?.toObjects(PaymentRequestDoc::class.java).orEmpty())
            }
        awaitClose { reg.remove() }
    }

    suspend fun reviewPaymentRequest(
        request: PaymentRequestDoc,
        approve: Boolean,
        note: String
    ): Result<Unit> =
        try {
            val uid = requireUid()
            val adminEmail = auth.currentUser?.email.orEmpty()
            val cleanNote = note.trim()
            if (!approve && cleanNote.length < 3) {
                return Result.failure(
                    IllegalArgumentException("পেমেন্ট বাতিল করতে হলে অবশ্যই কারণ লিখতে হবে। / Rejection reason is required.")
                )
            }

            // Use authoritative server timestamp for approval timing & countdown
            val serverClock = syncServerClock(uid).getOrNull()
            val serverNowMs = serverClock?.serverNowMillis ?: System.currentTimeMillis()
            val newStatus = if (approve) PaymentRequestStatus.APPROVED.name else PaymentRequestStatus.REJECTED.name

            db.collection("payment_requests").document(request.id).update(
                mapOf(
                    "status" to newStatus,
                    "adminNote" to cleanNote.ifBlank { if (approve) "Verified" else "Rejected" }.take(300),
                    "reviewedByEmail" to adminEmail.take(200),
                    "reviewedAtMillis" to serverNowMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (approve) {
                val accountRef = db.collection("business_accounts").document(request.userId)
                val accSnap = accountRef.get().await()
                val existingTier = accSnap.getString("subscriptionTier")
                val existingStart = accSnap.getLong("subscriptionStartMillis") ?: 0L
                val existingExp = accSnap.getLong("subscriptionExpiresAtMillis") ?: 0L
                val targetTier = SubscriptionTier.fromName(request.packageTier)

                val (startMillis, expiresAt) = SubscriptionEngine.computeRenewalWindow(
                    serverNowMillis = serverNowMs,
                    existingTierName = existingTier,
                    existingStartMillis = existingStart,
                    existingExpiresAtMillis = existingExp,
                    newTier = targetTier,
                    durationMonths = request.durationMonths.toInt()
                )

                accountRef.update(
                    mapOf(
                        "subscriptionTier" to request.packageTier,
                        "subscriptionMonths" to request.durationMonths,
                        "subscriptionStartMillis" to startMillis,
                        "subscriptionExpiresAtMillis" to expiresAt,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                val renewalId = "ren_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                db.collection("subscription_renewals").document(renewalId).set(
                    mapOf(
                        "userId" to request.userId,
                        "userEmail" to request.userEmail,
                        "shopName" to request.shopName,
                        "paymentRequestId" to request.id,
                        "packageTier" to request.packageTier,
                        "durationMonths" to request.durationMonths,
                        "amountBdt" to request.amountBdt,
                        "startedAtMillis" to startMillis,
                        "expiresAtMillis" to expiresAt,
                        "approvedByEmail" to adminEmail,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // In-App notification to customer
            val notifId = "nt_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            db.collection("users").document(request.userId).collection("notifications").document(notifId).set(
                mapOf(
                    "userId" to request.userId,
                    "title" to if (approve) "প্যাকেজ অনুমোদিত ও সক্রিয় হয়েছে" else "পেমেন্ট রিকোয়েস্ট বাতিল হয়েছে",
                    "body" to if (approve) {
                        "${request.packageTier} (${request.durationMonths} মাস) প্যাকেজ সক্রিয় হয়েছে। TrxID: ${request.transactionId}"
                    } else {
                        "কারণ: $cleanNote (TrxID: ${request.transactionId})"
                    },
                    "category" to "SUBSCRIPTION",
                    "readFlag" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Honest email outbox entry for customer
            if (request.userEmail.isNotBlank()) {
                val mailId = "mail_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                db.collection("email_outbox").document(mailId).set(
                    mapOf(
                        "recipientEmail" to request.userEmail,
                        "emailType" to if (approve) "PAYMENT_APPROVED" else "PAYMENT_REJECTED",
                        "subject" to if (approve) {
                            "MUNAF SHOP: ${request.packageTier} সাবস্ক্রিপশন সক্রিয় হয়েছে"
                        } else {
                            "MUNAF SHOP: পেমেন্ট রিকোয়েস্ট বাতিল হয়েছে"
                        },
                        "bodyPreview" to if (approve) {
                            "আপনার ৳${request.amountBdt.toInt()} (${request.durationMonths} মাস) পেমেন্ট অনুমোদিত হয়েছে।"
                        } else {
                            "বাতিলের কারণ: $cleanNote"
                        },
                        "relatedUserId" to request.userId,
                        "relatedOrderId" to request.id,
                        "status" to EmailOutboxStatus.PENDING_BACKEND_CONFIG.name,
                        "statusNote" to "Queued in Firestore email_outbox. Backend SMTP/Cloud Function required to dispatch.",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            logAudit(
                actorUid = uid,
                actorEmail = adminEmail,
                action = if (approve) "APPROVE_PAYMENT" else "REJECT_PAYMENT",
                targetId = request.id,
                details = "${request.shopName} (${request.packageTier} ${request.durationMonths}m ৳${request.amountBdt.toInt()}) -> $newStatus | Note: $cleanNote"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "payment_requests/${request.id}")
            Result.failure(e)
        }

    suspend fun moderateUserAccount(
        account: BusinessAccountDoc,
        action: String, // "SUSPEND", "BAN", "UNBAN"
        reason: String,
        suspendDays: Int = 7
    ): Result<Unit> =
        try {
            val uid = requireUid()
            val adminEmail = auth.currentUser?.email.orEmpty()
            val cleanReason = reason.trim()
            if (cleanReason.length < 3) {
                return Result.failure(
                    IllegalArgumentException("কারণ উল্লেখ করা বাধ্যতামূলক (কমপক্ষে ৩ অক্ষর)। / Mandatory reason required.")
                )
            }
            val serverNow = syncServerClock(uid).getOrNull()?.serverNowMillis ?: System.currentTimeMillis()
            val newStatus = when (action) {
                "SUSPEND" -> AccountModerationStatus.SUSPENDED.name
                "BAN" -> AccountModerationStatus.BANNED.name
                else -> AccountModerationStatus.ACTIVE.name
            }
            val blocked = newStatus != AccountModerationStatus.ACTIVE.name
            val suspendedUntil = if (action == "SUSPEND") {
                serverNow + (suspendDays.coerceIn(1, 365).toLong() * SubscriptionEngine.ONE_DAY_MILLIS)
            } else {
                0L
            }

            val batch = db.batch()
            // Update moderation status ONLY — NEVER delete any data under /users/{userId}/**
            batch.update(
                db.collection("business_accounts").document(account.userId),
                mapOf(
                    "blocked" to blocked,
                    "accountStatus" to newStatus,
                    "banReason" to if (blocked) cleanReason.take(400) else "",
                    "suspendedUntilMillis" to suspendedUntil,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val modId = "mod_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("moderation_history").document(modId),
                mapOf(
                    "userId" to account.userId,
                    "userEmail" to account.ownerEmail,
                    "shopName" to account.shopName,
                    "action" to action,
                    "reason" to cleanReason.take(400),
                    "suspendedUntilMillis" to suspendedUntil,
                    "actorUid" to uid,
                    "actorEmail" to adminEmail.take(200),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            val notifId = "nt_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("users").document(account.userId).collection("notifications").document(notifId),
                mapOf(
                    "userId" to account.userId,
                    "title" to when (action) {
                        "SUSPEND" -> "অ্যাকাউন্ট সাময়িক স্থগিত ($suspendDays দিন)"
                        "BAN" -> "অ্যাকাউন্ট ব্যান করা হয়েছে"
                        else -> "অ্যাকাউন্ট পুনরায় সক্রিয় হয়েছে"
                    },
                    "body" to "কারণ: $cleanReason। আপনার ব্যবসার পূর্বের সকল হিসাব সুরক্ষিত আছে।",
                    "category" to "MODERATION",
                    "readFlag" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            logAudit(
                actorUid = uid,
                actorEmail = adminEmail,
                action = "USER_$action",
                targetId = account.userId,
                details = "${account.shopName} -> $newStatus | Reason: $cleanReason"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "business_accounts/${account.userId}")
            Result.failure(e)
        }

    fun observeModerationHistory(): Flow<List<ModerationHistoryDoc>> = callbackFlow {
        val reg = db.collection("moderation_history")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snap?.toObjects(ModerationHistoryDoc::class.java).orEmpty())
            }
        awaitClose { reg.remove() }
    }

    // --- Complaints System ---

    fun observeMyComplaints(userId: String): Flow<List<ComplaintTicketDoc>> = callbackFlow {
        val reg = db.collection("complaints")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snap?.toObjects(ComplaintTicketDoc::class.java).orEmpty()
                    .sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeAllComplaints(): Flow<List<ComplaintTicketDoc>> = callbackFlow {
        val reg = db.collection("complaints")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snap?.toObjects(ComplaintTicketDoc::class.java).orEmpty())
            }
        awaitClose { reg.remove() }
    }

    fun observeComplaintReplies(ticketId: String): Flow<List<ComplaintReplyDoc>> = callbackFlow {
        val reg = db.collection("complaints").document(ticketId).collection("replies")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snap?.toObjects(ComplaintReplyDoc::class.java).orEmpty())
            }
        awaitClose { reg.remove() }
    }

    suspend fun submitComplaint(
        shopName: String,
        subject: String,
        description: String,
        attachmentUri: String?
    ): Result<String> =
        try {
            val uid = requireUid()
            val email = auth.currentUser?.email.orEmpty()
            val ticketId = "cmp_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val ticketCode = "TKT-${(100000..999999).random()}"
            val payload = buildMap<String, Any> {
                put("ticketCode", ticketCode)
                put("userId", uid)
                put("userEmail", email.take(200))
                put("shopName", shopName.ifBlank { "My Shop" }.take(120))
                put("subject", subject.trim().take(160))
                put("description", description.trim().take(2000))
                if (!attachmentUri.isNullOrBlank()) put("attachmentUri", attachmentUri.take(1000))
                put("status", ComplaintStatus.PENDING.name)
                put("lastReplyText", "")
                put("lastReplyBy", "")
                put("lastReplyAtMillis", 0L)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("complaints").document(ticketId).set(payload).await()
            Result.success(ticketCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "complaints")
            Result.failure(e)
        }

    suspend fun replyToComplaint(
        ticket: ComplaintTicketDoc,
        message: String,
        newStatus: ComplaintStatus?,
        senderName: String,
        senderRole: String // "CUSTOMER", "SUPER_ADMIN", "SUB_ADMIN"
    ): Result<Unit> =
        try {
            val uid = requireUid()
            val adminEmail = auth.currentUser?.email.orEmpty()
            val cleanMsg = message.trim()
            if (cleanMsg.isBlank()) {
                return Result.failure(IllegalArgumentException("রিপ্লাই বার্তা খালি রাখা যাবে না। / Reply cannot be blank."))
            }
            val nowMs = syncServerClock(uid).getOrNull()?.serverNowMillis ?: System.currentTimeMillis()
            val replyId = "rep_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val batch = db.batch()
            val ticketRef = db.collection("complaints").document(ticket.id)

            batch.set(
                ticketRef.collection("replies").document(replyId),
                mapOf(
                    "ticketId" to ticket.id,
                    "senderUid" to uid,
                    "senderName" to senderName.ifBlank { "Support" }.take(120),
                    "senderRole" to senderRole,
                    "message" to cleanMsg.take(2000),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            val ticketUpdate = buildMap<String, Any> {
                if (senderRole != "CUSTOMER" && newStatus != null) {
                    put("status", newStatus.name)
                }
                put("lastReplyText", cleanMsg.take(1000))
                put("lastReplyBy", senderName.ifBlank { "Support" }.take(120))
                put("lastReplyAtMillis", nowMs)
                put("updatedAt", FieldValue.serverTimestamp())
            }
            batch.update(ticketRef, ticketUpdate)

            if (senderRole != "CUSTOMER") {
                val targetStatus = newStatus?.name ?: ticket.status
                val isResolved = targetStatus == ComplaintStatus.RESOLVED.name
                val notifId = "nt_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                batch.set(
                    db.collection("users").document(ticket.userId).collection("notifications").document(notifId),
                    mapOf(
                        "userId" to ticket.userId,
                        "title" to if (isResolved) {
                            "অভিযোগ (${ticket.ticketCode}) সমাধান হয়েছে"
                        } else {
                            "অভিযোগ (${ticket.ticketCode})-এ অ্যাডমিনের উত্তর"
                        },
                        "body" to "${cleanMsg.take(220)} [অবস্থা: $targetStatus]",
                        "category" to "COMPLAINT",
                        "readFlag" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                if (ticket.userEmail.isNotBlank()) {
                    val mailId = "mail_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                    batch.set(
                        db.collection("email_outbox").document(mailId),
                        mapOf(
                            "recipientEmail" to ticket.userEmail,
                            "emailType" to "COMPLAINT_REPLY",
                            "subject" to "MUNAF SHOP Support [${ticket.ticketCode}]: $targetStatus",
                            "bodyPreview" to cleanMsg.take(500),
                            "relatedUserId" to ticket.userId,
                            "relatedOrderId" to ticket.id,
                            "status" to EmailOutboxStatus.PENDING_BACKEND_CONFIG.name,
                            "statusNote" to "Queued in Firestore email_outbox. Backend SMTP/Cloud Function required.",
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            batch.commit().await()

            if (senderRole != "CUSTOMER") {
                logAudit(
                    actorUid = uid,
                    actorEmail = adminEmail,
                    action = "COMPLAINT_REPLY",
                    targetId = ticket.ticketCode,
                    details = "Status=${newStatus?.name ?: ticket.status} | ${cleanMsg.take(150)}"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "complaints/${ticket.id}")
            Result.failure(e)
        }

    fun observeEmailOutbox(): Flow<List<EmailOutboxDoc>> = callbackFlow {
        val reg = db.collection("email_outbox")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snap?.toObjects(EmailOutboxDoc::class.java).orEmpty())
            }
        awaitClose { reg.remove() }
    }

    fun observeSubAdmins(): Flow<List<AdminUserDoc>> = callbackFlow {
        val reg = db.collection("admins")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snap?.toObjects(AdminUserDoc::class.java).orEmpty())
            }
        awaitClose { reg.remove() }
    }

    suspend fun saveSubAdmin(admin: AdminUserDoc): Result<Unit> =
        try {
            val uid = requireUid()
            val actorEmail = auth.currentUser?.email.orEmpty()
            val ref = db.collection("admins").document(admin.uid)
            val exists = ref.get().await().exists()
            if (!exists) {
                ref.set(
                    mapOf(
                        "uid" to admin.uid,
                        "email" to admin.email.trim(),
                        "name" to admin.name.trim(),
                        "role" to admin.role,
                        "canVerifyPayments" to admin.canVerifyPayments,
                        "canManageUsers" to admin.canManageUsers,
                        "canReplyComplaints" to admin.canReplyComplaints,
                        "canViewLimitedReports" to admin.canViewLimitedReports,
                        "active" to admin.active,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.update(
                    mapOf(
                        "email" to admin.email.trim(),
                        "name" to admin.name.trim(),
                        "role" to admin.role,
                        "canVerifyPayments" to admin.canVerifyPayments,
                        "canManageUsers" to admin.canManageUsers,
                        "canReplyComplaints" to admin.canReplyComplaints,
                        "canViewLimitedReports" to admin.canViewLimitedReports,
                        "active" to admin.active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            logAudit(uid, actorEmail, "SAVE_SUB_ADMIN", admin.uid, "${admin.email} active=${admin.active} complaints=${admin.canReplyComplaints}")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "admins/${admin.uid}")
            Result.failure(e)
        }

    fun observeAuditLogs(): Flow<List<AuditLogDoc>> = callbackFlow {
        val reg = db.collection("audit_logs")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snap?.toObjects(AuditLogDoc::class.java).orEmpty())
            }
        awaitClose { reg.remove() }
    }

    private suspend fun logAudit(
        actorUid: String,
        actorEmail: String,
        action: String,
        targetId: String,
        details: String
    ) {
        val logId = "aud_${UUID.randomUUID().toString().replace("-", "").take(16)}"
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
    }
}
