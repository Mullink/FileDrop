package com.liquorbee.mobile.labelprinting

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liquorbee.mobile.core.network.InventoryCategoryDto
import com.liquorbee.mobile.core.network.LabelPrintingItemDto
import com.liquorbee.mobile.core.ui.LiquorBeeBlue
import com.liquorbee.mobile.core.ui.LiquorBeeNavy
import java.text.NumberFormat
import java.util.Locale

@Composable
fun LabelPrintingScreen(viewModel: LabelPrintingViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.US) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Label Printing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LiquorBeeNavy, titleContentColor = LiquorBeeBlue)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            uiState.message?.let { message ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .clickable { viewModel.dismissMessage() }
                ) {
                    Text(message, modifier = Modifier.padding(12.dp))
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = uiState.searchText,
                    onValueChange = viewModel::onSearchChanged,
                    label = { Text("Search item, SKU, or UPC") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = uiState.syncCatalog, onCheckedChange = viewModel::onSyncToggled)
                    Text("Sync Catalog Before Printing")
                }

                FilterDropdown(
                    selected = uiState.selectedFilter,
                    onSelected = viewModel::onFilterSelected
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("Displaying: ${uiState.filteredItems.size} items")
                Text("Print Queue: ${uiState.queueCount} labels in queue", color = Color.Gray)

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = viewModel::printSelected,
                        enabled = !uiState.printing && uiState.selectedItemCodes.isNotEmpty()
                    ) {
                        Text(if (uiState.printing) "Printing…" else "Print Selected (${uiState.selectedItemCodes.size})")
                    }
                    OutlinedButton(onClick = viewModel::toggleQtyOnHandOnly) {
                        Text(if (uiState.qtyOnHandOnly) "Show All Items" else "Qty > 0 Only")
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::openCategoryDialog) {
                        Text("Print by Category")
                    }
                    TextButton(onClick = viewModel::selectAllVisible) { Text("Select All") }
                    TextButton(onClick = viewModel::clearSelection) { Text("Clear") }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.loading) {
                Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // No explicit `key` here - the catalog can contain duplicate/blank itemCode
                    // values, and Compose's LazyColumn throws a hard
                    // IllegalArgumentException("Key ... was already used") the moment two rows'
                    // keys collide. Falling back to the default positional key trades away item-
                    // level recomposition stability for a filter that can never crash.
                    items(uiState.filteredItems) { item ->
                        LabelItemRow(
                            item = item,
                            selected = uiState.selectedItemCodes.contains(item.itemCode),
                            onToggle = { viewModel.toggleItemSelection(item.itemCode) },
                            currency = currency
                        )
                    }
                }
            }
        }
    }

    if (uiState.showCategoryDialog) {
        CategoryPickerDialog(
            categories = uiState.categories,
            loading = uiState.categoriesLoading,
            selectedIds = uiState.selectedCategoryIds,
            onToggle = viewModel::toggleCategorySelection,
            onConfirm = viewModel::loadItemsByCategory,
            onDismiss = viewModel::dismissCategoryDialog
        )
    }
}

@Composable
private fun LabelItemRow(
    item: LabelPrintingItemDto,
    selected: Boolean,
    onToggle: () -> Unit,
    currency: NumberFormat
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(item.itemName.orEmpty(), fontWeight = FontWeight.SemiBold)
            Text("${item.itemCode}  •  ${item.upc.orEmpty()}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Text(
                "Cash: ${currency.format(item.cashPrice)}   Card: ${currency.format(item.cardPrice)}   Qty: ${item.qtyOnHand}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(selected: Int, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = LABEL_FILTER_OPTIONS.firstOrNull { it.value == selected }?.label ?: "Show All Items"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Filter Items") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LABEL_FILTER_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelected(option.value)
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryPickerDialog(
    categories: List<InventoryCategoryDto>,
    loading: Boolean,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Print by Category") },
        text = {
            if (loading) {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn {
                    items(categories, key = { it.categoryId }) { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(category.categoryId) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = selectedIds.contains(category.categoryId), onCheckedChange = { onToggle(category.categoryId) })
                            Text(category.name.orEmpty())
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = selectedIds.isNotEmpty()) { Text("Load Items") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
