package com.liquorbee.mobile.pricecheck

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquorbee.mobile.core.network.PriceCheckEditDto
import com.liquorbee.mobile.core.ui.*
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.delay

private val DraftSaver = listSaver<PriceCheckDraft, Any>(
    save = { listOf(it.name, it.upc, it.sku, it.description, it.price, it.cost, it.quantity, it.vendorId,
        it.categoryId, it.active, it.tracking, it.markup, it.margin, it.percentageError) },
    restore = { PriceCheckDraft(it[0] as String, it[1] as String, it[2] as String, it[3] as String,
        it[4] as String, it[5] as String, it[6] as String, it[7] as String, it[8] as String,
        it[9] as Boolean, it[10] as Boolean, it[11] as String, it[12] as String, it[13] as String) }
)

@Composable
internal fun PriceCheckEditor(state: PriceCheckUiState, onNext: () -> Unit, onBack: () -> Unit,
    onReload: () -> Unit, onSave: (PriceCheckEditDto) -> Unit) {
    val item = state.editorItem
    if (item == null) {
        BackHandler(onBack = onBack)
        Scaffold(containerColor = LbPage, topBar = { EditorTopBar(onBack, onNext, false) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).padding(16.dp), contentAlignment = Alignment.Center) {
                if (state.editorLoading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = LbActive, strokeWidth = 2.dp)
                        Text("Loading item details…", color = LbMuted, fontSize = 14.sp)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(state.editorError ?: "Could not load this item.", color = LbBad)
                    OutlinedButton(onClick = onReload) { Text("Retry") }
                    }
                }
            }
        }
        return
    }
    var draft by rememberSaveable(item.itemId, item.revision, stateSaver = DraftSaver) { mutableStateOf(PriceCheckDraft.from(item)) }
    val dirty = draft.isDirty(item)
    var showSavedBanner by remember { mutableStateOf(false) }
    LaunchedEffect(state.saveMessage, item.revision) {
        showSavedBanner = state.saveMessage != null
        if (showSavedBanner) {
            delay(4000)
            showSavedBanner = false
        }
    }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var validation by remember(item.revision) { mutableStateOf<String?>(null) }
    var showQuantityAdjustment by rememberSaveable(item.itemId, item.revision) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val focus = LocalFocusManager.current
    val money = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    val leave: (() -> Unit) -> Unit = { action -> if (!state.saving) { if (dirty) pending = action else action() } }
    BackHandler { leave(onBack) }
    LaunchedEffect(state.editorError, validation) {
        if (state.editorError != null || validation != null) scroll.animateScrollTo(0)
    }
    Scaffold(
        containerColor = LbPage,
        modifier = Modifier.imePadding(),
        topBar = { EditorTopBar({ leave(onBack) }, { leave(onNext) }, state.saving) },
        bottomBar = {
            Surface(color = LbPage, shadowElevation = 0.dp) {
                Column(Modifier.navigationBarsPadding()) {
                    HorizontalDivider(color = LbBorder)
                    if (dirty && !state.saving) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Unsaved changes",
                            color = LbMuted, fontSize = 12.sp)
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp,
                        top = if (state.saving || !dirty) 12.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { draft = PriceCheckDraft.from(item); validation = null; focus.clearFocus() },
                            enabled = dirty && !state.saving, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, LbBorder)) { Text("Reset") }
                        Button(onClick = {
                            validation = draft.error(item)
                            if (validation == null) { focus.clearFocus(); onSave(draft.request(item)) }
                        }, enabled = dirty && !state.saving, modifier = Modifier.weight(1.5f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = LiquorBeeNavy)) {
                            if (state.saving) CircularProgressIndicator(Modifier.size(16.dp).padding(end = 4.dp), strokeWidth = 2.dp, color = Color.White)
                            Text("Save")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            (validation ?: state.editorError)?.let { message ->
                SectionCard("Unable to save") {
                    Text(message, color = LbBad, fontSize = 13.sp)
                    if (state.editorError != null) TextButton(onClick = { leave(onReload) }, enabled = !state.saving) { Text("Reload item") }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.name, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, color = LbText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(if (draft.active) "Active" else "Inactive", draft.active)
                    item.discountEligible?.let { Text(if (it) "Discount eligible" else "Not discount eligible", color = LbMuted, fontSize = 12.sp) }
                }
                Surface(color = Color.White, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, LbBorder)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryValue("Cash price", draft.price.toDoubleOrNull()?.let(money::format) ?: "—", Modifier.weight(1f), LbGood)
                        SummaryValue(if (draft.price.toDoubleOrNull() != item.price) "Card · saved" else "Card price", item.cardPrice?.let(money::format) ?: "—", Modifier.weight(1f))
                    }
                }
            }
            SectionCard("Quantity on Hand") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedIconButton(onClick = { focus.clearFocus(); showQuantityAdjustment = true },
                        enabled = !state.saving && !item.hasMultipleStockRecords,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Add or subtract quantity" },
                        shape = RoundedCornerShape(6.dp)) {
                        Text("±", fontSize = 26.sp, fontWeight = FontWeight.Medium)
                    }
                    EditField("Quantity on hand", draft.quantity, state.saving || item.hasMultipleStockRecords,
                        KeyboardType.Number, Modifier.weight(1f), showLabel = false) { draft = draft.copy(quantity = it) }
                }
                if (item.hasMultipleStockRecords) Text("This item has multiple vendor stock records. Update quantity, cost and vendor in POS.", color = LbMuted, fontSize = 12.sp)
            }
            SectionCard("Category") {
                PopupPicker("Category", draft.categoryId,
                    state.categories.map { it.categoryId to it.name.orEmpty() }, item.categoryName,
                    state.saving) { draft = draft.copy(categoryId = it) }
            }
            SectionCard("Pricing") {
                FieldPair(
                    first = { modifier -> EditField("Cash price ($)", draft.price, state.saving, KeyboardType.Decimal, modifier) { draft = draft.withPricing(price = it) } },
                    second = { modifier -> EditField("Unit cost ($)", draft.cost, state.saving || item.hasMultipleStockRecords, KeyboardType.Decimal, modifier) { draft = draft.withPricing(cost = it) } }
                )
                FieldPair(
                    first = { modifier -> EditField("Markup (%)", draft.markup, state.saving, KeyboardType.Decimal, modifier) { draft = draft.withPercent(it, false) } },
                    second = { modifier -> EditField("Margin (%)", draft.margin, state.saving, KeyboardType.Decimal, modifier) { draft = draft.withPercent(it, true) } }
                )
                Text(draft.percentageError.ifBlank { "Markup and margin update cash price. Card price follows your store settings." },
                    color = if (draft.percentageError.isBlank()) LbMuted else LbBad, fontSize = 12.sp, lineHeight = 17.sp)
            }
            SectionCard("Item details") {
                EditField("Item name", draft.name, state.saving) { draft = draft.copy(name = it) }
                FieldPair(
                    first = { modifier -> EditField("UPC", draft.upc, state.saving, KeyboardType.Ascii, modifier) { draft = draft.copy(upc = it) } },
                    second = { modifier -> EditField("SKU", draft.sku, state.saving, KeyboardType.Ascii, modifier) { draft = draft.copy(sku = it) } }
                )
            }
            SectionCard("Availability") {
                SettingRow("Active item", "Available for sale", draft.active, state.saving) { draft = draft.copy(active = it) }
                HorizontalDivider(color = LbDivider)
                SettingRow("Track inventory", "Keep a quantity on hand", draft.tracking, state.saving) { draft = draft.copy(tracking = it) }
            }
        }
        AnimatedVisibility(
            visible = showSavedBanner && !dirty && !state.saving,
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut()
        ) {
            Surface(color = Color(0xFFECFDF3), shape = RoundedCornerShape(8.dp), shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Check, null, tint = LbGood, modifier = Modifier.size(20.dp))
                    Text("Changes saved", color = LbGood, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        }
    }
    if (showQuantityAdjustment && !state.saving && !item.hasMultipleStockRecords) {
        QuantityAdjustmentDialog(current = draft.quantity, onDismiss = { showQuantityAdjustment = false },
            onApply = { quantity ->
                draft = draft.copy(quantity = quantity)
                validation = null
                showQuantityAdjustment = false
            })
    }
    pending?.let { action ->
        AlertDialog(containerColor = androidx.compose.ui.graphics.Color.White, onDismissRequest = { pending = null }, title = { Text("Discard unsaved changes?") },
            text = { Text("Your item changes have not been saved.") },
            confirmButton = { TextButton(onClick = { pending = null; action() }) { Text("Discard changes") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Keep editing") } })
    }
}

@Composable
private fun QuantityAdjustmentDialog(current: String, onDismiss: () -> Unit, onApply: (String) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    var subtract by rememberSaveable { mutableStateOf(false) }
    val result = adjustedQuantity(current, amount, subtract)
    val error = when {
        current.toIntOrNull() == null -> "Enter a valid quantity on hand before adjusting it."
        amount.isBlank() -> null
        amount.toIntOrNull()?.let { it > 0 } != true -> "Enter a positive whole number."
        result == null -> "The resulting quantity is outside the supported range."
        else -> null
    }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(8.dp),
        title = { Text("Adjust quantity on hand", color = LbText, fontSize = 18.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Current quantity: $current", color = LbMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = !subtract, onClick = { subtract = false }, label = { Text("Add") },
                        leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) })
                    FilterChip(selected = subtract, onClick = { subtract = true }, label = { Text("Subtract") },
                        leadingIcon = { Icon(Icons.Default.Remove, null, Modifier.size(18.dp)) })
                }
                OutlinedTextField(value = amount, onValueChange = { amount = it },
                    label = { Text(if (subtract) "Quantity to subtract" else "Quantity to add") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, isError = error != null, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = LbBad, fontSize = 12.sp) }
                result?.let {
                    Text("New quantity: $it", color = LbText, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                Text("Apply updates the quantity field. Tap Save to save your changes.", color = LbMuted, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { result?.let(onApply) }, enabled = result != null) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun EditorTopBar(onBack: () -> Unit, onNext: () -> Unit, saving: Boolean) {
    TopAppBar(title = { Text("Price Check", fontSize = 19.sp, fontWeight = FontWeight.SemiBold) },
        navigationIcon = { IconButton(onClick = onBack, enabled = !saving) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = { TextButton(onClick = onNext, enabled = !saving,
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.5f))) {
            Icon(Icons.Default.QrCodeScanner, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Scan Item")
        } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = LiquorBeeNavy, titleContentColor = Color.White,
            navigationIconContentColor = Color.White, actionIconContentColor = Color.White))
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, LbBorder)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = LbText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun FieldPair(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 290.dp && fontScale <= 1.2f) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            first(Modifier.weight(1f)); second(Modifier.weight(1f))
        } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { first(Modifier.fillMaxWidth()); second(Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun EditField(label: String, value: String, disabled: Boolean, keyboard: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier, multiline: Boolean = false, showLabel: Boolean = true, onChange: (String) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showLabel) Text(label, color = LbMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        OutlinedTextField(value = value, onValueChange = onChange, enabled = !disabled,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }, singleLine = !multiline, minLines = if (multiline) 2 else 1,
            shape = RoundedCornerShape(6.dp), textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = LbText),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = LbBorder, focusedBorderColor = LbActive,
                unfocusedContainerColor = LbPage, focusedContainerColor = LbPage, disabledContainerColor = LbPage))
    }
}

