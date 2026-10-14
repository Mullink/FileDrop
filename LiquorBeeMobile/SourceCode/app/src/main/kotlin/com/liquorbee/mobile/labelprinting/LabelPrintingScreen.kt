package com.liquorbee.mobile.labelprinting

import androidx.compose.animation.*
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquorbee.mobile.core.network.LabelPrintingItemDto
import com.liquorbee.mobile.core.ui.*
import java.text.NumberFormat
import java.util.Locale

@Composable
fun LabelPrintingScreen(viewModel: LabelPrintingViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.queuedSuccessfully) {
        if (state.queuedSuccessfully) { delay(4_000); viewModel.dismissMessage() }
    }
    val visible = remember(state.items, state.appliedSearchFilter, state.qtyOnHandOnly) { state.filteredItems }
    val activeCount = (if (state.selectedFilter != 0) 1 else 0) + (if (state.sizeFilter != null) 1 else 0) +
        (if (state.qtyOnHandOnly) 1 else 0) + (if (state.activeCategoryIds.isNotEmpty()) 1 else 0)
    Scaffold(containerColor = LbPage,
        topBar = {
            TopAppBar(title = { Text("Label Printing") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            }, actions = {
                TextButton(onClick = viewModel::openQueue, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                    Icon(Icons.Default.Print, "Print queue", Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("${state.queueCount}", fontSize = 14.sp)
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = LiquorBeeNavy,
                titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White))
        },
        bottomBar = {
            Surface(color = LbPage) {
                Column(Modifier.navigationBarsPadding().imePadding()) {
                    HorizontalDivider(color = LbBorder)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = visible.isNotEmpty() && visible.all { it.itemCode in state.selectedItemCodes },
                            enabled = !state.loading && !state.printing && visible.isNotEmpty(),
                            onCheckedChange = { if (it) viewModel.selectAllVisible() else viewModel.clearSelection() })
                        Text("Select all shown", fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("${state.selectedItemCodes.size} selected", fontSize = 12.sp, color = LbMuted)
                    }
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(state.syncCatalog, viewModel::onSyncToggled, enabled = !state.printing)
                        Text("Sync catalog before printing", fontSize = 12.sp, color = LbMuted)
                    }
                    Button(onClick = viewModel::printSelected,
                        enabled = !state.loading && !state.printing && state.loadError == null && state.selectedItemCodes.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = LiquorBeeNavy)) {
                        if (state.printing) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        else Icon(Icons.Default.Print, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text(if (state.printing) "Sending…" else "Print Labels")
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxSize()) {
            state.message?.takeUnless { state.queuedSuccessfully }?.let { message ->
                Row(Modifier.fillMaxWidth().background(Color(0xFFF3F4F6)).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), fontSize = 13.sp, color = LbText)
                    IconButton(onClick = viewModel::dismissMessage) { Icon(Icons.Default.Close, "Dismiss") }
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = { filtersOpen = true; viewModel.ensureCategories() }, enabled = !state.printing,
                        modifier = Modifier.widthIn(min = 160.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, disabledContainerColor = Color.White),
                        shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, if (activeCount > 0) LbActive else LbBorder)) {
                        Icon(Icons.Default.FilterList, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(if (activeCount > 0) "Filters ($activeCount)" else "Filters")
                    }
                    if (!state.loading) Text("${visible.size} items", color = LbMuted, fontSize = 12.sp)
                }
                if (activeCount > 0) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.selectedFilter != 0) item { FilterChipLabel(filterLabel(state.selectedFilter), !state.printing) {
                        viewModel.applyFilters(0, state.sizeFilter, state.qtyOnHandOnly, state.activeCategoryIds)
                    } }
                    state.sizeFilter?.let { size -> item { FilterChipLabel(filterLabel(size), !state.printing) {
                        viewModel.applyFilters(state.selectedFilter, null, state.qtyOnHandOnly, state.activeCategoryIds)
                    } } }
                    if (state.qtyOnHandOnly) item { FilterChipLabel("In stock", !state.printing) {
                        viewModel.applyFilters(state.selectedFilter, state.sizeFilter, false, state.activeCategoryIds)
                    } }
                    if (state.activeCategoryIds.isNotEmpty()) item { FilterChipLabel("${state.activeCategoryIds.size} categories", !state.printing) {
                        viewModel.applyFilters(state.selectedFilter, state.sizeFilter, state.qtyOnHandOnly, emptySet())
                    } }
                }
                OutlinedTextField(state.searchText, viewModel::onSearchChanged, singleLine = true,
                    placeholder = { Text("Search item, SKU, or UPC", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (state.searchText.isNotEmpty()) IconButton(onClick = { viewModel.onSearchChanged("") }) { Icon(Icons.Default.Close, "Clear search") } },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LbActive, unfocusedBorderColor = LbBorder,
                        focusedContainerColor = Color.White, unfocusedContainerColor = Color.White))
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = LbActive, strokeWidth = 2.dp)
                        Text("Loading items…", color = LbMuted, fontSize = 14.sp)
                    }
                }
                state.loadError != null -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.loadError!!, color = LbBad); TextButton(onClick = viewModel::loadItems) { Text("Retry") }
                }
                visible.isEmpty() -> Text("No items match these filters.", Modifier.padding(24.dp), color = LbMuted)
                else -> LazyColumn(Modifier.weight(1f).fillMaxWidth().background(LbPage),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visible) { item ->
                        Surface(modifier = Modifier.fillMaxWidth(), color = Color.White,
                            shape = androidx.compose.ui.graphics.RectangleShape,
                            border = BorderStroke(1.dp, LbBorder)) {
                            LabelItemRow(item, item.itemCode in state.selectedItemCodes,
                                { if (!state.printing) viewModel.toggleItemSelection(item.itemCode) }, currency)
                        }
                    }
                }
            }
        }
        AnimatedVisibility(visible = state.queuedSuccessfully,
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
            enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            Surface(color = Color(0xFFECFDF3), shape = RoundedCornerShape(8.dp), shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Check, null, tint = LbGood, modifier = Modifier.size(20.dp))
                    Text("Successfully queued. Your labels will print soon.", color = LbGood, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
    }
    if (state.queueOpen) AlertDialog(onDismissRequest = viewModel::closeQueue,
        containerColor = Color.White, shape = RoundedCornerShape(8.dp),
        title = { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Print Queue", Modifier.weight(1f), color = LiquorBeeNavy)
            IconButton(onClick = viewModel::closeQueue) { Icon(Icons.Default.Close, "Close print queue") }
        } },
        text = {
            Column {
            state.queueRemoveError?.let { Text(it, color = LbBad, modifier = Modifier.padding(bottom = 8.dp)) }
            when {
                state.queueLoading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = LbActive, strokeWidth = 2.dp)
                }
                state.queueError != null -> Text(state.queueError!!, color = LbBad)
                state.queueItems.isEmpty() -> Text("No labels waiting to print.", color = LbMuted)
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.queueItems) { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.itemName.orEmpty(), color = LbText, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.removeQueuedItem(item.id) }, enabled = state.removingQueueId == null) {
                                    if (state.removingQueueId == item.id) CircularProgressIndicator(Modifier.size(20.dp), color = LbActive, strokeWidth = 2.dp)
                                    else Icon(Icons.Default.DeleteOutline, "Remove ${item.itemName.orEmpty()} from print queue", tint = LbBad)
                                }
                            }
                            Text(
                                listOfNotNull(item.itemCode?.takeIf { it.isNotBlank() }, item.price?.let { currency.format(it) }).joinToString(" • "),
                                color = LbMuted, fontSize = 12.sp
                            )
                            HorizontalDivider(color = LbBorder)
                        }
                    }
                }
            }
            }
        },
        confirmButton = { Button(onClick = viewModel::closeQueue, colors = ButtonDefaults.buttonColors(containerColor = LiquorBeeNavy)) { Text("Done") } },
        dismissButton = { TextButton(onClick = viewModel::openQueue, enabled = !state.queueLoading && state.removingQueueId == null) { Text("Refresh") } })
    if (filtersOpen) LabelFiltersDialog(state, onRetryCategories = viewModel::ensureCategories,
        onDismiss = { filtersOpen = false }, onApply = { list, size, stock, categories ->
            viewModel.applyFilters(list, size, stock, categories); filtersOpen = false
        })
}

