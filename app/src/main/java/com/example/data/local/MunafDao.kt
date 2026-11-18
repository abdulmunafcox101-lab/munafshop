package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.SyncStatus
import com.example.data.model.TransactionType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface MunafDao {

    // --- Business Profile ---
    @Query("SELECT * FROM business_profiles WHERE userId = :userId LIMIT 1")
    fun observeBusinessProfile(userId: String): Flow<BusinessProfileEntity?>

    @Query("SELECT * FROM business_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getBusinessProfile(userId: String): BusinessProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBusinessProfile(profile: BusinessProfileEntity)

    // --- Branches ---
    @Query("SELECT * FROM branches WHERE userId = :userId ORDER BY mainBranch DESC, createdAtMillis ASC")
    fun observeBranches(userId: String): Flow<List<BranchEntity>>

    @Query("SELECT * FROM branches WHERE userId = :userId")
    suspend fun getBranches(userId: String): List<BranchEntity>

    @Query("SELECT * FROM branches WHERE userId = :userId AND syncStatus != 'SYNCED'")
    suspend fun getPendingBranches(userId: String): List<BranchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBranch(branch: BranchEntity)

    // --- Products ---
    @Query("SELECT * FROM products WHERE userId = :userId ORDER BY name ASC")
    fun observeProducts(userId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    suspend fun getProductById(productId: String): ProductEntity?

    @Query("SELECT * FROM products WHERE userId = :userId AND syncStatus != 'SYNCED'")
    suspend fun getPendingProducts(userId: String): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProduct(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :productId AND userId = :userId")
    suspend fun deleteProduct(productId: String, userId: String)

    // --- Parties (Customers & Suppliers) ---
    @Query("SELECT * FROM parties WHERE userId = :userId ORDER BY dueAmount DESC, name ASC")
    fun observeParties(userId: String): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties WHERE id = :partyId LIMIT 1")
    suspend fun getPartyById(partyId: String): PartyEntity?

    @Query("SELECT * FROM parties WHERE userId = :userId AND syncStatus != 'SYNCED'")
    suspend fun getPendingParties(userId: String): List<PartyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertParty(party: PartyEntity)

    // --- Transactions ---
    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY txDateMillis DESC, createdAtMillis DESC")
    fun observeTransactions(userId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :txId LIMIT 1")
    suspend fun getTransactionById(txId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE userId = :userId AND syncStatus != 'SYNCED'")
    suspend fun getPendingTransactions(userId: String): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(tx: TransactionEntity)

    // --- Stock Logs ---
    @Query("SELECT * FROM stock_logs WHERE userId = :userId ORDER BY createdAtMillis DESC")
    fun observeStockLogs(userId: String): Flow<List<StockLogEntity>>

    @Query("SELECT * FROM stock_logs WHERE userId = :userId AND syncStatus != 'SYNCED'")
    suspend fun getPendingStockLogs(userId: String): List<StockLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStockLog(log: StockLogEntity)

    // --- Employees ---
    @Query("SELECT * FROM employees WHERE userId = :userId ORDER BY createdAtMillis DESC")
    fun observeEmployees(userId: String): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees WHERE userId = :userId AND syncStatus != 'SYNCED'")
    suspend fun getPendingEmployees(userId: String): List<EmployeeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEmployee(employee: EmployeeEntity)

    @Query("DELETE FROM employees WHERE id = :employeeId AND userId = :userId")
    suspend fun deleteEmployee(employeeId: String, userId: String)

    // --- Voice Notes ---
    @Query("SELECT * FROM voice_notes WHERE userId = :userId ORDER BY createdAtMillis DESC")
    fun observeVoiceNotes(userId: String): Flow<List<VoiceNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVoiceNote(note: VoiceNoteEntity)

    @Query("DELETE FROM voice_notes WHERE id = :noteId AND userId = :userId")
    suspend fun deleteVoiceNote(noteId: String, userId: String)

    // --- Notifications ---
    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY createdAtMillis DESC")
    fun observeNotifications(userId: String): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE userId = :userId AND syncStatus != 'SYNCED'")
    suspend fun getPendingNotifications(userId: String): List<NotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotification(notification: NotificationEntity)

    @Query("UPDATE notifications SET readFlag = 1, syncStatus = 'PENDING' WHERE userId = :userId")
    suspend fun markAllNotificationsRead(userId: String)

    // --- Atomic Accounting Workflows ---

    @Transaction
    suspend fun recordSaleAtomic(
        tx: TransactionEntity,
        items: List<TxLineItem>
    ) {
        val now = System.currentTimeMillis()
        upsertTransaction(tx)

        items.forEach { line ->
            val product = getProductById(line.productId)
            if (product != null) {
                val updatedStock = (product.stockQty - line.quantity).coerceAtLeast(0.0)
                upsertProduct(
                    product.copy(
                        stockQty = updatedStock,
                        syncStatus = SyncStatus.PENDING.name,
                        updatedAtMillis = now
                    )
                )
                upsertStockLog(
                    StockLogEntity(
                        id = "sl_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                        userId = tx.userId,
                        branchId = tx.branchId,
                        productId = product.id,
                        productName = product.name,
                        changeQty = -line.quantity,
                        reason = "SALE",
                        referenceTxId = tx.id,
                        syncStatus = SyncStatus.PENDING.name,
                        createdAtMillis = now
                    )
                )
            }
        }

        if (tx.partyId.isNotBlank()) {
            val party = getPartyById(tx.partyId)
            if (party != null) {
                val newTotal = party.totalAmount + tx.totalAmount
                val newPaid = party.paidAmount + tx.paidAmount
                val newDue = (newTotal - newPaid).coerceAtLeast(0.0)
                upsertParty(
                    party.copy(
                        totalAmount = newTotal,
                        paidAmount = newPaid,
                        dueAmount = newDue,
                        syncStatus = SyncStatus.PENDING.name,
                        updatedAtMillis = now
                    )
                )
            }
        }
    }

    @Transaction
    suspend fun cancelSaleAtomic(txId: String, userId: String) {
        val tx = getTransactionById(txId) ?: return
        if (tx.userId != userId || tx.status == "CANCELLED") return
        val now = System.currentTimeMillis()

        if (tx.txType == TransactionType.SALE.name) {
            val items = TxLineItem.fromJson(tx.lineItemsJson)
            items.forEach { line ->
                val product = getProductById(line.productId)
                if (product != null) {
                    upsertProduct(
                        product.copy(
                            stockQty = product.stockQty + line.quantity,
                            syncStatus = SyncStatus.PENDING.name,
                            updatedAtMillis = now
                        )
                    )
                    upsertStockLog(
                        StockLogEntity(
                            id = "sl_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                            userId = userId,
                            branchId = tx.branchId,
                            productId = product.id,
                            productName = product.name,
                            changeQty = line.quantity,
                            reason = "SALE_CANCEL",
                            referenceTxId = tx.id,
                            syncStatus = SyncStatus.PENDING.name,
                            createdAtMillis = now
                        )
                    )
                }
            }
            if (tx.partyId.isNotBlank()) {
                val party = getPartyById(tx.partyId)
                if (party != null) {
                    val newTotal = (party.totalAmount - tx.totalAmount).coerceAtLeast(0.0)
                    val newPaid = (party.paidAmount - tx.paidAmount).coerceAtLeast(0.0)
                    val newDue = (newTotal - newPaid).coerceAtLeast(0.0)
                    upsertParty(
                        party.copy(
                            totalAmount = newTotal,
                            paidAmount = newPaid,
                            dueAmount = newDue,
                            syncStatus = SyncStatus.PENDING.name,
                            updatedAtMillis = now
                        )
                    )
                }
            }
        } else if (tx.txType == TransactionType.PURCHASE.name) {
            val items = TxLineItem.fromJson(tx.lineItemsJson)
            items.forEach { line ->
                val product = getProductById(line.productId)
                if (product != null) {
                    upsertProduct(
                        product.copy(
                            stockQty = (product.stockQty - line.quantity).coerceAtLeast(0.0),
                            syncStatus = SyncStatus.PENDING.name,
                            updatedAtMillis = now
                        )
                    )
                    upsertStockLog(
                        StockLogEntity(
                            id = "sl_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                            userId = userId,
                            branchId = tx.branchId,
                            productId = product.id,
                            productName = product.name,
                            changeQty = -line.quantity,
                            reason = "PURCHASE_CANCEL",
                            referenceTxId = tx.id,
                            syncStatus = SyncStatus.PENDING.name,
                            createdAtMillis = now
                        )
                    )
                }
            }
            if (tx.partyId.isNotBlank()) {
                val party = getPartyById(tx.partyId)
                if (party != null) {
                    val newTotal = (party.totalAmount - tx.totalAmount).coerceAtLeast(0.0)
                    val newPaid = (party.paidAmount - tx.paidAmount).coerceAtLeast(0.0)
                    val newDue = (newTotal - newPaid).coerceAtLeast(0.0)
                    upsertParty(
                        party.copy(
                            totalAmount = newTotal,
                            paidAmount = newPaid,
                            dueAmount = newDue,
                            syncStatus = SyncStatus.PENDING.name,
                            updatedAtMillis = now
                        )
                    )
                }
            }
        }

        upsertTransaction(
            tx.copy(
                status = "CANCELLED",
                syncStatus = SyncStatus.PENDING.name,
                updatedAtMillis = now
            )
        )
    }

    @Transaction
    suspend fun recordPurchaseAtomic(
        tx: TransactionEntity,
        items: List<TxLineItem>
    ) {
        val now = System.currentTimeMillis()
        upsertTransaction(tx)

        items.forEach { line ->
            val product = getProductById(line.productId)
            if (product != null) {
                upsertProduct(
                    product.copy(
                        costPrice = if (line.unitPrice > 0) line.unitPrice else product.costPrice,
                        stockQty = product.stockQty + line.quantity,
                        syncStatus = SyncStatus.PENDING.name,
                        updatedAtMillis = now
                    )
                )
                upsertStockLog(
                    StockLogEntity(
                        id = "sl_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                        userId = tx.userId,
                        branchId = tx.branchId,
                        productId = product.id,
                        productName = product.name,
                        changeQty = line.quantity,
                        reason = "PURCHASE",
                        referenceTxId = tx.id,
                        syncStatus = SyncStatus.PENDING.name,
                        createdAtMillis = now
                    )
                )
            }
        }

        if (tx.partyId.isNotBlank()) {
            val party = getPartyById(tx.partyId)
            if (party != null) {
                val newTotal = party.totalAmount + tx.totalAmount
                val newPaid = party.paidAmount + tx.paidAmount
                val newDue = (newTotal - newPaid).coerceAtLeast(0.0)
                upsertParty(
                    party.copy(
                        totalAmount = newTotal,
                        paidAmount = newPaid,
                        dueAmount = newDue,
                        syncStatus = SyncStatus.PENDING.name,
                        updatedAtMillis = now
                    )
                )
            }
        }
    }

    @Transaction
    suspend fun recordPartyPaymentAtomic(tx: TransactionEntity) {
        val now = System.currentTimeMillis()
        upsertTransaction(tx)
        if (tx.partyId.isNotBlank()) {
            val party = getPartyById(tx.partyId)
            if (party != null) {
                val newPaid = party.paidAmount + tx.paidAmount
                val newDue = (party.totalAmount - newPaid).coerceAtLeast(0.0)
                upsertParty(
                    party.copy(
                        paidAmount = newPaid,
                        dueAmount = newDue,
                        syncStatus = SyncStatus.PENDING.name,
                        updatedAtMillis = now
                    )
                )
            }
        }
    }

    @Transaction
    suspend fun adjustStockAtomic(
        product: ProductEntity,
        deltaQty: Double,
        reason: String
    ) {
        val now = System.currentTimeMillis()
        val updatedStock = (product.stockQty + deltaQty).coerceAtLeast(0.0)
        upsertProduct(
            product.copy(
                stockQty = updatedStock,
                syncStatus = SyncStatus.PENDING.name,
                updatedAtMillis = now
            )
        )
        upsertStockLog(
            StockLogEntity(
                id = "sl_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                userId = product.userId,
                branchId = product.branchId,
                productId = product.id,
                productName = product.name,
                changeQty = deltaQty,
                reason = reason,
                referenceTxId = "",
                syncStatus = SyncStatus.PENDING.name,
                createdAtMillis = now
            )
        )
    }

    @Transaction
    suspend fun transferStockAtomic(
        sourceProduct: ProductEntity,
        targetBranchId: String,
        transferQty: Double
    ) {
        val now = System.currentTimeMillis()
        val actualQty = transferQty.coerceAtMost(sourceProduct.stockQty)
        if (actualQty <= 0.0) return

        upsertProduct(
            sourceProduct.copy(
                stockQty = sourceProduct.stockQty - actualQty,
                syncStatus = SyncStatus.PENDING.name,
                updatedAtMillis = now
            )
        )
        upsertStockLog(
            StockLogEntity(
                id = "sl_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                userId = sourceProduct.userId,
                branchId = sourceProduct.branchId,
                productId = sourceProduct.id,
                productName = sourceProduct.name,
                changeQty = -actualQty,
                reason = "TRANSFER_OUT",
                referenceTxId = targetBranchId,
                syncStatus = SyncStatus.PENDING.name,
                createdAtMillis = now
            )
        )

        val newProductId = "prod_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val targetProduct = sourceProduct.copy(
            id = newProductId,
            branchId = targetBranchId,
            stockQty = actualQty,
            syncStatus = SyncStatus.PENDING.name,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        upsertProduct(targetProduct)
        upsertStockLog(
            StockLogEntity(
                id = "sl_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                userId = sourceProduct.userId,
                branchId = targetBranchId,
                productId = targetProduct.id,
                productName = targetProduct.name,
                changeQty = actualQty,
                reason = "TRANSFER_IN",
                referenceTxId = sourceProduct.branchId,
                syncStatus = SyncStatus.PENDING.name,
                createdAtMillis = now
            )
        )
    }
}