@Composable
private fun PopupPicker(label: String, selectedId: String, options: List<Pair<String, String>>, fallback: String,
    disabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, if (expanded) LbActive else LbBorder), color = LbPage) {
            Row(Modifier.clickable(enabled = !disabled) { expanded = !expanded; query = "" }.heightIn(min = 50.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(options.firstOrNull { it.first == selectedId }?.second?.ifBlank { null }
                    ?: fallback.ifBlank { if (selectedId.isNotBlank()) "Current ${label.lowercase()} (not listed)" else "Choose ${label.lowercase()}" }, Modifier.weight(1f), color = LbText, fontSize = 14.sp)
                Icon(Icons.Default.ExpandMore, "Choose ${label.lowercase()}", tint = LbMuted, modifier = Modifier.size(20.dp))
            }
        }
        if (expanded && !disabled) {
            AlertDialog(
                onDismissRequest = { expanded = false },
                containerColor = Color.White,
                shape = RoundedCornerShape(8.dp),
                title = { Text("Choose ${label.lowercase()}", color = LbText, fontSize = 18.sp) },
                confirmButton = { TextButton(onClick = { expanded = false }) { Text("Cancel", color = LbMuted) } },
                text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, placeholder = { Text("Search ${label.lowercase()}", fontSize = 14.sp) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp))
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    val matches = options.filter { it.second.contains(query, ignoreCase = true) }
                    if (matches.isEmpty()) Text("No matching ${label.lowercase()}.", color = LbMuted, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                    matches.forEach { (id, name) ->
                        Row(Modifier.fillMaxWidth().clickable { onSelect(id); expanded = false }.heightIn(min = 48.dp).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name.ifBlank { "Unnamed ${label.lowercase()}" }, Modifier.weight(1f), fontSize = 14.sp, color = if (id == selectedId) LbActive else LbText)
                            if (id == selectedId) Icon(Icons.Default.Check, "Selected", tint = LbActive, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
                }
            )
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, disabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = LbText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = LbMuted, fontSize = 12.sp)
        }
        Switch(checked, onChange, enabled = !disabled, colors = SwitchDefaults.colors(checkedTrackColor = LiquorBeeNavy))
    }
}

@Composable
private fun SummaryValue(label: String, value: String, modifier: Modifier, color: Color = LbText) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 11.sp, color = LbMuted)
        Text(value, fontSize = 22.sp, lineHeight = 24.sp, maxLines = 1,
            fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun StatusChip(label: String, active: Boolean) {
    Text(label, color = if (active) LbGood else LbMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.background(if (active) Color(0xFFECFDF3) else LbDivider, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
}
