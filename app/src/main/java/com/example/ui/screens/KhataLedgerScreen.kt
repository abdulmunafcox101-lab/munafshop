package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.local.PartyEntity
import com.example.data.model.AppLanguage
import com.example.data.model.PartyType
import com.example.i18n.Strings
import com.example.i18n.tr
import com.example.ui.theme.DueRed
import com.example.ui.theme.ProfitGreen
import com.example.ui.viewmodel.BusinessUiState
import com.example.util.Formatters
import com.example.util.VoiceServices

@Composable
fun KhataLedgerScreen(
    state: BusinessUiState,
    voiceServices: VoiceServices,
    onAddParty: (PartyType, String, String, String, Double) -> Unit,
    onRecordPartyPayment: (PartyEntity, Double, String) -> Unit,
    onSaveVoiceNote: (String, String, Int, String, String) -> Unit,
    onDeleteVoiceNote: (String) -> Unit
) {
    val lang = state.language
    var tabIndex by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedParty by remember { mutableStateOf<PartyEntity?>(null) }

    val activeType = if (tabIndex == 0) PartyType.CUSTOMER else PartyType.SUPPLIER
    val parties = state.parties.filter {
        it.partyType == activeType.name &&
            (state.activeBranchId.isBlank() || it.branchId == state.activeBranchId) &&
            (search.isBlank() || it.name.contains(search, true) || it.phone.contains(search, true))
    }
    val totalDue = parties.sumOf { it.dueAmount }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("add_party_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Party")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text(tr(lang, "কাস্টমার খাতা", "Customers")) }
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text(tr(lang, "সাপ্লায়ার খাতা", "Suppliers")) }
                )
            }

            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (tabIndex == 0) tr(lang, "মোট পাওনা (বাকি)", "Total Receivable")
                            else tr(lang, "মোট দেনা", "Total Payable"),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = Formatters.bdt(totalDue),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = DueRed
                        )
                    }
                }

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text(tr(lang, "নাম বা মোবাইল দিয়ে খুঁজুন", "Search name or phone")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(parties, key = { it.id }) { party ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedParty = party },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(party.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                if (party.phone.isNotBlank()) {
                                    Text(party.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = "${tr(lang, "মোট", "Total")}: ${Formatters.bdt(party.totalAmount)} • ${tr(lang, "জমা", "Paid")}: ${Formatters.bdt(party.paidAmount)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = Formatters.bdt(party.dueAmount),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (party.dueAmount > 0) DueRed else ProfitGreen
                                )
                                Text(
                                    text = if (tabIndex == 0) tr(lang, "বাকি", "Due") else tr(lang, "দেনা", "Payable"),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddPartyDialog(
            partyType = activeType,
            lang = lang,
            onDismiss = { showAddDialog = false },
            onSave = { n, p, a, due ->
                onAddParty(activeType, n, p, a, due)
                showAddDialog = false
            }
        )
    }

    selectedParty?.let { party ->
        val freshParty = state.parties.firstOrNull { it.id == party.id } ?: party
        PartyDetailDialog(
            party = freshParty,
            state = state,
            voiceServices = voiceServices,
            onDismiss = { selectedParty = null },
            onCollectOrPay = { amt, note -> onRecordPartyPayment(freshParty, amt, note) },
            onSaveVoiceNote = onSaveVoiceNote,
            onDeleteVoiceNote = onDeleteVoiceNote
        )
    }
}

@Composable
private fun AddPartyDialog(
    partyType: PartyType,
    lang: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var openingDue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (partyType == PartyType.CUSTOMER) tr(lang, "নতুন কাস্টমার যোগ", "New Customer")
                else tr(lang, "নতুন সাপ্লায়ার যোগ", "New Supplier")
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(tr(lang, "নাম *", "Name *")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("party_name_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(tr(lang, "মোবাইল নম্বর", "Mobile")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(tr(lang, "ঠিকানা", "Address")) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = openingDue,
                    onValueChange = { openingDue = it },
                    label = { Text(tr(lang, "পূর্বের বাকি (৳)", "Opening Due (৳)")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, phone, address, openingDue.toDoubleOrNull() ?: 0.0) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_party_button")
            ) { Text(Strings.save(lang)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.cancel(lang)) }
        }
    )
}

@Composable
private fun PartyDetailDialog(
    party: PartyEntity,
    state: BusinessUiState,
    voiceServices: VoiceServices,
    onDismiss: () -> Unit,
    onCollectOrPay: (Double, String) -> Unit,
    onSaveVoiceNote: (String, String, Int, String, String) -> Unit,
    onDeleteVoiceNote: (String) -> Unit
) {
    val context = LocalContext.current
    val lang = state.language
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceServices.startRecordingVoiceNote().onSuccess { isRecording = true }
        }
    }

    val partyTx = state.transactions.filter { it.partyId == party.id }.take(10)
    val partyNotes = state.voiceNotes.filter { it.linkedPartyId == party.id }

    AlertDialog(
        onDismissRequest = {
            voiceServices.stopPlayback()
            onDismiss()
        },
        title = { Text("${party.name} (${Formatters.bdt(party.dueAmount)})") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = {
                            Text(
                                if (party.partyType == PartyType.CUSTOMER.name)
                                    tr(lang, "বাকি আদায় পরিমাণ (৳)", "Collect Amount (৳)")
                                else tr(lang, "পরিশোধ পরিমাণ (৳)", "Pay Amount (৳)")
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text(tr(lang, "নোট", "Note")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull() ?: 0.0
                            if (amt > 0) {
                                onCollectOrPay(amt, noteText)
                                amountText = ""
                                noteText = ""
                            }
                        },
                        enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (party.partyType == PartyType.CUSTOMER.name) tr(lang, "আদায় সংরক্ষণ", "Record Collection")
                            else tr(lang, "পরিশোধ সংরক্ষণ", "Record Payment")
                        )
                    }
                }

                item {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(tr(lang, "ভয়েস নোট", "Voice Notes"), style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(
                            onClick = {
                                if (!isRecording) {
                                    val hasPerm = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPerm) {
                                        voiceServices.startRecordingVoiceNote().onSuccess { isRecording = true }
                                    } else {
                                        micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                } else {
                                    isRecording = false
                                    val recorded = voiceServices.stopRecordingVoiceNote()
                                    if (recorded != null) {
                                        onSaveVoiceNote(
                                            "${party.name} Memo",
                                            recorded.first,
                                            recorded.second,
                                            party.id,
                                            party.name
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isRecording) tr(lang, "থামান", "Stop") else tr(lang, "রেকর্ড", "Record"))
                        }
                    }
                }

                items(partyNotes, key = { it.id }) { vn ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { voiceServices.playVoiceNote(vn.filePath) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                        }
                        Text("${vn.title} (${vn.durationSec}s)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { onDeleteVoiceNote(vn.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DueRed)
                        }
                    }
                }

                if (partyTx.isNotEmpty()) {
                    item {
                        HorizontalDivider()
                        Text(tr(lang, "লেনদেনের ইতিহাস", "Ledger History"), style = MaterialTheme.typography.titleSmall)
                    }
                    items(partyTx, key = { it.id }) { tx ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${tx.txType} • ${Formatters.formatDate(tx.txDateMillis)}", style = MaterialTheme.typography.bodySmall)
                            Text("${Formatters.bdt(tx.totalAmount)} (Paid: ${Formatters.bdt(tx.paidAmount)})", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                voiceServices.stopPlayback()
                onDismiss()
            }) { Text(Strings.close(lang)) }
        }
    )
}
