package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminDashboardKpis
import com.example.data.model.AdminUserDoc
import com.example.data.model.AppLanguage
import com.example.data.model.AuditLogDoc
import com.example.data.model.BusinessAccountDoc
import com.example.data.model.ComplaintReplyDoc
import com.example.data.model.ComplaintStatus
import com.example.data.model.ComplaintTicketDoc
import com.example.data.model.EmailOutboxDoc
import com.example.data.model.ModerationHistoryDoc
import com.example.data.model.PackagePriceHistoryDoc
import com.example.data.model.PaymentRequestDoc
import com.example.data.model.PaymentSettingsDoc
import com.example.data.model.SubscriptionPackageDoc
import com.example.i18n.Strings
import com.example.i18n.tr

private enum class AdminTab(val labelBn: String, val labelEn: String, val icon: ImageVector) {
    OVERVIEW("ড্যাশবোর্ড", "Overview", Icons.Default.Dashboard),
    PAYMENTS("পেমেন্ট", "Orders", Icons.Default.Payments),
    USERS("ইউজার", "Users", Icons.Default.Group),
    COMPLAINTS("অভিযোগ", "Complaints", Icons.Default.SupportAgent),
    PACKAGES("প্যাকেজ", "Packages", Icons.Default.Inventory),
    PAY_SETTINGS("বিকাশ/নগদ", "Pay Config", Icons.Default.Payment),
    TEAM_LOGS("টিম ও লগ", "Admins/Logs", Icons.Default.AdminPanelSettings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    language: AppLanguage,
    adminProfile: AdminUserDoc,
    kpis: AdminDashboardKpis,
    serverNowMillis: Long,
    allUsers: List<BusinessAccountDoc>,
    allPayments: List<PaymentRequestDoc>,
    allComplaints: List<ComplaintTicketDoc>,
    selectedReplies: List<ComplaintReplyDoc>,
    moderationHistory: List<ModerationHistoryDoc>,
    packages: List<SubscriptionPackageDoc>,
    priceHistory: List<PackagePriceHistoryDoc>,
    paymentSettings: PaymentSettingsDoc,
    emailOutbox: List<EmailOutboxDoc>,
    subAdmins: List<AdminUserDoc>,
    auditLogs: List<AuditLogDoc>,
    onSelectComplaintTicket: (String?) -> Unit,
    onReviewPayment: (PaymentRequestDoc, Boolean, String) -> Unit,
    onModerateUser: (BusinessAccountDoc, String, String, Int) -> Unit,
    onReplyComplaint: (ComplaintTicketDoc, String, ComplaintStatus) -> Unit,
    onUpdatePackage: (SubscriptionPackageDoc, Double) -> Unit,
    onSavePaymentSettings: (PaymentSettingsDoc) -> Unit,
    onSaveSubAdmin: (String, String, String, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val isSuper = adminProfile.role == "SUPER_ADMIN"
    val visibleTabs = remember(adminProfile) {
        buildList {
            add(AdminTab.OVERVIEW)
            if (isSuper || adminProfile.canVerifyPayments) add(AdminTab.PAYMENTS)
            if (isSuper || adminProfile.canManageUsers || adminProfile.canViewLimitedReports) add(AdminTab.USERS)
            if (isSuper || adminProfile.canReplyComplaints) add(AdminTab.COMPLAINTS)
            if (isSuper) {
                add(AdminTab.PACKAGES)
                add(AdminTab.PAY_SETTINGS)
                add(AdminTab.TEAM_LOGS)
            }
        }
    }
    var activeTab by remember(visibleTabs) { mutableStateOf(visibleTabs.first()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSuper) tr(language, "সুপার অ্যাডমিন প্যানেল", "Super Admin Control Panel")
                        else tr(language, "সাব-অ্যাডমিন প্যানেল", "Sub-Admin Panel")
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("admin_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr(language, "পিছনে", "Back"))
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val useRail = maxWidth >= 720.dp
            if (useRail) {
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail {
                        visibleTabs.forEach { tab ->
                            NavigationRailItem(
                                selected = activeTab == tab,
                                onClick = { activeTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.labelEn) },
                                label = { Text(if (language == AppLanguage.BN) tab.labelBn else tab.labelEn) },
                                modifier = Modifier.testTag("admin_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                        AdminTabBody(
                            activeTab = activeTab,
                            language = language,
                            adminProfile = adminProfile,
                            kpis = kpis,
                            serverNowMillis = serverNowMillis,
                            allUsers = allUsers,
                            allPayments = allPayments,
                            allComplaints = allComplaints,
                            selectedReplies = selectedReplies,
                            moderationHistory = moderationHistory,
                            packages = packages,
                            priceHistory = priceHistory,
                            paymentSettings = paymentSettings,
                            emailOutbox = emailOutbox,
                            subAdmins = subAdmins,
                            auditLogs = auditLogs,
                            onSelectComplaintTicket = onSelectComplaintTicket,
                            onReviewPayment = onReviewPayment,
                            onModerateUser = onModerateUser,
                            onReplyComplaint = onReplyComplaint,
                            onUpdatePackage = onUpdatePackage,
                            onSavePaymentSettings = onSavePaymentSettings,
                            onSaveSubAdmin = onSaveSubAdmin
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        visibleTabs.forEach { tab ->
                            FilterChip(
                                selected = activeTab == tab,
                                onClick = { activeTab = tab },
                                label = { Text(if (language == AppLanguage.BN) tab.labelBn else tab.labelEn) },
                                modifier = Modifier.testTag("admin_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                        AdminTabBody(
                            activeTab = activeTab,
                            language = language,
                            adminProfile = adminProfile,
                            kpis = kpis,
                            serverNowMillis = serverNowMillis,
                            allUsers = allUsers,
                            allPayments = allPayments,
                            allComplaints = allComplaints,
                            selectedReplies = selectedReplies,
                            moderationHistory = moderationHistory,
                            packages = packages,
                            priceHistory = priceHistory,
                            paymentSettings = paymentSettings,
                            emailOutbox = emailOutbox,
                            subAdmins = subAdmins,
                            auditLogs = auditLogs,
                            onSelectComplaintTicket = onSelectComplaintTicket,
                            onReviewPayment = onReviewPayment,
                            onModerateUser = onModerateUser,
                            onReplyComplaint = onReplyComplaint,
                            onUpdatePackage = onUpdatePackage,
                            onSavePaymentSettings = onSavePaymentSettings,
                            onSaveSubAdmin = onSaveSubAdmin
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminTabBody(
    activeTab: AdminTab,
    language: AppLanguage,
    adminProfile: AdminUserDoc,
    kpis: AdminDashboardKpis,
    serverNowMillis: Long,
    allUsers: List<BusinessAccountDoc>,
    allPayments: List<PaymentRequestDoc>,
    allComplaints: List<ComplaintTicketDoc>,
    selectedReplies: List<ComplaintReplyDoc>,
    moderationHistory: List<ModerationHistoryDoc>,
    packages: List<SubscriptionPackageDoc>,
    priceHistory: List<PackagePriceHistoryDoc>,
    paymentSettings: PaymentSettingsDoc,
    emailOutbox: List<EmailOutboxDoc>,
    subAdmins: List<AdminUserDoc>,
    auditLogs: List<AuditLogDoc>,
    onSelectComplaintTicket: (String?) -> Unit,
    onReviewPayment: (PaymentRequestDoc, Boolean, String) -> Unit,
    onModerateUser: (BusinessAccountDoc, String, String, Int) -> Unit,
    onReplyComplaint: (ComplaintTicketDoc, String, ComplaintStatus) -> Unit,
    onUpdatePackage: (SubscriptionPackageDoc, Double) -> Unit,
    onSavePaymentSettings: (PaymentSettingsDoc) -> Unit,
    onSaveSubAdmin: (String, String, String, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit
) {
    val isSuper = adminProfile.role == "SUPER_ADMIN"
    when (activeTab) {
        AdminTab.OVERVIEW -> AdminDashboardTab(
            language = language,
            adminProfile = adminProfile,
            kpis = kpis,
            emailOutbox = emailOutbox
        )
        AdminTab.PAYMENTS -> AdminPaymentsTab(
            language = language,
            payments = allPayments,
            onReviewPayment = onReviewPayment
        )
        AdminTab.USERS -> AdminUsersTab(
            language = language,
            users = allUsers,
            moderationHistory = moderationHistory,
            serverNowMillis = serverNowMillis,
            canManageUsers = isSuper || adminProfile.canManageUsers,
            onModerateUser = onModerateUser
        )
        AdminTab.COMPLAINTS -> AdminComplaintsTab(
            language = language,
            complaints = allComplaints,
            selectedReplies = selectedReplies,
            onSelectTicket = onSelectComplaintTicket,
            onReplyComplaint = onReplyComplaint
        )
        AdminTab.PACKAGES -> AdminPackagesTab(
            language = language,
            packages = packages,
            priceHistory = priceHistory,
            onUpdatePackage = onUpdatePackage
        )
        AdminTab.PAY_SETTINGS -> AdminPaymentSettingsTab(
            language = language,
            settings = paymentSettings,
            onSaveSettings = onSavePaymentSettings
        )
        AdminTab.TEAM_LOGS -> AdminEmailAndTeamTab(
            language = language,
            emailOutbox = emailOutbox,
            subAdmins = subAdmins,
            auditLogs = auditLogs,
            onSaveSubAdmin = onSaveSubAdmin
        )
    }
}
