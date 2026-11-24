package com.liquorbee.mobile.lbcart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquorbee.mobile.carts.CartsSnapshot
import com.liquorbee.mobile.carts.LbMarket
import com.liquorbee.mobile.carts.QtyStepper
import com.liquorbee.mobile.carts.QuantityDialog
import com.liquorbee.mobile.carts.ReorderBadge
import com.liquorbee.mobile.carts.ReorderInfo
import com.liquorbee.mobile.carts.ReorderSettingChip
import com.liquorbee.mobile.carts.ReorderSettingsDialog
import com.liquorbee.mobile.carts.ReorderState
import com.liquorbee.mobile.carts.money
import com.liquorbee.mobile.core.ui.LbActive
import com.liquorbee.mobile.core.ui.LbBad
import com.liquorbee.mobile.core.ui.LbBorder
import com.liquorbee.mobile.core.ui.LbControlShape
import com.liquorbee.mobile.core.ui.LbEmpty
import com.liquorbee.mobile.core.ui.LbFaint
import com.liquorbee.mobile.core.ui.LbFooterBar
import com.liquorbee.mobile.core.ui.LbMuted
import com.liquorbee.mobile.core.ui.LbPage
import com.liquorbee.mobile.core.ui.LbPanel
import com.liquorbee.mobile.core.ui.LbPill
import com.liquorbee.mobile.core.ui.LbPrimaryButton
import com.liquorbee.mobile.core.ui.LbScreen
import com.liquorbee.mobile.core.ui.LbSecondaryButton
import com.liquorbee.mobile.core.ui.LbTabs
import com.liquorbee.mobile.core.ui.LbText
import com.liquorbee.mobile.core.ui.LbWarn
import com.liquorbee.mobile.core.ui.LiquorBeeNavy
import com.liquorbee.mobile.wholesale.LineRow
import com.liquorbee.mobile.wholesale.itemCount

@Composable
fun LbCartScreen(viewModel: LbCartViewModel, onBack: () -> Unit, onOpenScan: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val carts by viewModel.carts.collectAsState()
    val reorder by viewModel.reorder.collectAsState()
    val lines = carts.lb
    val title = if (uiState.isList) "LB List" else "LB Shopping Cart"
    val showSuggested = reorder.supported && !uiState.isList
    val tab = if (showSuggested) uiState.tab else LbTab.Cart
    val estTotal = lines.sumOf { (reorder.infoFor(it.code)?.caseCost ?: 0.0) * it.cases }

    LbScreen(
        title = title,
        onBack = onBack,
        actions = { IconButton(onClick = onOpenScan) { Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan") } },
        tabs = if (!showSuggested) null else {
            {
                val n = reorder.reorderList.size
                LbTabs(listOf(if (reorder.loaded) "Suggested ($n)" else "Suggested", if (lines.isEmpty()) "Cart" else "Cart (${lines.size})"), tab.ordinal) {
                    viewModel.selectTab(LbTab.entries[it])
                }
            }
        },
        bottomBar = {
            if (lines.isNotEmpty()) {
                val cases = carts.lbCount
                LbFooterBar(
                    caption = if (estTotal > 0) "${itemCount(lines.size)} · $cases ${if (cases == 1) "case" else "cases"}" else itemCount(lines.size),
                    value = if (estTotal > 0) "~${money(estTotal)}" else "$cases ${if (cases == 1) "case" else "cases"}"
                ) {
                    if (reorder.canStageOrders && !uiState.isList) LbSecondaryButton("Stage", onClick = { viewModel.openStagedDialog(StagedDialog.Stage) })
                    if (uiState.canCheckout) LbPrimaryButton("Send", onClick = viewModel::askSubmit)
                    else TextButton(onClick = viewModel::askClear) { Text("Clear", color = LbBad) }
                }
            }
        },
        toast = uiState.toast,
        onToastShown = viewModel::toastShown
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (showSuggested) Toolbar(reorder, uiState, viewModel)
            when (tab) {
                LbTab.Suggested -> SuggestedTab(reorder, carts, uiState, viewModel)
                LbTab.Cart -> CartTab(reorder, carts, uiState, viewModel, onOpenScan)
            }
        }
    }

    if (uiState.reorderSettingsOpen) ReorderSettingsDialog(reorder, onSave = viewModel::saveReorderSettings,
        onRefresh = viewModel::reloadReorder, onDismiss = viewModel::closeReorderSettings)
    when (uiState.stagedDialog) {
        StagedDialog.Stage -> StageDialog(uiState, carts, viewModel)
        StagedDialog.Load -> LoadStagedDialog(uiState, carts, viewModel)
        null -> Unit
    }
    if (uiState.confirmSubmit) SubmitDialog(uiState, carts, estTotal, viewModel)
    if (uiState.confirmClear) AlertDialog(
        onDismissRequest = viewModel::dismissConfirm, containerColor = Color.White,
        title = { Text("Clear ${if (uiState.isList) "list" else "cart"}?", color = LbText, fontWeight = FontWeight.Medium) },
        text = { Text("Remove every item.", color = LbMuted) },
        confirmButton = { TextButton(onClick = viewModel::clearCart) { Text("Clear", color = LbBad) } },
        dismissButton = { TextButton(onClick = viewModel::dismissConfirm) { Text("Cancel", color = LbText) } }
    )
}

/** The report setting (forecast weeks / reorder point) and staged orders, above both tabs. */
@Composable
private fun Toolbar(reorder: ReorderState, uiState: LbCartUiState, viewModel: LbCartViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp)) {
        Box(Modifier.background(Color.White, LbControlShape)) { ReorderSettingChip(reorder, onClick = viewModel::openReorderSettings) }
        Box(Modifier.weight(1f))
        if (reorder.canStageOrders) TextButton(onClick = { viewModel.openStagedDialog(StagedDialog.Load) }) { Text("Load staged", color = LbActive) }
    }
}

