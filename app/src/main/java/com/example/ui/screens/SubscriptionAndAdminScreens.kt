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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.BusinessProfileEntity
import com.example.data.model.AppLanguage
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentRequestDoc
import com.example.data.model.PaymentRequestStatus
import com.example.data.model.PaymentSettingsDoc
import com.example.data.model.SubscriptionEngine
import com.example.data.model.SubscriptionPackageDoc
import com.example.data.model.SubscriptionRenewalDoc
import com.example.data.model.SubscriptionTier
import com.example.i18n.Strings
import com.example.i18n.tr
import com.example.ui.theme.DueRed
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.WarningAmber
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    language: AppLanguage,
    profile: BusinessProfileEntity?,
    serverNowMillis: Long,
    packages: List<SubscriptionPackageDoc>,
    paymentSettings: PaymentSettingsDoc,
    myRequests: List<PaymentRequestDoc>,
    myRenewals: List<SubscriptionRenewalDoc>,
    onSubmitPayment: (
        method: PaymentMethod,
        sender: String,
        trxId: String,
        amount: Double,
        lockedUnitMonthly: Double,
        tier: SubscriptionTier,
        months: Int,
        screenshotUri: String?
    ) -> Unit,
    onOpenComplaints: () -> Unit,
    onBack: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val currentTierName = profile?.subscriptionTier ?: SubscriptionTier.FREE.name
    val countdown = SubscriptionEngine.computeCountdown(
        tierName = currentTierName,
        startMillis = profile?.subscriptionStartMillis ?: 0L,
        expiresAtMillis = profile?.subscriptionExpiresAtMillis ?: 0L,
        serverNowMillis = serverNowMillis
    )

    val purchasablePackages = remember(packages) {
        packages.filter { it.tier != SubscriptionTier.FREE.name && it.enabled }
    }
    var selectedTier by remember(purchasablePackages) {
        mutableStateOf(
            SubscriptionTier.fromName(purchasablePackages.firstOrNull()?.tier ?: SubscriptionTier.BASIC.name)
        )
    }
    var selectedMonths by remember { mutableIntStateOf(1) }

    val availableMethods = remember(paymentSettings) {
        buildList {
            if (paymentSettings.bkashEnabled) add(PaymentMethod.BKASH)
            if (paymentSettings.nagadEnabled) add(PaymentMethod.NAGAD)
        }
    }
    var selectedMethod by remember(availableMethods) {
        mutableStateOf(availableMethods.firstOrNull() ?: PaymentMethod.BKASH)
    }
    var senderNumber by remember { mutableStateOf("") }
    var transactionId by remember { mutableStateOf("") }
    var screenshotUri by remember { mutableStateOf<Uri?>(null) }
    var copiedLabel by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) screenshotUri = uri
    }

    val chosenPkg = packages.firstOrNull { it.tier == selectedTier.name }
    val totalPayable = chosenPkg?.priceForMonths(selectedMonths) ?: 0.0
    val unitMonthly = chosenPkg?.monthlyPriceBdt ?: selectedTier.defaultPriceBdt

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr(language, "প্যাকেজ ও সাবস্ক্রিপশন", "Subscription & Packages")) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("sub_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr(language, "পিছনে", "Back"))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenComplaints, modifier = Modifier.testTag("sub_open_complaints")) {
                        Icon(Icons.Default.SupportAgent, contentDescription = tr(language, "সাপোর্ট ও অভিযোগ", "Support"))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live Server-Synchronized Countdown Card
            item {
                val bannerColor = when {
                    countdown.isExpired -> DueRed
                    countdown.isPaidActive -> EmeraldPrimary
                    else -> MaterialTheme.colorScheme.secondary
                }
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = bannerColor)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                tr(
                                    language,
                                    "বর্তমান প্ল্যান: $currentTierName ${if (countdown.isExpired) "(মেয়াদোত্তীর্ণ)" else ""}",
                                    "Active Plan: $currentTierName ${if (countdown.isExpired) "(Expired)" else ""}"
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (language == AppLanguage.BN) countdown.formattedBn() else countdown.formattedEn(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.testTag("subscription_live_countdown")
                        )
                        if (countdown.startMillis > 0L && countdown.expiresAtMillis > 0L) {
                            Text(
                                text = tr(
                                    language,
                                    "শুরু: ${Formatters.formatDate(countdown.startMillis)} • মেয়াদ শেষ: ${Formatters.formatDateTime(countdown.expiresAtMillis)}",
                                    "Start: ${Formatters.formatDate(countdown.startMillis)} • Expires: ${Formatters.formatDateTime(countdown.expiresAtMillis)}"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.92f)
                            )
                        }
                        Text(
                            text = tr(
                                language,
                                "সার্ভার টাইম অনুযায়ী যাচাইকৃত। মেয়াদ শেষ হলে পেইড ফিচার বন্ধ হয়, তবে আপনার হিসাব ও ডাটা অক্ষুণ্ণ থাকে।",
                                "Server-time verified. Expiry only pauses paid features while preserving all your accounting records."
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Available Package Tiers (respects Super Admin enable/disable & custom 1/3/6/12 pricing)
            items(packages, key = { it.tier }) { pkg ->
                val tierEnum = SubscriptionTier.fromName(pkg.tier)
                val isSelected = selectedTier == tierEnum && tierEnum != SubscriptionTier.FREE && pkg.enabled
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "${if (language == AppLanguage.BN) pkg.nameBn else pkg.nameEn} (${pkg.tier})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!pkg.enabled) {
                                    Text(
                                        tr(language, "বর্তমানে বন্ধ রয়েছে", "Currently Disabled by Admin"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DueRed
                                    )
                                }
                            }
                            Text(
                                text = if (pkg.monthlyPriceBdt <= 0.0) tr(language, "ফ্রি", "FREE")
                                else "${Formatters.bdt(pkg.priceForMonths(1))}/${tr(language, "মাস", "mo")}",
                                style = MaterialTheme.typography.titleMedium,
                                color = EmeraldPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (pkg.featuresSummaryBn.isNotBlank() || pkg.featuresSummaryEn.isNotBlank()) {
                            Text(
                                text = if (language == AppLanguage.BN) pkg.featuresSummaryBn else pkg.featuresSummaryEn,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = tr(
                                language,
                                "শাখা: ${pkg.maxBranches} • কর্মচারী: ${pkg.maxEmployees} • AI: ${if (pkg.aiEnabled) "হ্যাঁ" else "না"} • এক্সপোর্ট: ${if (pkg.exportEnabled) "হ্যাঁ" else "না"}",
                                "Branches: ${pkg.maxBranches} • Staff: ${pkg.maxEmployees} • AI: ${if (pkg.aiEnabled) "Yes" else "No"} • Exports: ${if (pkg.exportEnabled) "Yes" else "No"}"
                            ),
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (tierEnum != SubscriptionTier.FREE && pkg.enabled) {
                            Text(
                                text = "1m: ${Formatters.bdt(pkg.priceForMonths(1))} • 3m: ${Formatters.bdt(pkg.priceForMonths(3))} • 6m: ${Formatters.bdt(pkg.priceForMonths(6))} • 12m: ${Formatters.bdt(pkg.priceForMonths(12))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedButton(
                                onClick = { selectedTier = tierEnum },
                                modifier = Modifier.fillMaxWidth().testTag("select_pkg_${pkg.tier}")
                            ) {
                                Text(
                                    if (isSelected) tr(language, "নির্বাচিত ✓", "Selected ✓")
                                    else tr(language, "এই প্যাকেজ বেছে নিন", "Select Plan")
                                )
                            }
                        }
                    }
                }
            }

            // Dynamic bKash / Nagad Payment Form
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            tr(language, "ম্যানুয়াল পেমেন্ট নির্দেশনা (bKash / Nagad)", "Manual bKash / Nagad Payment"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // Dynamic merchant account numbers from Admin PaymentSettings
                        if (paymentSettings.bkashEnabled) {
                            PaymentAccountInfoRow(
                                methodLabel = "bKash (${paymentSettings.bkashAccountName})",
                                number = paymentSettings.bkashNumber,
                                copied = copiedLabel == "BKASH",
                                onCopy = {
                                    clipboard.setText(AnnotatedString(paymentSettings.bkashNumber))
                                    copiedLabel = "BKASH"
                                }
                            )
                        }
                        if (paymentSettings.nagadEnabled) {
                            PaymentAccountInfoRow(
                                methodLabel = "Nagad (${paymentSettings.nagadAccountName})",
                                number = paymentSettings.nagadNumber,
                                copied = copiedLabel == "NAGAD",
                                onCopy = {
                                    clipboard.setText(AnnotatedString(paymentSettings.nagadNumber))
                                    copiedLabel = "NAGAD"
                                }
                            )
                        }
                        if (!paymentSettings.bkashEnabled && !paymentSettings.nagadEnabled) {
                            Text(
                                tr(language, "সব পেমেন্ট মাধ্যম সাময়িকভাবে বন্ধ। সাপোর্টে যোগাযোগ করুন।", "All payment methods are temporarily disabled by Admin."),
                                color = DueRed,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        val instr = if (language == AppLanguage.BN) paymentSettings.instructionsBn else paymentSettings.instructionsEn
                        if (instr.isNotBlank()) {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
                                Text(instr, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                            }
                        }

                        HorizontalDivider()

                        Text(tr(language, "মেয়াদ নির্বাচন", "Subscription Duration"), style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 3, 6, 12).forEach { m ->
                                FilterChip(
                                    selected = selectedMonths == m,
                                    onClick = { selectedMonths = m },
                                    label = { Text("${m}m") },
                                    modifier = Modifier.testTag("duration_${m}m")
                                )
                            }
                        }

                        if (availableMethods.isNotEmpty()) {
                            Text(tr(language, "পেমেন্ট মাধ্যম", "Payment Method"), style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                availableMethods.forEach { method ->
                                    FilterChip(
                                        selected = selectedMethod == method,
                                        onClick = { selectedMethod = method },
                                        label = { Text(if (language == AppLanguage.BN) method.labelBn else method.labelEn) }
                                    )
                                }
                            }
                        }

                        Surface(
                            color = EmeraldPrimary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    tr(
                                        language,
                                        "নির্বাচিত প্যাকেজ: ${selectedTier.name} ($selectedMonths মাস)\nপ্রদেয় পরিমাণ: ${Formatters.bdt(totalPayable)}",
                                        "Selected: ${selectedTier.name} ($selectedMonths month(s))\nPayable: ${Formatters.bdt(totalPayable)}"
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EmeraldPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    tr(
                                        language,
                                        "অর্ডার জমা দেওয়ার পর এই মূল্য লক থাকবে — পরে প্যাকেজের দাম বদলালেও জমা দেওয়া অর্ডার পরিবর্তন হবে না।",
                                        "Order price locks on submission even if package pricing changes later."
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedTextField(
                            value = senderNumber,
                            onValueChange = { senderNumber = it },
                            label = { Text(tr(language, "যে নম্বর থেকে টাকা পাঠিয়েছেন *", "Sender Mobile Number *")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("payment_sender_input")
                        )
                        OutlinedTextField(
                            value = transactionId,
                            onValueChange = { transactionId = it.uppercase() },
                            label = { Text(tr(language, "Transaction ID (TrxID) *", "Transaction ID (TrxID) *")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("payment_trx_input")
                        )
                        OutlinedButton(
                            onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.testTag("pick_payment_screenshot_button")
                        ) {
                            Text(
                                if (screenshotUri != null) tr(language, "পেমেন্ট স্ক্রিনশট সংযুক্ত হয়েছে ✓", "Payment Screenshot Attached ✓")
                                else tr(language, "পেমেন্ট স্ক্রিনশট যোগ করুন", "Attach Payment Screenshot")
                            )
                        }

                        Button(
                            onClick = {
                                onSubmitPayment(
                                    selectedMethod,
                                    senderNumber.trim(),
                                    transactionId.trim(),
                                    totalPayable,
                                    unitMonthly,
                                    selectedTier,
                                    selectedMonths,
                                    screenshotUri?.toString()
                                )
                                senderNumber = ""
                                transactionId = ""
                                screenshotUri = null
                            },
                            enabled = availableMethods.isNotEmpty() &&
                                chosenPkg?.enabled == true &&
                                totalPayable > 0.0 &&
                                senderNumber.trim().length >= 6 &&
                                transactionId.trim().length >= 4,
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("submit_payment_request_button")
                        ) {
                            Text(tr(language, "পেমেন্ট অর্ডার জমা দিন", "Submit Payment Request"))
                        }
                    }
                }
            }

            // My Payment Request History
            if (myRequests.isNotEmpty()) {
                item {
                    Text(tr(language, "আমার পেমেন্ট অর্ডার ইতিহাস", "My Order History"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(myRequests, key = { it.id }) { req ->
                    val st = PaymentRequestStatus.fromName(req.status)
                    val stColor = when (st) {
                        PaymentRequestStatus.APPROVED -> ProfitGreen
                        PaymentRequestStatus.REJECTED -> DueRed
                        PaymentRequestStatus.PENDING -> WarningAmber
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("${req.packageTier} (${req.durationMonths}m) • ${Formatters.bdt(req.amountBdt)}", fontWeight = FontWeight.Bold)
                                AssistChip(onClick = {}, label = { Text(if (language == AppLanguage.BN) st.labelBn else st.labelEn) })
                            }
                            Text("${req.method} • Sender: ${req.senderNumber} • TrxID: ${req.transactionId}", style = MaterialTheme.typography.bodySmall)
                            if (req.adminNote.isNotBlank()) {
                                Text(tr(language, "অ্যাডমিন নোট: ${req.adminNote}", "Admin Note: ${req.adminNote}"), style = MaterialTheme.typography.bodySmall, color = stColor)
                            }
                        }
                    }
                }
            }

            // Subscription Renewal History
            if (myRenewals.isNotEmpty()) {
                item {
                    Text(tr(language, "নবায়নের ইতিহাস (Renewal History)", "Renewal History"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(myRenewals, key = { it.id }) { ren ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = ProfitGreen, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("${ren.packageTier} • ${ren.durationMonths}m • ${Formatters.bdt(ren.amountBdt)}", fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "${Formatters.formatDate(ren.startedAtMillis)} → ${Formatters.formatDate(ren.expiresAtMillis)}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onOpenComplaints,
                    modifier = Modifier.fillMaxWidth().testTag("subscription_complaint_shortcut")
                ) {
                    Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr(language, "পেমেন্ট বা সাবস্ক্রিপশন সমস্যা? অভিযোগ জানান", "Issue with Payment or Plan? Open Support Ticket"))
                }
            }
        }
    }
}

@Composable
private fun PaymentAccountInfoRow(
    methodLabel: String,
    number: String,
    copied: Boolean,
    onCopy: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(methodLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(number, style = MaterialTheme.typography.titleSmall, color = EmeraldPrimary, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (copied) "Copied ✓" else "Copy")
            }
        }
    }
}
