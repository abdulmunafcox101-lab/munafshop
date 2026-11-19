package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.AccountModerationStatus
import com.example.data.model.AppLanguage
import com.example.data.model.BusinessAccountDoc
import com.example.data.model.ModerationHistoryDoc
import com.example.data.model.PaymentRequestDoc
import com.example.data.model.PaymentRequestStatus
import com.example.data.model.SubscriptionEngine
import com.example.i18n.Strings
import com.example.i18n.tr
import com.example.ui.theme.DueRed
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.WarningAmber
import com.example.util.Formatters

private const val PAGE_SIZE = 10

@Composable
fun AdminPaymentsTab(
    language: AppLanguage,
    payments: List<PaymentRequestDoc>,
    onReviewPayment: (PaymentRequestDoc, Boolean, String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") }
    var page by remember { mutableIntStateOf(0) }
    var approveTarget by remember { mutableStateOf<PaymentRequestDoc?>(null) }
    var rejectTarget by remember { mutableStateOf<PaymentRequestDoc?>(null) }
    var previewScreenshot by remember { mutableStateOf<String?>(null) }

    val filtered = remember(payments, search, statusFilter) {
        payments.filter { req ->
            val matchFilter = statusFilter == "ALL" || req.status == statusFilter
            val q = search.trim()
            val matchQuery = q.isBlank() ||
                req.shopName.contains(q, true) ||
                req.userEmail.contains(q, true) ||
                req.transactionId.contains(q, true) ||
                req.senderNumber.contains(q, true)
            matchFilter && matchQuery
        }
    }
    val totalPages = ((filtered.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    val safePage = page.coerceIn(0, totalPages - 1)
    val pageItems = remember(filtered, safePage) {
        filtered.drop(safePage * PAGE_SIZE).take(PAGE_SIZE)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it; page = 0 },
                label = { Text(tr(language, "দোকান, ইমেইল, নম্বর বা TrxID খুঁজুন", "Search shop, email, sender or TrxID")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("admin_payments_search")
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL", "PENDING", "APPROVED", "REJECTED").forEach { st ->
                    FilterChip(
                        selected = statusFilter == st,
                        onClick = { statusFilter = st; page = 0 },
                        label = { Text(st) }
                    )
                }
            }
        }

        if (pageItems.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(
                        tr(language, "কোনো পেমেন্ট রিকোয়েস্ট পাওয়া যায়নি।", "No matching payment requests."),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(pageItems, key = { it.id }) { req ->
                val st = PaymentRequestStatus.fromName(req.status)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(req.shopName, fontWeight = FontWeight.Bold)
                                Text(req.userEmail, style = MaterialTheme.typography.labelSmall)
                            }
                            AssistChip(
                                onClick = {},
                                label = { Text(if (language == AppLanguage.BN) st.labelBn else st.labelEn) }
                            )
                        }
                        Text(
                            "${req.packageTier} (${req.durationMonths} মাস) • ${Formatters.bdt(req.amountBdt)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${req.method} • Sender: ${req.senderNumber} • TrxID: ${req.transactionId}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (!req.screenshotUri.isNullOrBlank()) {
                            OutlinedButton(onClick = { previewScreenshot = req.screenshotUri }) {
                                Text(tr(language, "পেমেন্ট স্ক্রিনশট দেখুন", "View Screenshot"))
                            }
                        }
                        if (req.adminNote.isNotBlank()) {
                            Text(
                                tr(language, "নোট: ${req.adminNote} (${req.reviewedByEmail})", "Note: ${req.adminNote}"),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (st == PaymentRequestStatus.REJECTED) DueRed else ProfitGreen
                            )
                        }
                        if (req.status == PaymentRequestStatus.PENDING.name) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { approveTarget = req },
                                    colors = ButtonDefaults.buttonColors(containerColor = ProfitGreen),
                                    modifier = Modifier.weight(1f).testTag("approve_pay_${req.id}")
                                ) { Text(tr(language, "অনুমোদন", "Approve")) }
                                Button(
                                    onClick = { rejectTarget = req },
                                    colors = ButtonDefaults.buttonColors(containerColor = DueRed),
                                    modifier = Modifier.weight(1f).testTag("reject_pay_${req.id}")
                                ) { Text(tr(language, "বাতিল", "Reject")) }
                            }
                        }
                    }
                }
            }
        }

        if (totalPages > 1) {
            item {
                PaginationBar(
                    language = language,
                    currentPage = safePage,
                    totalPages = totalPages,
                    onPageChange = { page = it }
                )
            }
        }
    }

    approveTarget?.let { req ->
        AlertDialog(
            onDismissRequest = { approveTarget = null },
            title = { Text(tr(language, "পেমেন্ট অনুমোদন নিশ্চিত করুন", "Confirm Payment Approval")) },
            text = {
                Text(
                    tr(
                        language,
                        "${req.shopName}-এর ${req.packageTier} (${req.durationMonths} মাস) • ${Formatters.bdt(req.amountBdt)} (TrxID: ${req.transactionId}) অনুমোদন করে সাবস্ক্রিপশন সক্রিয় করবেন?",
                        "Approve ${req.shopName}'s ${req.packageTier} (${req.durationMonths}m) • ${Formatters.bdt(req.amountBdt)} and activate subscription?"
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReviewPayment(req, true, "Verified by Admin")
                        approveTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ProfitGreen)
                ) { Text(tr(language, "হ্যাঁ, সক্রিয় করুন", "Approve & Activate")) }
            },
            dismissButton = { TextButton(onClick = { approveTarget = null }) { Text(Strings.cancel(language)) } }
        )
    }

    rejectTarget?.let { req ->
        var reason by remember(req.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text(tr(language, "পেমেন্ট বাতিলের কারণ দিন *", "Rejection Reason Required *")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${req.shopName} • TrxID: ${req.transactionId}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text(tr(language, "বাতিলের সুনির্দিষ্ট কারণ (কমপক্ষে ৩ অক্ষর)", "Rejection reason (min 3 chars)")) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag("reject_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReviewPayment(req, false, reason.trim())
                        rejectTarget = null
                    },
                    enabled = reason.trim().length >= 3,
                    colors = ButtonDefaults.buttonColors(containerColor = DueRed)
                ) { Text(tr(language, "বাতিল নিশ্চিত", "Confirm Reject")) }
            },
            dismissButton = { TextButton(onClick = { rejectTarget = null }) { Text(Strings.cancel(language)) } }
        )
    }

    previewScreenshot?.let { uri ->
        AlertDialog(
            onDismissRequest = { previewScreenshot = null },
            title = { Text(tr(language, "পেমেন্ট স্ক্রিনশট", "Payment Screenshot")) },
            text = {
                AsyncImage(
                    model = uri,
                    contentDescription = "Payment Screenshot",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(12.dp))
                )
            },
            confirmButton = { TextButton(onClick = { previewScreenshot = null }) { Text(Strings.close(language)) } }
        )
    }
}

