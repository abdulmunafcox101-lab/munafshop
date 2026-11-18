package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class BusinessAccountDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val ownerEmail: String = "",
    val shopName: String = "",
    val ownerName: String = "",
    val phone: String = "",
    val address: String = "",
    val businessType: String = "",
    val logoUri: String? = null,
    val subscriptionTier: String = SubscriptionTier.FREE.name,
    val subscriptionMonths: Long = 0L,
    val subscriptionExpiresAtMillis: Long = 0L,
    val blocked: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        if (ownerEmail.isNotBlank()) put("ownerEmail", ownerEmail.take(200))
        put("shopName", shopName.take(120))
        put("ownerName", ownerName.take(120))
        put("phone", phone.take(30))
        put("address", address.take(300))
        put("businessType", businessType.take(80))
        if (!logoUri.isNullOrBlank()) put("logoUri", logoUri.take(1000))
        put("subscriptionTier", SubscriptionTier.FREE.name)
        put("subscriptionMonths", 0L)
        put("subscriptionExpiresAtMillis", 0L)
        put("blocked", false)
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }

    fun toProfileUpdateMap(): Map<String, Any> = buildMap {
        if (ownerEmail.isNotBlank()) put("ownerEmail", ownerEmail.take(200))
        put("shopName", shopName.take(120))
        put("ownerName", ownerName.take(120))
        put("phone", phone.take(30))
        put("address", address.take(300))
        put("businessType", businessType.take(80))
        if (!logoUri.isNullOrBlank()) put("logoUri", logoUri.take(1000))
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@Keep
@IgnoreExtraProperties
data class SubscriptionPackageDoc(
    @DocumentId val id: String = "",
    val tier: String = SubscriptionTier.FREE.name,
    val nameBn: String = "",
    val nameEn: String = "",
    val monthlyPriceBdt: Double = 0.0,
    val maxBranches: Long = 1L,
    val maxEmployees: Long = 1L,
    val aiEnabled: Boolean = false,
    val exportEnabled: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PaymentRequestDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val shopName: String = "",
    val method: String = PaymentMethod.BKASH.name,
    val senderNumber: String = "",
    val transactionId: String = "",
    val amountBdt: Double = 0.0,
    val packageTier: String = SubscriptionTier.BASIC.name,
    val durationMonths: Long = 1L,
    val screenshotUri: String? = null,
    val status: String = PaymentRequestStatus.PENDING.name,
    val adminNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("userEmail", userEmail.take(200))
        put("shopName", shopName.take(120))
        put("method", method)
        put("senderNumber", senderNumber.take(25))
        put("transactionId", transactionId.take(64))
        put("amountBdt", amountBdt)
        put("packageTier", packageTier)
        put("durationMonths", durationMonths)
        if (!screenshotUri.isNullOrBlank()) put("screenshotUri", screenshotUri.take(1000))
        put("status", PaymentRequestStatus.PENDING.name)
        put("adminNote", "")
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@Keep
@IgnoreExtraProperties
data class AdminUserDoc(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "SUB_ADMIN",
    val canVerifyPayments: Boolean = false,
    val canManageUsers: Boolean = false,
    val canViewLimitedReports: Boolean = true,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AuditLogDoc(
    @DocumentId val id: String = "",
    val actorUid: String = "",
    val actorEmail: String = "",
    val action: String = "",
    val targetId: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class NotificationDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val body: String = "",
    val category: String = "",
    val readFlag: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)
