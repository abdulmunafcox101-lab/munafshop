package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.EmployeeEntity
import com.example.data.model.AdminUserDoc
import com.example.data.model.AppLanguage
import com.example.data.model.AppThemeMode
import com.example.data.model.EmployeeRole
import com.example.data.model.SubscriptionPackageDoc
import com.example.i18n.Strings
import com.example.i18n.tr
import com.example.ui.theme.DueRed
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ProfitGreen
import com.example.ui.viewmodel.BusinessUiState
import com.example.util.Formatters
import com.example.util.VoiceServices

@Composable
fun MoreHubScreen(
    state: BusinessUiState,
    activePackage: SubscriptionPackageDoc,
    adminProfile: AdminUserDoc?,
    voiceServices: VoiceServices,
    onOpenReports: () -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenComplaints: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    onOpenVoiceAccounting: () -> Unit,
    onOpenAdminPanel: () -> Unit,
    onSaveProfile: (String, String, String, String, String, String?) -> Unit,
    onCreateBranch: (String, String, String) -> Unit,
    onSaveEmployee: (String, String, EmployeeRole, String, String, Boolean, Boolean, Boolean, Boolean, Double) -> Unit,
    onDeleteEmployee: (String) -> Unit,
    onSwitchToOwner: () -> Unit,
    onSwitchToEmployee: (EmployeeEntity, String) -> Boolean,
    onDeleteVoiceNote: (String) -> Unit,
    onMarkNotificationsRead: () -> Unit,
    onChangeLanguage: (AppLanguage) -> Unit,
    onChangeTheme: (AppThemeMode) -> Unit,
    onToggleVoiceNotif: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit
) {
    val lang = state.language
    var showProfileDialog by remember { mutableStateOf(false) }
    var showBranchesDialog by remember { mutableStateOf(false) }
    var showEmployeesDialog by remember { mutableStateOf(false) }
    var showOperatorPinDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showVoiceNotesDialog by remember { mutableStateOf(false) }

    val unreadCount = state.notifications.count { !it.readFlag }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Shop & Subscription Banner Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        val uri = state.profile?.logoUri
                        if (!uri.isNullOrBlank()) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Shop Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(28.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(state.profile?.shopName ?: "MUNAF ONLINE SHOP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${state.operator.name} (${if (lang == AppLanguage.BN) state.operator.role.labelBn else state.operator.role.labelEn})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "${tr(lang, "প্যাকেজ", "Plan")}: ${if (lang == AppLanguage.BN) activePackage.nameBn else activePackage.nameEn} • ${state.userEmail}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showProfileDialog = true }, modifier = Modifier.testTag("edit_profile_button")) {
                        Text(tr(lang, "সম্পাদনা", "Edit"))
                    }
                }
            }
        }

        // Primary Hub Actions
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    HubRow(Icons.Default.BarChart, Strings.reports(lang), tr(lang, "লাভ-ক্ষতি, ক্যাশ ফ্লো, PDF ও Excel", "P&L, Cash Flow, PDF & Excel"), "hub_reports_row", onOpenReports)
                    HorizontalDivider()
                    HubRow(Icons.Default.WorkspacePremium, tr(lang, "সাবস্ক্রিপশন ও পেমেন্ট", "Subscription & Payment"), "${activePackage.nameBn} / ${activePackage.nameEn}", "hub_subscription_row", onOpenSubscription)
                    HorizontalDivider()
                    HubRow(Icons.Default.SupportAgent, tr(lang, "সহায়তা ও অভিযোগ", "Support & Complaints"), tr(lang, "টিকিট জমা ও সমাধান ট্র্যাক করুন", "Submit tickets & track admin replies"), "hub_complaints_row", onOpenComplaints)
                    HorizontalDivider()
                    HubRow(Icons.Default.AutoAwesome, Strings.aiAssistant(lang), tr(lang, "AI দিয়ে ব্যবসার হিসাব বিশ্লেষণ", "Ask AI about your sales & due"), "hub_ai_row", onOpenAiAssistant)
                    HorizontalDivider()
                    HubRow(Icons.Default.Mic, Strings.voiceAccounting(lang), tr(lang, "কথা বলে বিক্রি ও খরচ এন্ট্রি", "Speak to draft transactions"), "hub_voice_row", onOpenVoiceAccounting)
                    if (adminProfile != null) {
                        HorizontalDivider()
                        HubRow(
                            Icons.Default.AdminPanelSettings,
                            if (adminProfile.role == "SUPER_ADMIN") tr(lang, "সুপার অ্যাডমিন প্যানেল", "Super Admin Panel") else tr(lang, "সাব-অ্যাডমিন প্যানেল", "Sub-Admin Panel"),
                            tr(lang, "পেমেন্ট যাচাই, ইউজার ও প্যাকেজ নিয়ন্ত্রণ", "Verify payments, users & packages"),
                            "hub_admin_row",
                            onOpenAdminPanel
                        )
                    }
                }
            }
        }

        // Operations & RBAC
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    HubRow(
                        Icons.Default.Store,
                        tr(lang, "শাখা ব্যবস্থাপনা (${state.branches.size}/${activePackage.maxBranches})", "Branches (${state.branches.size}/${activePackage.maxBranches})"),
                        tr(lang, "একাধিক দোকান বা শাখা যোগ করুন", "Manage business branches"),
                        "hub_branches_row"
                    ) { showBranchesDialog = true }
                    HorizontalDivider()
                    HubRow(
                        Icons.Default.Badge,
                        tr(lang, "কর্মচারী ও ভূমিকা (${state.employees.size}/${activePackage.maxEmployees})", "Staff & RBAC (${state.employees.size}/${activePackage.maxEmployees})"),
                        tr(lang, "PIN ভিত্তিক কর্মচারী অ্যাক্সেস নিয়ন্ত্রণ", "Manage staff permissions & PIN"),
                        "hub_employees_row"
                    ) { showEmployeesDialog = true }
                    HorizontalDivider()
                    HubRow(
                        Icons.Default.Badge,
                        tr(lang, "অপারেটর পরিবর্তন করুন (PIN)", "Switch Operator (PIN)"),
                        tr(lang, "বর্তমান: ${state.operator.name}", "Current: ${state.operator.name}"),
                        "hub_switch_operator_row"
                    ) { showOperatorPinDialog = true }
                    HorizontalDivider()
                    HubRow(
                        Icons.Default.Notifications,
                        tr(lang, "নোটিফিকেশন ($unreadCount নতুন)", "Notifications ($unreadCount unread)"),
                        tr(lang, "বাকি, স্টক ও লেনদেন সতর্কতা", "Low stock & due alerts"),
                        "hub_notifications_row"
                    ) {
                        onMarkNotificationsRead()
                        showNotificationsDialog = true
                    }
                    HorizontalDivider()
                    HubRow(
                        Icons.Default.GraphicEq,
                        tr(lang, "ভয়েস নোট তালিকা (${state.voiceNotes.size})", "Voice Notes (${state.voiceNotes.size})"),
                        tr(lang, "কাস্টমার ও খাতার রেকর্ডকৃত অডিও", "Play or manage recorded notes"),
                        "hub_voice_notes_row"
                    ) { showVoiceNotesDialog = true }
                }
            }
        }

        // Settings: Language, Theme, Voice TTS, Cloud Sync
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr(lang, "সেটিংস ও সিঙ্ক", "Settings & Sync"), style = MaterialTheme.typography.titleMedium)

                    Text(tr(lang, "ভাষা / Language", "Language"), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = lang == AppLanguage.BN, onClick = { onChangeLanguage(AppLanguage.BN) }, label = { Text("বাংলা") }, modifier = Modifier.testTag("settings_lang_bn"))
                        FilterChip(selected = lang == AppLanguage.EN, onClick = { onChangeLanguage(AppLanguage.EN) }, label = { Text("English") }, modifier = Modifier.testTag("settings_lang_en"))
                    }

                    Text(tr(lang, "থিম মোড", "Theme Mode"), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = state.themeMode == AppThemeMode.SYSTEM, onClick = { onChangeTheme(AppThemeMode.SYSTEM) }, label = { Text("System") })
                        FilterChip(selected = state.themeMode == AppThemeMode.LIGHT, onClick = { onChangeTheme(AppThemeMode.LIGHT) }, label = { Text("Light") })
                        FilterChip(selected = state.themeMode == AppThemeMode.DARK, onClick = { onChangeTheme(AppThemeMode.DARK) }, label = { Text("Dark") })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tr(lang, "ভয়েস নোটিফিকেশন (TTS)", "Voice Notifications (TTS)"), style = MaterialTheme.typography.bodyMedium)
                            Text(tr(lang, "নতুন বিক্রি ও পেমেন্টে অডিও বার্তা", "Spoken confirmation on sale & payment"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.voiceNotifEnabled, onCheckedChange = onToggleVoiceNotif, modifier = Modifier.testTag("voice_tts_switch"))
                    }

                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onSyncNow, modifier = Modifier.weight(1f).testTag("more_sync_button")) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr(lang, "সিঙ্ক (${state.metrics.pendingSyncCount})", "Sync (${state.metrics.pendingSyncCount})"))
                        }
                        Button(
                            onClick = onSignOut,
                            colors = ButtonDefaults.buttonColors(containerColor = DueRed),
                            modifier = Modifier.weight(1f).testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr(lang, "লগআউট", "Sign Out"))
                        }
                    }
                }
            }
        }
    }

    if (showProfileDialog) {
        EditProfileDialog(state, onDismiss = { showProfileDialog = false }) { shop, owner, phone, addr, type, logo ->
            onSaveProfile(shop, owner, phone, addr, type, logo)
            showProfileDialog = false
        }
    }
    if (showBranchesDialog) {
        BranchesManagerDialog(state, activePackage, onCreateBranch) { showBranchesDialog = false }
    }
    if (showEmployeesDialog) {
        EmployeesManagerDialog(state, activePackage, onSaveEmployee, onDeleteEmployee) { showEmployeesDialog = false }
    }
    if (showOperatorPinDialog) {
        OperatorSwitchDialog(state, onSwitchToOwner, onSwitchToEmployee) { showOperatorPinDialog = false }
    }
    if (showNotificationsDialog) {
        NotificationsDialog(state) { showNotificationsDialog = false }
    }
    if (showVoiceNotesDialog) {
        VoiceNotesDialog(state, voiceServices, onDeleteVoiceNote) { showVoiceNotesDialog = false }
    }
}

