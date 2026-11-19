package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.AppLanguage
import com.example.data.model.ComplaintReplyDoc
import com.example.data.model.ComplaintStatus
import com.example.data.model.ComplaintTicketDoc
import com.example.i18n.Strings
import com.example.i18n.tr
import com.example.ui.theme.DueRed
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.WarningAmber
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerComplaintsScreen(
    language: AppLanguage,
    shopName: String,
    complaints: List<ComplaintTicketDoc>,
    selectedReplies: List<ComplaintReplyDoc>,
    onSelectTicket: (String?) -> Unit,
    onSubmitComplaint: (subject: String, description: String, attachmentUri: String?) -> Unit,
    onReplyAsCustomer: (ticket: ComplaintTicketDoc, message: String) -> Unit,
    onBack: () -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var attachmentUri by remember { mutableStateOf<Uri?>(null) }
    var activeTicket by remember { mutableStateOf<ComplaintTicketDoc?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) attachmentUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr(language, "সহায়তা ও অভিযোগ (Complaints)", "Support & Complaints")) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("complaints_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr(language, "পিছনে", "Back"))
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
            item {
                Card(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SupportAgent, contentDescription = null, tint = EmeraldPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                tr(language, "নতুন অভিযোগ / টিকিট তৈরি করুন", "Submit New Complaint Ticket"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            tr(
                                language,
                                "দোকান: $shopName • প্রতিটি অভিযোগে একটি ইউনিক Ticket ID তৈরি হবে এবং অ্যাডমিন উত্তর দিলে অ্যাপ-ভিতরের নোটিফিকেশন পাবেন।",
                                "Shop: $shopName • A unique Ticket ID is generated and you'll receive an In-App notification when Admin replies."
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            label = { Text(tr(language, "অভিযোগের বিষয় *", "Subject *")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("complaint_subject_input")
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text(tr(language, "বিস্তারিত বিবরণ *", "Detailed Description *")) },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth().testTag("complaint_desc_input")
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.testTag("complaint_attach_photo_button")
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (attachmentUri != null) tr(language, "ছবি সংযুক্ত হয়েছে ✓", "Image Attached ✓")
                                    else tr(language, "ছবি সংযুক্ত করুন", "Attach Image")
                                )
                            }
                            if (attachmentUri != null) {
                                AsyncImage(
                                    model = attachmentUri,
                                    contentDescription = "Complaint Attachment",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                                )
                            }
                        }
                        Button(
                            onClick = {
                                onSubmitComplaint(subject.trim(), description.trim(), attachmentUri?.toString())
                                subject = ""
                                description = ""
                                attachmentUri = null
                            },
                            enabled = subject.trim().length >= 2 && description.trim().length >= 3,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("submit_complaint_button")
                        ) {
                            Text(tr(language, "অভিযোগ জমা দিন", "Submit Ticket"))
                        }
                    }
                }
            }

            item {
                Text(
                    tr(language, "আমার অভিযোগের ইতিহাস (${complaints.size})", "My Ticket History (${complaints.size})"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (complaints.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(
                            tr(language, "এখনো কোনো অভিযোগ জমা দেওয়া হয়নি।", "No complaints submitted yet."),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                items(complaints, key = { it.id }) { ticket ->
                    val statusEnum = ComplaintStatus.fromName(ticket.status)
                    val badgeColor = complaintStatusColor(statusEnum)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                activeTicket = ticket
                                onSelectTicket(ticket.id)
                            }
                            .testTag("ticket_card_${ticket.ticketCode}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(ticket.ticketCode, style = MaterialTheme.typography.labelLarge, color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                                AssistChip(
                                    onClick = {},
                                    label = { Text(if (language == AppLanguage.BN) statusEnum.labelBn else statusEnum.labelEn) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = badgeColor.copy(alpha = 0.14f),
                                        labelColor = badgeColor
                                    )
                                )
                            }
                            Text(ticket.subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(ticket.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            if (ticket.lastReplyText.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "${ticket.lastReplyBy}: ${ticket.lastReplyText}",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(8.dp),
                                        maxLines = 2
                                    )
                                }
                            }
                            val ts = ticket.updatedAt?.toDate()?.time ?: ticket.createdAt?.toDate()?.time ?: 0L
                            if (ts > 0L) {
                                Text(
                                    Formatters.formatDateTime(ts),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val currentTicket = activeTicket?.let { sel -> complaints.firstOrNull { it.id == sel.id } ?: sel }
    if (currentTicket != null) {
        ComplaintConversationDialog(
            language = language,
            ticket = currentTicket,
            replies = selectedReplies,
            onDismiss = {
                activeTicket = null
                onSelectTicket(null)
            },
            onSendReply = { msg -> onReplyAsCustomer(currentTicket, msg) }
        )
    }
}

@Composable
private fun ComplaintConversationDialog(
    language: AppLanguage,
    ticket: ComplaintTicketDoc,
    replies: List<ComplaintReplyDoc>,
    onDismiss: () -> Unit,
    onSendReply: (String) -> Unit
) {
    var replyText by remember { mutableStateOf("") }
    val statusEnum = ComplaintStatus.fromName(ticket.status)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${ticket.ticketCode} • ${if (language == AppLanguage.BN) statusEnum.labelBn else statusEnum.labelEn}")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(ticket.subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(ticket.description, style = MaterialTheme.typography.bodySmall)
                if (!ticket.attachmentUri.isNullOrBlank()) {
                    AsyncImage(
                        model = ticket.attachmentUri,
                        contentDescription = "Ticket Attachment",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(10.dp))
                    )
                }
                HorizontalDivider()
                Text(tr(language, "কথোপকথনের ইতিহাস", "Conversation History"), style = MaterialTheme.typography.labelLarge)
                if (replies.isEmpty()) {
                    Text(tr(language, "এখনো কোনো উত্তর আসেনি।", "No replies yet."), style = MaterialTheme.typography.labelSmall)
                } else {
                    replies.forEach { rep ->
                        val isAdmin = rep.senderRole != "CUSTOMER"
                        Surface(
                            color = if (isAdmin) EmeraldPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    "${rep.senderName} (${rep.senderRole})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAdmin) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(rep.message, style = MaterialTheme.typography.bodySmall)
                                val repTime = rep.createdAt?.toDate()?.time ?: 0L
                                if (repTime > 0L) {
                                    Text(
                                        Formatters.formatDateTime(repTime),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                if (statusEnum != ComplaintStatus.RESOLVED && statusEnum != ComplaintStatus.REJECTED) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            label = { Text(tr(language, "উত্তর লিখুন", "Write reply")) },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (replyText.isNotBlank()) {
                                    onSendReply(replyText.trim())
                                    replyText = ""
                                }
                            },
                            enabled = replyText.isNotBlank()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Reply", tint = EmeraldPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(Strings.close(language)) } }
    )
}

fun complaintStatusColor(status: ComplaintStatus): Color = when (status) {
    ComplaintStatus.PENDING -> WarningAmber
    ComplaintStatus.IN_PROGRESS -> EmeraldPrimary
    ComplaintStatus.RESOLVED -> ProfitGreen
    ComplaintStatus.REJECTED -> DueRed
}
