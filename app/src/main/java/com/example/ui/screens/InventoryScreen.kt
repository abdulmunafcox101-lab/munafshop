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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.BranchEntity
import com.example.data.local.ProductEntity
import com.example.data.model.AppLanguage
import com.example.i18n.Strings
import com.example.i18n.tr
import com.example.ui.theme.DueRed
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.BusinessUiState
import com.example.util.Formatters

@Composable
fun InventoryScreen(
    state: BusinessUiState,
    onSaveProduct: (String?, String, String, String, String, Double, Double, Double, Double, String?) -> Unit,
    onAdjustStock: (ProductEntity, Double, String) -> Unit,
    onTransferStock: (ProductEntity, String, Double) -> Unit
) {
    val lang = state.language
    var tabIndex by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var adjustingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var transferringProduct by remember { mutableStateOf<ProductEntity?>(null) }

    val products = state.products.filter {
        (state.activeBranchId.isBlank() || it.branchId == state.activeBranchId) &&
            (search.isBlank() || it.name.contains(search, true) || it.sku.contains(search, true))
    }
    val logs = state.stockLogs.filter {
        state.activeBranchId.isBlank() || it.branchId == state.activeBranchId
    }

    Scaffold(
        floatingActionButton = {
            if (state.operator.canManageStock && tabIndex == 0) {
                FloatingActionButton(
                    onClick = { editingProduct = null; showAddDialog = true },
                    modifier = Modifier.testTag("add_product_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Product")
                }
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
                    text = { Text(tr(lang, "পণ্য তালিকা (${products.size})", "Products (${products.size})")) }
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text(tr(lang, "স্টক ইতিহাস", "Stock History")) }
                )
            }

            if (tabIndex == 0) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text(tr(lang, "পণ্যের নাম বা SKU খুঁজুন", "Search name or SKU")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(products, key = { it.id }) { p ->
                        val isLow = p.stockQty <= p.minStockAlert
                        val branchName = state.branches.firstOrNull { it.id == p.branchId }?.name.orEmpty()
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = state.operator.canManageStock) {
                                    editingProduct = p; showAddDialog = true
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!p.photoUri.isNullOrBlank()) {
                                        AsyncImage(
                                            model = Uri.parse(p.photoUri),
                                            contentDescription = p.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        if (isLow) {
                                            Spacer(Modifier.width(6.dp))
                                            Icon(Icons.Default.WarningAmber, contentDescription = "Low Stock", tint = WarningAmber, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Text(
                                        text = "${p.category} ${if (p.sku.isNotBlank()) "• ${p.sku}" else ""} ${if (branchName.isNotBlank()) "• $branchName" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${tr(lang, "ক্রয়", "Cost")}: ${Formatters.bdt(p.costPrice)} | ${tr(lang, "বিক্রয়", "Sell")}: ${Formatters.bdt(p.sellPrice)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = Formatters.qty(p.stockQty),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLow) DueRed else ProfitGreen
                                    )
                                    if (state.operator.canManageStock) {
                                        Row {
                                            IconButton(onClick = { adjustingProduct = p }) {
                                                Icon(Icons.Default.Tune, contentDescription = "Adjust Stock")
                                            }
                                            if (state.branches.size > 1) {
                                                IconButton(onClick = { transferringProduct = p }) {
                                                    Icon(Icons.Default.SwapHoriz, contentDescription = "Transfer Stock")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(log.productName, style = MaterialTheme.typography.titleSmall)
                                    Text("${log.reason} • ${Formatters.formatDateTime(log.createdAtMillis)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = if (log.changeQty >= 0) "+${Formatters.qty(log.changeQty)}" else Formatters.qty(log.changeQty),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (log.changeQty >= 0) ProfitGreen else DueRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ProductEditorDialog(
            existing = editingProduct,
            branches = state.branches,
            defaultBranchId = state.activeBranchId.ifBlank { state.branches.firstOrNull()?.id.orEmpty() },
            lang = lang,
            onDismiss = { showAddDialog = false },
            onSave = { bId, n, sku, cat, cost, sell, qty, minAlert, photo ->
                onSaveProduct(editingProduct?.id, bId, n, sku, cat, cost, sell, qty, minAlert, photo)
                showAddDialog = false
            }
        )
    }

    adjustingProduct?.let { prod ->
        StockAdjustmentDialog(
            product = prod,
            lang = lang,
            onDismiss = { adjustingProduct = null },
            onApply = { delta, reason ->
                onAdjustStock(prod, delta, reason)
                adjustingProduct = null
            }
        )
    }

    transferringProduct?.let { prod ->
        StockTransferDialog(
            product = prod,
            branches = state.branches.filter { it.id != prod.branchId },
            lang = lang,
            onDismiss = { transferringProduct = null },
            onTransfer = { targetId, qty ->
                onTransferStock(prod, targetId, qty)
                transferringProduct = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductEditorDialog(
    existing: ProductEntity?,
    branches: List<BranchEntity>,
    defaultBranchId: String,
    lang: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Double, Double, Double, Double, String?) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var sku by remember { mutableStateOf(existing?.sku ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "General") }
    var costText by remember { mutableStateOf(existing?.costPrice?.toString() ?: "") }
    var sellText by remember { mutableStateOf(existing?.sellPrice?.toString() ?: "") }
    var qtyText by remember { mutableStateOf(existing?.stockQty?.toString() ?: "0") }
    var minAlertText by remember { mutableStateOf(existing?.minStockAlert?.toString() ?: "5") }
    var branchId by remember { mutableStateOf(existing?.branchId ?: defaultBranchId) }
    var branchExpanded by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf(existing?.photoUri) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) tr(lang, "নতুন পণ্য যোগ", "Add Product") else tr(lang, "পণ্য সম্পাদনা", "Edit Product")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Text(tr(lang, "পণ্যের ছবি নির্বাচন", "Select Product Photo"))
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(tr(lang, "পণ্যের নাম *", "Name *")) }, modifier = Modifier.fillMaxWidth().testTag("product_name_input"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sku, onValueChange = { sku = it }, label = { Text("SKU") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text(tr(lang, "ক্যাটাগরি", "Category")) }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = costText, onValueChange = { costText = it }, label = { Text(tr(lang, "ক্রয়মূল্য", "Cost")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = sellText, onValueChange = { sellText = it }, label = { Text(tr(lang, "বিক্রয়মূল্য", "Sell")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = qtyText, onValueChange = { qtyText = it }, label = { Text(tr(lang, "বর্তমান স্টক", "Stock")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = minAlertText, onValueChange = { minAlertText = it }, label = { Text(tr(lang, "সতর্কতা সীমা", "Min Alert")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                if (branches.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = branchExpanded, onExpandedChange = { branchExpanded = !branchExpanded }) {
                        OutlinedTextField(
                            value = branches.firstOrNull { it.id == branchId }?.name ?: branches.first().name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(tr(lang, "শাখা", "Branch")) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(branchExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = branchExpanded, onDismissRequest = { branchExpanded = false }) {
                            branches.forEach { b ->
                                DropdownMenuItem(text = { Text(b.name) }, onClick = { branchId = b.id; branchExpanded = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        branchId, name, sku, category,
                        costText.toDoubleOrNull() ?: 0.0,
                        sellText.toDoubleOrNull() ?: 0.0,
                        qtyText.toDoubleOrNull() ?: 0.0,
                        minAlertText.toDoubleOrNull() ?: 5.0,
                        photoUri
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_product_button")
            ) { Text(Strings.save(lang)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel(lang)) } }
    )
}

@Composable
private fun StockAdjustmentDialog(
    product: ProductEntity,
    lang: AppLanguage,
    onDismiss: () -> Unit,
    onApply: (Double, String) -> Unit
) {
    var deltaText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("MANUAL_ADJUSTMENT") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${tr(lang, "স্টক সমন্বয়", "Adjust Stock")}: ${product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${tr(lang, "বর্তমান স্টক", "Current Stock")}: ${Formatters.qty(product.stockQty)}")
                OutlinedTextField(
                    value = deltaText,
                    onValueChange = { deltaText = it },
                    label = { Text(tr(lang, "পরিবর্তন (+ যোগ বা - বিয়োগ)", "Change (+ or -)")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(tr(lang, "কারণ (যেমন: Damage/Count)", "Reason")) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val d = deltaText.toDoubleOrNull() ?: 0.0
                    if (d != 0.0) onApply(d, reason)
                },
                enabled = (deltaText.toDoubleOrNull() ?: 0.0) != 0.0
            ) { Text(Strings.confirm(lang)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel(lang)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockTransferDialog(
    product: ProductEntity,
    branches: List<BranchEntity>,
    lang: AppLanguage,
    onDismiss: () -> Unit,
    onTransfer: (String, Double) -> Unit
) {
    var targetBranch by remember { mutableStateOf(branches.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }
    var qtyText by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(lang, "শাখায় স্টক স্থানান্তর", "Transfer Stock to Branch")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${product.name} (${tr(lang, "মজুদ", "Available")}: ${Formatters.qty(product.stockQty)})")
                if (branches.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = targetBranch?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(tr(lang, "গন্তব্য শাখা", "Destination Branch")) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            branches.forEach { b ->
                                DropdownMenuItem(text = { Text(b.name) }, onClick = { targetBranch = b; expanded = false })
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text(tr(lang, "পরিমাণ", "Quantity")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val b = targetBranch ?: return@Button
                    val q = qtyText.toDoubleOrNull() ?: 0.0
                    if (q > 0) onTransfer(b.id, q)
                },
                enabled = targetBranch != null && (qtyText.toDoubleOrNull() ?: 0.0) > 0.0
            ) { Text(tr(lang, "স্থানান্তর করুন", "Transfer")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel(lang)) } }
    )
}
