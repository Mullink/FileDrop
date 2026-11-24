package com.liquorbee.mobile.itemresolve

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liquorbee.mobile.LiquorBeeApplication
import com.liquorbee.mobile.core.di.AppViewModelFactory
import com.liquorbee.mobile.core.ui.LbActive
import com.liquorbee.mobile.core.ui.LbBad
import com.liquorbee.mobile.core.ui.LbBorder
import com.liquorbee.mobile.core.ui.LbDivider
import com.liquorbee.mobile.core.ui.LbFaint
import com.liquorbee.mobile.core.ui.LbGood
import com.liquorbee.mobile.core.ui.LbMuted
import com.liquorbee.mobile.core.ui.LbPage
import com.liquorbee.mobile.core.ui.LbText
import com.liquorbee.mobile.core.ui.LiquorBeeNavy
import java.text.NumberFormat
import java.util.Locale

private val Shape8 = RoundedCornerShape(8.dp)
private val Shape6 = RoundedCornerShape(6.dp)
private val currency = NumberFormat.getCurrencyInstance(Locale.US)

/**
 * Full-screen "item not found" flow for any scanning screen. Shown while [barcode] is non-null;
 * calls [onResolved] with the barcode once it has been linked to an item or a new item created,
 * so the caller can simply re-run its own lookup.
 */