private fun filterLabel(code: Int): String = LABEL_FILTER_OPTIONS.first { it.value == code }.label
    .removePrefix("Show ").replace(" ABC Items", "").replace("Show All Items", "All items")

@Composable
private fun FilterChipLabel(label: String, enabled: Boolean, onRemove: () -> Unit) {
    InputChip(selected = true, onClick = onRemove, enabled = enabled, label = { Text(label, fontSize = 11.sp) },
        trailingIcon = { Icon(Icons.Default.Close, "Remove $label", Modifier.size(14.dp)) },
        colors = InputChipDefaults.inputChipColors(selectedContainerColor = Color(0xFFEFF6FF), selectedLabelColor = LbActive))
}

@Composable
private fun labelFieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = LbActive,
    unfocusedBorderColor = LbBorder, focusedContainerColor = LbPage, unfocusedContainerColor = LbPage)

@Composable
private fun LabelFiltersDialog(state: LabelPrintingUiState, onRetryCategories: () -> Unit, onDismiss: () -> Unit,
    onApply: (Int, Int?, Boolean, Set<String>) -> Unit) {
    var list by rememberSaveable { mutableStateOf(state.selectedFilter) }
    var size by rememberSaveable { mutableStateOf(state.sizeFilter) }
    var stock by rememberSaveable { mutableStateOf(state.qtyOnHandOnly) }
    var categories by remember { mutableStateOf(state.activeCategoryIds) }
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(8.dp),
        title = { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Filters", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close filters") }
        } },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Stock", color = LbMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!stock, { stock = false }, label = { Text("All items") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LiquorBeeNavy, selectedLabelColor = Color.White))
                    FilterChip(stock, { stock = true }, label = { Text("In stock") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LiquorBeeNavy, selectedLabelColor = Color.White))
                }
                FilterChoice("List", list, LABEL_FILTER_OPTIONS.filter { it.value > -4000 }.sortedBy { if (it.value == 0) 0 else 1 }) { list = it }
                FilterChoice("ABC size", size ?: 0, listOf(LabelFilterOption(0, "Any size")) + LABEL_FILTER_OPTIONS.filter { it.value <= -4000 }) { size = it.takeIf { code -> code != 0 } }
                Text("Category (${categories.size} selected)", color = LbMuted)
                OutlinedTextField(query, { query = it }, placeholder = { Text("Search categories") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), colors = labelFieldColors())
                if (state.categoriesLoading) CircularProgressIndicator(Modifier.size(24.dp), color = LbActive, strokeWidth = 2.dp)
                else if (state.categoryError != null) {
                    Text(state.categoryError, color = LbBad)
                    TextButton(onClick = onRetryCategories) { Text("Retry categories") }
                } else {
                    val matches = state.categories.filter { it.name.orEmpty().contains(query, ignoreCase = true) }
                    Row {
                        TextButton(onClick = { categories = categories + matches.map { it.categoryId } }) { Text("Select All") }
                        TextButton(onClick = { categories = emptySet() }) { Text("Clear All") }
                    }
                    if (matches.isEmpty()) Text("No matching categories", color = LbMuted)
                    matches.forEach { category ->
                        Row(Modifier.fillMaxWidth().clickable { categories = if (category.categoryId in categories) categories - category.categoryId else categories + category.categoryId }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(category.categoryId in categories, onCheckedChange = null)
                            Text(category.name.orEmpty(), fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onApply(list, size, stock, categories) }, shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LiquorBeeNavy)) { Text("Apply filters") } },
        dismissButton = { TextButton(onClick = { list = 0; size = null; stock = false; categories = emptySet(); query = "" }) { Text("Clear all") } }
    )
}

@Composable
private fun FilterChoice(label: String, selected: Int, options: List<LabelFilterOption>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Text(label, color = LbMuted)
    ExposedDropdownMenuBox(expanded, { expanded = it }) {
        OutlinedTextField(options.firstOrNull { it.value == selected }?.label.orEmpty().removePrefix("Show "), {},
            readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(6.dp), colors = labelFieldColors())
        ExposedDropdownMenu(expanded, { expanded = false }, modifier = Modifier.background(Color.White)) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option.label.removePrefix("Show "), fontSize = 13.sp) },
                onClick = { onSelect(option.value); expanded = false }) }
        }
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