@Composable
private fun SuggestedTab(reorder: ReorderState, carts: CartsSnapshot, uiState: LbCartUiState, viewModel: LbCartViewModel) {
    val q = uiState.search.trim().lowercase()
    val rows = reorder.reorderList.filter { q.isEmpty() || it.code.lowercase().contains(q) || it.description.orEmpty().lowercase().contains(q) }
    when {
        reorder.loading && !reorder.loaded -> LbEmpty("Loading the ${reorder.settings.label} report…", loading = true)
        reorder.error != null && !reorder.loaded -> LbEmpty("Couldn't load the report", reorder.error) {
            LbSecondaryButton("Try again", onClick = viewModel::reloadReorder)
        }
        else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            item {
                OutlinedTextField(value = uiState.search, onValueChange = viewModel::onSearch, singleLine = true,
                    placeholder = { Text("Search suggested items", color = LbFaint) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = LbFaint) },
                    shape = LbControlShape, modifier = Modifier.fillMaxWidth().background(Color.White, LbControlShape))
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (reorder.reorderList.isEmpty()) "Nothing to reorder" else "${rows.size} to reorder · ${reorder.settings.label}",
                        color = LbMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    if (reorder.reorderList.isNotEmpty()) TextButton(onClick = viewModel::addAllSuggested) { Text("Add all", color = LbActive) }
                }
            }
            if (rows.isEmpty()) item {
                Box(Modifier.fillMaxWidth().heightIn(min = 240.dp)) {
                    LbEmpty(if (q.isNotEmpty()) "No matching items" else "Nothing needs reordering",
                        if (q.isNotEmpty()) null else "Nothing sold enough to use up what's on hand and on order for this setting.")
                }
            }
            items(rows, key = { it.code }) { info -> SuggestedRow(info, carts.lbQty(info.code), onCases = { viewModel.setCases(info, it) }) }
        }
    }
}

