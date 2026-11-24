package com.liquorbee.mobile.carts

import com.liquorbee.mobile.core.ui.LbWarn
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquorbee.mobile.core.network.SubCustomerCatalogItemDto
import com.liquorbee.mobile.itemresolve.UnknownBarcodeFlow
import com.liquorbee.mobile.core.ui.LbActive
import com.liquorbee.mobile.core.ui.ScanMode
import com.liquorbee.mobile.core.ui.ScanModeToggle
import com.liquorbee.mobile.core.ui.LbBad
import com.liquorbee.mobile.core.ui.LbBorder
import com.liquorbee.mobile.core.ui.LbDivider
import com.liquorbee.mobile.core.ui.LbFaint
import com.liquorbee.mobile.core.ui.LbGood
import com.liquorbee.mobile.core.ui.LbMuted
import com.liquorbee.mobile.core.ui.LbPage
import com.liquorbee.mobile.core.ui.LbText
import com.liquorbee.mobile.core.ui.LiquorBeeNavy

private val WholesaleAccent = Color(0xFF006EF8)
private val LbAccent = Color(0xFFB45309)
private val RestockAccent = Color(0xFFC2410C)
private val PriceAccent = Color(0xFF166534)

@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onOpenWholesale: () -> Unit,
    onOpenLbCart: () -> Unit,
    onPriceCheck: (query: String) -> Unit,
    onEditItem: (itemId: String?, query: String, price: Double?) -> Unit,
    onOpenPickList: (id: Int) -> Unit = {},
    /** Switch to Item edits (Price Check), carrying the current item or search. */
    onSwitchToItemEdits: ((query: String) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val carts by viewModel.carts.collectAsState()
    val restock by viewModel.restock.collectAsState()
    val reorder by viewModel.reorder.collectAsState()
    val insights by viewModel.insights.collectAsState()
    val wholesaleEnabled by (LocalContext.current.applicationContext as com.liquorbee.mobile.LiquorBeeApplication)
        .wholesaleAccess.enabled.collectAsState()
    var reorderSettingsOpen by remember { mutableStateOf(false) }
    var cartsOpen by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    // Which list's quantity is being typed in (tap the number on a tile).
    var editingList by remember { mutableStateOf<String?>(null) }

    // Keep the search field focused so the next hardware scan lands in it without a tap.
    // Focus without popping the soft keyboard - on these handhelds the hardware scanner types into
    // the focused field, and the keyboard would just cover the item. Tapping the field shows it.
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(uiState.focusRequestToken, uiState.catalogError) {
        if (uiState.catalogError == null) {
            runCatching { searchFocus.requestFocus() }
            keyboard?.hide()
            // The IME can re-show itself just after focus moves (e.g. arriving from Home).
            kotlinx.coroutines.delay(200)
            keyboard?.hide()
        }
    }

    Scaffold(
        containerColor = LbPage,
        topBar = {
            TopAppBar(
                title = {
                    if (onSwitchToItemEdits != null) ScanModeToggle(ScanMode.StockMgmt, onSelect = {
                        val sel = uiState.selected
                        onSwitchToItemEdits(sel?.upc?.takeIf { it.isNotBlank() } ?: sel?.itemCode ?: uiState.query.trim())
                    }) else Text("Scan", fontWeight = FontWeight.Medium)
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                // One Carts button (both carts together) keeps the item and its list tiles on screen without scrolling.
                actions = {
                    ToolbarCart("Carts", (if (wholesaleEnabled) carts.wholesaleCount else 0) + carts.lbCount, Icons.Filled.ShoppingCart) { cartsOpen = true }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LiquorBeeNavy, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White, actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (uiState.catalogError != null) {
                CenteredMessage {
                    Text(uiState.catalogError.orEmpty(), color = LbBad, textAlign = TextAlign.Center)
                    Button(onClick = { viewModel.loadCatalog(forceRefresh = true) }, modifier = Modifier.padding(top = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LiquorBeeNavy), shape = FieldShape) { Text("Try Again") }
                }
            } else {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
                    ScanSearchField(
                        value = uiState.query,
                        loading = uiState.searching,
                        onValueChange = viewModel::onQueryChanged,
                        onSubmit = viewModel::onSearchSubmitted,
                        onClear = viewModel::clearQuery,
                        modifier = Modifier.focusRequester(searchFocus)
                    )
                    // Fixed single-line slot: a message appearing must never shift the action
                    // buttons below it, or the next tap lands on the wrong one.
                    val msg = uiState.message
                    Text(msg?.text.orEmpty(), color = if (msg?.isError == true) LbBad else LbGood, fontSize = 13.sp,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(18.dp))
                }
                val selected = uiState.selected
                when {
                    uiState.catalogLoading -> CenteredMessage {
                        CircularProgressIndicator(color = LbActive)
                        Text("Loading items…",
                            color = LbMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                    }
                    uiState.query.isNotBlank() -> SearchResults(uiState, onSelect = viewModel::select)
                    selected != null -> Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ItemCard(selected, onClose = viewModel::clearSelection, reorder = reorder, insights = insights,
                            linkedBarcode = uiState.linkedBarcode, onReorderSettings = { reorderSettingsOpen = true },
                            onUseSuggestedPrice = { price ->
                                onEditItem(selected.itemId?.takeIf { it.isNotBlank() },
                                    selected.upc?.takeIf { it.isNotBlank() } ?: selected.itemCode.orEmpty(), price)
                            })
                        val account = carts.currentAccount
                        val wholesaleQty = carts.wholesaleQty(account.key, selected.itemCode)
                        val lbQty = carts.lbQty(selected.itemCode)
                        val restockQty = restock.qtyFor(selected.itemCode)
                        val lbName = if (uiState.lbCanCheckout) "LB Cart" else "LB List"
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (wholesaleEnabled) ActionTile("Wholesale", account.label, Icons.Filled.Storefront, WholesaleAccent, Modifier.weight(1f),
                                enabled = !uiState.busy, onClick = viewModel::addToWholesale, onLongClick = viewModel::openAccountPicker,
                                qty = wholesaleQty, onQty = { viewModel.setWholesaleQty(it) }, onEditQty = { editingList = "wholesale" })
                            ActionTile(lbName, "Cases", Icons.Filled.LocalShipping, LbAccent, Modifier.weight(1f),
                                onClick = viewModel::addToLb, onLongClick = onOpenLbCart,
                                qty = lbQty, onQty = { viewModel.setLbQty(it) }, onEditQty = { editingList = "lb" })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ActionTile("Restock", "On list", Icons.Filled.Inventory2, RestockAccent, Modifier.weight(1f),
                                enabled = !uiState.restockBusy, onClick = viewModel::addToRestock,
                                onLongClick = { viewModel.openRestockPickList(onOpenPickList) },
                                qty = restockQty, onQty = { viewModel.setRestockQty(it) }, onEditQty = { editingList = "restock" })
                            ActionTile("Edit Item", "Price & details", Icons.Filled.Edit, PriceAccent, Modifier.weight(1f),
                                onClick = { onEditItem(selected.itemId?.takeIf { it.isNotBlank() },
                                    selected.upc?.takeIf { it.isNotBlank() } ?: selected.itemCode.orEmpty(), null) })
                        }
                    }
                    else -> CenteredMessage {
                        Box(Modifier.size(72.dp).background(LbActive.copy(alpha = 0.08f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.QrCodeScanner, null, tint = LbActive, modifier = Modifier.size(36.dp))
                        }
                        Text("Scan an item", color = LbText, fontWeight = FontWeight.Medium, fontSize = 17.sp, modifier = Modifier.padding(top = 12.dp))
                        // Nothing scanned yet: both carts, ready to open.
                        UnifiedCarts(carts, lbName = if (uiState.lbCanCheckout) "LB Cart" else "LB List", showWholesale = wholesaleEnabled,
                            onOpenWholesale = onOpenWholesale, onOpenLbCart = onOpenLbCart,
                            onChangeAccount = viewModel::openAccountPicker, modifier = Modifier.padding(top = 20.dp))
                    }
                }
            }
        }
    }

    val editingItem = uiState.selected
    if (editingList != null && editingItem != null) {
        val list = editingList
        val (title, initial) = when (list) {
            "wholesale" -> "Wholesale \u00b7 ${carts.currentAccount.label}" to carts.wholesaleQty(carts.currentAccount.key, editingItem.itemCode)
            "lb" -> "${if (uiState.lbCanCheckout) "LB Cart" else "LB List"} (cases)" to carts.lbQty(editingItem.itemCode)
            else -> "Restock" to restock.qtyFor(editingItem.itemCode)
        }
        QuantityDialog(title = title, initial = initial,
            onDismiss = { editingList = null; viewModel.refocus() },
            onSave = { qty ->
                when (list) {
                    "wholesale" -> viewModel.setWholesaleQty(qty)
                    "lb" -> viewModel.setLbQty(qty)
                    else -> viewModel.setRestockQty(qty)
                }
                editingList = null; viewModel.refocus()
            })
    }

    UnknownBarcodeFlow(barcode = uiState.unknownBarcode, onDismiss = viewModel::dismissUnknown,
        onResolved = { barcode, note -> viewModel.onUnknownResolved(barcode, note) },
        onScanAnother = viewModel::scanFromOutside)

    if (reorderSettingsOpen) ReorderSettingsDialog(reorder,
        onSave = { viewModel.saveReorderSettings(it); reorderSettingsOpen = false; viewModel.refocus() },
        onRefresh = viewModel::reloadReorder,
        onDismiss = { reorderSettingsOpen = false; viewModel.refocus() })

    if (cartsOpen) androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = { cartsOpen = false; viewModel.refocus() }, containerColor = LbPage
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
            Text("Carts", color = LbText, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 12.dp))
            UnifiedCarts(carts, lbName = if (uiState.lbCanCheckout) "LB Cart" else "LB List", showWholesale = wholesaleEnabled,
                onOpenWholesale = { cartsOpen = false; onOpenWholesale() },
                onOpenLbCart = { cartsOpen = false; onOpenLbCart() },
                onChangeAccount = { cartsOpen = false; viewModel.openAccountPicker() })
        }
    }

    if (uiState.accountPickerOpen) AccountPickerDialog(uiState, current = carts.currentAccount, onPick = viewModel::chooseAccount, onDismiss = viewModel::dismissAccountPicker)
}

