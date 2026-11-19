package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.data.model.AdminUserDoc
import com.example.data.model.AppLanguage
import com.example.data.model.AuditLogDoc
import com.example.data.model.ComplaintReplyDoc
import com.example.data.model.ComplaintStatus
import com.example.data.model.ComplaintTicketDoc
import com.example.data.model.EmailOutboxDoc
import com.example.data.model.PackagePriceHistoryDoc
import com.example.data.model.PaymentSettingsDoc
import com.example.data.model.SubscriptionPackageDoc
import com.example.i18n.Strings
import com.example.i18n.tr
import com.example.ui.theme.DueRed
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.WarningAmber
import com.example.util.Formatters

@Composable
fun AdminComplaintsTab(
    language: AppLanguage,
    complaints: List<ComplaintTicketDoc>,
    selectedReplies: List<ComplaintReplyDoc>,
    onSelectTicket: (String?) -> Unit,
    onReplyComplaint: (ComplaintTicketDoc, String, ComplaintStatus) -> Unit
) {
    var statusFilter by remember { mutableStateOf("ALL") }
    var search by remember { mutableStateOf("") }
    var activeTicket by remember { mutableStateOf<ComplaintTicketDoc?>(null) }

    val filtered = remember(complaints, statusFilter, search) {
        complaints.filter { t ->
            val matchSt = statusFilter == "ALL" || t.status == statusFilter
            val q = search.trim()
            val matchQ = q.isBlank() || t.ticketCode.contains(q, true) || t.shopName.contains(q, true) || t.subject.contains(q, true)
            matchSt && matchQ
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text(tr(language, "Ticket ID, দোকান বা বিষয় খুঁজুন", "Search Ticket ID, shop or subject")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL", "PENDING", "IN_PROGRESS", "RESOLVED", "REJECTED").forEach { st ->
                    FilterChip(selected = statusFilter == st, onClick = { statusFilter = st }, label = { Text(st) })
                }
            }
        }

        items(filtered, key = { it.id }) { ticket ->
            val st = ComplaintStatus.fromName(ticket.status)
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    activeTicket = ticket
                    onSelectTicket(ticket.id)
                },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${ticket.ticketCode} • ${ticket.shopName}", fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                        AssistChip(onClick = {}, label = { Text(if (language == AppLanguage.BN) st.labelBn else st.labelEn) })
                    }
                    Text(ticket.subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(ticket.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    if (ticket.lastReplyText.isNotBlank()) {
                        Text("${ticket.lastReplyBy}: ${ticket.lastReplyText}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    val openTicket = activeTicket?.let { sel -> complaints.firstOrNull { it.id == sel.id } ?: sel }
    if (openTicket != null) {
        var replyText by remember(openTicket.id) { mutableStateOf("") }
        var selectedStatus by remember(openTicket.id) { mutableStateOf(ComplaintStatus.fromName(openTicket.status)) }
        AlertDialog(
            onDismissRequest = { activeTicket = null; onSelectTicket(null) },
            title = { Text("${openTicket.ticketCode} • ${openTicket.shopName}") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(openTicket.subject, fontWeight = FontWeight.Bold)
                    Text(openTicket.description, style = MaterialTheme.typography.bodySmall)
                    if (!openTicket.attachmentUri.isNullOrBlank()) {
                        AsyncImage(
                            model = openTicket.attachmentUri,
                            contentDescription = "Complaint Attachment",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(10.dp))
                        )
                    }
                    HorizontalDivider()
                    selectedReplies.forEach { rep ->
                        Surface(
                            color = if (rep.senderRole == "CUSTOMER") MaterialTheme.colorScheme.surfaceVariant else EmeraldPrimary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("${rep.senderName} (${rep.senderRole})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(rep.message, style = MaterialTheme.typography.bodySmall)
                                val ms = rep.createdAt?.toDate()?.time ?: 0L
                                if (ms > 0L) Text(Formatters.formatDateTime(ms), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text(tr(language, "স্ট্যাটাস পরিবর্তন", "Update Status"), style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ComplaintStatus.entries.forEach { st ->
                            FilterChip(
                                selected = selectedStatus == st,
                                onClick = { selectedStatus = st },
                                label = { Text(if (language == AppLanguage.BN) st.labelBn else st.labelEn) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        label = { Text(tr(language, "অ্যাডমিন উত্তর লিখুন *", "Admin Reply *")) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReplyComplaint(openTicket, replyText.trim(), selectedStatus)
                        replyText = ""
                    },
                    enabled = replyText.trim().isNotBlank()
                ) { Text(tr(language, "উত্তর পাঠান", "Send Reply")) }
            },
            dismissButton = {
                TextButton(onClick = { activeTicket = null; onSelectTicket(null) }) { Text(Strings.close(language)) }
            }
        )
    }
}

@Composable
fun AdminPackagesTab(
    language: AppLanguage,
    packages: List<SubscriptionPackageDoc>,
    priceHistory: List<PackagePriceHistoryDoc>,
    onUpdatePackage: (SubscriptionPackageDoc, Double) -> Unit
) {
    var editingPkg by remember { mutableStateOf<SubscriptionPackageDoc?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(packages, key = { it.tier }) { pkg ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${pkg.nameBn} (${pkg.tier})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        AssistChip(
                            onClick = {},
                            label = { Text(if (pkg.enabled) tr(language, "চালু", "Enabled") else tr(language, "বন্ধ", "Disabled")) }
                        )
                    }
                    Text(
                        "1m: ৳${pkg.priceForMonths(1).toInt()} • 3m: ৳${pkg.priceForMonths(3).toInt()} • 6m: ৳${pkg.priceForMonths(6).toInt()} • 12m: ৳${pkg.priceForMonths(12).toInt()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldPrimary
                    )
                    Text(
                        tr(
                            language,
                            "শাখা: ${pkg.maxBranches} • কর্মচারী: ${pkg.maxEmployees} • AI: ${pkg.aiEnabled} • এক্সপোর্ট: ${pkg.exportEnabled}",
                            "Branches: ${pkg.maxBranches} • Staff: ${pkg.maxEmployees} • AI: ${pkg.aiEnabled} • Export: ${pkg.exportEnabled}"
                        ),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Button(onClick = { editingPkg = pkg }, modifier = Modifier.fillMaxWidth()) {
                        Text(tr(language, "দাম, ফিচার ও স্ট্যাটাস পরিবর্তন", "Edit Pricing, Features & Status"))
                    }
                }
            }
        }

        item {
            Text(tr(language, "মূল্য ও ফিচার পরিবর্তনের ইতিহাস", "Price & Feature Change History"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(priceHistory.take(25), key = { it.id }) { h ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                    Text("${h.tier} • ${h.actorEmail}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Text(h.summary, style = MaterialTheme.typography.bodySmall)
                    val ms = h.createdAt?.toDate()?.time ?: 0L
                    if (ms > 0L) Text(Formatters.formatDateTime(ms), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    editingPkg?.let { pkg ->
        var p1 by remember(pkg.tier) { mutableStateOf(pkg.priceForMonths(1).toInt().toString()) }
        var p3 by remember(pkg.tier) { mutableStateOf(pkg.priceForMonths(3).toInt().toString()) }
        var p6 by remember(pkg.tier) { mutableStateOf(pkg.priceForMonths(6).toInt().toString()) }
        var p12 by remember(pkg.tier) { mutableStateOf(pkg.priceForMonths(12).toInt().toString()) }
        var branches by remember(pkg.tier) { mutableStateOf(pkg.maxBranches.toString()) }
        var employees by remember(pkg.tier) { mutableStateOf(pkg.maxEmployees.toString()) }
        var featuresBn by remember(pkg.tier) { mutableStateOf(pkg.featuresSummaryBn) }
        var featuresEn by remember(pkg.tier) { mutableStateOf(pkg.featuresSummaryEn) }
        var aiEnabled by remember(pkg.tier) { mutableStateOf(pkg.aiEnabled) }
        var exportEnabled by remember(pkg.tier) { mutableStateOf(pkg.exportEnabled) }
        var enabled by remember(pkg.tier) { mutableStateOf(pkg.enabled) }

        AlertDialog(
            onDismissRequest = { editingPkg = null },
            title = { Text("${pkg.tier} • ${tr(language, "প্যাকেজ কনফিগারেশন", "Configure Package")}") },
            text = {
                Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(p1, { p1 = it }, label = { Text("1 Month (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        OutlinedTextField(p3, { p3 = it }, label = { Text("3 Months (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(p6, { p6 = it }, label = { Text("6 Months (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        OutlinedTextField(p12, { p12 = it }, label = { Text("12 Months (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(branches, { branches = it }, label = { Text(tr(language, "শাখা সীমা", "Branches")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        OutlinedTextField(employees, { employees = it }, label = { Text(tr(language, "কর্মচারী সীমা", "Staff Limit")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(featuresBn, { featuresBn = it }, label = { Text("Features (বাংলা)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(featuresEn, { featuresEn = it }, label = { Text("Features (English)") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = aiEnabled, onCheckedChange = { aiEnabled = it }); Text("AI")
                        Spacer(Modifier.width(8.dp))
                        Checkbox(checked = exportEnabled, onCheckedChange = { exportEnabled = it }); Text("Export")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(language, "প্যাকেজ চালু রাখুন", "Package Enabled"))
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val m1 = p1.toDoubleOrNull() ?: pkg.monthlyPriceBdt
                    onUpdatePackage(
                        pkg.copy(
                            monthlyPriceBdt = m1,
                            price1mBdt = m1,
                            price3mBdt = p3.toDoubleOrNull() ?: (m1 * 3),
                            price6mBdt = p6.toDoubleOrNull() ?: (m1 * 6),
                            price12mBdt = p12.toDoubleOrNull() ?: (m1 * 12),
                            maxBranches = branches.toLongOrNull() ?: pkg.maxBranches,
                            maxEmployees = employees.toLongOrNull() ?: pkg.maxEmployees,
                            aiEnabled = aiEnabled,
                            exportEnabled = exportEnabled,
                            enabled = enabled,
                            featuresSummaryBn = featuresBn,
                            featuresSummaryEn = featuresEn
                        ),
                        pkg.monthlyPriceBdt
                    )
                    editingPkg = null
                }) { Text(Strings.save(language)) }
            },
            dismissButton = { TextButton(onClick = { editingPkg = null }) { Text(Strings.cancel(language)) } }
        )
    }
}

@Composable
fun AdminPaymentSettingsTab(
    language: AppLanguage,
    settings: PaymentSettingsDoc,
    onSaveSettings: (PaymentSettingsDoc) -> Unit
) {
    var bkashName by remember(settings) { mutableStateOf(settings.bkashAccountName) }
    var bkashNum by remember(settings) { mutableStateOf(settings.bkashNumber) }
    var bkashOn by remember(settings) { mutableStateOf(settings.bkashEnabled) }
    var nagadName by remember(settings) { mutableStateOf(settings.nagadAccountName) }
    var nagadNum by remember(settings) { mutableStateOf(settings.nagadNumber) }
    var nagadOn by remember(settings) { mutableStateOf(settings.nagadEnabled) }
    var instrBn by remember(settings) { mutableStateOf(settings.instructionsBn) }
    var instrEn by remember(settings) { mutableStateOf(settings.instructionsEn) }
    var confirmSave by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr(language, "bKash ও Nagad পেমেন্ট সেটিংস", "bKash & Nagad Payment Settings"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(language, "bKash চালু রাখুন", "Enable bKash"), fontWeight = FontWeight.SemiBold)
                        Switch(checked = bkashOn, onCheckedChange = { bkashOn = it })
                    }
                    OutlinedTextField(bkashName, { bkashName = it }, label = { Text("bKash Account Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(bkashNum, { bkashNum = it }, label = { Text("bKash Number") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(language, "Nagad চালু রাখুন", "Enable Nagad"), fontWeight = FontWeight.SemiBold)
                        Switch(checked = nagadOn, onCheckedChange = { nagadOn = it })
                    }
                    OutlinedTextField(nagadName, { nagadName = it }, label = { Text("Nagad Account Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(nagadNum, { nagadNum = it }, label = { Text("Nagad Number") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    HorizontalDivider()
                    OutlinedTextField(instrBn, { instrBn = it }, label = { Text("Payment Instructions (বাংলা)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(instrEn, { instrEn = it }, label = { Text("Payment Instructions (English)") }, minLines = 2, modifier = Modifier.fillMaxWidth())

                    Button(
                        onClick = { confirmSave = true },
                        enabled = bkashNum.trim().length >= 5 && nagadNum.trim().length >= 5,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_payment_settings_button")
                    ) {
                        Text(tr(language, "পেমেন্ট সেটিংস সংরক্ষণ", "Save Payment Settings"))
                    }
                }
            }
        }
    }

    if (confirmSave) {
        AlertDialog(
            onDismissRequest = { confirmSave = false },
            title = { Text(tr(language, "সেটিংস পরিবর্তন নিশ্চিত করুন", "Confirm Settings Update")) },
            text = { Text(tr(language, "নতুন bKash/Nagad নম্বর সাথে সাথে গ্রাহকদের পেমেন্ট স্ক্রিনে দেখাবে। সংরক্ষণ করবেন?", "Updated payment numbers will take effect immediately on customer checkout. Save?")) },
            confirmButton = {
                Button(onClick = {
                    onSaveSettings(
                        settings.copy(
                            bkashAccountName = bkashName,
                            bkashNumber = bkashNum,
                            bkashEnabled = bkashOn,
                            nagadAccountName = nagadName,
                            nagadNumber = nagadNum,
                            nagadEnabled = nagadOn,
                            instructionsBn = instrBn,
                            instructionsEn = instrEn
                        )
                    )
                    confirmSave = false
                }) { Text(Strings.save(language)) }
            },
            dismissButton = { TextButton(onClick = { confirmSave = false }) { Text(Strings.cancel(language)) } }
        )
    }
}

@Composable
fun AdminEmailAndTeamTab(
    language: AppLanguage,
    emailOutbox: List<EmailOutboxDoc>,
    subAdmins: List<AdminUserDoc>,
    auditLogs: List<AuditLogDoc>,
    onSaveSubAdmin: (String, String, String, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit
) {
    var uidInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var canPay by remember { mutableStateOf(true) }
    var canUsers by remember { mutableStateOf(false) }
    var canComplaints by remember { mutableStateOf(true) }
    var canReports by remember { mutableStateOf(true) }
    var auditSearch by remember { mutableStateOf("") }
    var auditPage by remember { mutableIntStateOf(0) }

    val filteredLogs = remember(auditLogs, auditSearch) {
        val q = auditSearch.trim()
        if (q.isBlank()) auditLogs else auditLogs.filter {
            it.action.contains(q, true) || it.actorEmail.contains(q, true) || it.details.contains(q, true)
        }
    }
    val logPages = ((filteredLogs.size + 10 - 1) / 10).coerceAtLeast(1)
    val safeLogPage = auditPage.coerceIn(0, logPages - 1)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Sub-Admin Role Manager
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr(language, "Sub-Admin নিয়োগ ও পারমিশন (শুধু Owner)", "Sub-Admin Permissions (Owner Only)"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(uidInput, { uidInput = it }, label = { Text("Firebase Auth UID *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(emailInput, { emailInput = it }, label = { Text("Google Email *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(nameInput, { nameInput = it }, label = { Text(tr(language, "নাম *", "Name *")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = canPay, onCheckedChange = { canPay = it }); Text(tr(language, "পেমেন্ট যাচাই", "Verify Pay"))
                        Checkbox(checked = canComplaints, onCheckedChange = { canComplaints = it }); Text(tr(language, "অভিযোগ উত্তর", "Complaints"))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = canUsers, onCheckedChange = { canUsers = it }); Text(tr(language, "ইউজার ব্যান/স্থগিত", "Users"))
                        Checkbox(checked = canReports, onCheckedChange = { canReports = it }); Text(tr(language, "সীমিত রিপোর্ট", "Ltd Report"))
                    }
                    Button(
                        onClick = {
                            onSaveSubAdmin(uidInput, emailInput, nameInput, canPay, canUsers, canComplaints, canReports, true)
                            uidInput = ""; emailInput = ""; nameInput = ""
                        },
                        enabled = uidInput.isNotBlank() && emailInput.contains("@") && nameInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(tr(language, "Sub-Admin সংরক্ষণ", "Save Sub-Admin")) }
                }
            }
        }

        items(subAdmins, key = { it.uid }) { adm ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${adm.name} (${adm.role})", fontWeight = FontWeight.Bold)
                        Text(adm.email, style = MaterialTheme.typography.labelSmall)
                        Text(
                            "Pay=${adm.canVerifyPayments} • Users=${adm.canManageUsers} • Complaints=${adm.canReplyComplaints}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (adm.role != "SUPER_ADMIN") {
                        OutlinedButton(onClick = {
                            onSaveSubAdmin(
                                adm.uid, adm.email, adm.name,
                                adm.canVerifyPayments, adm.canManageUsers, adm.canReplyComplaints, adm.canViewLimitedReports,
                                !adm.active
                            )
                        }) {
                            Text(if (adm.active) tr(language, "নিষ্ক্রিয়", "Disable") else tr(language, "সক্রিয়", "Enable"))
                        }
                    }
                }
            }
        }

        // Email Outbox Queue (Honest Status)
        item {
            Card(colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.14f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr(language, "ইমেইল আউটবক্স (${emailOutbox.size}) — ব্যাকএন্ড সেটআপ বাকি", "Email Outbox (${emailOutbox.size}) — Pending Backend Service"), fontWeight = FontWeight.Bold)
                    Text(
                        tr(
                            language,
                            "নিরাপত্তার কারণে অ্যাপের ভেতরে SMTP পাসওয়ার্ড রাখা হয়নি। প্রতিটি অর্ডার ও অনুমোদনের ইমেইল Firestore email_outbox-এ সংরক্ষিত হচ্ছে; Firebase Trigger Email / Cloud Function যুক্ত করলে স্বয়ংক্রিয়ভাবে পাঠানো হবে।",
                            "No SMTP credentials are embedded in app code. Emails are queued in Firestore email_outbox with status PENDING_BACKEND_CONFIG until a server Cloud Function / Trigger Email backend is configured."
                        ),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        items(emailOutbox.take(15), key = { it.id }) { mail ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                    Text("${mail.emailType} → ${mail.recipientEmail}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Text(mail.subject, style = MaterialTheme.typography.bodySmall)
                    Text(mail.bodyPreview, style = MaterialTheme.typography.labelSmall)
                    Text("Status: ${mail.status}", style = MaterialTheme.typography.labelSmall, color = WarningAmber)
                }
            }
        }

        // Immutable Audit Logs
        item {
            Text(tr(language, "অপরিবর্তনীয় Audit Logs (${filteredLogs.size})", "Immutable Audit Logs (${filteredLogs.size})"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = auditSearch,
                onValueChange = { auditSearch = it; auditPage = 0 },
                label = { Text(tr(language, "অ্যাকশন বা অ্যাডমিন খুঁজুন", "Search audit logs")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        items(filteredLogs.drop(safeLogPage * 10).take(10), key = { it.id }) { log ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                    Text("${log.action} • ${log.actorEmail}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Text(log.details, style = MaterialTheme.typography.bodySmall)
                    val ms = log.createdAt?.toDate()?.time ?: 0L
                    if (ms > 0L) Text(Formatters.formatDateTime(ms), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (logPages > 1) {
            item {
                PaginationBar(language, safeLogPage, logPages) { auditPage = it }
            }
        }
    }
}
