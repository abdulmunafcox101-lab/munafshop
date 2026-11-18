package com.example.data.model

enum class AppLanguage(val code: String, val label: String) {
    BN("bn", "বাংলা"),
    EN("en", "English");

    companion object {
        fun fromCode(code: String?): AppLanguage = entries.firstOrNull { it.code == code } ?: BN
    }
}

enum class AppThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromName(raw: String?): AppThemeMode = entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

enum class SubscriptionTier(
    val level: Int,
    val nameBn: String,
    val nameEn: String,
    val defaultPriceBdt: Double,
    val defaultMaxBranches: Int,
    val defaultMaxEmployees: Int,
    val aiIncluded: Boolean,
    val exportIncluded: Boolean
) {
    FREE(0, "ফ্রি", "Free", 0.0, 1, 1, false, false),
    BASIC(1, "বেসিক", "Basic", 299.0, 2, 3, true, true),
    PREMIUM(2, "প্রিমিয়াম", "Premium", 599.0, 5, 10, true, true),
    BUSINESS(3, "বিজনেস", "Business", 1199.0, 50, 100, true, true);

    companion object {
        fun fromName(raw: String?): SubscriptionTier = entries.firstOrNull { it.name == raw } ?: FREE
    }
}

enum class TransactionType(val labelBn: String, val labelEn: String) {
    SALE("বিক্রি", "Sale"),
    PURCHASE("ক্রয়", "Purchase"),
    DUE_COLLECTION("বাকি আদায়", "Due Collection"),
    SUPPLIER_PAYMENT("সাপ্লায়ার পরিশোধ", "Supplier Payment"),
    INCOME("অন্যান্য আয়", "Other Income"),
    EXPENSE("খরচ", "Expense");

    companion object {
        fun fromName(raw: String?): TransactionType = entries.firstOrNull { it.name == raw } ?: SALE
    }
}

enum class PartyType(val labelBn: String, val labelEn: String) {
    CUSTOMER("কাস্টমার", "Customer"),
    SUPPLIER("সাপ্লায়ার", "Supplier");

    companion object {
        fun fromName(raw: String?): PartyType = entries.firstOrNull { it.name == raw } ?: CUSTOMER
    }
}

enum class EmployeeRole(val labelBn: String, val labelEn: String) {
    OWNER("মালিক", "Owner"),
    MANAGER("ম্যানেজার", "Manager"),
    EMPLOYEE("কর্মচারী", "Employee");

    companion object {
        fun fromName(raw: String?): EmployeeRole = entries.firstOrNull { it.name == raw } ?: EMPLOYEE
    }
}

enum class PaymentMethod(val labelBn: String, val labelEn: String) {
    BKASH("বিকাশ (bKash)", "bKash"),
    NAGAD("নগদ (Nagad)", "Nagad");

    companion object {
        fun fromName(raw: String?): PaymentMethod = entries.firstOrNull { it.name == raw } ?: BKASH
    }
}

enum class PaymentRequestStatus(val labelBn: String, val labelEn: String) {
    PENDING("অপেক্ষমান", "Pending"),
    APPROVED("অনুমোদিত", "Approved"),
    REJECTED("বাতিল", "Rejected");

    companion object {
        fun fromName(raw: String?): PaymentRequestStatus = entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class SyncStatus {
    PENDING, SYNCED, FAILED;

    companion object {
        fun fromName(raw: String?): SyncStatus = entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class ReportPeriod(val labelBn: String, val labelEn: String) {
    TODAY("আজ", "Today"),
    WEEK("সপ্তাহ", "Week"),
    MONTH("মাস", "Month"),
    YEAR("বছর", "Year"),
    ALL("সব", "All Time")
}
