package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.local.BranchEntity
import com.example.data.local.BusinessProfileEntity
import com.example.data.local.EmployeeEntity
import com.example.data.local.MunafDao
import com.example.data.local.NotificationEntity
import com.example.data.local.PartyEntity
import com.example.data.local.ProductEntity
import com.example.data.local.StockLogEntity
import com.example.data.local.TransactionEntity
import com.example.data.local.TxLineItem
import com.example.data.local.VoiceNoteEntity
import com.example.data.model.BusinessAccountDoc
import com.example.data.model.SyncStatus
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class SyncSummary(
    val syncedCount: Int,
    val failedCount: Int,
    val lastError: String? = null
)

class BusinessRepository(
    private val dao: MunafDao,
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context, dao: MunafDao) : this(
        dao = dao,
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google.")

    fun observeBusinessProfile(userId: String): Flow<BusinessProfileEntity?> =
        dao.observeBusinessProfile(userId)

    fun observeBranches(userId: String): Flow<List<BranchEntity>> =
        dao.observeBranches(userId)

    fun observeProducts(userId: String): Flow<List<ProductEntity>> =
        dao.observeProducts(userId)

    fun observeParties(userId: String): Flow<List<PartyEntity>> =
        dao.observeParties(userId)

    fun observeTransactions(userId: String): Flow<List<TransactionEntity>> =
        dao.observeTransactions(userId)

    fun observeStockLogs(userId: String): Flow<List<StockLogEntity>> =
        dao.observeStockLogs(userId)

    fun observeEmployees(userId: String): Flow<List<EmployeeEntity>> =
        dao.observeEmployees(userId)

    fun observeVoiceNotes(userId: String): Flow<List<VoiceNoteEntity>> =
        dao.observeVoiceNotes(userId)

    fun observeNotifications(userId: String): Flow<List<NotificationEntity>> =
        dao.observeNotifications(userId)

    suspend fun saveBusinessProfile(profile: BusinessProfileEntity): Result<Unit> =
        try {
            val uid = requireUserId()
            check(uid == profile.userId)
            val now = System.currentTimeMillis()
            val updated = profile.copy(
                syncStatus = SyncStatus.PENDING.name,
                updatedAtMillis = now
            )
            dao.upsertBusinessProfile(updated)

            // Ensure at least a Main Branch exists offline-first
            val existingBranches = dao.getBranches(uid)
            if (existingBranches.isEmpty()) {
                dao.upsertBranch(
                    BranchEntity(
                        id = "br_main_$uid".take(40),
                        userId = uid,
                        name = "প্রধান শাখা (Main)",
                        address = profile.address,
                        phone = profile.phone,
                        mainBranch = true,
                        syncStatus = SyncStatus.PENDING.name,
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                )
            }
            syncPendingToCloud(uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun saveBranch(branch: BranchEntity): Result<Unit> =
        try {
            val uid = requireUserId()
            check(uid == branch.userId)
            dao.upsertBranch(branch.copy(syncStatus = SyncStatus.PENDING.name))
            syncPendingToCloud(uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun saveProduct(product: ProductEntity): Result<Unit> =
        try {
            val uid = requireUserId()
            check(uid == product.userId)
            val isNew = dao.getProductById(product.id) == null
            dao.upsertProduct(product.copy(syncStatus = SyncStatus.PENDING.name))
            if (isNew && product.stockQty > 0) {
                dao.upsertStockLog(
                    StockLogEntity(
                        id = "sl_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                        userId = uid,
                        branchId = product.branchId,
                        productId = product.id,
                        productName = product.name,
                        changeQty = product.stockQty,
                        reason = "OPENING_STOCK",
                        referenceTxId = "",
                        syncStatus = SyncStatus.PENDING.name,
                        createdAtMillis = System.currentTimeMillis()
                    )
                )
            }
            syncPendingToCloud(uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun adjustStock(product: ProductEntity, deltaQty: Double, reason: String): Result<Unit> =
        try {
            val uid = requireUserId()
            check(uid == product.userId)
            dao.adjustStockAtomic(product, deltaQty, reason)
            syncPendingToCloud(uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun transferStock(
        sourceProduct: ProductEntity,
        targetBranchId: String,
        qty: Double
    ): Result<Unit> =
        try {
            val uid = requireUserId()
            check(uid == sourceProduct.userId)
            dao.transferStockAtomic(sourceProduct, targetBranchId, qty)
            syncPendingToCloud(uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun saveParty(party: PartyEntity): Result<Unit> =
        try {
            val uid = requireUserId()
            check(uid == party.userId)
            dao.upsertParty(party.copy(syncStatus = SyncStatus.PENDING.name))
            syncPendingToCloud(uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun recordSale(tx: TransactionEntity, items: List<TxLineItem>): Result<Unit> =
        try {
            val uid = requireUserId()
            check(uid == tx.userId)
            dao.recordSaleAtomic(tx.copy(syncStatus = SyncStatus.PENDING.name), items)
            checkLowStockAlerts(uid, items)
            syncPendingToCloud(uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun cancelTransaction(txId: String): Result<Unit> =
        try {
            val uid = requireUserId()
            dao.cancelSaleAtomic(txId, uid)
            syncPendingToCloud(uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun recordPurchase(tx: TransactionEntity, items: List<TxLineItem>): Result<Unit> =
        try {
            val uid = requireUserId()
            check(uid == tx.userId)
            dao.recordPurchaseAtomic(tx.copy(syncStatus = SyncStatus.PENDING.name), items)
            syncPendingToCloud(uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun recordPartyPayment(tx: TransactionEntity): Result<Unit> =
        try {
            val uid = requireUserId()
            check(uid == tx.userId)
            dao.recordPartyPaymentAtomic(tx.copy(syncStatus = SyncStatus.PENDING.name))
            syncPendingToCloud(uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun recordSimpleTransaction(tx: TransactionEntity): Result<Unit> =
        try {
            val uid = requireUserId()
            check(uid == tx.userId)
            dao.upsertTransaction(tx.copy(syncStatus = SyncStatus.PENDING.name))
            syncPendingToCloud(uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun saveEmployee(employee: EmployeeEntity): Result<Unit> =
        try {
            val uid = requireUserId()
            check(uid == employee.userId)
            dao.upsertEmployee(employee.copy(syncStatus = SyncStatus.PENDING.name))
            syncPendingToCloud(uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun deleteEmployee(employeeId: String): Result<Unit> =
        try {
            val uid = requireUserId()
            dao.deleteEmployee(employeeId, uid)
            try {
                db.collection("users").document(uid).collection("employees")
                    .document(employeeId).delete().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, "users/$uid/employees/$employeeId")
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun saveVoiceNote(note: VoiceNoteEntity) {
        dao.upsertVoiceNote(note)
    }

    suspend fun deleteVoiceNote(noteId: String, userId: String) {
        dao.deleteVoiceNote(noteId, userId)
    }

    suspend fun addLocalNotification(
        userId: String,
        title: String,
        body: String,
        category: String
    ) {
        val id = "nt_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        dao.upsertNotification(
            NotificationEntity(
                id = id,
                userId = userId,
                title = title,
                body = body,
                category = category,
                readFlag = false,
                syncStatus = SyncStatus.PENDING.name,
                createdAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun markAllNotificationsRead(userId: String) {
        dao.markAllNotificationsRead(userId)
    }

    private suspend fun checkLowStockAlerts(userId: String, items: List<TxLineItem>) {
        items.forEach { line ->
            val p = dao.getProductById(line.productId)
            if (p != null && p.stockQty <= p.minStockAlert) {
                addLocalNotification(
                    userId = userId,
                    title = "স্টক সতর্কতা / Low Stock",
                    body = "${p.name} - বর্তমান স্টক: ${p.stockQty.toInt()}",
                    category = "STOCK_ALERT"
                )
            }
        }
    }

    /**
     * Pulls remote business profile, branches, products, parties, transactions, employees
     * on login / multi-device restore without overwriting unsynced local mutations.
     */
    suspend fun pullFromCloud(userId: String): Result<Unit> =
        try {
            val accountSnap = db.collection("business_accounts").document(userId).get().await()
            if (accountSnap.exists()) {
                val doc = accountSnap.toObject(
                    BusinessAccountDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (doc != null) {
                    val local = dao.getBusinessProfile(userId)
                    dao.upsertBusinessProfile(
                        BusinessProfileEntity(
                            userId = userId,
                            ownerEmail = doc.ownerEmail,
                            shopName = doc.shopName,
                            ownerName = doc.ownerName,
                            phone = doc.phone,
                            address = doc.address,
                            businessType = doc.businessType,
                            logoUri = doc.logoUri ?: local?.logoUri,
                            subscriptionTier = doc.subscriptionTier,
                            subscriptionMonths = doc.subscriptionMonths.toInt(),
                            subscriptionStartMillis = doc.subscriptionStartMillis,
                            subscriptionExpiresAtMillis = doc.subscriptionExpiresAtMillis,
                            blocked = doc.blocked,
                            accountStatus = doc.accountStatus.ifBlank { if (doc.blocked) "BANNED" else "ACTIVE" },
                            banReason = doc.banReason,
                            suspendedUntilMillis = doc.suspendedUntilMillis,
                            lastExpiryWarningDays = local?.lastExpiryWarningDays ?: 0,
                            syncStatus = SyncStatus.SYNCED.name,
                            updatedAtMillis = doc.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                        )
                    )
                }
            }

            // Branches
            val branchesSnap = db.collection("users").document(userId).collection("branches").get().await()
            val pendingBranchIds = dao.getPendingBranches(userId).map { it.id }.toSet()
            branchesSnap.documents.forEach { doc ->
                if (doc.id !in pendingBranchIds) {
                    val d = doc.data ?: return@forEach
                    dao.upsertBranch(
                        BranchEntity(
                            id = doc.id,
                            userId = userId,
                            name = d["name"] as? String ?: "Main",
                            address = d["address"] as? String ?: "",
                            phone = d["phone"] as? String ?: "",
                            mainBranch = d["mainBranch"] as? Boolean ?: false,
                            syncStatus = SyncStatus.SYNCED.name,
                            createdAtMillis = (d["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
                            updatedAtMillis = (d["updatedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: System.currentTimeMillis()
                        )
                    )
                }
            }

            // Products
            val prodSnap = db.collection("users").document(userId).collection("products").get().await()
            val pendingProdIds = dao.getPendingProducts(userId).map { it.id }.toSet()
            prodSnap.documents.forEach { doc ->
                if (doc.id !in pendingProdIds) {
                    val d = doc.data ?: return@forEach
                    dao.upsertProduct(
                        ProductEntity(
                            id = doc.id,
                            userId = userId,
                            branchId = d["branchId"] as? String ?: "",
                            name = d["name"] as? String ?: "",
                            sku = d["sku"] as? String ?: "",
                            category = d["category"] as? String ?: "General",
                            costPrice = (d["costPrice"] as? Number)?.toDouble() ?: 0.0,
                            sellPrice = (d["sellPrice"] as? Number)?.toDouble() ?: 0.0,
                            stockQty = (d["stockQty"] as? Number)?.toDouble() ?: 0.0,
                            minStockAlert = (d["minStockAlert"] as? Number)?.toDouble() ?: 5.0,
                            photoUri = d["photoUri"] as? String,
                            syncStatus = SyncStatus.SYNCED.name
                        )
                    )
                }
            }

            // Parties
            val partySnap = db.collection("users").document(userId).collection("parties").get().await()
            val pendingPartyIds = dao.getPendingParties(userId).map { it.id }.toSet()
            partySnap.documents.forEach { doc ->
                if (doc.id !in pendingPartyIds) {
                    val d = doc.data ?: return@forEach
                    dao.upsertParty(
                        PartyEntity(
                            id = doc.id,
                            userId = userId,
                            branchId = d["branchId"] as? String ?: "",
                            partyType = d["partyType"] as? String ?: "CUSTOMER",
                            name = d["name"] as? String ?: "",
                            phone = d["phone"] as? String ?: "",
                            address = d["address"] as? String ?: "",
                            totalAmount = (d["totalAmount"] as? Number)?.toDouble() ?: 0.0,
                            paidAmount = (d["paidAmount"] as? Number)?.toDouble() ?: 0.0,
                            dueAmount = (d["dueAmount"] as? Number)?.toDouble() ?: 0.0,
                            syncStatus = SyncStatus.SYNCED.name
                        )
                    )
                }
            }

            // Transactions
            val txSnap = db.collection("users").document(userId).collection("transactions").get().await()
            val pendingTxIds = dao.getPendingTransactions(userId).map { it.id }.toSet()
            txSnap.documents.forEach { doc ->
                if (doc.id !in pendingTxIds) {
                    val d = doc.data ?: return@forEach
                    dao.upsertTransaction(
                        TransactionEntity(
                            id = doc.id,
                            userId = userId,
                            branchId = d["branchId"] as? String ?: "",
                            txType = d["txType"] as? String ?: "SALE",
                            partyId = d["partyId"] as? String ?: "",
                            partyName = d["partyName"] as? String ?: "",
                            subtotal = (d["subtotal"] as? Number)?.toDouble() ?: 0.0,
                            discount = (d["discount"] as? Number)?.toDouble() ?: 0.0,
                            totalAmount = (d["totalAmount"] as? Number)?.toDouble() ?: 0.0,
                            paidAmount = (d["paidAmount"] as? Number)?.toDouble() ?: 0.0,
                            dueAmount = (d["dueAmount"] as? Number)?.toDouble() ?: 0.0,
                            cogsTotal = (d["cogsTotal"] as? Number)?.toDouble() ?: 0.0,
                            category = d["category"] as? String ?: "",
                            note = d["note"] as? String ?: "",
                            status = d["status"] as? String ?: "COMPLETED",
                            operatorName = d["operatorName"] as? String ?: "Owner",
                            lineItemsJson = d["lineItemsJson"] as? String ?: "[]",
                            txDateMillis = (d["txDateMillis"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                            syncStatus = SyncStatus.SYNCED.name
                        )
                    )
                }
            }

            // Employees
            val empSnap = db.collection("users").document(userId).collection("employees").get().await()
            val pendingEmpIds = dao.getPendingEmployees(userId).map { it.id }.toSet()
            empSnap.documents.forEach { doc ->
                if (doc.id !in pendingEmpIds) {
                    val d = doc.data ?: return@forEach
                    dao.upsertEmployee(
                        EmployeeEntity(
                            id = doc.id,
                            userId = userId,
                            name = d["name"] as? String ?: "",
                            phone = d["phone"] as? String ?: "",
                            role = d["role"] as? String ?: "EMPLOYEE",
                            pinCode = d["pinCode"] as? String ?: "1234",
                            branchId = d["branchId"] as? String ?: "",
                            canSell = d["canSell"] as? Boolean ?: true,
                            canPurchase = d["canPurchase"] as? Boolean ?: false,
                            canViewReports = d["canViewReports"] as? Boolean ?: false,
                            canManageStock = d["canManageStock"] as? Boolean ?: false,
                            monthlySalary = (d["monthlySalary"] as? Number)?.toDouble() ?: 0.0,
                            active = d["active"] as? Boolean ?: true,
                            syncStatus = SyncStatus.SYNCED.name
                        )
                    )
                }
            }

            // Notifications (including Admin payment approval/rejection alerts)
            val notifSnap = db.collection("users").document(userId).collection("notifications").get().await()
            val pendingNotifIds = dao.getPendingNotifications(userId).map { it.id }.toSet()
            notifSnap.documents.forEach { doc ->
                if (doc.id !in pendingNotifIds) {
                    val d = doc.data ?: return@forEach
                    dao.upsertNotification(
                        NotificationEntity(
                            id = doc.id,
                            userId = userId,
                            title = d["title"] as? String ?: "",
                            body = d["body"] as? String ?: "",
                            category = d["category"] as? String ?: "GENERAL",
                            readFlag = d["readFlag"] as? Boolean ?: false,
                            syncStatus = SyncStatus.SYNCED.name,
                            createdAtMillis = (d["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: System.currentTimeMillis()
                        )
                    )
                }
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users/$userId")
            Result.failure(e)
        }

    /**
     * Pushes all pending Room records to Cloud Firestore idempotently using deterministic IDs.
     * Safe for offline-first retry: duplicate writes are prevented by fixed document IDs.
     */
    suspend fun syncPendingToCloud(userId: String): SyncSummary {
        var synced = 0
        var failed = 0
        var lastError: String? = null

        // 1. Business Profile
        val profile = dao.getBusinessProfile(userId)
        if (profile != null && profile.syncStatus != SyncStatus.SYNCED.name) {
            val docPath = "business_accounts/$userId"
            try {
                val docRef = db.collection("business_accounts").document(userId)
                val existing = docRef.get().await()
                val doc = BusinessAccountDoc(
                    userId = userId,
                    ownerEmail = profile.ownerEmail,
                    shopName = profile.shopName,
                    ownerName = profile.ownerName,
                    phone = profile.phone,
                    address = profile.address,
                    businessType = profile.businessType,
                    logoUri = profile.logoUri
                )
                if (!existing.exists()) {
                    docRef.set(doc.toCreateMap()).await()
                } else {
                    docRef.update(doc.toProfileUpdateMap()).await()
                }
                dao.upsertBusinessProfile(profile.copy(syncStatus = SyncStatus.SYNCED.name))
                synced++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, docPath)
                dao.upsertBusinessProfile(profile.copy(syncStatus = SyncStatus.FAILED.name))
                failed++
                lastError = e.localizedMessage
            }
        }

        // 2. Branches
        dao.getPendingBranches(userId).forEach { branch ->
            val path = "users/$userId/branches/${branch.id}"
            try {
                val ref = db.collection("users").document(userId).collection("branches").document(branch.id)
                val existing = ref.get().await()
                if (!existing.exists()) {
                    ref.set(
                        mapOf(
                            "userId" to userId,
                            "name" to branch.name.take(100),
                            "address" to branch.address.take(300),
                            "phone" to branch.phone.take(30),
                            "mainBranch" to branch.mainBranch,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    ref.update(
                        mapOf(
                            "name" to branch.name.take(100),
                            "address" to branch.address.take(300),
                            "phone" to branch.phone.take(30),
                            "mainBranch" to branch.mainBranch,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                dao.upsertBranch(branch.copy(syncStatus = SyncStatus.SYNCED.name))
                synced++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, path)
                dao.upsertBranch(branch.copy(syncStatus = SyncStatus.FAILED.name))
                failed++
                lastError = e.localizedMessage
            }
        }

        // 3. Products
        dao.getPendingProducts(userId).forEach { p ->
            val path = "users/$userId/products/${p.id}"
            try {
                val ref = db.collection("users").document(userId).collection("products").document(p.id)
                val existing = ref.get().await()
                if (!existing.exists()) {
                    val map = buildMap<String, Any> {
                        put("userId", userId)
                        put("branchId", p.branchId)
                        put("name", p.name.take(120))
                        put("sku", p.sku.take(64))
                        put("category", p.category.ifBlank { "General" }.take(80))
                        put("costPrice", p.costPrice)
                        put("sellPrice", p.sellPrice)
                        put("stockQty", p.stockQty)
                        put("minStockAlert", p.minStockAlert)
                        if (!p.photoUri.isNullOrBlank()) put("photoUri", p.photoUri.take(1000))
                        put("createdAt", FieldValue.serverTimestamp())
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    ref.set(map).await()
                } else {
                    val map = buildMap<String, Any> {
                        put("branchId", p.branchId)
                        put("name", p.name.take(120))
                        put("sku", p.sku.take(64))
                        put("category", p.category.ifBlank { "General" }.take(80))
                        put("costPrice", p.costPrice)
                        put("sellPrice", p.sellPrice)
                        put("stockQty", p.stockQty)
                        put("minStockAlert", p.minStockAlert)
                        if (!p.photoUri.isNullOrBlank()) put("photoUri", p.photoUri.take(1000))
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    ref.update(map).await()
                }
                dao.upsertProduct(p.copy(syncStatus = SyncStatus.SYNCED.name))
                synced++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, path)
                dao.upsertProduct(p.copy(syncStatus = SyncStatus.FAILED.name))
                failed++
                lastError = e.localizedMessage
            }
        }

        // 4. Parties
        dao.getPendingParties(userId).forEach { party ->
            val path = "users/$userId/parties/${party.id}"
            try {
                val ref = db.collection("users").document(userId).collection("parties").document(party.id)
                val existing = ref.get().await()
                if (!existing.exists()) {
                    ref.set(
                        mapOf(
                            "userId" to userId,
                            "branchId" to party.branchId,
                            "partyType" to party.partyType,
                            "name" to party.name.take(120),
                            "phone" to party.phone.take(30),
                            "address" to party.address.take(300),
                            "totalAmount" to party.totalAmount,
                            "paidAmount" to party.paidAmount,
                            "dueAmount" to party.dueAmount,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    ref.update(
                        mapOf(
                            "branchId" to party.branchId,
                            "name" to party.name.take(120),
                            "phone" to party.phone.take(30),
                            "address" to party.address.take(300),
                            "totalAmount" to party.totalAmount,
                            "paidAmount" to party.paidAmount,
                            "dueAmount" to party.dueAmount,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                dao.upsertParty(party.copy(syncStatus = SyncStatus.SYNCED.name))
                synced++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, path)
                dao.upsertParty(party.copy(syncStatus = SyncStatus.FAILED.name))
                failed++
                lastError = e.localizedMessage
            }
        }

        // 5. Transactions
        dao.getPendingTransactions(userId).forEach { tx ->
            val path = "users/$userId/transactions/${tx.id}"
            try {
                val ref = db.collection("users").document(userId).collection("transactions").document(tx.id)
                val existing = ref.get().await()
                if (!existing.exists()) {
                    ref.set(
                        mapOf(
                            "userId" to userId,
                            "branchId" to tx.branchId,
                            "txType" to tx.txType,
                            "partyId" to tx.partyId.take(128),
                            "partyName" to tx.partyName.take(120),
                            "subtotal" to tx.subtotal,
                            "discount" to tx.discount,
                            "totalAmount" to tx.totalAmount,
                            "paidAmount" to tx.paidAmount,
                            "dueAmount" to tx.dueAmount,
                            "cogsTotal" to tx.cogsTotal,
                            "category" to tx.category.take(80),
                            "note" to tx.note.take(500),
                            "status" to tx.status,
                            "operatorName" to tx.operatorName.ifBlank { "Owner" }.take(100),
                            "lineItemsJson" to tx.lineItemsJson.take(20000),
                            "txDateMillis" to tx.txDateMillis,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else if (existing.getString("status") != "CANCELLED") {
                    ref.update(
                        mapOf(
                            "partyId" to tx.partyId.take(128),
                            "partyName" to tx.partyName.take(120),
                            "subtotal" to tx.subtotal,
                            "discount" to tx.discount,
                            "totalAmount" to tx.totalAmount,
                            "paidAmount" to tx.paidAmount,
                            "dueAmount" to tx.dueAmount,
                            "cogsTotal" to tx.cogsTotal,
                            "category" to tx.category.take(80),
                            "note" to tx.note.take(500),
                            "status" to tx.status,
                            "lineItemsJson" to tx.lineItemsJson.take(20000),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                dao.upsertTransaction(tx.copy(syncStatus = SyncStatus.SYNCED.name, syncError = null))
                synced++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, path)
                dao.upsertTransaction(
                    tx.copy(
                        syncStatus = SyncStatus.FAILED.name,
                        syncError = e.localizedMessage
                    )
                )
                failed++
                lastError = e.localizedMessage
            }
        }

        // 6. Stock Logs (Immutable create-only)
        dao.getPendingStockLogs(userId).forEach { log ->
            val path = "users/$userId/stock_logs/${log.id}"
            try {
                val ref = db.collection("users").document(userId).collection("stock_logs").document(log.id)
                val existing = ref.get().await()
                if (!existing.exists()) {
                    ref.set(
                        mapOf(
                            "userId" to userId,
                            "branchId" to log.branchId,
                            "productId" to log.productId,
                            "productName" to log.productName.take(120),
                            "changeQty" to log.changeQty,
                            "reason" to log.reason.take(120),
                            "referenceTxId" to log.referenceTxId.take(128),
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                dao.upsertStockLog(log.copy(syncStatus = SyncStatus.SYNCED.name))
                synced++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, path)
                dao.upsertStockLog(log.copy(syncStatus = SyncStatus.FAILED.name))
                failed++
                lastError = e.localizedMessage
            }
        }

        // 7. Employees
        dao.getPendingEmployees(userId).forEach { emp ->
            val path = "users/$userId/employees/${emp.id}"
            try {
                val ref = db.collection("users").document(userId).collection("employees").document(emp.id)
                val existing = ref.get().await()
                if (!existing.exists()) {
                    ref.set(
                        mapOf(
                            "userId" to userId,
                            "name" to emp.name.take(100),
                            "phone" to emp.phone.take(30),
                            "role" to emp.role,
                            "pinCode" to emp.pinCode.take(12),
                            "branchId" to emp.branchId,
                            "canSell" to emp.canSell,
                            "canPurchase" to emp.canPurchase,
                            "canViewReports" to emp.canViewReports,
                            "canManageStock" to emp.canManageStock,
                            "monthlySalary" to emp.monthlySalary,
                            "active" to emp.active,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    ref.update(
                        mapOf(
                            "name" to emp.name.take(100),
                            "phone" to emp.phone.take(30),
                            "role" to emp.role,
                            "pinCode" to emp.pinCode.take(12),
                            "branchId" to emp.branchId,
                            "canSell" to emp.canSell,
                            "canPurchase" to emp.canPurchase,
                            "canViewReports" to emp.canViewReports,
                            "canManageStock" to emp.canManageStock,
                            "monthlySalary" to emp.monthlySalary,
                            "active" to emp.active,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                dao.upsertEmployee(emp.copy(syncStatus = SyncStatus.SYNCED.name))
                synced++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, path)
                dao.upsertEmployee(emp.copy(syncStatus = SyncStatus.FAILED.name))
                failed++
                lastError = e.localizedMessage
            }
        }

        return SyncSummary(synced, failed, lastError)
    }
}
