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
    val subscriptionStartMillis: Long = 0L,
    val subscriptionExpiresAtMillis: Long = 0L,
    val blocked: Boolean = false,
    val accountStatus: String = AccountModerationStatus.ACTIVE.name,
    val banReason: String = "",
    val suspendedUntilMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedStatus(): AccountModerationStatus =
        AccountModerationStatus.fromName(accountStatus, blockedFallback = blocked)

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
        put("subscriptionStartMillis", 0L)
        put("subscriptionExpiresAtMillis", 0L)
        put("blocked", false)
        put("accountStatus", AccountModerationStatus.ACTIVE.name)
        put("banReason", "")
        put("suspendedUntilMillis", 0L)
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
    val price1mBdt: Double = 0.0,
    val price3mBdt: Double = 0.0,
    val price6mBdt: Double = 0.0,
    val price12mBdt: Double = 0.0,
    val maxBranches: Long = 1L,
    val maxEmployees: Long = 1L,
    val aiEnabled: Boolean = false,
    val exportEnabled: Boolean = false,
    val enabled: Boolean = true,
    val featuresSummaryBn: String = "",
    val featuresSummaryEn: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun priceForMonths(months: Int): Double {
        val explicit = when (months) {
            1 -> price1mBdt
            3 -> price3mBdt
            6 -> price6mBdt
            12 -> price12mBdt
            else -> 0.0
        }
        if (explicit > 0.0) return explicit
        if (monthlyPriceBdt <= 0.0) return 0.0
        val factor = when (months) {
            3 -> 0.90
            6 -> 0.85
            12 -> 0.75
            else -> 1.0
        }
        return kotlin.math.round(monthlyPriceBdt * months * factor)
    }
}

@Keep
@IgnoreExtraProperties
data class PackagePriceHistoryDoc(
    @DocumentId val id: String = "",
    val tier: String = "",
    val actorUid: String = "",
    val actorEmail: String = "",
    val previousMonthlyBdt: Double = 0.0,
    val newMonthlyBdt: Double = 0.0,
    val price1mBdt: Double = 0.0,
    val price3mBdt: Double = 0.0,
    val price6mBdt: Double = 0.0,
    val price12mBdt: Double = 0.0,
    val maxBranches: Long = 1L,
    val maxEmployees: Long = 1L,
    val packageEnabled: Boolean = true,
    val summary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PaymentSettingsDoc(
    @DocumentId val id: String = "",
    val bkashAccountName: String = "MUNAF ONLINE SHOP",
    val bkashNumber: String = "01700-000000",
    val bkashEnabled: Boolean = true,
    val nagadAccountName: String = "MUNAF ONLINE SHOP",
    val nagadNumber: String = "01800-000000",
    val nagadEnabled: Boolean = true,
    val instructionsBn: String = "Send Money করে সঠিক Sender নম্বর ও Transaction ID (TrxID) লিখে জমা দিন।",
    val instructionsEn: String = "Send Money to the active number, then submit your Sender number and exact Transaction ID (TrxID).",
    val updatedByEmail: String = "",
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
    val lockedUnitMonthlyBdt: Double = 0.0,
    val packageTier: String = SubscriptionTier.BASIC.name,
    val durationMonths: Long = 1L,
    val screenshotUri: String? = null,
    val status: String = PaymentRequestStatus.PENDING.name,
    val adminNote: String = "",
    val reviewedByEmail: String = "",
    val reviewedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("userEmail", userEmail.take(200))
        put("shopName", shopName.take(120))
        put("method", method)
        put("senderNumber", senderNumber.take(25))
        put("transactionId", transactionId.trim().uppercase().take(64))
        put("amountBdt", amountBdt)
        put("lockedUnitMonthlyBdt", lockedUnitMonthlyBdt)
        put("packageTier", packageTier)
        put("durationMonths", durationMonths)
        if (!screenshotUri.isNullOrBlank()) put("screenshotUri", screenshotUri.take(1000))
        put("status", PaymentRequestStatus.PENDING.name)
        put("adminNote", "")
        put("reviewedByEmail", "")
        put("reviewedAtMillis", 0L)
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@Keep
@IgnoreExtraProperties
data class SubscriptionRenewalDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val shopName: String = "",
    val paymentRequestId: String = "",
    val packageTier: String = SubscriptionTier.BASIC.name,
    val durationMonths: Long = 1L,
    val amountBdt: Double = 0.0,
    val startedAtMillis: Long = 0L,
    val expiresAtMillis: Long = 0L,
    val approvedByEmail: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ComplaintTicketDoc(
    @DocumentId val id: String = "",
    val ticketCode: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val shopName: String = "",
    val subject: String = "",
    val description: String = "",
    val attachmentUri: String? = null,
    val status: String = ComplaintStatus.PENDING.name,
    val lastReplyText: String = "",
    val lastReplyBy: String = "",
    val lastReplyAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ComplaintReplyDoc(
    @DocumentId val id: String = "",
    val ticketId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderRole: String = "CUSTOMER",
    val message: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ModerationHistoryDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val shopName: String = "",
    val action: String = "SUSPEND", // SUSPEND, BAN, UNBAN
    val reason: String = "",
    val suspendedUntilMillis: Long = 0L,
    val actorUid: String = "",
    val actorEmail: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class EmailOutboxDoc(
    @DocumentId val id: String = "",
    val recipientEmail: String = "",
    val emailType: String = "", // ORDER_ALERT_OWNER, PAYMENT_APPROVED, PAYMENT_REJECTED, COMPLAINT_REPLY
    val subject: String = "",
    val bodyPreview: String = "",
    val relatedUserId: String = "",
    val relatedOrderId: String = "",
    val status: String = EmailOutboxStatus.PENDING_BACKEND_CONFIG.name,
    val statusNote: String = "Queued in Firestore email_outbox. Requires Firebase Trigger Email / Cloud Function backend to dispatch SMTP.",
    @ServerTimestamp val createdAt: Timestamp? = null
)

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
    val canReplyComplaints: Boolean = false,
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
