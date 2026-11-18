package com.example.i18n

import com.example.data.model.AppLanguage

fun tr(lang: AppLanguage, bn: String, en: String): String =
    if (lang == AppLanguage.BN) bn else en

object Strings {
    fun appTitle(lang: AppLanguage) = "MUNAF ONLINE SHOP"
    fun tagline(lang: AppLanguage) = tr(
        lang,
        "স্মার্ট ব্যবসার ডিজিটাল খাতা ও হিসাব",
        "Smart Business Ledger & Accounting"
    )
    fun signInWithGoogle(lang: AppLanguage) = tr(lang, "Google দিয়ে লগইন করুন", "Sign in with Google")
    fun navDashboard(lang: AppLanguage) = tr(lang, "হোম", "Home")
    fun navTransactions(lang: AppLanguage) = tr(lang, "বেচাকেনা", "Trade")
    fun navKhata(lang: AppLanguage) = tr(lang, "খাতা", "Khata")
    fun navStock(lang: AppLanguage) = tr(lang, "স্টক", "Stock")
    fun navMore(lang: AppLanguage) = tr(lang, "আরও", "More")

    fun todaySales(lang: AppLanguage) = tr(lang, "আজকের বিক্রি", "Today's Sales")
    fun todayPurchase(lang: AppLanguage) = tr(lang, "আজকের ক্রয়", "Today's Purchase")
    fun todayExpense(lang: AppLanguage) = tr(lang, "আজকের খরচ", "Today's Expense")
    fun totalReceivable(lang: AppLanguage) = tr(lang, "মোট পাওনা (বাকি)", "Total Receivable")
    fun totalPayable(lang: AppLanguage) = tr(lang, "মোট দেনা", "Total Payable")
    fun todayProfit(lang: AppLanguage) = tr(lang, "আজকের লাভ", "Today's Profit")
    fun monthlyProfit(lang: AppLanguage) = tr(lang, "মাসিক লাভ", "Monthly Profit")
    fun cashBalance(lang: AppLanguage) = tr(lang, "মোট ক্যাশ ব্যালেন্স", "Cash Balance")

    fun newSale(lang: AppLanguage) = tr(lang, "নতুন বিক্রি", "New Sale")
    fun newPurchase(lang: AppLanguage) = tr(lang, "নতুন ক্রয়", "New Purchase")
    fun collectDue(lang: AppLanguage) = tr(lang, "বাকি আদায়", "Collect Due")
    fun addExpense(lang: AppLanguage) = tr(lang, "খরচ যোগ", "Add Expense")
    fun addIncome(lang: AppLanguage) = tr(lang, "অন্যান্য আয়", "Other Income")
    fun voiceAccounting(lang: AppLanguage) = tr(lang, "ভয়েস হিসাব", "Voice Entry")
    fun aiAssistant(lang: AppLanguage) = tr(lang, "AI সহায়ক", "AI Assistant")
    fun reports(lang: AppLanguage) = tr(lang, "রিপোর্ট", "Reports")

    fun save(lang: AppLanguage) = tr(lang, "সংরক্ষণ করুন", "Save")
    fun cancel(lang: AppLanguage) = tr(lang, "বাতিল", "Cancel")
    fun confirm(lang: AppLanguage) = tr(lang, "নিশ্চিত করুন", "Confirm")
    fun close(lang: AppLanguage) = tr(lang, "বন্ধ করুন", "Close")
    fun allBranches(lang: AppLanguage) = tr(lang, "সব শাখা", "All Branches")
    fun offlinePending(lang: AppLanguage) = tr(lang, "অফলাইন সিঙ্ক বাকি", "Pending Sync")
    fun synced(lang: AppLanguage) = tr(lang, "সিঙ্ক সম্পন্ন", "Synced")
}