@Composable
private fun SearchResults(uiState: ScanUiState, onSelect: (SubCustomerCatalogItemDto) -> Unit) {
    if (uiState.noResults && !uiState.searching) {
        CenteredMessage { Text("No matching items.", color = LbMuted) }
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), modifier = Modifier.fillMaxSize()) {
        items(uiState.results) { item ->
            Column(Modifier.fillMaxWidth().background(Color.White).clickable { onSelect(item) }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).heightIn(min = 40.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(item.itemName.orEmpty(), color = LbText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(item.itemCode, item.upc?.takeIf { it.isNotBlank() }).joinToString(" · "),
                            color = LbFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(money(item.retailPrice), color = LbGood, fontSize = 14.sp, fontWeight = FontWeight.Medium, style = TabularNumbers)
                }
                HorizontalDivider(color = LbDivider)
            }
        }
    }
}

@Composable
private fun ItemCard(
    item: SubCustomerCatalogItemDto,
    onClose: () -> Unit,
    reorder: ReorderState,
    insights: ItemInsightsState,
    linkedBarcode: String?,
    onReorderSettings: () -> Unit,
    onUseSuggestedPrice: (Double) -> Unit
) {
    Column(Modifier.fillMaxWidth().background(Color.White, CardShape).border(1.dp, LbBorder, CardShape).padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(item.itemName.orEmpty(), color = LbText, fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp,
                modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Clear item", tint = LbFaint, modifier = Modifier.size(20.dp))
            }
        }
        Text(listOfNotNull(item.itemCode?.let { "SKU $it" }, item.upc?.takeIf { it.isNotBlank() }?.let { "UPC $it" }).joinToString("   "),
            color = LbMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text(money(item.retailPrice), color = LbGood, fontSize = 30.sp, fontWeight = FontWeight.Medium, style = TabularNumbers,
                modifier = Modifier.weight(1f))
            val reserve = rememberQtyOnReserve(item.itemCode)
            Column(horizontalAlignment = Alignment.End) {
                Text("${item.qtyOnHand} on hand", color = if (item.qtyOnHand > 0) LbText else LbBad, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (reserve != null) {
                    Text("$reserve on reserve", color = if (reserve > 0) LbText else LbMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        // Status lines: barcode link, reorder (tap to change the setting), margin, shelf review.
        Column(Modifier.fillMaxWidth().padding(top = 12.dp).border(1.dp, LbBorder, FieldShape)) {
            var first = true
            @Composable fun line(label: String, text: String, color: Color, detail: String? = null, onClick: (() -> Unit)? = null) {
                if (!first) androidx.compose.material3.HorizontalDivider(color = LbBorder)
                first = false
                StatusLine(label, text, color, detail, onClick)
            }
            if (linkedBarcode != null) line("Link", "\u2713 Barcode $linkedBarcode linked to this item", LbGood)
            reorderStatus(item.itemCode, reorder)?.let { (text, color) -> line("Reorder", text, color, onClick = onReorderSettings) }
            marginStatus(item, insights)?.let { (text, color, detail) ->
                // Below target with a suggested price: tap opens Edit Item with that price typed in, ready to save.
                val suggested = suggestedPrice(item, insights)
                line("Margin", text, color, if (suggested != null) "$detail \u00b7 tap to use" else detail,
                    onClick = suggested?.let { { onUseSuggestedPrice(it) } })
            }
            shelfStatus(item, insights)?.let { (text, color, detail) -> line("Shelf", text, color, detail) }
                // Shelf Review only covers items with stock on hand.
                ?: if (insights.shelfAvailable && item.qtyOnHand <= 0) line("Shelf", "Not reviewed \u00b7 no stock on hand", LbMuted) else Unit
            if (first && insights.loading) line("Status", "Loading margin and shelf review\u2026", LbMuted)
        }
    }
}

@Composable
private fun StatusLine(label: String, text: String, color: Color, detail: String?, onClick: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = LbMuted, fontSize = 12.sp, modifier = Modifier.width(58.dp).padding(top = 1.dp))
        Column(Modifier.weight(1f)) {
            Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (detail != null) Text(detail, color = LbMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

/**
 * Margin vs the store's target from Margin Monitoring (below target = the web page's "below" rule).
 * Without a Margin Monitoring row, the margin is worked out from the catalog price and cost, with no target.
 */
private fun marginStatus(item: SubCustomerCatalogItemDto, insights: ItemInsightsState): Triple<String, Color, String?>? {
    val row = insights.pricingFor(item.itemId, item.itemCode)
    fun pct(v: Double) = String.format(java.util.Locale.US, "%.1f%%", v)
    if (row != null && row.currentPrice > 0) {
        val margin = row.currentMargin
        val target = row.desiredMargin
        val detail = listOfNotNull("Cost ${money(row.currentCost)}",
            row.suggestedPrice.takeIf { it > 0 && target > 0 && margin < target }?.let { "suggested ${money(it)}" }).joinToString(" \u00b7 ")
        return when {
            margin < 0 -> Triple("Below cost \u00b7 ${pct(margin)} margin", LbBad, detail)
            target > 0 && margin < target -> Triple("Below target \u00b7 ${pct(margin)} vs ${pct(target)}", LbWarn, detail)
            target > 0 -> Triple("On target \u00b7 ${pct(margin)} (target ${pct(target)})", LbGood, detail)
            else -> Triple("${pct(margin)} margin", LbText, detail)
        }
    }
    if (item.retailPrice <= 0 || item.cost <= 0) return null
    val margin = (item.retailPrice - item.cost) / item.retailPrice * 100
    return Triple(if (margin < 0) "Below cost \u00b7 ${pct(margin)} margin" else "${pct(margin)} margin", if (margin < 0) LbBad else LbText,
        "Cost ${money(item.cost)}")
}

/** Margin Monitoring's suggested price when the item is under its target margin. */
private fun suggestedPrice(item: SubCustomerCatalogItemDto, insights: ItemInsightsState): Double? {
    val row = insights.pricingFor(item.itemId, item.itemCode) ?: return null
    return row.suggestedPrice.takeIf { it > 0 && row.desiredMargin > 0 && row.currentMargin < row.desiredMargin && it != row.currentPrice }
}

/** Shelf Review tier (last 90 days) with its suggested action, same rules as the web page. */
private fun shelfStatus(item: SubCustomerCatalogItemDto, insights: ItemInsightsState): Triple<String, Color, String?>? {
    val row = insights.shelfFor(item.itemId, item.itemCode) ?: return null
    val tier = ShelfTier.of(row)
    val days = if (row.daysOfSupplyRemaining >= 9999) "\u221e days of supply" else "${row.daysOfSupplyRemaining.toInt()} days of supply"
    val text = "${tier.label} \u00b7 $days \u00b7 ${row.sellThruPct.toInt()}% sell-through"
    val color = when (tier) { ShelfTier.Dead -> LbBad; ShelfTier.VerySlow, ShelfTier.Slow -> LbWarn; ShelfTier.Ok -> LbGood }
    val detail = listOfNotNull(tier.action.takeIf { it.isNotEmpty() },
        "already marked down".takeIf { row.alreadyMarkedDown }).joinToString(" \u00b7 ").ifEmpty { null }
    return Triple(text, color, detail)
}

/**
 * One destination on the Scan screen. Tapping the tile adds one; when [onQty] is given, a
 * [-] qty [+] row sets that list's quantity for this item directly (tap the number to type it).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    qty: Int = 0,
    onQty: ((Int) -> Unit)? = null,
    onEditQty: (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier
            .height(116.dp)
            .background(Color.White, CardShape)
            .border(1.dp, if (qty > 0) accent.copy(alpha = 0.45f) else LbBorder, CardShape)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick?.let { { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it() } }
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(accent.copy(alpha = 0.10f), CardShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text(title, color = LbText, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = LbMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onQty != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TileStep(Icons.Filled.Remove, "Decrease $title", enabled = enabled && qty > 0) { onQty(qty - 1) }
                Text("$qty", color = if (qty > 0) accent else LbFaint, fontSize = 20.sp, fontWeight = FontWeight.Medium,
                    style = TabularNumbers, textAlign = TextAlign.Center, maxLines = 1,
                    modifier = Modifier.weight(1f).clickable(enabled = enabled && onEditQty != null) { onEditQty?.invoke() }.padding(vertical = 6.dp))
                TileStep(Icons.Filled.Add, "Increase $title", enabled = enabled) { onQty(qty + 1) }
            }
        } else {
            Text("Open", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

@Composable
private fun TileStep(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).border(1.dp, LbBorder, CardShape).background(if (enabled) LbPage else Color.White, CardShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = label, tint = if (enabled) LbText else LbFaint, modifier = Modifier.size(20.dp)) }
}

/** Both carts in one card: what's in each, and a button to open it. */
@Composable
private fun UnifiedCarts(
    carts: CartsSnapshot, lbName: String, showWholesale: Boolean,
    onOpenWholesale: () -> Unit, onOpenLbCart: () -> Unit, onChangeAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().background(Color.White, CardShape).border(1.dp, LbBorder, CardShape)) {
        if (showWholesale) {
            val n = carts.wholesaleCount
            CartRow(Icons.Filled.Storefront, WholesaleAccent, "Wholesale",
                "${if (n == 0) "Empty" else "$n ${if (n == 1) "item" else "items"}"} \u00b7 for ${carts.currentAccount.label}",
                n, onOpen = onOpenWholesale, secondary = "Change customer" to onChangeAccount)
            HorizontalDivider(color = LbDivider)
        }
        val c = carts.lbCount
        CartRow(Icons.Filled.ShoppingCart, LbAccent, lbName,
            if (c == 0) "Empty" else "$c ${if (c == 1) "case" else "cases"}", c, onOpen = onOpenLbCart)
    }
}

@Composable
private fun CartRow(icon: ImageVector, accent: Color, title: String, subtitle: String, count: Int,
    onOpen: () -> Unit, secondary: Pair<String, () -> Unit>? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        BadgedBox(badge = {
            if (count > 0) Badge(containerColor = accent, contentColor = Color.White) { Text(if (count > 99) "99+" else "$count") }
        }) {
            Box(Modifier.size(40.dp).background(accent.copy(alpha = 0.10f), CardShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = LbText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = LbMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            secondary?.let { (label, action) ->
                Text(label, color = LbActive, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp).clickable(onClick = action))
            }
        }
        Button(onClick = onOpen, shape = FieldShape, contentPadding = PaddingValues(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("Open") }
    }
}

/** A cart in Scan's toolbar: its icon with the item count as a badge. */
@Composable
private fun ToolbarCart(title: String, count: Int, icon: ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = {
            if (count > 0) Badge(containerColor = Color.White, contentColor = LiquorBeeNavy) {
                Text(if (count > 99) "99+" else "$count", fontWeight = FontWeight.Medium)
            }
        }) {
            Icon(icon, contentDescription = if (count > 0) "$title, $count items" else title)
        }
    }
}

@Composable
private fun AccountPickerDialog(uiState: ScanUiState, current: WholesaleAccount, onPick: (WholesaleAccount) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Build wholesale orders for…", color = LbText, fontSize = 17.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                AccountRow(WholesaleAccount.General.label, "Retail pricing", selected = current.isGeneral) { onPick(WholesaleAccount.General) }
                when {
                    uiState.accountsLoading -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LbActive, modifier = Modifier.size(24.dp))
                    }
                    uiState.accountsError != null -> Text(uiState.accountsError, color = LbBad, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                    uiState.accounts.isEmpty() -> Text("No linked wholesale accounts.", color = LbMuted, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                    else -> uiState.accounts.forEach { account ->
                        HorizontalDivider(color = LbDivider)
                        AccountRow(account.label, account.email ?: "Linked account", selected = account.key == current.key) { onPick(account) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = LbText) } }
    )
}

@Composable
private fun AccountRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = LbText, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = LbFaint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Current", tint = LbActive, modifier = Modifier.size(20.dp))
    }
}
