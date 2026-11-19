package com.example.data.model

import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.max

data class SubscriptionCountdown(
    val tier: SubscriptionTier,
    val startMillis: Long,
    val expiresAtMillis: Long,
    val serverNowMillis: Long,
    val remainingMillis: Long,
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
    val remainingWholeDaysCeil: Long,
    val isPaidActive: Boolean,
    val isExpired: Boolean
) {
    fun formattedBn(): String {
        if (tier == SubscriptionTier.FREE) return "ফ্রি প্ল্যান (মেয়াদ সীমাহীন)"
        if (isExpired) return "মেয়াদ শেষ — ফ্রি প্ল্যানে নেমেছে (ডাটা সুরক্ষিত)"
        return "${days} দিন ${hours} ঘণ্টা ${minutes} মিঃ ${seconds} সেঃ"
    }

    fun formattedEn(): String {
        if (tier == SubscriptionTier.FREE) return "Free Plan (Unlimited)"
        if (isExpired) return "Expired — Downgraded to Free (Data Safe)"
        return "${days}d ${hours}h ${minutes}m ${seconds}s"
    }
}

data class AdminDashboardKpis(
    val totalUsers: Int = 0,
    val activeUsers: Int = 0,
    val suspendedUsers: Int = 0,
    val bannedUsers: Int = 0,
    val freeUsers: Int = 0,
    val paidSubscribers: Int = 0,
    val totalSubscriptionRevenueBdt: Double = 0.0,
    val dailyRevenueBdt: Double = 0.0,
    val monthlyRevenueBdt: Double = 0.0,
    val yearlyRevenueBdt: Double = 0.0,
    val pendingPaymentRequests: Int = 0,
    val newPackageOrdersCount: Int = 0,
    val pendingComplaints: Int = 0,
    val expiringSoonCount: Int = 0
)

object SubscriptionEngine {

    const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L

    /**
     * Authoritative server time calculated using captured offset (`serverTime - deviceTimeAtSync`).
     * Prevents users from extending subscriptions by rolling back the Android system clock.
     */
    fun computeServerNowMillis(
        deviceNowMillis: Long,
        serverOffsetMillis: Long?,
        minTrustedServerFloorMillis: Long = 0L
    ): Long {
        val candidate = if (serverOffsetMillis != null) {
            deviceNowMillis + serverOffsetMillis
        } else {
            deviceNowMillis
        }
        return max(candidate, minTrustedServerFloorMillis)
    }

    fun computeCountdown(
        tierName: String?,
        startMillis: Long,
        expiresAtMillis: Long,
        serverNowMillis: Long
    ): SubscriptionCountdown {
        val tier = SubscriptionTier.fromName(tierName)
        if (tier == SubscriptionTier.FREE) {
            return SubscriptionCountdown(
                tier = SubscriptionTier.FREE,
                startMillis = 0L,
                expiresAtMillis = 0L,
                serverNowMillis = serverNowMillis,
                remainingMillis = 0L,
                days = 0L,
                hours = 0L,
                minutes = 0L,
                seconds = 0L,
                remainingWholeDaysCeil = 0L,
                isPaidActive = false,
                isExpired = false
            )
        }
        if (expiresAtMillis <= 0L) {
            return SubscriptionCountdown(
                tier = tier,
                startMillis = startMillis,
                expiresAtMillis = 0L,
                serverNowMillis = serverNowMillis,
                remainingMillis = 0L,
                days = 0L,
                hours = 0L,
                minutes = 0L,
                seconds = 0L,
                remainingWholeDaysCeil = 0L,
                isPaidActive = false,
                isExpired = true
            )
        }
        val diff = (expiresAtMillis - serverNowMillis).coerceAtLeast(0L)
        val expired = serverNowMillis >= expiresAtMillis
        val totalSec = diff / 1000L
        val days = totalSec / 86400L
        val hours = (totalSec % 86400L) / 3600L
        val minutes = (totalSec % 3600L) / 60L
        val seconds = totalSec % 60L
        val wholeDaysCeil = if (diff <= 0L) 0L else ceil(diff.toDouble() / ONE_DAY_MILLIS.toDouble()).toLong()

        return SubscriptionCountdown(
            tier = tier,
            startMillis = startMillis,
            expiresAtMillis = expiresAtMillis,
            serverNowMillis = serverNowMillis,
            remainingMillis = diff,
            days = days,
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            remainingWholeDaysCeil = wholeDaysCeil,
            isPaidActive = !expired,
            isExpired = expired
        )
    }

    /**
     * Resolves active feature entitlements. Automatically downgrades expired subscriptions to FREE
     * while preserving all user accounting data locally and in cloud.
     */
    fun resolveActivePackage(
        tierName: String?,
        expiresAtMillis: Long,
        serverNowMillis: Long,
        packages: List<SubscriptionPackageDoc>
    ): SubscriptionPackageDoc {
        val tier = SubscriptionTier.fromName(tierName)
        val expired = tier != SubscriptionTier.FREE &&
            (expiresAtMillis <= 0L || serverNowMillis >= expiresAtMillis)
        val effectiveTier = if (expired) SubscriptionTier.FREE.name else tier.name
        return packages.firstOrNull { it.tier == effectiveTier } ?: SubscriptionPackageDoc(
            tier = SubscriptionTier.FREE.name,
            nameBn = SubscriptionTier.FREE.nameBn,
            nameEn = SubscriptionTier.FREE.nameEn,
            monthlyPriceBdt = 0.0,
            price1mBdt = 0.0,
            price3mBdt = 0.0,
            price6mBdt = 0.0,
            price12mBdt = 0.0,
            maxBranches = 1L,
            maxEmployees = 1L,
            aiEnabled = false,
            exportEnabled = false,
            enabled = true
        )
    }

