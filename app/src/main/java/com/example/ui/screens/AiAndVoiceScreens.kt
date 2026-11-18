package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.example.data.model.AppLanguage
import com.example.data.model.TransactionType
import com.example.data.repository.VoiceAccountingDraft
import com.example.i18n.Strings
import com.example.i18n.tr
import com.example.ui.theme.DueRed
import com.example.ui.viewmodel.AiChatMessage
import com.example.ui.viewmodel.VoiceDraftState
import com.example.util.VoiceServices

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAccountingScreen(
    language: AppLanguage,
    draftState: VoiceDraftState,
    voiceServices: VoiceServices,
    onStartListening: () -> Unit,
    onSpeechCaptured: (String) -> Unit,
    onSpeechError: (String) -> Unit,
    onConfirmDraft: (VoiceAccountingDraft) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var commandText by remember { mutableStateOf("") }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartListening()
            voiceServices.startSpeechRecognition(
                language = language,
                onResult = { spoken ->
                    commandText = spoken
                    onSpeechCaptured(spoken)
                },
                onError = onSpeechError
            )
        } else {
            onSpeechError("Microphone permission denied.")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.voiceAccounting(language)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        tr(language, "উদাহরণ কমান্ড বলুন বা লিখুন:", "Speak or type a command:"),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text("• \"আজ ৫০০ টাকার পণ্য বিক্রি করেছি\"", style = MaterialTheme.typography.bodySmall)
                    Text("• \"রহিম ২০০ টাকা জমা দিয়েছে\"", style = MaterialTheme.typography.bodySmall)
                    Text("• \"আজ ৩০০ টাকা খরচ হয়েছে\"", style = MaterialTheme.typography.bodySmall)
                }
            }

            Button(
                onClick = {
                    val hasPerm = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) {
                        onStartListening()
                        voiceServices.startSpeechRecognition(
                            language = language,
                            onResult = { spoken ->
                                commandText = spoken
                                onSpeechCaptured(spoken)
                            },
                            onError = onSpeechError
                        )
                    } else {
                        micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("voice_mic_button")
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (draftState is VoiceDraftState.Listening) tr(language, "শুনছি... কথা বলুন", "Listening...")
                    else tr(language, "ভয়েস রেকর্ড করে হিসাব করুন", "Tap to Speak Command")
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = commandText,
                    onValueChange = { commandText = it },
                    label = { Text(tr(language, "অথবা কমান্ড লিখুন", "Or type command")) },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onSpeechCaptured(commandText) },
                    enabled = commandText.isNotBlank(),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text(tr(language, "খসড়া", "Parse"))
                }
            }

            when (draftState) {
                VoiceDraftState.Parsing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(tr(language, "হিসাবের খসড়া তৈরি হচ্ছে...", "Preparing accounting draft..."))
                    }
                }
                is VoiceDraftState.Error -> {
                    Text(draftState.message, color = DueRed, style = MaterialTheme.typography.bodySmall)
                }
                is VoiceDraftState.Ready -> {
                    var txType by remember(draftState) { mutableStateOf(draftState.draft.txType) }
                    var amountText by remember(draftState) { mutableStateOf(draftState.draft.amount.toString()) }
                    var partyText by remember(draftState) { mutableStateOf(draftState.draft.partyName) }
                    var noteText by remember(draftState) { mutableStateOf(draftState.draft.note) }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                tr(language, "নিশ্চিতকরণ খসড়া (সংরক্ষণের আগে যাচাই করুন)", "Confirmation Draft (Verify before saving)"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (draftState.draft.needsClarification && draftState.draft.clarificationQuestion.isNotBlank()) {
                                Text(draftState.draft.clarificationQuestion, color = DueRed, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TransactionType.entries.forEach { t ->
                                    FilterChip(
                                        selected = txType == t,
                                        onClick = { txType = t },
                                        label = { Text(if (language == AppLanguage.BN) t.labelBn else t.labelEn) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it },
                                label = { Text(tr(language, "পরিমাণ (৳)", "Amount (৳)")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = partyText,
                                onValueChange = { partyText = it },
                                label = { Text(tr(language, "কাস্টমার / সাপ্লায়ার", "Party Name")) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = noteText,
                                onValueChange = { noteText = it },
                                label = { Text(tr(language, "নোট", "Note")) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                                    Text(Strings.cancel(language))
                                }
                                Button(
                                    onClick = {
                                        val amt = amountText.toDoubleOrNull() ?: 0.0
                                        if (amt > 0) {
                                            onConfirmDraft(
                                                draftState.draft.copy(
                                                    txType = txType,
                                                    amount = amt,
                                                    partyName = partyText,
                                                    note = noteText
                                                )
                                            )
                                        }
                                    },
                                    enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0.0,
                                    modifier = Modifier.weight(1f).testTag("confirm_voice_draft_button")
                                ) {
                                    Text(Strings.confirm(language))
                                }
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiBusinessAssistantScreen(
    language: AppLanguage,
    aiAllowed: Boolean,
    messages: List<AiChatMessage>,
    isStreaming: Boolean,
    onAsk: (String) -> Unit,
    onBack: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val suggestions = if (language == AppLanguage.BN) {
        listOf(
            "আজ কত টাকা বিক্রি হয়েছে?",
            "এই মাসে কত লাভ হয়েছে?",
            "কার কাছে কত টাকা বাকি?",
            "কোন পণ্যের স্টক কম?"
        )
    } else {
        listOf(
            "What are today's total sales?",
            "How much net profit this month?",
            "Who owes the highest due?",
            "Which products are low in stock?"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(Strings.aiAssistant(language))
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (!aiAllowed) {
                Card(
                    modifier = Modifier.padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Text(
                        text = tr(
                            language,
                            "AI বিজনেস অ্যাসিস্ট্যান্ট ব্যবহার করতে Basic বা তার উপরের প্যাকেজ সক্রিয় করুন।",
                            "Please upgrade to Basic or higher to unlock the AI Business Assistant."
                        ),
                        modifier = Modifier.padding(16.dp)
                    )
                }
                return@Scaffold
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { q ->
                    AssistChip(
                        onClick = { onAsk(q) },
                        label = { Text(q, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Text(
                                text = tr(
                                    language,
                                    "আমি আপনার দোকানের নিজস্ব অনুমোদিত ডেটা বিশ্লেষণ করে প্রশ্নের উত্তর দিতে পারি। আমি কোনো হিসাব নিজে থেকে পরিবর্তন করি না।",
                                    "I answer questions strictly from your shop's authorized data. I cannot modify accounting records."
                                ),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                items(messages, key = { it.id }) { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (msg.isUser) MaterialTheme.colorScheme.primary
                            else if (msg.isError) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth(0.86f)
                        ) {
                            Text(
                                text = msg.text.ifBlank { "..." },
                                modifier = Modifier.padding(12.dp),
                                color = if (msg.isUser) MaterialTheme.colorScheme.onPrimary
                                else if (msg.isError) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(tr(language, "ব্যবসা সম্পর্কে প্রশ্ন করুন...", "Ask about your business...")) },
                    modifier = Modifier.weight(1f).testTag("ai_question_input")
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val q = input
                        input = ""
                        onAsk(q)
                    },
                    enabled = input.isNotBlank() && !isStreaming,
                    modifier = Modifier.testTag("ai_send_button")
                ) {
                    if (isStreaming) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}
