package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminDashboardKpis
import com.example.data.model.AdminUserDoc
import com.example.data.model.AppLanguage
import com.example.data.model.EmailOutboxDoc
import com.example.i18n.tr
import com.example.ui.theme.DueRed
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.WarningAmber
import com.example.util.Formatters

@Composable
fun AdminDashboardTab(
    language: AppLanguage,
    adminProfile: AdminUserDoc,
    kpis: AdminDashboardKpis,
    emailOutbox: List<EmailOutboxDoc>
) {
    val isSuperAdmin = adminProfile.role == "SUPER_ADMIN"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Welcome Header
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isSuperAdmin) {
                            tr(language, "সুপার অ্যাডমিন / মালিক কন্ট্রোল প্যানেল", "Super Admin / Owner Control Panel")
                        } else {
                            tr(language, "সাব-অ্যাডমিন অপারেশন ড্যাশবোর্ড", "Sub-Admin Operations Dashboard")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${adminProfile.name} (${adminProfile.email}) • Role: ${adminProfile.role}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (!isSuperAdmin) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            tr(language, "সীমিত Sub-Admin অ্যাক্সেস", "Restricted Sub-Admin Access"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            tr(
                                language,
                                "নিরাপত্তা নীতিমালা অনুযায়ী পূর্ণ রাজস্ব (Revenue), পেমেন্ট কনফিগারেশন, অ্যাডমিন ব্যবস্থাপনা এবং Audit Log শুধুমাত্র Owner/Super Admin-এর জন্য সংরক্ষিত। আপনার অনুমোদিত ট্যাবগুলো থেকে কাজ করুন।",
                                "Full owner revenue reports, payment settings, admin roles, and audit logs are restricted to the Super Admin Owner. Use your permitted tabs below."
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            KpiMiniBox(tr(language, "অপেক্ষমান পেমেন্ট", "Pending Pay"), kpis.pendingPaymentRequests.toString(), WarningAmber, Modifier.weight(1f))
                            KpiMiniBox(tr(language, "চলমান অভিযোগ", "Open Tickets"), kpis.pendingComplaints.toString(), EmeraldPrimary, Modifier.weight(1f))
                        }
                    }
                }
            }
            return@LazyColumn
        }

        // All 11 Owner KPI Cards
        item {
            Text(tr(language, "সার্বিক পরিসংখ্যান (১-১১ মেট্রিক্স)", "Platform Overview (All Metrics)"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiMiniBox(tr(language, "মোট নিবন্ধিত ইউজার", "Total Users"), kpis.totalUsers.toString(), EmeraldPrimary, Modifier.weight(1f))
                    KpiMiniBox(tr(language, "সক্রিয় ইউজার", "Active Users"), kpis.activeUsers.toString(), ProfitGreen, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiMiniBox(tr(language, "স্থগিত/ব্যান ইউজার", "Suspended / Banned"), "${kpis.suspendedUsers} / ${kpis.bannedUsers}", DueRed, Modifier.weight(1f))
                    KpiMiniBox(tr(language, "ফ্রি বনাম পেইড", "Free vs Paid"), "${kpis.freeUsers} / ${kpis.paidSubscribers}", EmeraldPrimary, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiMiniBox(tr(language, "পেন্ডিং পেমেন্ট", "Pending Payments"), kpis.pendingPaymentRequests.toString(), WarningAmber, Modifier.weight(1f))
                    KpiMiniBox(tr(language, "আজকের নতুন অর্ডার", "New Orders Today"), kpis.newPackageOrdersCount.toString(), EmeraldPrimary, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiMiniBox(tr(language, "পেন্ডিং অভিযোগ", "Pending Complaints"), kpis.pendingComplaints.toString(), WarningAmber, Modifier.weight(1f))
                    KpiMiniBox(tr(language, "মেয়াদ শেষ হচ্ছে (≤৭ দিন)", "Expiring Soon (≤7d)"), kpis.expiringSoonCount.toString(), DueRed, Modifier.weight(1f))
                }
            }
        }

        // Daily, Monthly, Yearly & Total Subscription Revenue Reports (Owner Only)
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr(language, "সাবস্ক্রিপশন আয় রিপোর্ট (শুধুমাত্র মালিক)", "Subscription Revenue Reports (Owner Only)"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = Formatters.bdt(kpis.totalSubscriptionRevenueBdt),
                        style = MaterialTheme.typography.headlineMedium,
                        color = ProfitGreen,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        tr(language, "মোট অনুমোদিত সাবস্ক্রিপশন আয়", "Total Approved Subscription Revenue"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KpiMiniBox(tr(language, "আজকের আয়", "Daily"), Formatters.bdt(kpis.dailyRevenueBdt), ProfitGreen, Modifier.weight(1f))
                        KpiMiniBox(tr(language, "চলতি মাস", "Monthly"), Formatters.bdt(kpis.monthlyRevenueBdt), EmeraldPrimary, Modifier.weight(1f))
                        KpiMiniBox(tr(language, "চলতি বছর", "Yearly"), Formatters.bdt(kpis.yearlyRevenueBdt), EmeraldPrimary, Modifier.weight(1f))
                    }
                }
            }
        }

        // Honest Backend Service & Notification Status Card
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        tr(language, "ইমেইল ও পুশ নোটিফিকেশন সিস্টেম স্ট্যাটাস", "Email & Push Backend Status"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        tr(
                            language,
                            "• In-App নোটিফিকেশন: সরাসরি চালু আছে (Firestore users/{uid}/notifications)\n• Email Outbox (${emailOutbox.size}টি কিউ): ব্যাকএন্ড সেটআপ বাকি (SMTP / Trigger Email Cloud Function সংযুক্ত করলে স্বয়ংক্রিয়ভাবে মেইল যাবে)\n• Remote FCM Push: ডিভাইস-টু-ডিভাইস পুশের জন্য Cloud Function + FCM সার্ভার টোকেন কনফিগারেশন বাকি।",
                            "• In-App Notifications: Active via Firestore.\n• Email Outbox (${emailOutbox.size} queued): Pending Backend Service (requires Trigger Email extension or Cloud Function SMTP).\n• Remote Push (FCM): Pending Cloud Function server dispatch setup."
                        ),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiMiniBox(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.11f)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}