    /**
     * Returns 7, 3, or 1 if the user crossed a reminder threshold and hasn't been notified for it yet.
     * Returns 0 if already expired (uses -1 marker once) or >7 days remain.
     */
    fun evaluateExpiryReminderThreshold(
        countdown: SubscriptionCountdown,
        lastNotifiedThreshold: Int
    ): Int? {
        if (countdown.tier == SubscriptionTier.FREE) return null
        if (countdown.isExpired) {
            return if (lastNotifiedThreshold != -1) -1 else null
        }
        val remDays = countdown.remainingWholeDaysCeil
        val targetThreshold = when {
            remDays <= 1L -> 1
            remDays <= 3L -> 3
            remDays <= 7L -> 7
            else -> return null
        }
        return if (lastNotifiedThreshold == 0 || lastNotifiedThreshold > targetThreshold) {
            targetThreshold
        } else {
            null
        }
    }

    /**
     * Computes new start and expiry timestamps using server time.
     * If user already has an active unexpired subscription of the same tier, extends from current expiry!
     */
    fun computeRenewalWindow(
        serverNowMillis: Long,
        existingTierName: String?,
        existingStartMillis: Long,
        existingExpiresAtMillis: Long,
        newTier: SubscriptionTier,
        durationMonths: Int
    ): Pair<Long, Long> {
        val cleanMonths = durationMonths.coerceIn(1, 24)
        val durationMillis = cleanMonths.toLong() * 30L * ONE_DAY_MILLIS
        val isExtendingSameActive = existingTierName == newTier.name && existingExpiresAtMillis > serverNowMillis
        val start = if (isExtendingSameActive && existingStartMillis > 0L) existingStartMillis else serverNowMillis
        val anchor = if (isExtendingSameActive) existingExpiresAtMillis else serverNowMillis
        return start to (anchor + durationMillis)
    }

    fun normalizeTrxId(raw: String): String =
        raw.trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "")

    fun computeAdminDashboardKpis(
        users: List<BusinessAccountDoc>,
        payments: List<PaymentRequestDoc>,
        complaints: List<ComplaintTicketDoc>,
        serverNowMillis: Long
    ): AdminDashboardKpis {
        var activeUsers = 0
        var suspendedUsers = 0
        var bannedUsers = 0
        var freeUsers = 0
        var paidSubscribers = 0
        var expiringSoonCount = 0

        users.forEach { acc ->
            when (acc.resolvedStatus()) {
                AccountModerationStatus.SUSPENDED -> suspendedUsers++
                AccountModerationStatus.BANNED -> bannedUsers++
                AccountModerationStatus.ACTIVE -> activeUsers++
            }
            val countdown = computeCountdown(
                tierName = acc.subscriptionTier,
                startMillis = acc.subscriptionStartMillis,
                expiresAtMillis = acc.subscriptionExpiresAtMillis,
                serverNowMillis = serverNowMillis
            )
            if (countdown.isPaidActive) {
                paidSubscribers++
                if (countdown.remainingMillis <= 7L * ONE_DAY_MILLIS) {
                    expiringSoonCount++
                }
            } else {
                freeUsers++
            }
        }

        val dayStart = startOfDayMillis(serverNowMillis)
        val monthStart = startOfMonthMillis(serverNowMillis)
        val yearStart = startOfYearMillis(serverNowMillis)

        var totalRevenue = 0.0
        var dailyRevenue = 0.0
        var monthlyRevenue = 0.0
        var yearlyRevenue = 0.0
        var pendingCount = 0
        var newOrdersToday = 0

        payments.forEach { req ->
            val createdMs = req.createdAt?.toDate()?.time ?: 0L
            if (req.status == PaymentRequestStatus.PENDING.name) {
                pendingCount++
            }
            if (createdMs >= dayStart) {
                newOrdersToday++
            }
            if (req.status == PaymentRequestStatus.APPROVED.name) {
                val revTime = if (req.reviewedAtMillis > 0L) req.reviewedAtMillis else (req.updatedAt?.toDate()?.time ?: createdMs)
                totalRevenue += req.amountBdt
                if (revTime >= dayStart) dailyRevenue += req.amountBdt
                if (revTime >= monthStart) monthlyRevenue += req.amountBdt
                if (revTime >= yearStart) yearlyRevenue += req.amountBdt
            }
        }

        val pendingComplaints = complaints.count {
            it.status == ComplaintStatus.PENDING.name || it.status == ComplaintStatus.IN_PROGRESS.name
        }

        return AdminDashboardKpis(
            totalUsers = users.size,
            activeUsers = activeUsers,
            suspendedUsers = suspendedUsers,
            bannedUsers = bannedUsers,
            freeUsers = freeUsers,
            paidSubscribers = paidSubscribers,
            totalSubscriptionRevenueBdt = totalRevenue,
            dailyRevenueBdt = dailyRevenue,
            monthlyRevenueBdt = monthlyRevenue,
            yearlyRevenueBdt = yearlyRevenue,
            pendingPaymentRequests = pendingCount,
            newPackageOrdersCount = newOrdersToday,
            pendingComplaints = pendingComplaints,
            expiringSoonCount = expiringSoonCount
        )
    }

    private fun startOfDayMillis(nowMillis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfMonthMillis(nowMillis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfYearMillis(nowMillis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
