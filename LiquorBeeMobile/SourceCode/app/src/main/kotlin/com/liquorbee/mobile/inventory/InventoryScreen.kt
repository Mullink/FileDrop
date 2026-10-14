package com.liquorbee.mobile.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.unit.sp
import com.liquorbee.mobile.core.ui.LbBorder
import com.liquorbee.mobile.core.ui.LbText
import com.liquorbee.mobile.core.ui.LbMuted
import com.liquorbee.mobile.core.ui.LbActive
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.liquorbee.mobile.core.network.InventoryCategoryDto
import com.liquorbee.mobile.core.network.InventorySessionHeaderDto
import com.liquorbee.mobile.core.ui.LiquorBeeBlue
import com.liquorbee.mobile.core.ui.LiquorBeeNavy

@Composable
fun InventoryScreen(viewModel: InventoryViewModel, onExit: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    val title = when (uiState.view) {
        InventoryView.SESSIONS -> "Inventory Module"
        InventoryView.CATEGORY_SELECT -> "New Session"
        InventoryView.CATEGORY_ITEMS -> "Enter Counted Quantities"
        InventoryView.CAPTURE -> "Count Inventory"
        InventoryView.REVIEW -> if (uiState.reviewReadOnly) "Review (Read-Only)" else "Review Session"
    }

    // Hardware/gesture back navigates one step within the flow (matching the web app's own
    // "Back to Categories"/"Back to Sessions" buttons) rather than immediately leaving the whole
    // Inventory Module from a nested screen.
    BackHandler(enabled = uiState.view != InventoryView.SESSIONS) {
        when (uiState.view) {
            InventoryView.CATEGORY_SELECT -> viewModel.backToSessions()
            InventoryView.CATEGORY_ITEMS -> viewModel.backToCategorySelect()
            InventoryView.CAPTURE, InventoryView.REVIEW -> viewModel.backToSessions()
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = {
                        when (uiState.view) {
                            InventoryView.SESSIONS -> onExit()
                            InventoryView.CATEGORY_SELECT -> viewModel.backToSessions()
                            InventoryView.CATEGORY_ITEMS -> viewModel.backToCategorySelect()
                            InventoryView.CAPTURE, InventoryView.REVIEW -> viewModel.backToSessions()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (uiState.view == InventoryView.CAPTURE) {
                        IconButton(onClick = viewModel::openSessionOptions) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Session Options", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LiquorBeeNavy, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White, actionIconContentColor = Color.White)
            )
        },
        floatingActionButton = {
            if (uiState.view == InventoryView.SESSIONS) {
                FloatingActionButton(onClick = viewModel::onNewSessionTapped, containerColor = LiquorBeeNavy, contentColor = Color.White, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "New Session")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            uiState.message?.let { message ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(12.dp).clickable { viewModel.dismissMessage() }
                ) { Text(message, modifier = Modifier.padding(12.dp)) }
            }

            when (uiState.view) {
                InventoryView.SESSIONS -> SessionsListView(uiState, viewModel)
                InventoryView.CATEGORY_SELECT -> CategorySelectView(uiState, viewModel)
                InventoryView.CATEGORY_ITEMS -> CategoryItemsView(uiState, viewModel)
                InventoryView.CAPTURE -> InventoryCaptureView(uiState, viewModel)
                InventoryView.REVIEW -> InventoryReviewView(uiState, viewModel)
            }
        }
    }

    if (uiState.showAddedByDialog) {
        AlertDialog(containerColor = androidx.compose.ui.graphics.Color.White,
            onDismissRequest = viewModel::dismissAddedByDialog,
            title = { Text("Who's counting?") },
            text = {
                OutlinedTextField(
                    value = uiState.addedByName,
                    onValueChange = viewModel::onAddedByChanged,
                    label = { Text("Your name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmAddedBy) { Text("Continue") } },
            dismissButton = { TextButton(onClick = viewModel::dismissAddedByDialog) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SessionsListView(uiState: InventoryUiState, viewModel: InventoryViewModel) {
    if (uiState.sessionsLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), color = LbActive, strokeWidth = 2.dp)
                Text("Loading sessions…", color = LbMuted, fontSize = 14.sp)
            }
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Inventory sessions", Modifier.weight(1f), color = LiquorBeeNavy,
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("${uiState.sessions.size} total", color = LbMuted, fontSize = 12.sp)
            }
        }
        if (uiState.sessions.isEmpty()) item {
            Surface(color = Color.White, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, LbBorder)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Inventory2, null, tint = LbActive, modifier = Modifier.size(32.dp))
                    Text("No sessions yet", color = LbText, fontWeight = FontWeight.SemiBold)
                    Text("Tap + to start your first inventory count.", color = LbMuted, fontSize = 13.sp)
                }
            }
        }
        items(uiState.sessions, key = { it.sessionId }) { session ->
            SessionCard(session = session, onClick = { viewModel.openSession(session) })
        }
    }
}

private val OPENABLE_STATUSES = setOf("IN PROGRESS", "UNDER REVIEW", "COMPLETED", "PENDING SUBMISSION")

@Composable
private fun SessionCard(session: InventorySessionHeaderDto, onClick: () -> Unit) {
    val actionable = session.status in OPENABLE_STATUSES
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = actionable, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, LbBorder)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = Color(0xFFEFF6FF), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Inventory2, null, tint = LbActive, modifier = Modifier.padding(9.dp).size(20.dp))
                }
                Text(session.sessionName?.takeIf { it.isNotBlank() } ?: "Session #${session.sessionId}",
                    modifier = Modifier.weight(1f), color = LiquorBeeNavy, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (actionable) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = LbMuted, modifier = Modifier.size(20.dp))
            }
            StatusBadge(session.status.orEmpty())
            Text("${if (session.isCategorySession) "Category count" else "Full count"} • ${session.itemCountBefore} items",
                color = LbMuted, fontSize = 13.sp)
            session.createdBy?.takeIf { it.isNotBlank() }?.let {
                Text("By $it", color = LbMuted, fontSize = 12.sp)
            }
            if (!actionable) Text("This session is ${session.status?.lowercase()} — view on desktop",
                color = LbMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val color = when (status) {
        "IN PROGRESS" -> LbActive
        "UNDER REVIEW", "PENDING SUBMISSION" -> Color(0xFF946200)
        "COMPLETED" -> Color(0xFF18794E)
        else -> LbMuted
    }
    Surface(color = color.copy(alpha = 0.09f), shape = RoundedCornerShape(4.dp)) {
        Text(status.lowercase().replaceFirstChar { it.uppercase() }, color = color,
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
    }
}

@Composable
private fun CategorySelectView(uiState: InventoryUiState, viewModel: InventoryViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = uiState.newSessionName,
            onValueChange = viewModel::onNewSessionNameChanged,
            label = { Text("Session name (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = uiState.zeroOutCurrentQty, onCheckedChange = viewModel::onZeroOutChanged)
            Text("Zero out current qty before counting")
        }
        OutlinedTextField(
            value = uiState.categorySearchFilter,
            onValueChange = viewModel::onCategorySearchChanged,
            label = { Text("Search categories") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            TextButton(onClick = viewModel::selectAllCategories) { Text("Select All") }
            TextButton(onClick = viewModel::clearAllCategories) { Text("Clear") }
        }

        if (uiState.categoriesLoading) {
            Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.filteredCategories, key = { it.categoryId }) { category ->
                    CategoryRow(category, uiState.selectedCategoryIds.contains(category.categoryId), viewModel::toggleCategorySelection)
                }
            }
        }

        OutlinedButton(
            onClick = viewModel::createScratchSession,
            enabled = !uiState.sessionCreating,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text(if (uiState.sessionCreating) "Starting…" else "Or Start a Full Store Count Instead")
        }

        Button(
            onClick = viewModel::proceedToLoadCategoryItems,
            enabled = uiState.selectedCategoryIds.isNotEmpty() && !uiState.sessionCreating,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text(if (uiState.sessionCreating) "Loading…" else "Load Items (${uiState.selectedCategoryIds.size})")
        }
    }
}

