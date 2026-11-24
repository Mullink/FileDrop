package com.liquorbee.mobile.wholesale

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquorbee.mobile.carts.OrderLine
import com.liquorbee.mobile.carts.QtyStepper
import com.liquorbee.mobile.carts.QuantityDialog
import com.liquorbee.mobile.carts.TabularNumbers
import com.liquorbee.mobile.carts.WholesaleAccount
import com.liquorbee.mobile.carts.money
import com.liquorbee.mobile.core.network.WholesaleOrderDto
import com.liquorbee.mobile.core.ui.LbActive
import com.liquorbee.mobile.core.ui.LbBad
import com.liquorbee.mobile.core.ui.LbBorder
import com.liquorbee.mobile.core.ui.LbEmpty
import com.liquorbee.mobile.core.ui.LbFaint
import com.liquorbee.mobile.core.ui.LbFooterBar
import com.liquorbee.mobile.core.ui.LbGood
import com.liquorbee.mobile.core.ui.LbMuted
import com.liquorbee.mobile.core.ui.LbPanel
import com.liquorbee.mobile.core.ui.LbPill
import com.liquorbee.mobile.core.ui.LbPrimaryButton
import com.liquorbee.mobile.core.ui.LbScreen
import com.liquorbee.mobile.core.ui.LbSecondaryButton
import com.liquorbee.mobile.core.ui.LbTabs
import com.liquorbee.mobile.core.ui.LbText
import com.liquorbee.mobile.core.ui.LbControlShape
import com.liquorbee.mobile.core.ui.LiquorBeeNavy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun WholesaleScreen(
    viewModel: WholesaleViewModel,
    onBack: () -> Unit,
    onOpenScan: () -> Unit,
    onOpenOrder: (orderId: String) -> Unit,
    initialTab: WholesaleTab? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val carts by viewModel.carts.collectAsState()
    LaunchedEffect(initialTab) { if (initialTab != null) viewModel.selectTab(initialTab) }

    val account = carts.currentAccount
    val cart = carts.wholesaleCart(account.key)
    val lines = cart?.lines.orEmpty()

    LbScreen(
        title = "Wholesale",
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenScan) { Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan") }
            if (uiState.tab == WholesaleTab.Orders) IconButton(onClick = viewModel::loadOrders) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
        },
        tabs = {
            LbTabs(listOf(if (lines.isEmpty()) "Cart" else "Cart (${lines.size})", "Orders"), uiState.tab.ordinal) {
                viewModel.selectTab(WholesaleTab.entries[it])
            }
        },
        bottomBar = {
            if (uiState.tab == WholesaleTab.Cart && lines.isNotEmpty()) {
                LbFooterBar(caption = "${itemCount(lines.size)} · ${account.label}", value = money(cart?.total ?: 0.0)) {
                    TextButton(onClick = viewModel::askClear) { Text("Clear", color = LbBad) }
                    LbPrimaryButton("Place order", onClick = viewModel::askSubmit)
                }
            }
        },
        toast = uiState.toast,
        onToastShown = viewModel::toastShown
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (uiState.tab) {
                WholesaleTab.Cart -> CartTab(account, lines, carts.wholesale.filter { !it.account.isGeneral || it.lines.isNotEmpty() }
                    .filter { it.lines.isNotEmpty() && it.account.key != account.key }.map { it.account to it.lines.size },
                    viewModel, onOpenScan)
                WholesaleTab.Orders -> OrdersTab(uiState, viewModel, onOpenOrder)
            }
        }
    }

    if (uiState.accountPickerOpen) AccountPicker(uiState, carts.wholesale.associate { it.account.key to it.lines.size }, account, viewModel)
    if (uiState.confirmSubmit) SubmitDialog(uiState, account, lines, cart?.total ?: 0.0, viewModel)
    if (uiState.confirmClear) AlertDialog(
        onDismissRequest = viewModel::dismissConfirm, containerColor = Color.White,
        title = { Text("Clear cart?", color = LbText, fontWeight = FontWeight.Medium) },
        text = { Text("Remove every item from ${account.label}'s cart.", color = LbMuted) },
        confirmButton = { TextButton(onClick = viewModel::clearCart) { Text("Clear", color = LbBad) } },
        dismissButton = { TextButton(onClick = viewModel::dismissConfirm) { Text("Cancel", color = LbText) } }
    )
}

