package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.TransactionEntity
import com.example.data.model.AppLanguage
import com.example.data.model.ReportPeriod
import com.example.data.model.TransactionType
import com.example.i18n.Strings
import com.example.i18n.tr
import com.example.ui.theme.DueRed
import com.example.ui.theme.DueRedBg
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.EmeraldPrimaryLight
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenBg
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.BusinessUiState
import com.example.util.Formatters

@Composable
fun DashboardScreen(
    state: BusinessUiState,
    onSelectBranch: (String) -> Unit,
    onSelectPeriod: (ReportPeriod) -> Unit,
    onSyncNow: () -> Unit,
    onOpenNewSale: () -> Unit,
    onOpenNewPurchase: () -> Unit,
    onOpenDueCollect: () -> Unit,
    onOpenExpense: () -> Unit,
    onOpenVoiceAccounting: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenNotifications: () -> Unit
) {
    val lang = state.language
    val m = state.metrics
    var showBranchMenu by remember { mutableStateOf(false) }
    val activeBranchLabel = if (state.activeBranchId.isBlank()) {
        Strings.allBranches(lang)
    } else {
        state.branches.firstOrNull { it.id == state.activeBranchId }?.name ?: Strings.allBranches(lang)
    }
    val unreadNotifs = state.notifications.count { !it.readFlag }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Top Business Header + Branch Selector + Sync & Notifications
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.profile?.shopName ?: "MUNAF ONLINE SHOP",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${state.operator.name} • ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showBranchMenu = true }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Store,
                                    contentDescription = "Branch",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = activeBranchLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showBranchMenu,
                                onDismissRequest = { showBranchMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(Strings.allBranches(lang)) },
                                    onClick = {
                                        onSelectBranch("")
                                        showBranchMenu = false
                                    }
                                )
                                state.branches.forEach { br ->
                                    DropdownMenuItem(
                                        text = { Text(br.name) },
                                        onClick = {
                                            onSelectBranch(br.id)
                                            showBranchMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Sync Pill
                AssistChip(
                    onClick = onSyncNow,
                    label = {
                        Text(
                            text = if (m.pendingSyncCount > 0) "${m.pendingSyncCount}" else Strings.synced(lang),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        if (state.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = if (m.pendingSyncCount > 0) Icons.Default.CloudSync else Icons.Default.CloudDone,
                                contentDescription = "Sync status",
                                modifier = Modifier.size(16.dp),
                                tint = if (m.pendingSyncCount > 0) WarningAmber else ProfitGreen
                            )
                        }
                    },
                    modifier = Modifier.testTag("sync_now_chip")
                )

                IconButton(
                    onClick = onOpenNotifications,
                    modifier = Modifier.testTag("notifications_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (unreadNotifs > 0) {
                                Badge { Text(unreadNotifs.toString()) }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                    }
                }
            }
        }

        // 2. Hero Cash Balance & Profit Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(EmeraldPrimary, EmeraldPrimaryLight))
                        )
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = Strings.cashBalance(lang),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Surface(
                                color = GoldAccent.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(
                                    text = state.profile?.subscriptionTier ?: "FREE",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = Formatters.bdt(m.netCashBalance),
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HeroMiniStat(
                                label = Strings.todayProfit(lang),
                                value = Formatters.bdt(m.todayProfit),
                                modifier = Modifier.weight(1f)
                            )
                            HeroMiniStat(
                                label = Strings.monthlyProfit(lang),
                                value = Formatters.bdt(m.monthlyProfit),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 3. Date Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = state.selectedPeriod == period,
                        onClick = { onSelectPeriod(period) },
                        label = { Text(if (lang == AppLanguage.BN) period.labelBn else period.labelEn) }
                    )
                }
            }
        }

        // 4. 2x3 KPI Summary Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile(
                        title = Strings.todaySales(lang),
                        value = Formatters.bdt(m.todaySales),
                        icon = Icons.Default.PointOfSale,
                        tint = ProfitGreen,
                        bg = ProfitGreenBg,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        title = Strings.todayPurchase(lang),
                        value = Formatters.bdt(m.todayPurchase),
                        icon = Icons.Default.AddShoppingCart,
                        tint = InfoBlue,
                        bg = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile(
                        title = Strings.totalReceivable(lang),
                        value = Formatters.bdt(m.totalReceivable),
                        icon = Icons.Default.ArrowDownward,
                        tint = WarningAmber,
                        bg = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        title = Strings.totalPayable(lang),
                        value = Formatters.bdt(m.totalPayable),
                        icon = Icons.Default.ArrowUpward,
                        tint = DueRed,
                        bg = DueRedBg,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile(
                        title = Strings.todayExpense(lang),
                        value = Formatters.bdt(m.todayExpense),
                        icon = Icons.Default.Payments,
                        tint = DueRed,
                        bg = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        title = tr(lang, "স্টক অবস্থা", "Stock Status"),
                        value = if (m.lowStockCount > 0) {
                            tr(lang, "${m.lowStockCount} কম স্টক", "${m.lowStockCount} Low")
                        } else {
                            Formatters.bdt(m.totalStockValue)
                        },
                        icon = if (m.lowStockCount > 0) Icons.Default.WarningAmber else Icons.Default.Inventory2,
                        tint = if (m.lowStockCount > 0) WarningAmber else EmeraldPrimary,
                        bg = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 5. Quick Actions Grid
        item {
            Text(
                text = tr(lang, "দ্রুত কার্যক্রম", "Quick Actions"),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionTile(
                        label = Strings.newSale(lang),
                        icon = Icons.Default.PointOfSale,
                        color = EmeraldPrimary,
                        onClick = onOpenNewSale,
                        modifier = Modifier.weight(1f).testTag("quick_new_sale")
                    )
                    QuickActionTile(
                        label = Strings.newPurchase(lang),
                        icon = Icons.Default.AddShoppingCart,
                        color = InfoBlue,
                        onClick = onOpenNewPurchase,
                        modifier = Modifier.weight(1f).testTag("quick_new_purchase")
                    )
                    QuickActionTile(
                        label = Strings.collectDue(lang),
                        icon = Icons.Default.AccountBalanceWallet,
                        color = ProfitGreen,
                        onClick = onOpenDueCollect,
                        modifier = Modifier.weight(1f).testTag("quick_collect_due")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionTile(
                        label = Strings.addExpense(lang),
                        icon = Icons.Default.Payments,
                        color = DueRed,
                        onClick = onOpenExpense,
                        modifier = Modifier.weight(1f).testTag("quick_expense")
                    )
                    QuickActionTile(
                        label = Strings.voiceAccounting(lang),
                        icon = Icons.Default.Mic,
                        color = GoldAccent,
                        onClick = onOpenVoiceAccounting,
                        modifier = Modifier.weight(1f).testTag("quick_voice_entry")
                    )
                    QuickActionTile(
                        label = Strings.aiAssistant(lang),
                        icon = Icons.Default.AutoAwesome,
                        color = EmeraldPrimaryLight,
                        onClick = onOpenAiAssistant,
                        modifier = Modifier.weight(1f).testTag("quick_ai_assistant")
                    )
                }
            }
        }

        // 6. Reports Shortcut Banner
        if (state.operator.canViewReports) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenReports() }
                        .testTag("dashboard_reports_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = "Reports",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr(lang, "লাভ-ক্ষতি, ক্যাশ ফ্লো ও PDF/Excel রিপোর্ট", "Profit & Loss, Cash Flow & PDF/Excel"),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = tr(lang, "বিস্তারিত আর্থিক রিপোর্ট দেখুন ও এক্সপোর্ট করুন", "View full accounting reports and export"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.TrendingUp, contentDescription = null)
                    }
                }
            }
        }

        // 7. Recent Transactions
        item {
            Text(
                text = tr(lang, "সাম্প্রতিক লেনদেন", "Recent Transactions"),
                style = MaterialTheme.typography.titleMedium
            )
        }

        val scopedTx = state.transactions
            .filter { state.activeBranchId.isBlank() || it.branchId == state.activeBranchId }
            .take(8)

        if (scopedTx.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = tr(lang, "এখনো কোনো লেনদেন হয়নি", "No transactions yet"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(scopedTx, key = { it.id }) { tx ->
                TransactionSummaryRow(tx = tx, lang = lang)
            }
        }
    }
}