@Composable
private fun HubRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditProfileDialog(
    state: BusinessUiState,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String?) -> Unit
) {
    val lang = state.language
    val p = state.profile
    var shop by remember { mutableStateOf(p?.shopName.orEmpty()) }
    var owner by remember { mutableStateOf(p?.ownerName.orEmpty()) }
    var phone by remember { mutableStateOf(p?.phone.orEmpty()) }
    var address by remember { mutableStateOf(p?.address.orEmpty()) }
    var type by remember { mutableStateOf(p?.businessType.orEmpty()) }
    var logoUri by remember { mutableStateOf<Uri?>(p?.logoUri?.let { Uri.parse(it) }) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) logoUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(lang, "ব্যবসার প্রোফাইল", "Business Profile")) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(tr(lang, "দোকানের লোগো পরিবর্তন করুন", "Change Shop Logo"))
                }
                OutlinedTextField(value = shop, onValueChange = { shop = it }, label = { Text(tr(lang, "দোকানের নাম", "Shop Name")) })
                OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text(tr(lang, "মালিকের নাম", "Owner Name")) })
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(tr(lang, "মোবাইল নম্বর", "Phone")) })
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(tr(lang, "ঠিকানা", "Address")) })
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text(tr(lang, "ব্যবসার ধরন", "Business Type")) })
            }
        },
        confirmButton = {
            Button(onClick = { if (shop.isNotBlank()) onSave(shop, owner, phone, address, type, logoUri?.toString()) }) {
                Text(Strings.save(lang))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel(lang)) } }
    )
}