@Composable
private fun CartTab(
    account: WholesaleAccount,
    lines: List<OrderLine>,
    otherCarts: List<Pair<WholesaleAccount, Int>>,
    viewModel: WholesaleViewModel,
    onOpenScan: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        item {
            LbPanel(padding = PaddingValues(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Order for", color = LbMuted, fontSize = 12.sp)
                        Text(account.label, color = LbText, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = viewModel::openAccountPicker) { Text("Change", color = LbActive) }
                }
            }
        }
        if (otherCarts.isNotEmpty()) item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Other carts:", color = LbMuted, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
                otherCarts.forEach { (acct, n) ->
                    FilterChip(selected = false, onClick = { viewModel.chooseAccount(acct) }, label = { Text("${acct.label} ($n)") },
                        colors = FilterChipDefaults.filterChipColors(containerColor = Color.White))
                }
            }
        }
        if (lines.isEmpty()) item {
            Box(Modifier.fillMaxWidth().heightIn(min = 320.dp)) {
                LbEmpty("No items for ${account.label}", "Scan items and tap Wholesale to add them.") {
                    LbPrimaryButton("Scan items", onClick = onOpenScan)
                }
            }
        } else items(lines, key = { it.itemCode }) { line ->
            LineRow(
                title = line.itemName ?: line.itemCode,
                subtitle = "${line.itemCode}  ·  ${money(line.unitPrice)} each",
                qty = line.qty, trailing = money(line.lineTotal),
                onQty = { viewModel.setQty(line.itemCode, it) }
            )
        }
    }
}

/** One cart line: name, code, stepper, total, remove. Shared look with the LB cart. */
@Composable
internal fun LineRow(
    title: String,
    subtitle: String,
    qty: Int,
    trailing: String?,
    onQty: (Int) -> Unit,
    detail: (@Composable () -> Unit)? = null
) {
    var editing by remember { mutableStateOf(false) }
    LbPanel(padding = PaddingValues(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(title, color = LbText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = LbFaint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                if (detail != null) Box(Modifier.padding(top = 6.dp)) { detail() }
            }
            IconButton(onClick = { onQty(0) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove", tint = LbFaint, modifier = Modifier.size(20.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            QtyStepper(qty = qty, onQty = onQty, onEdit = { editing = true })
            Spacer(Modifier.weight(1f))
            if (trailing != null) Text(trailing, color = LbText, fontSize = 15.sp, fontWeight = FontWeight.Medium, style = TabularNumbers,
                modifier = Modifier.padding(end = 8.dp))
        }
    }
    if (editing) QuantityDialog(title = title, initial = qty, onDismiss = { editing = false }, onSave = { onQty(it); editing = false })
}

@Composable
private fun OrdersTab(uiState: WholesaleUiState, viewModel: WholesaleViewModel, onOpenOrder: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OrderFilter.entries.forEach { f ->
                val n = uiState.orders.count { o -> f.matches(o) }
                FilterChip(selected = uiState.filter == f, onClick = { viewModel.setFilter(f) },
                    label = { Text(if (uiState.ordersLoaded) "${f.label} ($n)" else f.label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LiquorBeeNavy, selectedLabelColor = Color.White, containerColor = Color.White))
            }
        }
        if (uiState.ordersLoading && uiState.ordersLoaded) {
            Text("Refreshing from the POS…", color = LbMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
        }
        when {
            uiState.ordersLoading && !uiState.ordersLoaded -> LbEmpty("Loading orders…", loading = true)
            uiState.ordersError != null && !uiState.ordersLoaded -> LbEmpty("Couldn't load orders", uiState.ordersError) {
                LbSecondaryButton("Try again", onClick = viewModel::loadOrders)
            }
            uiState.filteredOrders.isEmpty() -> LbEmpty(
                if (uiState.filter == OrderFilter.Today) "No orders today" else "No ${uiState.filter.label.lowercase()} orders",
                if (uiState.filter == OrderFilter.Today || uiState.filter == OrderFilter.Open) "Orders you place show up here." else null)
            else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.filteredOrders, key = { it.orderId ?: it.label }) { order ->
                    OrderRow(order, onClick = { order.orderId?.let(onOpenOrder) })
                }
            }
        }
    }
}

