package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.SyncStatus
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "business_profiles")
data class BusinessProfileEntity(
    @PrimaryKey val userId: String,
    val ownerEmail: String,
    val shopName: String,
    val ownerName: String,
    val phone: String,
    val address: String,
    val businessType: String,
    val logoUri: String? = null,
    val subscriptionTier: String = "FREE",
    val subscriptionMonths: Int = 0,
    val subscriptionStartMillis: Long = 0L,
    val subscriptionExpiresAtMillis: Long = 0L,
    val blocked: Boolean = false,
    val accountStatus: String = "ACTIVE",
    val banReason: String = "",
    val suspendedUntilMillis: Long = 0L,
    val lastExpiryWarningDays: Int = 0,
    val syncStatus: String = SyncStatus.PENDING.name,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "branches")
data class BranchEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val address: String,
    val phone: String,
    val mainBranch: Boolean = false,
    val syncStatus: String = SyncStatus.PENDING.name,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val branchId: String,
    val name: String,
    val sku: String,
    val category: String,
    val costPrice: Double,
    val sellPrice: Double,
    val stockQty: Double,
    val minStockAlert: Double,
    val photoUri: String? = null,
    val syncStatus: String = SyncStatus.PENDING.name,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "parties")
data class PartyEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val branchId: String,
    val partyType: String, // CUSTOMER or SUPPLIER
    val name: String,
    val phone: String,
    val address: String,
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val dueAmount: Double = 0.0,
    val syncStatus: String = SyncStatus.PENDING.name,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val branchId: String,
    val txType: String, // SALE, PURCHASE, DUE_COLLECTION, SUPPLIER_PAYMENT, INCOME, EXPENSE
    val partyId: String = "",
    val partyName: String = "",
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val dueAmount: Double = 0.0,
    val cogsTotal: Double = 0.0,
    val category: String = "",
    val note: String = "",
    val status: String = "COMPLETED", // COMPLETED or CANCELLED
    val operatorName: String = "Owner",
    val lineItemsJson: String = "[]",
    val txDateMillis: Long = System.currentTimeMillis(),
    val syncStatus: String = SyncStatus.PENDING.name,
    val syncError: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "stock_logs")
data class StockLogEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val branchId: String,
    val productId: String,
    val productName: String,
    val changeQty: Double,
    val reason: String,
    val referenceTxId: String = "",
    val syncStatus: String = SyncStatus.PENDING.name,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "employees")
data class EmployeeEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val phone: String,
    val role: String, // MANAGER or EMPLOYEE
    val pinCode: String,
    val branchId: String,
    val canSell: Boolean = true,
    val canPurchase: Boolean = false,
    val canViewReports: Boolean = false,
    val canManageStock: Boolean = false,
    val monthlySalary: Double = 0.0,
    val active: Boolean = true,
    val syncStatus: String = SyncStatus.PENDING.name,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "voice_notes")
data class VoiceNoteEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val filePath: String,
    val durationSec: Int,
    val linkedPartyId: String = "",
    val linkedTxId: String = "",
    val linkedLabel: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val body: String,
    val category: String,
    val readFlag: Boolean = false,
    val syncStatus: String = SyncStatus.PENDING.name,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class TxLineItem(
    val productId: String,
    val productName: String,
    val quantity: Double,
    val unitPrice: Double,
    val unitCost: Double
) {
    val lineTotal: Double get() = quantity * unitPrice

    companion object {
        fun toJson(items: List<TxLineItem>): String {
            val arr = JSONArray()
            items.forEach { item ->
                arr.put(
                    JSONObject().apply {
                        put("productId", item.productId)
                        put("productName", item.productName)
                        put("quantity", item.quantity)
                        put("unitPrice", item.unitPrice)
                        put("unitCost", item.unitCost)
                    }
                )
            }
            return arr.toString()
        }

        fun fromJson(json: String): List<TxLineItem> {
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { idx ->
                    val obj = arr.getJSONObject(idx)
                    TxLineItem(
                        productId = obj.optString("productId"),
                        productName = obj.optString("productName"),
                        quantity = obj.optDouble("quantity", 0.0),
                        unitPrice = obj.optDouble("unitPrice", 0.0),
                        unitCost = obj.optDouble("unitCost", 0.0)
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