@Composable
private fun SuggestedRow(info: ReorderInfo, inCart: Int, onCases: (Int) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    LbPanel(padding = PaddingValues(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)) {
        Text(info.description ?: info.code, color = LbText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(info.code, info.caseCost?.takeIf { it > 0 }?.let { "${money(it)}/cs" },
            info.availableCases?.let { "$it cs avail" }, info.casesInLb?.takeIf { it > 0 }?.let { "$it in LiquorBee" }).joinToString("  ·  "),
            color = LbFaint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(info.summary.removePrefix("Reorder · "), color = LbWarn, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp))
            if (inCart > 0) QtyStepper(qty = inCart, onQty = onCases, onEdit = { editing = true })
            else LbPrimaryButton("Add ${info.casesToOrder} cs", onClick = { onCases(info.casesToOrder) })
        }
    }
    if (editing) QuantityDialog(title = info.description ?: info.code, initial = inCart, onDismiss = { editing = false },
        onSave = { onCases(it); editing = false })
}

@Composable
private fun CartTab(reorder: ReorderState, carts: CartsSnapshot, uiState: LbCartUiState, viewModel: LbCartViewModel, onOpenScan: () -> Unit) {
    val lines = carts.lb
    if (lines.isEmpty()) {
        LbEmpty(if (uiState.isList) "LB list is empty" else "Cart is empty",
            if (reorder.supported && !uiState.isList) "Add items from Suggested, scan them, or load a staged order." else "Scan items and tap LB to add cases.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (reorder.supported && !uiState.isList) LbSecondaryButton("Suggested", onClick = { viewModel.selectTab(LbTab.Suggested) })
                LbPrimaryButton("Scan items", onClick = onOpenScan)
            }
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        if (uiState.canCheckout && reorder.loaded) item {
            val flagged = lines.count { reorder.infoFor(it.code)?.needsReorder == true }
            Text("$flagged of ${lines.size} ${if (lines.size == 1) "item needs" else "items need"} reordering · ${reorder.settings.label}",
                color = LbMuted, fontSize = 13.sp)
        }
        items(lines, key = { it.code }) { line ->
            val cost = reorder.infoFor(line.code)?.caseCost?.takeIf { it > 0 }
            LineRow(
                title = line.itemName ?: line.code,
                subtitle = listOfNotNull(line.code, cost?.let { "${money(it)}/cs" }).joinToString("  ·  "),
                qty = line.cases,
                trailing = cost?.let { money(it * line.cases) } ?: if (line.cases == 1) "1 case" else "${line.cases} cases",
                onQty = { viewModel.setLineCases(line.code, it) },
                detail = if (reorder.supported) { { ReorderBadge(line.code, reorder, onClick = viewModel::openReorderSettings) } } else null
            )
        }
        if (uiState.canCheckout) item {
            TextButton(onClick = viewModel::askClear) { Text("Clear cart", color = LbBad) }
        }
    }
}

@Composable
private fun SubmitDialog(uiState: LbCartUiState, carts: CartsSnapshot, estTotal: Double, viewModel: LbCartViewModel) {
    val cases = carts.lbCount
    val allowed = uiState.restrictedPosUser || cases >= LbMarket.MS_PURCHASE_NOW_MIN_CASES
    AlertDialog(
        onDismissRequest = viewModel::dismissConfirm,
        containerColor = Color.White,
        title = { Text("Send to LiquorBee", color = LbText, fontWeight = FontWeight.Medium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Add ${itemCount(carts.lb.size)} ($cases cases${if (estTotal > 0) ", ~${money(estTotal)}" else ""}) to your LiquorBee order?",
                    color = LbMuted, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Purchase now", color = LbText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(if (allowed) "Order right away instead of adding to LiquorBee's fill list."
                            else "Needs at least ${LbMarket.MS_PURCHASE_NOW_MIN_CASES} cases.", color = LbMuted, fontSize = 12.sp)
                    }
                    Switch(checked = allowed && (uiState.purchaseNow || uiState.restrictedPosUser), onCheckedChange = viewModel::setPurchaseNow,
                        enabled = allowed && !uiState.restrictedPosUser && !uiState.working)
                }
                uiState.error?.let { Text(it, color = LbBad, fontSize = 13.sp) }
            }
        },
        confirmButton = { LbPrimaryButton("Send", onClick = viewModel::submit, loading = uiState.working) },
        dismissButton = { TextButton(onClick = viewModel::dismissConfirm, enabled = !uiState.working) { Text("Cancel", color = LbText) } }
    )
}

