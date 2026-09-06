package com.liquorbee.mobile.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.liquorbee.mobile.core.network.InventoryHistoryDto
import com.liquorbee.mobile.core.network.InventorySessionItemNameDto
import com.liquorbee.mobile.core.ui.LiquorBeeBlue
import com.liquorbee.mobile.core.ui.LiquorBeeNavy

@Composable
fun InventoryCaptureView(uiState: InventoryUiState, viewModel: InventoryViewModel) {
    val barcodeFocusRequester = remember { FocusRequester() }

    // Same reasoning as PriceCheckScreen - the iMin hardware scan-trigger keys inject the
    // scanned barcode as simulated keystrokes into whatever field currently has focus, so this
    // field needs to be focused by default and again after every successful log.
    LaunchedEffect(uiState.captureFocusToken) {
        barcodeFocusRequester.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = uiState.barcodeText,
                onValueChange = viewModel::onBarcodeChanged,
                label = { Text("Scan or enter barcode/SKU") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.weight(1f).focusRequester(barcodeFocusRequester)
            )
        }

        if (uiState.captureLookupLoading) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        }

        if (uiState.captureNotFound && !uiState.showCreateNewItemDialog) {
            Text("Not found. ", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        TextButton(onClick = viewModel::toggleManualSearch, modifier = Modifier.padding(top = 4.dp)) {
            Text(if (uiState.showManualSearch) "Hide Name Search" else "Search by Name Instead")
        }

        if (uiState.showManualSearch) {
            OutlinedTextField(
                value = uiState.manualSearchQuery,
                onValueChange = viewModel::onManualSearchChanged,
                label = { Text("Item name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            uiState.manualSearchResults.forEach { result ->
                Text(
                    "${result.itemName} (${result.itemCode ?: result.barcode ?: ""})",
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.selectItem(result) }.padding(vertical = 8.dp)
                )
            }
        }

        uiState.capturedItem?.let { item ->
            CapturedItemCard(item = item, uiState = uiState, viewModel = viewModel)
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Text("Recent Adjustments", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        if (uiState.recentHistory.isEmpty()) {
            Text("No recent adjustments.", color = Color.Gray)
        } else {
            uiState.recentHistory.take(20).forEach { entry -> RecentHistoryRow(entry) }
        }
    }

    if (uiState.showSessionOptionsDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSessionOptions,
            title = { Text("Session Options") },
            text = {
                Column {
                    Text("You can manage your active inventory session below.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = viewModel::openFinalizeConfirm, modifier = Modifier.fillMaxWidth()) {
                        Text("✅ Finalize Inventory Session")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = viewModel::openCancelConfirm, modifier = Modifier.fillMaxWidth()) {
                        Text("🗑️ Abandon Inventory Session")
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = viewModel::dismissSessionOptions) { Text("Close") } }
        )
    }

    if (uiState.showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCancelConfirm,
            title = { Text("Cancel Inventory Session") },
            text = { Text("Are you sure you want to cancel this inventory session? This cannot be undone.") },
            confirmButton = { TextButton(onClick = viewModel::confirmCancelSession) { Text("Yes, Cancel") } },
            dismissButton = { TextButton(onClick = viewModel::dismissCancelConfirm) { Text("Back") } }
        )
    }

    if (uiState.showFinalizeConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissFinalizeConfirm,
            title = { Text("Finalize Inventory Session") },
            text = { Text("This marks the session complete and updates your catalog quantities immediately. Continue?") },
            confirmButton = { TextButton(onClick = viewModel::confirmFinalizeSession) { Text("Yes, Finalize") } },
            dismissButton = { TextButton(onClick = viewModel::dismissFinalizeConfirm) { Text("Cancel") } }
        )
    }

    if (uiState.showCreateNewItemDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCreateNewItemDialog,
            title = { Text("Create New Item") },
            text = {
                Column {
                    OutlinedTextField(
                        value = uiState.newItemName,
                        onValueChange = viewModel::onNewItemNameChanged,
                        label = { Text("Item Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.newItemSku,
                        onValueChange = viewModel::onNewItemSkuChanged,
                        label = { Text("SKU (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    OutlinedTextField(
                        value = uiState.newItemUpc,
                        onValueChange = viewModel::onNewItemUpcChanged,
                        label = { Text("UPC (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            },
            confirmButton = { TextButton(onClick = viewModel::submitNewItem) { Text("Create") } },
            dismissButton = { TextButton(onClick = viewModel::dismissCreateNewItemDialog) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CapturedItemCard(item: InventorySessionItemNameDto, uiState: InventoryUiState, viewModel: InventoryViewModel) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.itemName.orEmpty(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("Current Qty: ${item.currentQty}", color = Color.Gray)

            Spacer(modifier = Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = uiState.captureAction == "plus",
                    onClick = { viewModel.onCaptureActionChanged("plus") },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Add") }
                SegmentedButton(
                    selected = uiState.captureAction == "minus",
                    onClick = { viewModel.onCaptureActionChanged("minus") },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Subtract") }
            }

            OutlinedTextField(
                value = uiState.captureQtyText,
                onValueChange = viewModel::onCaptureQtyChanged,
                label = { Text("Quantity") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )

            Button(
                onClick = viewModel::submitCaptureEntry,
                enabled = !uiState.captureSubmitting,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Text(if (uiState.captureSubmitting) "Logging…" else "Log Entry")
            }
        }
    }
}

@Composable
private fun RecentHistoryRow(entry: InventoryHistoryDto) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(entry.itemName.orEmpty())
            entry.addedBy?.let { Text(it, color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
        }
        Text(
            if (entry.qtyAdjustment >= 0) "+${entry.qtyAdjustment}" else "${entry.qtyAdjustment}",
            color = if (entry.qtyAdjustment >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold
        )
    }
}
