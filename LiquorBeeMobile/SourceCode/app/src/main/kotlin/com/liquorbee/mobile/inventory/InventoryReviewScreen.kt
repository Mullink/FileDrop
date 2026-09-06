package com.liquorbee.mobile.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun InventoryReviewView(uiState: InventoryUiState, viewModel: InventoryViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (uiState.reviewPendingSubmission) {
                Text(
                    "This session is staged for submission - review the counts below, then confirm to push quantities to your catalog.",
                    color = Color.Gray
                )
            } else {
                OutlinedTextField(
                    value = uiState.reviewSearchFilter,
                    onValueChange = viewModel::onReviewSearchChanged,
                    label = { Text("Search items") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (!uiState.reviewReadOnly && !uiState.reviewPendingSubmission) {
                Text("${uiState.changedReviewItemCount} item(s) modified", color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
            }
        }

        if (uiState.reviewLoading) {
            Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(uiState.filteredReviewItems, key = { it.itemId }) { item ->
                    ReviewItemRow(
                        item = item,
                        readOnly = uiState.reviewReadOnly || uiState.reviewPendingSubmission,
                        onFinalQtyChanged = { viewModel.onFinalQtyChanged(item.itemId, it) }
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            when {
                uiState.reviewPendingSubmission -> Button(
                    onClick = viewModel::confirmCompleteSession,
                    enabled = !uiState.reviewSubmitting,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (uiState.reviewSubmitting) "Completing…" else "Yes, Complete - Update Catalog") }

                !uiState.reviewReadOnly -> Button(
                    onClick = viewModel::openFinalizeReviewConfirm,
                    enabled = !uiState.reviewSubmitting,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (uiState.reviewSubmitting) "Saving…" else "Finalize Changes") }
            }
        }
    }

    if (uiState.showFinalizeReviewConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissFinalizeReviewConfirm,
            title = { Text("Finalize Changes") },
            text = { Text("This moves the session to Pending Submission. Inventory quantities will NOT change until you confirm completion afterward. Continue?") },
            confirmButton = { TextButton(onClick = viewModel::confirmFinalizeReview) { Text("Yes, Finalize") } },
            dismissButton = { TextButton(onClick = viewModel::dismissFinalizeReviewConfirm) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ReviewItemRow(item: ReviewItemUi, readOnly: Boolean, onFinalQtyChanged: (Int?) -> Unit) {
    val rowColor = when {
        item.newItem == 1 -> Color(0xFFF3E5F5)
        item.qtyMatched == 0 -> Color(0xFFFDECEA)
        item.qtyMatched == 1 -> Color(0xFFE8F5E9)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = rowColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.itemName, fontWeight = FontWeight.SemiBold)
                Text("${item.itemCode} • ${item.barcode}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                Text("Before: ${item.qtyBefore}   After: ${item.qtyAfter}", style = MaterialTheme.typography.bodySmall)
            }
            if (readOnly) {
                Text("${item.finalQty}", fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
            } else {
                OutlinedTextField(
                    value = item.finalQty.toString(),
                    onValueChange = { text -> onFinalQtyChanged(text.toIntOrNull()) },
                    label = { Text("Final") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(100.dp)
                )
            }
        }
    }
}