@Composable
private fun StageDialog(uiState: LbCartUiState, carts: CartsSnapshot, viewModel: LbCartViewModel) {
    val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = LiquorBeeNavy, selectedLabelColor = Color.White, containerColor = Color.White)
    AlertDialog(
        onDismissRequest = viewModel::dismissStagedDialog,
        containerColor = Color.White,
        title = { Text("Stage order", color = LbText, fontWeight = FontWeight.Medium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Save ${itemCount(carts.lb.size)} (${carts.lbCount} cases) to finish later here or on the web. The cart is cleared.",
                    color = LbMuted, fontSize = 14.sp)
                FilterChip(selected = uiState.stageTargetId == null, onClick = { viewModel.setStageTarget(null) }, label = { Text("New order") }, colors = chipColors)
                if (uiState.stageTargetId == null) {
                    OutlinedTextField(value = uiState.stageName, onValueChange = viewModel::setStageName, label = { Text("Order name") },
                        placeholder = { Text("e.g. Friday restock") }, singleLine = true, enabled = !uiState.working,
                        shape = LbControlShape, modifier = Modifier.fillMaxWidth())
                }
                if (uiState.stagedLoading) CircularProgressIndicator(color = LbActive, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                else if (uiState.stagedOrders.isNotEmpty()) {
                    Text("Or add to an existing order", color = LbMuted, fontSize = 12.sp)
                    LazyColumn(Modifier.heightIn(max = 200.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(uiState.stagedOrders, key = { it.orderId }) { order ->
                            val selected = uiState.stageTargetId == order.orderId
                            Text("${order.name} · ${itemCount(order.lines.size)}", color = if (selected) LbActive else LbText, fontSize = 14.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().background(if (selected) LbActive.copy(alpha = 0.08f) else LbPage, LbControlShape)
                                    .clickable { viewModel.setStageTarget(order.orderId) }.padding(horizontal = 12.dp, vertical = 10.dp))
                        }
                    }
                }
                uiState.error?.let { Text(it, color = LbBad, fontSize = 13.sp) }
            }
        },
        confirmButton = { LbPrimaryButton("Stage", onClick = viewModel::stageOrder, loading = uiState.working) },
        dismissButton = { TextButton(onClick = viewModel::dismissStagedDialog, enabled = !uiState.working) { Text("Cancel", color = LbText) } }
    )
}

@Composable
private fun LoadStagedDialog(uiState: LbCartUiState, carts: CartsSnapshot, viewModel: LbCartViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissStagedDialog,
        containerColor = Color.White,
        title = { Text("Load staged order", color = LbText, fontWeight = FontWeight.Medium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (carts.lb.isNotEmpty()) Text("Loading replaces the ${itemCount(carts.lb.size)} in your cart.", color = LbBad, fontSize = 13.sp)
                when {
                    uiState.stagedLoading -> CircularProgressIndicator(color = LbActive, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    uiState.stagedOrders.isEmpty() -> Text(uiState.error ?: "No staged orders yet. Use Stage on the cart to save one.",
                        color = if (uiState.error != null) LbBad else LbMuted, fontSize = 14.sp)
                    else -> LazyColumn(Modifier.height(320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(uiState.stagedOrders, key = { it.orderId }) { order ->
                            Column(Modifier.fillMaxWidth().background(LbPage, LbControlShape)
                                .clickable { viewModel.loadStagedOrder(order) }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(order.name, color = LbText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text(listOfNotNull("${itemCount(order.lines.size)} · ${order.cases} cases", order.createdDate?.take(10)).joinToString(" · "),
                                    color = LbMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = viewModel::dismissStagedDialog) { Text("Cancel", color = LbText) } }
    )
}