@Composable
private fun CategoryRow(category: InventoryCategoryDto, selected: Boolean, onToggle: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle(category.categoryId) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle(category.categoryId) })
        Text(category.name.orEmpty())
    }
}

@Composable
private fun CategoryItemsView(uiState: InventoryUiState, viewModel: InventoryViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = uiState.categoryItemsFilter,
                onValueChange = viewModel::onCategoryItemsFilterChanged,
                label = { Text("Search items") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text("${uiState.categoryItemsWithAfterQty} / ${uiState.categoryItems.size} filled", color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                OutlinedButton(onClick = viewModel::toggleShowOnlyUnfilled) {
                    Text(if (uiState.showOnlyUnfilled) "Show All" else "Show Unfilled")
                }
                OutlinedButton(onClick = viewModel::fillAllToZero) { Text("Fill Unfilled to 0") }
                OutlinedButton(onClick = viewModel::fillNominal) { Text("Fill to Before Qty") }
            }
        }

        if (uiState.categoryItemsLoading) {
            Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(uiState.filteredCategoryItems, key = { it.itemId }) { item ->
                    CategoryItemRow(item = item, onAfterQtyChanged = { viewModel.onAfterQtyChanged(item.itemId, it) })
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Button(
                onClick = viewModel::submitCategoryInventory,
                enabled = uiState.categoryItemsWithAfterQty > 0 && !uiState.categoryItemsSubmitting,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (uiState.categoryItemsSubmitting) "Saving…" else "Save & Move to Review")
            }
            OutlinedButton(
                onClick = viewModel::saveCategoryInventoryForLater,
                enabled = !uiState.categoryItemsSubmitting,
                modifier = Modifier.weight(1f)
            ) {
                Text("Save for Later")
            }
        }
    }
}

@Composable
private fun CategoryItemRow(item: CategoryInventoryItemUi, onAfterQtyChanged: (Int?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.SemiBold)
            Text("${item.sku}  •  ${item.upc}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Text("Before Qty: ${item.beforeQty}", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(
            value = item.afterQty?.toString() ?: "",
            onValueChange = { text -> onAfterQtyChanged(text.toIntOrNull()) },
            label = { Text("After") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(100.dp)
        )
    }
}
