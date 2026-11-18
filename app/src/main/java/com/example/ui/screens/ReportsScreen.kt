package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableView
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.ReportPeriod
import com.example.data.model.TransactionType
import com.example.i18n.Strings
import com.example.i18n.tr
import com.example.ui.theme.DueRed
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.BusinessUiState
import com.example.util.Formatters
import com.example.util.ReportExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    state: BusinessUiState,
    exportAllowed: Boolean,
    onSelectPeriod: (ReportPeriod) -> Unit,
    onSelectBranch: (String) -> Unit,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val lang = state.language
    val m = state.metrics

    if (!state.operator.canViewReports) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(Strings.reports(lang)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { pad ->
            Column(modifier = Modifier.fillMaxSize().padding(pad).padding(24.dp)) {
                Text(
                    text = tr(lang, "আর্থিক রিপোর্ট দেখার অনুমতি আপনার নেই। মালিকের অনুমোদন প্রয়োজন।", "You do not have permission to view financial reports."),
                    color = DueRed
                )
            }
        }
        return
    }

    val startMillis = Formatters.periodStartMillis(state.selectedPeriod)
    val filteredTx = state.transactions.filter {
        it.status == "COMPLETED" &&
            it.txDateMillis >= startMillis &&
            (state.activeBranchId.isBlank() || it.branchId == state.activeBranchId)
    }

    val shopName = state.profile?.shopName ?: "MUNAF ONLINE SHOP"
    val branchLabel = state.branches.firstOrNull { it.id == state.activeBranchId }?.name ?: Strings.allBranches(lang)
    val periodLabel = if (lang == AppLanguage.BN) state.selectedPeriod.labelBn else state.selectedPeriod.labelEn

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.reports(lang)) },
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
            // Period & Branch Filters
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReportPeriod.entries.forEach { p ->
                        FilterChip(
                            selected = state.selectedPeriod == p,
                            onClick = { onSelectPeriod(p) },
                            label = { Text(if (lang == AppLanguage.BN) p.labelBn else p.labelEn) }
                        )
                    }
                }
                if (state.branches.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.activeBranchId.isBlank(),
                            onClick = { onSelectBranch("") },
                            label = { Text(Strings.allBranches(lang)) }
                        )
                        state.branches.forEach { b ->
                            FilterChip(
                                selected = state.activeBranchId == b.id,
                                onClick = { onSelectBranch(b.id) },
                                label = { Text(b.name) }
                            )
                        }
                    }
                }
            }

            // Export Buttons
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (!exportAllowed) {
                                onMessage(tr(lang, "PDF এক্সপোর্ট করতে Basic বা তার উপরের প্যাকেজ প্রয়োজন", "Upgrade to Basic+ to export PDF"))
                                return@Button
                            }
                            val lines = listOf(
                                "Total Sales" to Formatters.bdt(m.periodSales),
                                "Cash Sales" to Formatters.bdt(m.periodCashSales),
                                "Due Sales (Receivable)" to Formatters.bdt(m.periodDueSales),
                                "Due Collected" to Formatters.bdt(m.periodDueCollected),
                                "COGS" to Formatters.bdt(m.periodCogs),
                                "Expenses" to Formatters.bdt(m.periodExpense),
                                "Other Income" to Formatters.bdt(m.periodOtherIncome),
                                "True Net Profit" to Formatters.bdt(m.periodNetProfit),
                                "Cash Inflow" to Formatters.bdt(m.cashInflow),
                                "Cash Outflow" to Formatters.bdt(m.cashOutflow),
                                "Cash Balance" to Formatters.bdt(m.netCashBalance)
                            )
                            ReportExporter.exportPdfReport(context, shopName, branchLabel, periodLabel, lines, filteredTx)
                                .onSuccess { file ->
                                    ReportExporter.shareFile(context, file, "application/pdf", "Share PDF Report")
                                }
                                .onFailure { onMessage(it.localizedMessage ?: "PDF export failed") }
                        },
                        modifier = Modifier.weight(1f).testTag("export_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("PDF Export")
                    }

                    OutlinedButton(
                        onClick = {
                            if (!exportAllowed) {
                                onMessage(tr(lang, "Excel এক্সপোর্ট করতে Basic বা তার উপরের প্যাকেজ প্রয়োজন", "Upgrade to Basic+ to export Excel"))
                                return@OutlinedButton
                            }
                            ReportExporter.exportCsvReport(context, shopName, filteredTx)
                                .onSuccess { file ->
                                    ReportExporter.shareFile(context, file, "text/csv", "Share Excel CSV Report")
                                }
                                .onFailure { onMessage(it.localizedMessage ?: "CSV export failed") }
                        },
                        modifier = Modifier.weight(1f).testTag("export_excel_button")
                    ) {
                        Icon(Icons.Default.TableView, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Excel CSV")
                    }
                }
            }

            // Visual Comparison Bar Chart (Canvas)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(tr(lang, "আয়-ব্যয় ও লাভের তুলনা", "Financial Overview Chart"), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(12.dp))
                        val bars = listOf(
                            Triple(tr(lang, "বিক্রি", "Sales"), m.periodSales, ProfitGreen),
                            Triple(tr(lang, "নগদ আদায়", "Cash In"), m.cashInflow, EmeraldPrimary),
                            Triple(tr(lang, "ক্রয়", "Purchase"), m.periodPurchase, InfoBlue),
                            Triple(tr(lang, "খরচ", "Expense"), m.periodExpense, DueRed),
                            Triple(tr(lang, "প্রকৃত লাভ", "Net Profit"), m.periodNetProfit.coerceAtLeast(0.0), WarningAmber)
                        )
                        val maxVal = (bars.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        ) {
                            val barWidth = size.width / (bars.size * 2f)
                            bars.forEachIndexed { idx, (_, value, color) ->
                                val h = ((value / maxVal).toFloat() * (size.height - 16f)).coerceAtLeast(6f)
                                val x = idx * (barWidth * 2f) + barWidth * 0.5f
                                drawRect(
                                    color = color,
                                    topLeft = Offset(x, size.height - h),
                                    size = Size(barWidth, h)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            bars.forEach { (label, _, color) ->
                                Text(label, style = MaterialTheme.typography.labelSmall, color = color)
                            }
                        }
                    }
                }
            }

            // Profit & Loss Statement (Strictly separates Due Sales from Cash & Net Profit)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr(lang, "লাভ-ক্ষতি বিবরণী (Profit & Loss)", "Profit & Loss Statement"), style = MaterialTheme.typography.titleMedium)
                        ReportRow(tr(lang, "মোট বিক্রি (নগদ + বাকি)", "Total Sales (Cash + Due)"), Formatters.bdt(m.periodSales))
                        ReportRow(tr(lang, "  • নগদ বিক্রি", "  • Cash Sales"), Formatters.bdt(m.periodCashSales))
                        ReportRow(tr(lang, "  • বাকি বিক্রি (আয় হিসেবে নয়, পাওনা)", "  • Due Sales (Receivable)"), Formatters.bdt(m.periodDueSales), WarningAmber)
                        ReportRow(tr(lang, "বিক্রিত পণ্যের ক্রয়মূল্য (COGS)", "Cost of Goods Sold (COGS)"), "- ${Formatters.bdt(m.periodCogs)}")
                        ReportRow(tr(lang, "অন্যান্য আয়", "Other Income"), "+ ${Formatters.bdt(m.periodOtherIncome)}", ProfitGreen)
                        ReportRow(tr(lang, "পরিচালন খরচ (Expense)", "Operating Expenses"), "- ${Formatters.bdt(m.periodExpense)}", DueRed)
                        HorizontalDivider()
                        ReportRow(
                            tr(lang, "প্রকৃত নিট লাভ (Net Profit)", "True Net Profit"),
                            Formatters.bdt(m.periodNetProfit),
                            if (m.periodNetProfit >= 0) ProfitGreen else DueRed,
                            bold = true
                        )
                    }
                }
            }

            // Cash Flow Statement
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr(lang, "ক্যাশ ফ্লো (নগদ প্রবাহ)", "Cash Flow Summary"), style = MaterialTheme.typography.titleMedium)
                        ReportRow(tr(lang, "বাকি আদায় (নগদ প্রাপ্তি)", "Due Collection (Cash In)"), Formatters.bdt(m.periodDueCollected), ProfitGreen)
                        ReportRow(tr(lang, "মোট নগদ আগমন (Cash Inflow)", "Total Cash Inflow"), Formatters.bdt(m.cashInflow), ProfitGreen)
                        ReportRow(tr(lang, "মোট নগদ নির্গমন (Cash Outflow)", "Total Cash Outflow"), Formatters.bdt(m.cashOutflow), DueRed)
                        HorizontalDivider()
                        ReportRow(tr(lang, "মোট ক্যাশ ব্যালেন্স", "Current Cash Balance"), Formatters.bdt(m.netCashBalance), bold = true)
                    }
                }
            }

            // Branch-Wise Breakdown
            if (state.branches.isNotEmpty()) {
                item {
                    Text(tr(lang, "শাখাভিত্তিক রিপোর্ট", "Branch-wise Report"), style = MaterialTheme.typography.titleMedium)
                }
                items(state.branches, key = { it.id }) { branch ->
                    val bTx = state.transactions.filter {
                        it.status == "COMPLETED" && it.txDateMillis >= startMillis && it.branchId == branch.id
                    }
                    val bSales = bTx.filter { it.txType == TransactionType.SALE.name }.sumOf { it.totalAmount }
                    val bCogs = bTx.filter { it.txType == TransactionType.SALE.name }.sumOf { it.cogsTotal }
                    val bExp = bTx.filter { it.txType == TransactionType.EXPENSE.name }.sumOf { it.totalAmount }
                    val bProfit = bSales - bCogs - bExp
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(branch.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("${tr(lang, "বিক্রি", "Sales")}: ${Formatters.bdt(bSales)} • ${tr(lang, "খরচ", "Exp")}: ${Formatters.bdt(bExp)}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                text = Formatters.bdt(bProfit),
                                fontWeight = FontWeight.Bold,
                                color = if (bProfit >= 0) ProfitGreen else DueRed
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportRow(label: String, value: String, color: Color? = null, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = color ?: MaterialTheme.colorScheme.onSurface
        )
    }
}
