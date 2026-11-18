package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminUserDoc
import com.example.data.model.AppLanguage
import com.example.data.model.AuditLogDoc
import com.example.data.model.BusinessAccountDoc
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentRequestDoc
import com.example.data.model.PaymentRequestStatus
import com.example.data.model.SubscriptionPackageDoc
import com.example.data.model.SubscriptionTier
import com.example.i18n.Strings
import com.example.i18n.tr
import com.example.ui.theme.DueRed
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.WarningAmber
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    language: AppLanguage,
    currentTierName: String,
    shopName: String,
    packages: List<SubscriptionPackageDoc>,
    myRequests: List<PaymentRequestDoc>,
    onSubmitPayment: (PaymentMethod, String, String, Double, SubscriptionTier, Int, String?) -> Unit,
    onBack: () -> Unit
) {
    var selectedTier by remember { mutableStateOf(SubscriptionTier.BASIC) }
    var selectedMonths by remember { mutableIntStateOf(1) }
    var method by remember { mutableStateOf(PaymentMethod.BKASH) }
    var senderNumber by remember { mutableStateOf("") }
    var trxId by remember { mutableStateOf("") }
    var screenshotUri by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) screenshotUri = uri
    }

    val chosenPkg = packages.firstOrNull { it.tier == selectedTier.name }
    val basePrice = chosenPkg?.monthlyPriceBdt ?: selectedTier.defaultPriceBdt
    val discountMultiplier = when (selectedMonths) {
        3 -> 0.95
        6 -> 0.90
        12 -> 0.80
        else -> 1.0
    }
    val totalPayable = basePrice * selectedMonths * discountMultiplier

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr(language, "সাবস্ক্রিপশন ও পেমেন্ট", "Subscription & Payment")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(tr(language, "বর্তমান প্যাকেজ: $currentTierName", "Active Plan: $currentTierName"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            tr(language, "মেয়াদ শেষ হলেও আপনার পুরোনো কোনো হিসাব বা ডেটা মুছে ফেলা হবে না।", "Your historical accounting data is never deleted if a plan expires."),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Package Cards
            items(packages, key = { it.tier }) { pkg ->
                val tier = SubscriptionTier.fromName(pkg.tier)
                val isSelected = selectedTier == tier
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${pkg.nameBn} / ${pkg.nameEn}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${Formatters.bdt(pkg.monthlyPriceBdt)}/${tr(language, "মাস", "mo")}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            text = "${tr(language, "শাখা", "Branches")}: ${pkg.maxBranches} • ${tr(language, "কর্মচারী", "Staff")}: ${pkg.maxEmployees} • AI: ${if (pkg.aiEnabled) "✓" else "✗"} • Export: ${if (pkg.exportEnabled) "✓" else "✗"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (tier != SubscriptionTier.FREE) {
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(onClick = { selectedTier = tier }) {
                                Text(if (isSelected) tr(language, "নির্বাচিত", "Selected") else tr(language, "এই প্যাকেজ নিন", "Select Plan"))
                            }
                        }
                    }
                }
            }

            // Duration & Manual Payment
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(tr(language, "মেয়াদ নির্বাচন করুন", "Select Duration"), style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 3, 6, 12).forEach { m ->
                                FilterChip(
                                    selected = selectedMonths == m,
                                    onClick = { selectedMonths = m },
                                    label = { Text("${m}${tr(language, "মাস", "m")}") }
                                )
                            }
                        }
                        Text(
                            text = "${tr(language, "মোট প্রদেয়", "Total Payable")}: ${Formatters.bdt(totalPayable)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        HorizontalDivider()
                        Text(tr(language, "ম্যানুয়াল পেমেন্ট (bKash / Nagad)", "Manual Payment (bKash / Nagad)"), style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PaymentMethod.entries.forEach { pm ->
                                FilterChip(
                                    selected = method == pm,
                                    onClick = { method = pm },
                                    label = { Text(if (language == AppLanguage.BN) pm.labelBn else pm.labelEn) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = senderNumber,
                            onValueChange = { senderNumber = it },
                            label = { Text(tr(language, "প্রেরকের নম্বর (যেমন 017...)", "Sender Mobile Number")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth().testTag("payment_sender_input")
                        )
                        OutlinedTextField(
                            value = trxId,
                            onValueChange = { trxId = it },
                            label = { Text(tr(language, "Transaction ID (TrxID)", "Transaction ID")) },
                            modifier = Modifier.fillMaxWidth().testTag("payment_trx_input")
                        )
                        OutlinedButton(onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Text(
                                if (screenshotUri != null) tr(language, "স্ক্রিনশট সংযুক্ত ✓", "Screenshot Attached ✓")
                                else tr(language, "পেমেন্ট স্ক্রিনশট দিন (ঐচ্ছিক)", "Attach Screenshot (Optional)")
                            )
                        }
                        Button(
                            onClick = {
                                onSubmitPayment(
                                    method, senderNumber, trxId,
                                    totalPayable, selectedTier, selectedMonths,
                                    screenshotUri?.toString()
                                )
                                senderNumber = ""; trxId = ""; screenshotUri = null
                            },
                            enabled = senderNumber.length >= 6 && trxId.length >= 4 && totalPayable > 0,
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("submit_payment_button")
                        ) {
                            Text(tr(language, "পেমেন্ট ভেরিফিকেশনের জন্য পাঠান", "Submit for Admin Verification"))
                        }

                        HorizontalDivider()
                        Text(
                            text = tr(
                                language,
                                "অটোমেটিক পেমেন্ট গেটওয়ে (SSLCommerz / bKash Merchant API): লাইভ মার্চেন্ট ক্রেডেনশিয়াল ও সার্ভার ওয়েবহুক ছাড়া অটোমেটিক পেমেন্ট সক্রিয় দেখানো হয় না। বর্তমানে ম্যানুয়াল bKash/Nagad যাচাই সম্পূর্ণ সক্রিয়।",
                                "Automatic Payment Gateway (SSLCommerz / bKash Tokenized): Requires server-side merchant credentials & webhook verification. No simulated auto-payment is enabled; use Manual Verification above."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (myRequests.isNotEmpty()) {
                item { Text(tr(language, "আমার পেমেন্ট ইতিহাস", "My Payment History"), style = MaterialTheme.typography.titleMedium) }
                items(myRequests, key = { it.id }) { req ->
                    val st = PaymentRequestStatus.fromName(req.status)
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("${req.packageTier} (${req.durationMonths}m) • ${req.method}", fontWeight = FontWeight.Bold)
                                Text("TrxID: ${req.transactionId} • ${Formatters.bdt(req.amountBdt)}", style = MaterialTheme.typography.bodySmall)
                                if (req.adminNote.isNotBlank()) Text(req.adminNote, style = MaterialTheme.typography.labelSmall)
                            }
                            Text(
                                text = if (language == AppLanguage.BN) st.labelBn else st.labelEn,
                                color = when (st) {
                                    PaymentRequestStatus.APPROVED -> ProfitGreen
                                    PaymentRequestStatus.REJECTED -> DueRed
                                    PaymentRequestStatus.PENDING -> WarningAmber
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    language: AppLanguage,
    adminProfile: AdminUserDoc,
    allUsers: List<BusinessAccountDoc>,
    allPayments: List<PaymentRequestDoc>,
    packages: List<SubscriptionPackageDoc>,
    subAdmins: List<AdminUserDoc>,
    auditLogs: List<AuditLogDoc>,
    onReviewPayment: (PaymentRequestDoc, Boolean, String) -> Unit,
    onToggleBlock: (BusinessAccountDoc, Boolean) -> Unit,
    onUpdatePackage: (SubscriptionPackageDoc) -> Unit,
    onSaveSubAdmin: (String, String, String, Boolean, Boolean, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val isSuper = adminProfile.role == "SUPER_ADMIN"
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = buildList {
        if (isSuper || adminProfile.canVerifyPayments) add(tr(language, "পেমেন্ট", "Payments"))
        if (isSuper || adminProfile.canManageUsers || adminProfile.canViewLimitedReports) add(tr(language, "ইউজার", "Users"))
        if (isSuper) {
            add(tr(language, "প্যাকেজ", "Packages"))
            add(tr(language, "সাব-অ্যাডমিন", "Sub-Admins"))
            add(tr(language, "অডিট লগ", "Audit Logs"))
        }
    }

    var showSubAdminDialog by remember { mutableStateOf(false) }

    val totalRevenue = allPayments.filter { it.status == PaymentRequestStatus.APPROVED.name }.sumOf { it.amountBdt }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isSuper) "Super Admin Panel" else "Sub-Admin Panel")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad).background(MaterialTheme.colorScheme.background)) {
            if (isSuper) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(tr(language, "মোট প্ল্যাটফর্ম আয়", "Total Platform Revenue"), style = MaterialTheme.typography.labelMedium)
                            Text(Formatters.bdt(totalRevenue), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(tr(language, "মোট ব্যবসা", "Businesses"), style = MaterialTheme.typography.labelMedium)
                            Text(allUsers.size.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (tabs.isNotEmpty()) {
                TabRow(selectedTabIndex = tabIndex.coerceAtMost(tabs.lastIndex)) {
                    tabs.forEachIndexed { idx, title ->
                        Tab(selected = tabIndex == idx, onClick = { tabIndex = idx }, text = { Text(title, maxLines = 1) })
                    }
                }

                val activeTabTitle = tabs.getOrNull(tabIndex) ?: ""
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (activeTabTitle) {
                        tr(language, "পেমেন্ট", "Payments") -> {
                            items(allPayments, key = { it.id }) { req ->
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(req.shopName, fontWeight = FontWeight.Bold)
                                            Text(req.status, color = if (req.status == "APPROVED") ProfitGreen else WarningAmber, fontWeight = FontWeight.Bold)
                                        }
                                        Text("${req.method} • ${req.senderNumber} • Trx: ${req.transactionId}", style = MaterialTheme.typography.bodySmall)
                                        Text("${req.packageTier} (${req.durationMonths}m) — ${Formatters.bdt(req.amountBdt)}", style = MaterialTheme.typography.bodyMedium)
                                        if (req.status == "PENDING") {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(onClick = { onReviewPayment(req, true, "Verified") }) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Approve")
                                                }
                                                OutlinedButton(
                                                    onClick = { onReviewPayment(req, false, "Invalid TrxID") },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DueRed)
                                                ) { Text("Reject") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        tr(language, "ইউজার", "Users") -> {
                            items(allUsers, key = { it.userId }) { u ->
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(u.shopName, fontWeight = FontWeight.Bold)
                                            Text("${u.ownerName} • ${u.ownerEmail}", style = MaterialTheme.typography.bodySmall)
                                            Text("Tier: ${u.subscriptionTier} • Blocked: ${u.blocked}", style = MaterialTheme.typography.labelSmall)
                                        }
                                        if (isSuper || adminProfile.canManageUsers) {
                                            OutlinedButton(onClick = { onToggleBlock(u, !u.blocked) }) {
                                                Text(if (u.blocked) "Unblock" else "Block")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        tr(language, "প্যাকেজ", "Packages") -> {
                            items(packages, key = { it.tier }) { pkg ->
                                var price by remember(pkg) { mutableStateOf(pkg.monthlyPriceBdt.toString()) }
                                var branches by remember(pkg) { mutableStateOf(pkg.maxBranches.toString()) }
                                var staff by remember(pkg) { mutableStateOf(pkg.maxEmployees.toString()) }
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("${pkg.tier} (${pkg.nameBn})", fontWeight = FontWeight.Bold)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price") }, modifier = Modifier.weight(1f))
                                            OutlinedTextField(value = branches, onValueChange = { branches = it }, label = { Text("Branches") }, modifier = Modifier.weight(1f))
                                            OutlinedTextField(value = staff, onValueChange = { staff = it }, label = { Text("Staff") }, modifier = Modifier.weight(1f))
                                        }
                                        Button(onClick = {
                                            onUpdatePackage(
                                                pkg.copy(
                                                    monthlyPriceBdt = price.toDoubleOrNull() ?: pkg.monthlyPriceBdt,
                                                    maxBranches = branches.toLongOrNull() ?: pkg.maxBranches,
                                                    maxEmployees = staff.toLongOrNull() ?: pkg.maxEmployees
                                                )
                                            )
                                        }) { Text(Strings.save(language)) }
                                    }
                                }
                            }
                        }
                        tr(language, "সাব-অ্যাডমিন", "Sub-Admins") -> {
                            item {
                                Button(onClick = { showSubAdminDialog = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(tr(language, "নতুন সাব-অ্যাডমিন যোগ করুন", "Add Sub-Admin"))
                                }
                            }
                            items(subAdmins, key = { it.uid }) { sa ->
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                        Text("${sa.name} (${sa.email})", fontWeight = FontWeight.Bold)
                                        Text("UID: ${sa.uid}", style = MaterialTheme.typography.labelSmall)
                                        Text("Payments: ${sa.canVerifyPayments} • Users: ${sa.canManageUsers} • Reports: ${sa.canViewLimitedReports}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        tr(language, "অডিট লগ", "Audit Logs") -> {
                            items(auditLogs, key = { it.id }) { log ->
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text(log.action, fontWeight = FontWeight.Bold)
                                        Text("${log.actorEmail} • ${log.details}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSubAdminDialog) {
        var uid by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var canPay by remember { mutableStateOf(true) }
        var canUsers by remember { mutableStateOf(false) }
        var canRep by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showSubAdminDialog = false },
            title = { Text("Add / Update Sub-Admin") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = uid, onValueChange = { uid = it }, label = { Text("Firebase User UID *") })
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email *") })
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name *") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = canPay, onCheckedChange = { canPay = it })
                        Text("Verify Payments")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = canUsers, onCheckedChange = { canUsers = it })
                        Text("Manage Users")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = canRep, onCheckedChange = { canRep = it })
                        Text("View Limited Reports")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveSubAdmin(uid, email, name, canPay, canUsers, canRep)
                        showSubAdminDialog = false
                    },
                    enabled = uid.isNotBlank() && email.isNotBlank() && name.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSubAdminDialog = false }) { Text("Cancel") } }
        )
    }
}
