package com.liquorbee.mobile.picklists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquorbee.mobile.carts.CardShape
import com.liquorbee.mobile.carts.CenteredMessage
import com.liquorbee.mobile.carts.FieldShape
import com.liquorbee.mobile.core.network.PickListSource
import com.liquorbee.mobile.core.network.PickListSummaryDto
import com.liquorbee.mobile.core.ui.LbActive
import com.liquorbee.mobile.core.ui.LbBad
import com.liquorbee.mobile.core.ui.LbBorder
import com.liquorbee.mobile.core.ui.LbFaint
import com.liquorbee.mobile.core.ui.LbGood
import com.liquorbee.mobile.core.ui.LbMuted
import com.liquorbee.mobile.core.ui.LbPage
import com.liquorbee.mobile.core.ui.LbText
import com.liquorbee.mobile.core.ui.LiquorBeeNavy

internal fun sourceColor(source: String): Color = when (source) {
    PickListSource.RESTOCK -> Color(0xFFEA7A12)
    PickListSource.WHOLESALE -> Color(0xFF0B84D8)
    PickListSource.ENTERPRISE -> Color(0xFF7C3AED)
    else -> Color(0xFF6B7280)
}

internal fun sourceLabel(source: String): String = source

@Composable
internal fun SourceBadge(source: String) {
    val color = sourceColor(source)
    Text(sourceLabel(source), color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(color.copy(alpha = 0.12f), FieldShape).padding(horizontal = 8.dp, vertical = 3.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickListsScreen(viewModel: PickListsViewModel, onBack: () -> Unit, onOpen: (Int) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    // Reload when coming back from a list so progress is current.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        containerColor = LbPage,
        topBar = {
            TopAppBar(
                title = { Text("Pick Lists", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LiquorBeeNavy, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White, actionIconContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                OutlinedTextField(
                    value = state.search, onValueChange = viewModel::setSearch,
                    placeholder = { Text("Search name or order #", color = LbFaint) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = LbMuted, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (state.search.isNotEmpty()) IconButton(onClick = { viewModel.setSearch("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = LbFaint, modifier = Modifier.size(20.dp))
                        }
                    },
                    singleLine = true, shape = FieldShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.refresh() }),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LbFaint, unfocusedBorderColor = LbBorder,
                        focusedContainerColor = Color.White, unfocusedContainerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PickListStatus.entries.forEach { s -> Chip(s.label, state.status == s) { viewModel.setStatus(s) } }
                    }
                    RangePicker(state.range, viewModel::setRange)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("All types", state.source == null) { viewModel.setSource(null) }
                    listOf(PickListSource.RESTOCK, PickListSource.WHOLESALE, PickListSource.ENTERPRISE, PickListSource.MANUAL).forEach { src ->
                        Chip(sourceLabel(src), state.source == src) { viewModel.setSource(if (state.source == src) null else src) }
                    }
                }
            }
            when {
                state.loading && state.lists.isEmpty() -> CenteredMessage { CircularProgressIndicator(color = LbActive) }
                state.error != null && state.lists.isEmpty() -> CenteredMessage {
                    Text(state.error.orEmpty(), color = LbBad, textAlign = TextAlign.Center)
                    Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 16.dp), shape = FieldShape,
                        colors = ButtonDefaults.buttonColors(containerColor = LiquorBeeNavy)) { Text("Try Again") }
                }
                state.lists.isEmpty() -> CenteredMessage {
                    Icon(Icons.Filled.Checklist, null, tint = LbFaint, modifier = Modifier.size(52.dp))
                    Text(if (state.status == PickListStatus.Open) "No open pick lists" else "No pick lists found",
                        color = LbText, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                    Text("Restock items, wholesale orders and Enterprise lists show up here.",
                        color = LbMuted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()) {
                    if (state.loading) item { LinearProgressIndicator(color = LbActive, modifier = Modifier.fillMaxWidth()) }
                    items(state.lists, key = { it.id }) { list -> PickListRow(list) { onOpen(list.id) } }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 13.sp) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LiquorBeeNavy, selectedLabelColor = Color.White,
            containerColor = Color.White))
}

@Composable
private fun RangePicker(range: PickListRange, onPick: (PickListRange) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(range.label + " ▾", color = LbActive, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable { open = true }.padding(horizontal = 6.dp, vertical = 10.dp))
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PickListRange.entries.forEach { r ->
                DropdownMenuItem(text = { Text(r.label) }, onClick = { open = false; onPick(r) })
            }
        }
    }
}

@Composable
private fun PickListRow(list: PickListSummaryDto, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White, CardShape).border(1.dp, LbBorder, CardShape)
        .clickable(onClick = onClick).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (list.source == PickListSource.RESTOCK) "Restock list" else list.name, color = LbText, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            SourceBadge(list.source)
        }
        val meta = listOfNotNull(
            PickListFormat.dateTime(list.createdDate).ifBlank { null },
            list.sourceRef?.takeIf { it.isNotBlank() }?.let { "#$it" },
            list.createdBy?.takeIf { it.isNotBlank() }?.let { "by $it" })
        if (meta.isNotEmpty()) Text(meta.joinToString("  ·  "), color = LbMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        list.notes?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = LbFaint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        val progress = if (list.itemCount == 0) 0f else list.checkedCount.toFloat() / list.itemCount
        LinearProgressIndicator(progress = { progress }, color = if (list.isCompleted || progress >= 1f) LbGood else LbActive,
            trackColor = Color(0xFFE5E7EB), modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp))
        Row(Modifier.padding(top = 6.dp)) {
            Text("${list.checkedCount} of ${list.itemCount} ${if (list.itemCount == 1) "item" else "items"} picked",
                color = LbText, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (list.missingCount > 0) Text("${list.missingCount} missing", color = LbBad, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        if (list.isCompleted) {
            Text("Completed ${PickListFormat.dateTime(list.completedDate)}${list.completedBy?.let { " by $it" } ?: ""}",
                color = LbGood, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