@Composable
private fun BranchesManagerDialog(
    state: BusinessUiState,
    pkg: SubscriptionPackageDoc,
    onCreateBranch: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val lang = state.language
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(lang, "শাখা ব্যবস্থাপনা (${state.branches.size}/${pkg.maxBranches})", "Branches (${state.branches.size}/${pkg.maxBranches})")) },
        text = {
            Column(modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.branches.forEach { b ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(b.name + if (b.mainBranch) " ★" else "", fontWeight = FontWeight.Bold)
                            if (b.address.isNotBlank()) Text(b.address, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                HorizontalDivider()
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(tr(lang, "নতুন শাখার নাম", "New Branch Name")) })
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(tr(lang, "ঠিকানা", "Address")) })
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(tr(lang, "ফোন", "Phone")) })
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) { onCreateBranch(name, address, phone); name = ""; address = ""; phone = "" } },
                enabled = name.isNotBlank()
            ) { Text(tr(lang, "শাখা যোগ করুন", "Add Branch")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.close(lang)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmployeesManagerDialog(
    state: BusinessUiState,
    pkg: SubscriptionPackageDoc,
    onSaveEmployee: (String, String, EmployeeRole, String, String, Boolean, Boolean, Boolean, Boolean, Double) -> Unit,
    onDeleteEmployee: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val lang = state.language
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("1234") }
    var role by remember { mutableStateOf(EmployeeRole.EMPLOYEE) }
    var branchId by remember { mutableStateOf(state.branches.firstOrNull()?.id.orEmpty()) }
    var canSell by remember { mutableStateOf(true) }
    var canPurchase by remember { mutableStateOf(false) }
    var canReports by remember { mutableStateOf(false) }
    var canStock by remember { mutableStateOf(true) }
    var salary by remember { mutableStateOf("") }
    var branchMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(lang, "কর্মচারী ও RBAC (${state.employees.size}/${pkg.maxEmployees})", "Staff & RBAC (${state.employees.size}/${pkg.maxEmployees})")) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.employees.forEach { emp ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${emp.name} (${emp.role})", fontWeight = FontWeight.Bold)
                            Text("PIN: ${emp.pinCode} • ${Formatters.bdt(emp.monthlySalary)}", style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { onDeleteEmployee(emp.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove Employee", tint = DueRed)
                        }
                    }
                    HorizontalDivider()
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(tr(lang, "নাম", "Name")) })
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(tr(lang, "মোবাইল", "Phone")) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("PIN") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = salary, onValueChange = { salary = it }, label = { Text(tr(lang, "বেতন", "Salary")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = role == EmployeeRole.MANAGER, onClick = { role = EmployeeRole.MANAGER }, label = { Text("Manager") })
                    FilterChip(selected = role == EmployeeRole.EMPLOYEE, onClick = { role = EmployeeRole.EMPLOYEE }, label = { Text("Employee") })
                }
                if (state.branches.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = branchMenu, onExpandedChange = { branchMenu = it }) {
                        val bName = state.branches.firstOrNull { it.id == branchId }?.name ?: state.branches.first().name
                        OutlinedTextField(
                            value = bName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(tr(lang, "শাখা", "Branch")) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(branchMenu) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = branchMenu, onDismissRequest = { branchMenu = false }) {
                            state.branches.forEach { b ->
                                DropdownMenuItem(text = { Text(b.name) }, onClick = { branchId = b.id; branchMenu = false })
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = canSell, onCheckedChange = { canSell = it }); Text(tr(lang, "বিক্রি", "Sell"))
                    Checkbox(checked = canPurchase, onCheckedChange = { canPurchase = it }); Text(tr(lang, "ক্রয়", "Buy"))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = canStock, onCheckedChange = { canStock = it }); Text(tr(lang, "স্টক", "Stock"))
                    Checkbox(checked = canReports, onCheckedChange = { canReports = it }); Text(tr(lang, "রিপোর্ট", "Report"))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSaveEmployee(name, phone, role, pin, branchId, canSell, canPurchase, canReports, canStock, salary.toDoubleOrNull() ?: 0.0)
                        name = ""; phone = ""; salary = ""
                    }
                },
                enabled = name.isNotBlank()
            ) { Text(Strings.save(lang)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.close(lang)) } }
    )
}