@Composable
private fun HeroMiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.14f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(2.dp))
            Text(text = value, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    bg: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(86.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TransactionSummaryRow(
    tx: TransactionEntity,
    lang: AppLanguage,
    onClick: (() -> Unit)? = null
) {
    val type = TransactionType.fromName(tx.txType)
    val isInflow = type == TransactionType.SALE || type == TransactionType.DUE_COLLECTION || type == TransactionType.INCOME
    val cancelled = tx.status == "CANCELLED"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (lang == AppLanguage.BN) type.labelBn else type.labelEn,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (cancelled) DueRed else MaterialTheme.colorScheme.primary
                    )
                    if (cancelled) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = tr(lang, "[বাতিল]", "[CANCELLED]"),
                            style = MaterialTheme.typography.labelSmall,
                            color = DueRed
                        )
                    }
                }
                Text(
                    text = tx.partyName.ifBlank { tx.category },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = Formatters.formatDateTime(tx.txDateMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Formatters.bdt(tx.totalAmount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (cancelled) MaterialTheme.colorScheme.onSurfaceVariant else if (isInflow) ProfitGreen else DueRed
                )
                if (tx.dueAmount > 0 && !cancelled) {
                    Text(
                        text = "${tr(lang, "বাকি", "Due")}: ${Formatters.bdt(tx.dueAmount)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningAmber
                    )
                }
            }
        }
    }
}