@Composable
fun AdminUsersTab(
    language: AppLanguage,
    users: List<BusinessAccountDoc>,
    moderationHistory: List<ModerationHistoryDoc>,
    serverNowMillis: Long,
    canManageUsers: Boolean,
    onModerateUser: (BusinessAccountDoc, String, String, Int) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    var page by remember { mutableIntStateOf(0) }
    var moderationDialog by remember { mutableStateOf<Pair<BusinessAccountDoc, String>?>(null) }
    var showHistoryForUser by remember { mutableStateOf<BusinessAccountDoc?>(null) }

    val filtered = remember(users, search, filter, serverNowMillis) {
        users.filter { acc ->
            val status = acc.resolvedStatus()
            val cd = SubscriptionEngine.computeCountdown(
                acc.subscriptionTier, acc.subscriptionStartMillis, acc.subscriptionExpiresAtMillis, serverNowMillis
            )
            val matchFilter = when (filter) {
                "ACTIVE" -> status == AccountModerationStatus.ACTIVE
                "PAID" -> cd.isPaidActive
                "FREE" -> !cd.isPaidActive
                "EXPIRING" -> cd.isPaidActive && cd.remainingMillis <= 7L * SubscriptionEngine.ONE_DAY_MILLIS
                "SUSPENDED" -> status == AccountModerationStatus.SUSPENDED
                "BANNED" -> status == AccountModerationStatus.BANNED
                else -> true
            }
            val q = search.trim()
            val matchSearch = q.isBlank() ||
                acc.shopName.contains(q, true) ||
                acc.ownerName.contains(q, true) ||
                acc.ownerEmail.contains(q, true) ||
                acc.phone.contains(q, true)
            matchFilter && matchSearch
        }
    }
    val totalPages = ((filtered.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    val safePage = page.coerceIn(0, totalPages - 1)
    val pageItems = remember(filtered, safePage) {
        filtered.drop(safePage * PAGE_SIZE).take(PAGE_SIZE)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it; page = 0 },
                label = { Text(tr(language, "নাম, দোকান, ইমেইল বা ফোন দিয়ে খুঁজুন", "Search name, shop, email or phone")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("admin_users_search")
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL", "ACTIVE", "PAID", "FREE", "EXPIRING", "SUSPENDED", "BANNED").forEach { key ->
                    FilterChip(
                        selected = filter == key,
                        onClick = { filter = key; page = 0 },
                        label = { Text(key) }
                    )
                }
            }
        }

        items(pageItems, key = { it.userId }) { acc ->
            val modStatus = acc.resolvedStatus()
            val countdown = SubscriptionEngine.computeCountdown(
                acc.subscriptionTier, acc.subscriptionStartMillis, acc.subscriptionExpiresAtMillis, serverNowMillis
            )
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${acc.shopName} (${acc.ownerName})", fontWeight = FontWeight.Bold)
                            Text("${acc.ownerEmail} • ${acc.phone}", style = MaterialTheme.typography.labelSmall)
                        }
                        AssistChip(
                            onClick = {},
                            label = { Text(if (language == AppLanguage.BN) modStatus.labelBn else modStatus.labelEn) }
                        )
                    }
                    Text(
                        text = "${tr(language, "প্যাকেজ", "Plan")}: ${acc.subscriptionTier} • ${
                            if (language == AppLanguage.BN) countdown.formattedBn() else countdown.formattedEn()
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (countdown.isExpired) DueRed else EmeraldPrimary
                    )
                    if (acc.banReason.isNotBlank()) {
                        Text(
                            tr(language, "ব্যান/স্থগিত কারণ: ${acc.banReason}", "Restriction Reason: ${acc.banReason}"),
                            style = MaterialTheme.typography.labelSmall,
                            color = DueRed
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { showHistoryForUser = acc }) {
                            Text(tr(language, "ইতিহাস", "History"))
                        }
                        if (canManageUsers) {
                            if (modStatus == AccountModerationStatus.ACTIVE) {
                                OutlinedButton(onClick = { moderationDialog = acc to "SUSPEND" }) {
                                    Text(tr(language, "স্থগিত", "Suspend"), color = WarningAmber)
                                }
                                Button(
                                    onClick = { moderationDialog = acc to "BAN" },
                                    colors = ButtonDefaults.buttonColors(containerColor = DueRed)
                                ) { Text(tr(language, "ব্যান", "Ban")) }
                            } else {
                                Button(
                                    onClick = { moderationDialog = acc to "UNBAN" },
                                    colors = ButtonDefaults.buttonColors(containerColor = ProfitGreen)
                                ) { Text(tr(language, "আনব্যান / সক্রিয়", "Unban")) }
                            }
                        }
                    }
                }
            }
        }

        if (totalPages > 1) {
            item {
                PaginationBar(
                    language = language,
                    currentPage = safePage,
                    totalPages = totalPages,
                    onPageChange = { page = it }
                )
            }
        }
    }

    moderationDialog?.let { (acc, action) ->
        var reason by remember(acc.userId, action) { mutableStateOf("") }
        var daysStr by remember(acc.userId, action) { mutableStateOf("7") }
        AlertDialog(
            onDismissRequest = { moderationDialog = null },
            title = {
                Text(
                    when (action) {
                        "SUSPEND" -> tr(language, "${acc.shopName}-কে সাময়িক স্থগিত করুন", "Suspend ${acc.shopName}")
                        "BAN" -> tr(language, "${acc.shopName}-কে স্থায়ী ব্যান করুন", "Permanently Ban ${acc.shopName}")
                        else -> tr(language, "${acc.shopName}-কে আনব্যান করুন", "Unban ${acc.shopName}")
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr(
                            language,
                            "গ্রাহকের কোনো ব্যবসায়িক হিসাব/ডেটা ডিলিট করা হবে না। কারণ লেখা বাধ্যতামূলক।",
                            "User's business accounting data will NOT be deleted. Reason is mandatory."
                        ),
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (action == "SUSPEND") {
                        OutlinedTextField(
                            value = daysStr,
                            onValueChange = { daysStr = it },
                            label = { Text(tr(language, "স্থগিতের দিন সংখ্যা", "Suspension Days")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text(tr(language, "কারণ লিখুন (কমপক্ষে ৩ অক্ষর) *", "Mandatory Reason (min 3 chars) *")) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag("moderation_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onModerateUser(acc, action, reason.trim(), daysStr.toIntOrNull() ?: 7)
                        moderationDialog = null
                    },
                    enabled = reason.trim().length >= 3,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (action == "UNBAN") ProfitGreen else DueRed
                    )
                ) { Text(Strings.confirm(language)) }
            },
            dismissButton = { TextButton(onClick = { moderationDialog = null }) { Text(Strings.cancel(language)) } }
        )
    }

    showHistoryForUser?.let { acc ->
        val logs = moderationHistory.filter { it.userId == acc.userId }
        AlertDialog(
            onDismissRequest = { showHistoryForUser = null },
            title = { Text("${acc.shopName} • ${tr(language, "ব্যান/আনব্যান ইতিহাস", "Moderation History")}") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (logs.isEmpty()) {
                        Text(tr(language, "কোনো রেকর্ড নেই।", "No moderation history."))
                    } else {
                        logs.forEach { item ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                    Text("${item.action} by ${item.actorEmail}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                    Text(item.reason, style = MaterialTheme.typography.bodySmall)
                                    val ms = item.createdAt?.toDate()?.time ?: 0L
                                    if (ms > 0L) Text(Formatters.formatDateTime(ms), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistoryForUser = null }) { Text(Strings.close(language)) } }
        )
    }
}

@Composable
fun PaginationBar(
    language: AppLanguage,
    currentPage: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = { onPageChange(currentPage - 1) }, enabled = currentPage > 0) {
            Text(tr(language, "পূর্ববর্তী", "Prev"))
        }
        Text("${currentPage + 1} / $totalPages", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { onPageChange(currentPage + 1) }, enabled = currentPage + 1 < totalPages) {
            Text(tr(language, "পরবর্তী", "Next"))
        }
    }
}
