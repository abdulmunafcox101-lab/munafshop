package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.PartyEntity
import com.example.data.local.ProductEntity
import com.example.data.local.TransactionEntity
import com.example.data.local.TxLineItem
import com.example.data.model.AppLanguage
import com.example.data.model.PartyType
import com.example.data.model.TransactionType
import com.example.i18n.Strings
import com.example.i18n.tr
import com.example.ui.theme.DueRed
import com.example.ui.theme.ProfitGreen
import com.example.ui.viewmodel.BusinessUiState
import com.example.util.Formatters

@Composable
fun TransactionsScreen(
    state: BusinessUiState,
    initialTab: Int = 0,
    onRecordSale: (List<TxLineItem>, Double, Double, Double, PartyEntity?, String, String) -> Unit,
    onRecordPurchase: (List<TxLineItem>, Double, Double, PartyEntity?, String, String) -> Unit,
    onRecordIncomeExpense: (Boolean, Double, String, String) -> Unit,
    onCancelTransaction: (String) -> Unit
) {
    val lang = state.language
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var inspectedTx by remember { mutableStateOf<TransactionEntity?>(null) }

    val tabs = listOf(
        Strings.newSale(lang),
        Strings.newPurchase(lang),
        tr(lang, "আয়/খরচ", "Income/Exp"),
        tr(lang, "ইতিহাস", "History")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(title, maxLines = 1) },
                    modifier = Modifier.testTag("tx_tab_$idx")
                )
            }
        }

        when (selectedTab) {
            0 -> SaleEntryForm(state, onRecordSale)
            1 -> PurchaseEntryForm(state, onRecordPurchase)
            2 -> IncomeExpenseForm(lang, onRecordIncomeExpense)
            3 -> TransactionHistoryList(
                state = state,
                onInspect = { inspectedTx = it }
            )
        }
    }

    inspectedTx?.let { tx ->
        InvoiceDetailDialog(
            tx = tx,
            shopName = state.profile?.shopName ?: "MUNAF ONLINE SHOP",
            lang = lang,
            onDismiss = { inspectedTx = null },
            onCancelTx = {
                onCancelTransaction(tx.id)
                inspectedTx = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaleEntryForm(
    state: BusinessUiState,
    onSubmit: (List<TxLineItem>, Double, Double, Double, PartyEntity?, String, String) -> Unit
) {
    val lang = state.language
    val scopedProducts = state.products.filter { state.activeBranchId.isBlank() || it.branchId == state.activeBranchId }
    val customers = state.parties.filter { it.partyType == PartyType.CUSTOMER.name }

    val cart = remember { mutableStateListOf<TxLineItem>() }
    var selectedProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var prodMenuExpanded by remember { mutableStateOf(false) }
    var qtyText by remember { mutableStateOf("1") }
    var unitPriceText by remember { mutableStateOf("") }

    var directTotalText by remember { mutableStateOf("") }
    var discountText by remember { mutableStateOf("0") }
    var paymentMode by remember { mutableStateOf("CASH") } // CASH, DUE, PARTIAL
    var paidText by remember { mutableStateOf("") }
    var selectedCustomer by remember { mutableStateOf<PartyEntity?>(null) }
    var custMenuExpanded by remember { mutableStateOf(false) }
    var walkInName by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val subtotal = if (cart.isNotEmpty()) cart.sumOf { it.lineTotal } else (directTotalText.toDoubleOrNull() ?: 0.0)
    val discount = (discountText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, subtotal)
    val netTotal = (subtotal - discount).coerceAtLeast(0.0)
    val paid = when (paymentMode) {
        "CASH" -> netTotal
        "DUE" -> 0.0
        else -> (paidText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, netTotal)
    }
    val due = (netTotal - paid).coerceAtLeast(0.0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!state.operator.canSell) {
            Text(
                text = tr(lang, "আপনার বিক্রি করার অনুমতি নেই।", "You do not have permission to record sales."),
                color = DueRed
            )
            return
        }

        Text(tr(lang, "পণ্য নির্বাচন (স্টক থেকে)", "Select Product (from Inventory)"), style = MaterialTheme.typography.titleSmall)
        ExposedDropdownMenuBox(
            expanded = prodMenuExpanded,
            onExpandedChange = { prodMenuExpanded = !prodMenuExpanded }
        ) {
            OutlinedTextField(
                value = selectedProduct?.let { "${it.name} (Stock: ${Formatters.qty(it.stockQty)})" } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(tr(lang, "পণ্য বাছাই করুন", "Choose Product")) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(prodMenuExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = prodMenuExpanded,
                onDismissRequest = { prodMenuExpanded = false }
            ) {
                scopedProducts.forEach { p ->
                    DropdownMenuItem(
                        text = { Text("${p.name} • ${Formatters.bdt(p.sellPrice)} (${Formatters.qty(p.stockQty)})") },
                        onClick = {
                            selectedProduct = p
                            unitPriceText = p.sellPrice.toString()
                            prodMenuExpanded = false
                        }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = qtyText,
                onValueChange = { qtyText = it },
                label = { Text(tr(lang, "পরিমাণ", "Qty")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = unitPriceText,
                onValueChange = { unitPriceText = it },
                label = { Text(tr(lang, "দর (৳)", "Price")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val p = selectedProduct ?: return@Button
                    val q = qtyText.toDoubleOrNull() ?: 1.0
                    val pr = unitPriceText.toDoubleOrNull() ?: p.sellPrice
                    if (q > 0) {
                        cart.add(TxLineItem(p.id, p.name, q, pr, p.costPrice))
                        selectedProduct = null
                        qtyText = "1"
                        unitPriceText = ""
                    }
                },
                enabled = selectedProduct != null,
                modifier = Modifier.height(52.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
            }
        }

        if (cart.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    cart.forEachIndexed { idx, line ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${line.productName} × ${Formatters.qty(line.quantity)}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(Formatters.bdt(line.lineTotal), fontWeight = FontWeight.Bold)
                            IconButton(onClick = { cart.removeAt(idx) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = DueRed)
                            }
                        }
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = directTotalText,
                onValueChange = { directTotalText = it },
                label = { Text(tr(lang, "সরাসরি বিক্রয়মূল্য (৳) (পণ্য ছাড়া)", "Quick Sale Amount (৳)")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("quick_sale_amount_input")
            )
        }

        OutlinedTextField(
            value = discountText,
            onValueChange = { discountText = it },
            label = { Text(tr(lang, "ডিসকাউন্ট (৳)", "Discount (৳)")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        // Payment Mode Chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = paymentMode == "CASH",
                onClick = { paymentMode = "CASH" },
                label = { Text(tr(lang, "নগদ (Cash)", "Cash")) }
            )
            FilterChip(
                selected = paymentMode == "DUE",
                onClick = { paymentMode = "DUE" },
                label = { Text(tr(lang, "বাকি (Due)", "Due")) }
            )
            FilterChip(
                selected = paymentMode == "PARTIAL",
                onClick = { paymentMode = "PARTIAL" },
                label = { Text(tr(lang, "আংশিক", "Partial")) }
            )
        }

        if (paymentMode == "PARTIAL") {
            OutlinedTextField(
                value = paidText,
                onValueChange = { paidText = it },
                label = { Text(tr(lang, "নগদ জমা (৳)", "Paid Amount (৳)")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        }

        ExposedDropdownMenuBox(
            expanded = custMenuExpanded,
            onExpandedChange = { custMenuExpanded = !custMenuExpanded }
        ) {
            OutlinedTextField(
                value = selectedCustomer?.name ?: tr(lang, "নগদ / ওয়াক-ইন কাস্টমার", "Walk-in Customer"),
                onValueChange = {},
                readOnly = true,
                label = { Text(tr(lang, "কাস্টমার নির্বাচন", "Select Customer")) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(custMenuExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = custMenuExpanded,
                onDismissRequest = { custMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(tr(lang, "নগদ / ওয়াক-ইন কাস্টমার", "Walk-in Customer")) },
                    onClick = {
                        selectedCustomer = null
                        custMenuExpanded = false
                    }
                )
                customers.forEach { c ->
                    DropdownMenuItem(
                        text = { Text("${c.name} (${tr(lang, "বাকি", "Due")}: ${Formatters.bdt(c.dueAmount)})") },
                        onClick = {
                            selectedCustomer = c
                            custMenuExpanded = false
                        }
                    )
                }
            }
        }

        if (selectedCustomer == null) {
            OutlinedTextField(
                value = walkInName,
                onValueChange = { walkInName = it },
                label = { Text(tr(lang, "কাস্টমারের নাম (ঐচ্ছিক)", "Customer Name (Optional)")) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(tr(lang, "নোট / বিবরণ", "Note")) },
            modifier = Modifier.fillMaxWidth()
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr(lang, "মোট বিল:", "Net Total:"))
                    Text(Formatters.bdt(netTotal), fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr(lang, "নগদ আদায়:", "Paid:"))
                    Text(Formatters.bdt(paid), color = ProfitGreen, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr(lang, "বাকি:", "Due:"))
                    Text(Formatters.bdt(due), color = DueRed, fontWeight = FontWeight.Bold)
                }
            }
        }

        Button(
            onClick = {
                onSubmit(cart.toList(), netTotal + discount, discount, paid, selectedCustomer, walkInName, note)
                cart.clear()
                directTotalText = ""
                discountText = "0"
                paidText = ""
                note = ""
            },
            enabled = netTotal > 0.0,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("save_sale_button"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(tr(lang, "বিক্রি সংরক্ষণ করুন", "Save Sale"))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseEntryForm(
    state: BusinessUiState,
    onSubmit: (List<TxLineItem>, Double, Double, PartyEntity?, String, String) -> Unit
) {
    val lang = state.language
    val scopedProducts = state.products.filter { state.activeBranchId.isBlank() || it.branchId == state.activeBranchId }
    val suppliers = state.parties.filter { it.partyType == PartyType.SUPPLIER.name }

    val cart = remember { mutableStateListOf<TxLineItem>() }
    var selectedProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var prodExpanded by remember { mutableStateOf(false) }
    var qtyText by remember { mutableStateOf("1") }
    var costText by remember { mutableStateOf("") }
    var quickAmount by remember { mutableStateOf("") }
    var paidText by remember { mutableStateOf("") }
    var selectedSupplier by remember { mutableStateOf<PartyEntity?>(null) }
    var supExpanded by remember { mutableStateOf(false) }
    var supplierName by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val total = if (cart.isNotEmpty()) cart.sumOf { it.lineTotal } else (quickAmount.toDoubleOrNull() ?: 0.0)
    val paid = (paidText.toDoubleOrNull() ?: total).coerceIn(0.0, total)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!state.operator.canPurchase) {
            Text(tr(lang, "আপনার ক্রয় করার অনুমতি নেই।", "Permission denied for Purchase"), color = DueRed)
            return
        }

        ExposedDropdownMenuBox(expanded = prodExpanded, onExpandedChange = { prodExpanded = !prodExpanded }) {
            OutlinedTextField(
                value = selectedProduct?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(tr(lang, "ক্রয়ের পণ্য", "Product to Restock")) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(prodExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = prodExpanded, onDismissRequest = { prodExpanded = false }) {
                scopedProducts.forEach { p ->
                    DropdownMenuItem(
                        text = { Text("${p.name} (Cost: ${Formatters.bdt(p.costPrice)})") },
                        onClick = {
                            selectedProduct = p
                            costText = p.costPrice.toString()
                            prodExpanded = false
                        }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = qtyText,
                onValueChange = { qtyText = it },
                label = { Text(tr(lang, "পরিমাণ", "Qty")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = costText,
                onValueChange = { costText = it },
                label = { Text(tr(lang, "ক্রয়মূল্য (৳)", "Cost")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val p = selectedProduct ?: return@Button
                    val q = qtyText.toDoubleOrNull() ?: 1.0
                    val c = costText.toDoubleOrNull() ?: p.costPrice
                    if (q > 0) {
                        cart.add(TxLineItem(p.id, p.name, q, c, c))
                        selectedProduct = null
                        qtyText = "1"
                        costText = ""
                    }
                },
                enabled = selectedProduct != null,
                modifier = Modifier.height(52.dp)
            ) { Icon(Icons.Default.Add, contentDescription = "Add") }
        }

        if (cart.isEmpty()) {
            OutlinedTextField(
                value = quickAmount,
                onValueChange = { quickAmount = it },
                label = { Text(tr(lang, "মোট ক্রয়মূল্য (৳)", "Total Purchase (৳)")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            cart.forEachIndexed { idx, line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${line.productName} × ${Formatters.qty(line.quantity)}", modifier = Modifier.weight(1f))
                    Text(Formatters.bdt(line.lineTotal), fontWeight = FontWeight.Bold)
                    IconButton(onClick = { cart.removeAt(idx) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = DueRed)
                    }
                }
            }
        }

        OutlinedTextField(
            value = paidText,
            onValueChange = { paidText = it },
            label = { Text(tr(lang, "নগদ পরিশোধ (৳) (খালি রাখলে সম্পূর্ণ নগদ)", "Cash Paid (blank = full)")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        ExposedDropdownMenuBox(expanded = supExpanded, onExpandedChange = { supExpanded = !supExpanded }) {
            OutlinedTextField(
                value = selectedSupplier?.name ?: tr(lang, "নতুন / অন্যান্য সাপ্লায়ার", "Other Supplier"),
                onValueChange = {},
                readOnly = true,
                label = { Text(tr(lang, "সাপ্লায়ার", "Supplier")) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(supExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = supExpanded, onDismissRequest = { supExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(tr(lang, "নতুন / অন্যান্য সাপ্লায়ার", "Other Supplier")) },
                    onClick = { selectedSupplier = null; supExpanded = false }
                )
                suppliers.forEach { s ->
                    DropdownMenuItem(
                        text = { Text("${s.name} (Dena: ${Formatters.bdt(s.dueAmount)})") },
                        onClick = { selectedSupplier = s; supExpanded = false }
                    )
                }
            }
        }

        if (selectedSupplier == null) {
            OutlinedTextField(
                value = supplierName,
                onValueChange = { supplierName = it },
                label = { Text(tr(lang, "সাপ্লায়ারের নাম", "Supplier Name")) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(tr(lang, "রসিদ নোট", "Purchase Note")) },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                onSubmit(cart.toList(), total, paid, selectedSupplier, supplierName, note)
                cart.clear(); quickAmount = ""; paidText = ""; note = ""
            },
            enabled = total > 0.0,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_purchase_button")
        ) {
            Text(tr(lang, "ক্রয় ও স্টক সংরক্ষণ করুন", "Save Purchase & Update Stock"))
        }
    }
}

@Composable
private fun IncomeExpenseForm(
    lang: AppLanguage,
    onSubmit: (Boolean, Double, String, String) -> Unit
) {
    var isExpense by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("দোকান ভাড়া / Shop Rent") }
    var note by remember { mutableStateOf("") }

    val expenseCategories = listOf(
        "দোকান ভাড়া / Shop Rent",
        "বিদ্যুৎ বিল / Electricity",
        "কর্মচারীর বেতন / Salary",
        "পরিবহন / Transport",
        "অন্যান্য খরচ / Other"
    )
    val incomeCategories = listOf(
        "সার্ভিস চার্জ / Service Charge",
        "অতিরিক্ত আয় / Extra Income",
        "কমিশন / Commission"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = isExpense,
                onClick = {
                    isExpense = true
                    category = expenseCategories.first()
                },
                label = { Text(tr(lang, "খরচ (Expense)", "Expense")) }
            )
            FilterChip(
                selected = !isExpense,
                onClick = {
                    isExpense = false
                    category = incomeCategories.first()
                },
                label = { Text(tr(lang, "অন্যান্য আয় (Income)", "Other Income")) }
            )
        }

        Text(tr(lang, "ক্যাটাগরি", "Category"), style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (if (isExpense) expenseCategories else incomeCategories).forEach { cat ->
                FilterChip(
                    selected = category == cat,
                    onClick = { category = cat },
                    label = { Text(cat) }
                )
            }
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text(tr(lang, "টাকার পরিমাণ (৳)", "Amount (৳)")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag("income_expense_amount_input")
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(tr(lang, "বিবরণ / নোট", "Description")) },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val amt = amountText.toDoubleOrNull() ?: 0.0
                if (amt > 0) {
                    onSubmit(isExpense, amt, category, note)
                    amountText = ""
                    note = ""
                }
            },
            enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0.0,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_income_expense_button")
        ) {
            Text(Strings.save(lang))
        }
    }
}

@Composable
private fun TransactionHistoryList(
    state: BusinessUiState,
    onInspect: (TransactionEntity) -> Unit
) {
    val lang = state.language
    val list = state.transactions.filter {
        state.activeBranchId.isBlank() || it.branchId == state.activeBranchId
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(list, key = { it.id }) { tx ->
            TransactionSummaryRow(tx = tx, lang = lang, onClick = { onInspect(tx) })
        }
    }
}

@Composable
private fun InvoiceDetailDialog(
    tx: TransactionEntity,
    shopName: String,
    lang: AppLanguage,
    onDismiss: () -> Unit,
    onCancelTx: () -> Unit
) {
    val lines = remember(tx.lineItemsJson) { TxLineItem.fromJson(tx.lineItemsJson) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$shopName - ${tr(lang, "রসিদ / Invoice", "Invoice")}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${tr(lang, "ধরন", "Type")}: ${tx.txType} (${tx.status})")
                Text("${tr(lang, "তারিখ", "Date")}: ${Formatters.formatDateTime(tx.txDateMillis)}")
                Text("${tr(lang, "পার্টি", "Party")}: ${tx.partyName.ifBlank { tx.category }}")
                Text("${tr(lang, "অপারেটর", "Operator")}: ${tx.operatorName}")
                HorizontalDivider()
                lines.forEach { line ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${line.productName} × ${Formatters.qty(line.quantity)}")
                        Text(Formatters.bdt(line.lineTotal))
                    }
                }
                if (lines.isNotEmpty()) HorizontalDivider()
                Text("${tr(lang, "মোট", "Total")}: ${Formatters.bdt(tx.totalAmount)}", fontWeight = FontWeight.Bold)
                Text("${tr(lang, "পরিশোধ", "Paid")}: ${Formatters.bdt(tx.paidAmount)}", color = ProfitGreen)
                if (tx.dueAmount > 0) {
                    Text("${tr(lang, "বাকি", "Due")}: ${Formatters.bdt(tx.dueAmount)}", color = DueRed)
                }
                if (tx.note.isNotBlank()) Text("${tr(lang, "নোট", "Note")}: ${tx.note}")
            }
        },
        confirmButton = {
            if (tx.status == "COMPLETED" && (tx.txType == TransactionType.SALE.name || tx.txType == TransactionType.PURCHASE.name)) {
                OutlinedButton(
                    onClick = onCancelTx,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DueRed)
                ) {
                    Text(tr(lang, "বাতিল ও স্টক ফেরত", "Void & Restore"))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.close(lang)) }
        }
    )
}