@Composable
private fun OperatorSwitchDialog(
    state: BusinessUiState,
    onSwitchToOwner: () -> Unit,
    onSwitchToEmployee: (EmployeeEntity, String) -> Boolean,
    onDismiss: () -> Unit
) {
    val lang = state.language
    var selectedEmp by remember { mutableStateOf<EmployeeEntity?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(lang, "অপারেটর / ভূমিকা পরিবর্তন", "Switch Operator Session")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onSwitchToOwner(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr(lang, "মালিক মোডে ফিরুন (সব অনুমতি)", "Switch to Owner Mode (Full Access)"))
                }
                state.employees.forEach { emp ->
                    OutlinedButton(
                        onClick = { selectedEmp = emp; errorText = null; pinInput = "" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${emp.name} (${emp.role})")
                    }
                }
                if (selectedEmp != null) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        label = { Text(tr(lang, "${selectedEmp!!.name}-এর PIN দিন", "Enter PIN for ${selectedEmp!!.name}")) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (onSwitchToEmployee(selectedEmp!!, pinInput)) {
                                onDismiss()
                            } else {
                                errorText = tr(lang, "ভুল PIN", "Incorrect PIN")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(tr(lang, "লগইন করুন", "Verify PIN")) }
                }
                if (errorText != null) Text(errorText!!, color = DueRed, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.close(lang)) } }
    )
}