@Composable
fun UnknownBarcodeFlow(barcode: String?, onDismiss: () -> Unit, onResolved: (barcode: String, note: String?) -> Unit,
    /** A different barcode scanned while this screen is up: close it and search that one instead. */
    onScanAnother: ((String) -> Unit)? = null) {
    if (barcode == null) return
    val app = LocalContext.current.applicationContext as LiquorBeeApplication
    val viewModel: UnknownBarcodeViewModel = viewModel(factory = AppViewModelFactory(app))
    val live by viewModel.state.collectAsState()
    // start() runs after the first frame, so until it has picked up this barcode the view model still
    // holds the previous (or empty) state. Draw a spinner for those frames instead of flashing a page.
    val state = if (live.barcode != barcode) UnknownBarcodeState(barcode = barcode, loading = true) else live

    LaunchedEffect(barcode) { viewModel.start(barcode) }
    LaunchedEffect(state.resolvedBarcode) {
        state.resolvedBarcode?.let { val note = state.linkNote; viewModel.reset(); onResolved(it, note) }
    }
    val close = { viewModel.reset(); onDismiss() }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            containerColor = LbPage,
            topBar = {
                TopAppBar(
                    title = { Text(when { state.creating -> "Create Item"; state.loading -> "Looking up item…"; else -> "Item Not Found" }, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        if (state.creating) IconButton(onClick = viewModel::closeCreate, enabled = !state.working) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        else IconButton(onClick = close) { Icon(Icons.Filled.Close, "Close") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = LiquorBeeNavy, titleContentColor = Color.White,
                        navigationIconContentColor = Color.White)
                )
            },
            bottomBar = {
                Surface(color = Color.White, shadowElevation = 8.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
                        state.error?.let { Text(it, color = LbBad, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                        Button(
                            onClick = if (state.creating) viewModel::create else viewModel::openCreate,
                            enabled = !state.working && !state.loading,
                            shape = Shape6, modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LiquorBeeNavy)
                        ) {
                            if (state.working) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            else {
                                if (!state.creating) Icon(Icons.Filled.AddCircleOutline, null, modifier = Modifier.size(18.dp).padding(end = 0.dp))
                                Text(if (state.creating) "  Create Item" else "  Create New Item", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (state.creating) CreateForm(state, viewModel) else Options(state, viewModel)
            }
            // The dialog is its own window, so the screen behind can't hear the scanner. Listen here
            // (not on the Create form, whose fields are being typed in) and move on to the new scan.
            if (onScanAnother != null && !state.creating && state.linkOptions == null) {
                com.liquorbee.mobile.core.ui.ScanCapture(key = "unknown-$barcode") { code ->
                    if (code != barcode) { viewModel.reset(); onScanAnother(code) }
                }
            }
        }
    }

    state.linkOptions?.let { LinkOptionsDialog(it, state, viewModel) }
}

/**
 * Link choices: "Link only" just attaches the barcode; "Link & update" also applies the checked
 * looked-up values (name via the Quantic item, size / units per case via the item metadata).
 */
@Composable
private fun LinkOptionsDialog(o: LinkOptions, state: UnknownBarcodeState, viewModel: UnknownBarcodeViewModel) {
    val c = o.candidate
    val anyChecked = (o.newName != null && o.applyName) || (o.newSku != null && o.applySku) ||
        (o.newSize != null && o.applySize) || (o.newUnitsPerCase != null && o.applyUnits)
    AlertDialog(
        onDismissRequest = viewModel::dismissLinkOptions,
        containerColor = Color.White,
        title = { Text("Link barcode", color = LbText, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Link ${state.barcode} to ${c.item.itemName ?: c.item.itemCode}.", color = LbMuted, fontSize = 14.sp)
                if (c.hasRealUpc) {
                    Text("This replaces its current UPC ${c.item.upc}.", color = LbBad, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                if (o.hasUpdates) {
                    HorizontalDivider(color = LbDivider)
                    Text("Also update with ${state.identified?.source ?: "looked-up"} values:", color = LbText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    o.newName?.let { name ->
                        UpdateCheck("Name", c.item.itemName ?: "—", null, o.applyName, { viewModel.updateLinkOptions(o.copy(applyName = it)) })
                        if (o.applyName) {
                            OutlinedTextField(value = name, onValueChange = { viewModel.updateLinkOptions(o.copy(newName = it, nameEdited = true)) },
                                label = { Text("New name") }, shape = Shape6, enabled = !state.working,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp), modifier = Modifier.fillMaxWidth())
                        }
                    }
                    o.newSku?.let { sku ->
                        UpdateCheck("SKU", c.item.itemCode ?: "—", sku, o.applySku, { viewModel.updateLinkOptions(o.copy(applySku = it)) })
                    }
                    o.newSize?.let { size ->
                        UpdateCheck("Size", c.effectiveSize?.let { formatSize(it) } ?: "Unknown", formatSize(size), o.applySize,
                            { viewModel.updateLinkOptions(o.copy(applySize = it)) })
                    }
                    o.newUnitsPerCase?.let { units ->
                        UpdateCheck("Units / case", c.meta?.unitsPerCase?.takeIf { it > 0 }?.toString() ?: "Unknown", units.toString(), o.applyUnits,
                            { viewModel.updateLinkOptions(o.copy(applyUnits = it)) })
                    }
                }
                state.error?.let { Text(it, color = LbBad, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            if (o.hasUpdates) {
                Button(onClick = { viewModel.confirmLink(applyUpdates = true) }, enabled = !state.working && anyChecked, shape = Shape6,
                    colors = ButtonDefaults.buttonColors(containerColor = LiquorBeeNavy)) {
                    if (state.working) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    else Text("Link & update")
                }
            } else {
                Button(onClick = { viewModel.confirmLink(applyUpdates = false) }, enabled = !state.working, shape = Shape6,
                    colors = ButtonDefaults.buttonColors(containerColor = if (c.hasRealUpc) LbBad else LiquorBeeNavy)) {
                    if (state.working) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    else Text(if (c.hasRealUpc) "Replace & link" else "Link")
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = viewModel::dismissLinkOptions, enabled = !state.working) { Text("Cancel", color = LbText) }
                if (o.hasUpdates) {
                    TextButton(onClick = { viewModel.confirmLink(applyUpdates = false) }, enabled = !state.working) { Text("Link only", color = LbActive) }
                }
            }
        }
    )
}

@Composable
private fun UpdateCheck(label: String, current: String, new: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Column(Modifier.weight(1f)) {
            Text(label, color = LbText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(if (new != null) "$current  →  $new" else "Currently: $current", color = LbMuted, fontSize = 12.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Options(state: UnknownBarcodeState, viewModel: UnknownBarcodeViewModel) {
    Card {
        Text("Barcode", color = LbMuted, fontSize = 12.sp)
        Text(state.barcode, color = LbText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("isn't linked to any of your items.", color = LbMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }

    if (state.loading) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LbActive) }
        return
    }

    state.alreadyInPos?.let { existing ->
        Card {
            Text("ALREADY IN YOUR ITEMS", color = Color(0xFFB45309), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(existing.itemName ?: existing.itemCode.orEmpty(), color = LbText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            Text("SKU ${existing.itemCode}  ·  UPC ${existing.upc}", color = LbMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            Text("This barcode is already on one of your items, so there's no need to create it again. If you can't find it, it may be turned off in your POS.",
                color = LbMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }

    val match = state.identified
    if (match != null) {
        Card {
            Text("FOUND IN ${match.source.uppercase()}", color = LbGood, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(match.name, color = LbText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            val details = listOfNotNull(match.sku?.let { "Code $it" },
                match.size?.takeIf { ItemMatching.size(match.name) == null }?.let { formatSize(it) },
                match.cost?.let { "Cost ${currency.format(it)}" }, match.unitsPerCase?.let { "$it / case" })
            if (details.isNotEmpty()) Text(details.joinToString("  ·  "), color = LbMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
    } else if (state.alreadyInPos == null) {
        Card {
            Text("Not in the LiquorBee catalog.", color = LbText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (state.universalAvailable) {
                if (state.onlineLookupRunning) {
                    // Started automatically: the person can still link or create while it runs.
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        CircularProgressIndicator(color = LbActive, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Text("  Searching the web for this barcode…", color = LbActive, fontSize = 13.sp)
                    }
                    Text("This can take up to a minute. You can link or create the item meanwhile.",
                        color = LbMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                } else {
                    state.onlineLookupMessage?.let { Text(it, color = LbMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
                    OutlinedButton(onClick = { viewModel.lookupOnline(force = state.onlineLookupTried) }, enabled = !state.working,
                        shape = Shape6, modifier = Modifier.padding(top = 10.dp)) {
                        Icon(Icons.Filled.TravelExplore, null, tint = LbActive, modifier = Modifier.size(18.dp))
                        Text(if (state.onlineLookupTried) "  Try again online" else "  Look up online", color = LbActive)
                    }
                }
            } else {
                // Lookup couldn't be reached at all (server down, or not enabled for this login).
                Text(ONLINE_LOOKUP_UNAVAILABLE, color = LbBad, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Text("Link it to one of your items below, or create a new item.", color = LbMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    if (state.candidates.isNotEmpty()) {
        SectionLabel("Is it one of your items? Link this barcode to it:")
        CandidateList(state.candidates, enabled = !state.working, scannedSize = match?.size, onLink = viewModel::requestLink)
    } else if (match != null) {
        Text("No similar items found in your store.", color = LbMuted, fontSize = 13.sp)
    }

    SectionLabel(if (state.candidates.isEmpty()) "Find one of your items to link:" else "Not listed? Search your items:")
    OutlinedTextField(
        value = state.manualQuery, onValueChange = viewModel::onManualQuery,
        placeholder = { Text("Name or SKU", color = LbFaint) },
        leadingIcon = { Icon(Icons.Filled.Search, null, tint = LbMuted) },
        singleLine = true, shape = Shape6, modifier = Modifier.fillMaxWidth()
    )
    if (state.manualResults.isNotEmpty()) CandidateList(state.manualResults, enabled = !state.working, scannedSize = match?.size, onLink = viewModel::requestLink)
}

/**
 * Store items that might be the scanned product. Tap a row to see its details - size (read from
 * the item name, since the POS has no size field), price, cost, stock and current UPC - and how
 * the size compares with the scanned product, before deciding to link.
 */
@Composable
private fun CandidateList(items: List<LinkCandidate>, enabled: Boolean, scannedSize: String?, onLink: (LinkCandidate) -> Unit) {
    var expanded by remember(items) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().background(Color.White, Shape8).border(1.dp, LbBorder, Shape8)) {
        items.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(color = LbDivider)
            val key = "${c.item.itemCode}|${c.item.upc}|$i"
            val isOpen = expanded == key
            Column(Modifier.fillMaxWidth().clickable { expanded = if (isOpen) null else key }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
                Icon(if (isOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (isOpen) "Hide details" else "Show details",
                    tint = LbFaint, modifier = Modifier.padding(end = 6.dp).size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.item.itemName ?: c.item.itemCode.orEmpty(), color = LbText, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            append(c.item.itemCode.orEmpty())
                            if (c.reason == SAME_CODE) append("  ·  $SAME_CODE")
                            append("  ·  ")
                            withStyle(androidx.compose.ui.text.SpanStyle(color = if (c.hasRealUpc) LbMuted else LbGood, fontWeight = FontWeight.SemiBold)) {
                                append(if (c.hasRealUpc) "Has UPC" else "No UPC")
                            }
                        },
                        color = LbFaint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                    val reserve = com.liquorbee.mobile.carts.rememberQtyOnReserve(c.item.itemCode)
                    Text(listOfNotNull("${c.item.qtyOnHand} on hand", reserve?.let { "$it on reserve" }, currency.format(c.item.retailPrice))
                        .joinToString("  ·  "), color = LbMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp))
                }
                OutlinedButton(onClick = { onLink(c) }, enabled = enabled, shape = Shape6) {
                    Icon(Icons.Filled.Link, null, modifier = Modifier.size(16.dp), tint = LbActive)
                    Text(" Link", color = LbActive)
                }
            }
            if (isOpen) CandidateDetails(c, scannedSize)
            }
        }
    }
}

@Composable
private fun CandidateDetails(c: LinkCandidate, scannedSize: String?) {
    val itemSize = c.effectiveSize
    Column(Modifier.fillMaxWidth().background(LbPage).padding(start = 38.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DetailRow("Size") {
            Text(itemSize?.let { formatSize(it) } ?: "Unknown", color = if (itemSize == null) LbFaint else LbText,
                fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (itemSize != null && scannedSize != null) {
                val same = itemSize == scannedSize
                Text(if (same) "  ✓ matches scan" else "  ≠ scan is ${formatSize(scannedSize)}",
                    color = if (same) LbGood else LbBad, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        // Where the size came from - the item name, or the item metadata when the name has none.
        // The metadata is backfilled and occasionally wrong, so call out a disagreement.
        val sizeNote = when {
            c.nameSize != null && c.metaSize != null && c.nameSize != c.metaSize ->
                "From item name · item details say ${formatSize(c.metaSize!!)}"
            c.nameSize != null -> "From item name"
            c.metaSize != null -> "From item details"
            else -> null
        }
        sizeNote?.let { DetailRow("") { Text(it, color = LbFaint, fontSize = 11.sp) } }
        c.meta?.unitsPerCase?.takeIf { it > 0 }?.let { DetailRow("Units / case") { DetailValue(it.toString()) } }
        DetailRow("Price") { DetailValue(currency.format(c.item.retailPrice)) }
        if (c.item.cost > 0) DetailRow("Cost") { DetailValue(currency.format(c.item.cost)) }
        DetailRow("On hand") { DetailValue(c.item.qtyOnHand.toString()) }
        com.liquorbee.mobile.carts.rememberQtyOnReserve(c.item.itemCode)?.let { DetailRow("On reserve") { DetailValue(it.toString()) } }
        DetailRow("SKU") { DetailValue(c.item.itemCode.orEmpty()) }
        DetailRow("Current UPC") { DetailValue(if (c.hasRealUpc) c.item.upc.orEmpty() else "None") }
    }
}

@Composable
private fun DetailRow(label: String, value: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = LbMuted, fontSize = 12.sp, modifier = Modifier.width(92.dp))
        value()
    }
}

@Composable
private fun DetailValue(text: String) = Text(text, color = LbText, fontSize = 13.sp, fontWeight = FontWeight.Medium,
    maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun CreateForm(state: UnknownBarcodeState, viewModel: UnknownBarcodeViewModel) {
    val f = state.form
    // Long catalog names wrap (up to 3 lines) instead of scrolling out of sight.
    OutlinedTextField(value = f.name, onValueChange = viewModel::onNameTyped, label = { Text("Item name *") },
        singleLine = false, minLines = 1, maxLines = 3, shape = Shape6, modifier = Modifier.fillMaxWidth())
    NameFormatPicker(f.nameStyle, f.nameEdited) { viewModel.setNameStyle(it) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(value = f.sku, onValueChange = { viewModel.updateForm(f.copy(sku = it, skuGenerated = false)) },
            label = { Text("SKU *") }, singleLine = true, shape = Shape6, modifier = Modifier.weight(1.2f),
            trailingIcon = {
                IconButton(onClick = viewModel::generateSku, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Generate a UC- SKU", tint = LbMuted, modifier = Modifier.size(18.dp))
                }
            })
        Field("UPC / Barcode", f.upc, Modifier.weight(1.2f), KeyboardType.Number) { viewModel.updateForm(f.copy(upc = it)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Field("Selling price *", f.price, Modifier.weight(1f), KeyboardType.Decimal) { viewModel.updateForm(f.copy(price = it.filter { c -> c.isDigit() || c == '.' }, priceHint = null)) }
        Field("Unit cost *", f.cost, Modifier.weight(1f), KeyboardType.Decimal) { viewModel.updateForm(f.copy(cost = it.filter { c -> c.isDigit() || c == '.' })) }
    }
    f.priceHint?.let { Text(it, color = LbMuted, fontSize = 12.sp) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Field("Units per case", f.unitsPerCase, Modifier.weight(1f), KeyboardType.Number) { viewModel.updateForm(f.copy(unitsPerCase = it.filter(Char::isDigit))) }
        Field("Qty on hand", f.qtyInHand, Modifier.weight(1f), KeyboardType.Number) { viewModel.updateForm(f.copy(qtyInHand = it.filter(Char::isDigit))) }
    }
}

/** "Name format: Name (SIZE) - SKU ▾" - picking one rebuilds the name; shows "custom" once it's been typed over. */
@Composable
private fun NameFormatPicker(style: NameStyle, edited: Boolean, onPick: (NameStyle) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Name format:", color = LbMuted, fontSize = 12.sp)
        Box {
            TextButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                Text(if (edited) "${style.label} (edited)" else style.label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.Filled.ExpandMore, contentDescription = "Choose name format", modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                NameStyle.entries.forEach { s ->
                    DropdownMenuItem(text = { Text(s.label) }, onClick = { open = false; onPick(s) })
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, shape = Shape6,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), modifier = modifier)
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White, Shape8).border(1.dp, LbBorder, Shape8).padding(14.dp)) { content() }
}

@Composable
private fun SectionLabel(text: String) = Text(text, color = LbText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

private const val SAME_CODE = "Same item code"

private fun formatSize(ml: String): String = formatSizeMl(ml)