@Composable
private fun OrderRow(order: WholesaleOrderDto, onClick: () -> Unit) {
    LbPanel(Modifier.clickable(onClick = onClick), padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(order.customerName?.takeIf { it.isNotBlank() } ?: "General Customer", color = LbText, fontSize = 14.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(order.label.takeIf { it.isNotBlank() }?.let { "#$it" }, shortDate(order.createdDate)).joinToString("  ·  "),
                    color = LbMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(order.total), color = LbText, fontSize = 15.sp, fontWeight = FontWeight.Medium, style = TabularNumbers)
                Box(Modifier.padding(top = 4.dp)) { StatusPill(order) }
            }
        }
    }
}

@Composable
internal fun StatusPill(order: WholesaleOrderDto) {
    LbPill(order.statusLabel, when {
        order.isClosed -> LbMuted
        order.orderStatus == 2 -> LbGood
        else -> LbActive
    })
}

@Composable
private fun AccountPicker(uiState: WholesaleUiState, counts: Map<String, Int>, current: WholesaleAccount, viewModel: WholesaleViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissAccountPicker,
        containerColor = Color.White,
        title = { Text("Order for", color = LbText, fontWeight = FontWeight.Medium) },
        text = {
            Column {
                val all = listOf(WholesaleAccount.General) + uiState.accounts
                all.forEach { acct ->
                    val n = counts[acct.key] ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.chooseAccount(acct) }
                            .background(if (acct.key == current.key) LbActive.copy(alpha = 0.06f) else Color.Transparent, LbControlShape)
                            .padding(horizontal = 10.dp, vertical = 12.dp)) {
                        Text(acct.label, color = if (acct.key == current.key) LbActive else LbText, fontSize = 15.sp,
                            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (n > 0) Text(itemCount(n), color = LbMuted, fontSize = 12.sp)
                    }
                    HorizontalDivider(color = LbBorder)
                }
                if (uiState.accountsLoading) CircularProgressIndicator(color = LbActive, strokeWidth = 2.dp,
                    modifier = Modifier.padding(top = 12.dp).size(20.dp))
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = viewModel::dismissAccountPicker) { Text("Close", color = LbText) } }
    )
}

@Composable
private fun SubmitDialog(uiState: WholesaleUiState, account: WholesaleAccount, lines: List<OrderLine>, total: Double, viewModel: WholesaleViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissConfirm,
        containerColor = Color.White,
        title = { Text("Place order", color = LbText, fontWeight = FontWeight.Medium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Send ${itemCount(lines.size)} (${money(total)}) to the register for ${account.label}?", color = LbMuted, fontSize = 14.sp)
                OutlinedTextField(value = uiState.notes, onValueChange = viewModel::onNotesChanged, label = { Text("Notes (optional)") },
                    enabled = !uiState.working, shape = LbControlShape, maxLines = 3, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Build pick list", color = LbText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("You can also send it later from the order.", color = LbMuted, fontSize = 12.sp)
                    }
                    Switch(checked = uiState.buildPickList, onCheckedChange = viewModel::setBuildPickList, enabled = !uiState.working)
                }
                uiState.error?.let { Text(it, color = LbBad, fontSize = 13.sp) }
            }
        },
        confirmButton = { LbPrimaryButton("Place order", onClick = viewModel::submit, loading = uiState.working) },
        dismissButton = { TextButton(onClick = viewModel::dismissConfirm, enabled = !uiState.working) { Text("Cancel", color = LbText) } }
    )
}

internal fun itemCount(n: Int) = if (n == 1) "1 item" else "$n items"

/** "2026-11-20T10:03:00" -> "Nov 20, 10:03 AM"; falls back to the raw date part. */
internal fun shortDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val parsed = parseOrderDate(raw) ?: return raw.take(10)
    // Shown in the phone's time zone (the server sends UTC).
    return java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US).format(parsed)
}