@Composable
private fun NotificationsDialog(state: BusinessUiState, onDismiss: () -> Unit) {
    val lang = state.language
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(lang, "নোটিফিকেশন", "Notifications")) },
        text = {
            Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.notifications.isEmpty()) {
                    Text(tr(lang, "কোনো নতুন নোটিফিকেশন নেই।", "No notifications yet."))
                } else {
                    state.notifications.forEach { n ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(n.title, fontWeight = FontWeight.Bold)
                                Text(n.body, style = MaterialTheme.typography.bodySmall)
                                Text(Formatters.formatDateTime(n.createdAtMillis), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(Strings.close(lang)) } }
    )
}

@Composable
private fun VoiceNotesDialog(
    state: BusinessUiState,
    voiceServices: VoiceServices,
    onDeleteVoiceNote: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val lang = state.language
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(lang, "সংরক্ষিত ভয়েস নোট", "Saved Voice Notes")) },
        text = {
            Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.voiceNotes.isEmpty()) {
                    Text(tr(lang, "কোনো ভয়েস নোট নেই। খাতা স্ক্রিন থেকে রেকর্ড করুন।", "No voice notes recorded yet."))
                } else {
                    state.voiceNotes.forEach { vn ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(vn.title, fontWeight = FontWeight.Bold)
                                    Text("${vn.linkedLabel} • ${vn.durationSec}s", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { voiceServices.playVoiceNote(vn.filePath) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = ProfitGreen)
                                }
                                IconButton(onClick = { onDeleteVoiceNote(vn.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DueRed)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(Strings.close(lang)) } }
    )
}
