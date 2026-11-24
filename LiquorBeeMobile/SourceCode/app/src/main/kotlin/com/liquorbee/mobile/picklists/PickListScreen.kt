package com.liquorbee.mobile.picklists

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Email
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquorbee.mobile.carts.CardShape
import com.liquorbee.mobile.carts.CenteredMessage
import com.liquorbee.mobile.carts.FieldShape
import com.liquorbee.mobile.carts.QuantityDialog
import com.liquorbee.mobile.carts.ScanSearchField
import com.liquorbee.mobile.carts.TabularNumbers
import com.liquorbee.mobile.core.network.PickListDto
import com.liquorbee.mobile.core.network.PickListItemDto
import com.liquorbee.mobile.core.network.PickListSource
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickListScreen(viewModel: PickListViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val list = state.list
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var confirmComplete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PickListItemDto?>(null) }

    // Keep the field focused (keyboard hidden) so the hardware scanner checks items off directly.
    LaunchedEffect(state.focusToken) {
        if (list != null) {
            runCatching { focus.requestFocus() }
            keyboard?.hide()
            kotlinx.coroutines.delay(200)
            keyboard?.hide()
        }
    }

    Scaffold(
        containerColor = LbPage,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(list?.let { if (it.source == PickListSource.RESTOCK) "Restock list" else it.name } ?: "Pick List",
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        list?.let {
                            Text(listOfNotNull(sourceLabel(it.source), PickListFormat.dateTime(it.createdDate).ifBlank { null },
                                it.sourceRef?.takeIf { r -> r.isNotBlank() }?.let { r -> "#$r" }).joinToString("  ·  "),
                                fontSize = 12.sp, color = Color(0xFFCBD5E1), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (list != null) IconButton(onClick = viewModel::openEmail) { Icon(Icons.Filled.Email, contentDescription = "Email pick list") }
                    IconButton(onClick = viewModel::load) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LiquorBeeNavy, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White, actionIconContentColor = Color.White)
            )
        },
        bottomBar = {
            if (list != null) BottomActions(list, state.working, onComplete = { confirmComplete = true }, onReopen = viewModel::reopen)
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CenteredMessage { CircularProgressIndicator(color = LbActive) }
                list == null -> CenteredMessage {
                    Text(state.error ?: "Could not load the pick list.", color = LbBad, textAlign = TextAlign.Center)
                    Button(onClick = viewModel::load, modifier = Modifier.padding(top = 16.dp), shape = FieldShape,
                        colors = ButtonDefaults.buttonColors(containerColor = LiquorBeeNavy)) { Text("Try Again") }
                }
                else -> {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                        Progress(list)
                        if (!list.isCompleted) {
                            ScanSearchField(value = state.query, loading = false, onValueChange = viewModel::onQuery,
                                onSubmit = viewModel::onSubmit, onClear = viewModel::clearQuery,
                                modifier = Modifier.padding(top = 12.dp).focusRequester(focus))
                        }
                        val msg = state.message
                        Text(msg?.text.orEmpty(), color = if (msg?.isError == true) LbBad else LbGood, fontSize = 13.sp,
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PickFilter.entries.forEach { f ->
                                val count = when (f) {
                                    PickFilter.ToPick -> list.items.count { !it.isChecked && !it.isMissing }
                                    PickFilter.Missing -> list.items.count { it.isMissing }
                                    PickFilter.Done -> list.items.count { it.isChecked }
                                    PickFilter.All -> list.items.size
                                }
                                FilterChip(selected = state.filter == f, onClick = { viewModel.setFilter(f) },
                                    label = { Text("${f.label} $count", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LiquorBeeNavy,
                                        selectedLabelColor = Color.White, containerColor = Color.White))
                            }
                        }
                    }
                    val visible = state.visibleItems
                    if (visible.isEmpty()) {
                        CenteredMessage {
                            Text(when {
                                state.query.isNotBlank() -> "No items match \"${state.query}\"."
                                state.filter == PickFilter.ToPick && list.items.isNotEmpty() -> "Everything is picked. ✓"
                                else -> "Nothing here."
                            }, color = LbMuted, textAlign = TextAlign.Center)
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                            modifier = Modifier.fillMaxSize()) {
                            item {
                                Column(Modifier.fillMaxWidth().background(Color.White, CardShape).border(1.dp, LbBorder, CardShape)) {
                                    visible.forEachIndexed { i, item ->
                                        if (i > 0) HorizontalDivider(color = LbDivider)
                                        PickRow(item, readOnly = list.isCompleted,
                                            onToggle = { viewModel.toggle(item) },
                                            onEditQty = { editing = item },
                                            onMissing = { viewModel.markMissing(item, !item.isMissing) },
                                            onAddToCart = { viewModel.openAddToCart(item) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.emailOpen && list != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissEmail,
            containerColor = Color.White,
            title = { Text("Email pick list", color = LbText, fontWeight = FontWeight.Medium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sends the items, quantities and what's been picked from no-reply@liquorbee.com.", color = LbMuted, fontSize = 13.sp)
                    OutlinedTextField(value = state.emailTo, onValueChange = viewModel::onEmailTo, label = { Text("To") },
                        placeholder = { Text("name@example.com") }, singleLine = true, enabled = !state.emailSending,
                        supportingText = { Text("Separate several addresses with commas.") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
                        shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = state.emailNote, onValueChange = viewModel::onEmailNote, label = { Text("Note (optional)") },
                        placeholder = { Text("e.g. Please pull these before Friday's delivery") }, minLines = 3, maxLines = 6,
                        enabled = !state.emailSending, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth())
                    state.emailError?.let { Text(it, color = LbBad, fontSize = 13.sp) }
                }
            },
            confirmButton = {
                Button(onClick = viewModel::sendEmail, enabled = !state.emailSending, shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LiquorBeeNavy)) {
                    if (state.emailSending) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    else Text("Send")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissEmail, enabled = !state.emailSending) { Text("Cancel", color = LbText) } }
        )
    }

    if (confirmComplete && list != null) {
        val left = list.items.count { !it.isChecked && !it.isMissing }
        AlertDialog(
            onDismissRequest = { confirmComplete = false },
            containerColor = Color.White,
            title = { Text("Complete pick list?", color = LbText, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(buildString {
                    append(if (left == 0) "All items are picked or marked missing." else "$left ${if (left == 1) "item hasn't" else "items haven't"} been picked yet.")
                    if (list.source == PickListSource.RESTOCK) append(" The next item you add to Restock starts a new list.")
                }, color = LbMuted)
            },
            confirmButton = {
                TextButton(onClick = { confirmComplete = false; viewModel.complete() }) { Text("Complete", color = LbActive) }
            },
            dismissButton = { TextButton(onClick = { confirmComplete = false }) { Text("Cancel", color = LbText) } }
        )
    }

    editing?.let { item ->
        QuantityDialog(title = "Picked · ${item.itemName ?: item.itemCode} (of ${item.qty})", initial = item.pickedQty,
            onDismiss = { editing = null; viewModel.refocus() },
            onSave = { viewModel.setPickedQty(item, it); editing = null; viewModel.refocus() })
    }

    state.cartFor?.let { item ->
        QuantityDialog(title = "Add ${item.itemName ?: item.itemCode} to the LB cart (cases)", initial = 1,
            onDismiss = viewModel::dismissAddToCart, onSave = viewModel::addToCart)
    }
}

@Composable
private fun Progress(list: PickListDto) {
    val progress = if (list.itemCount == 0) 0f else list.checkedCount.toFloat() / list.itemCount
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${list.checkedCount} of ${list.itemCount} picked", color = LbText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f))
        if (list.missingCount > 0) Text("${list.missingCount} missing", color = LbBad, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
    LinearProgressIndicator(progress = { progress }, color = if (progress >= 1f || list.isCompleted) LbGood else LbActive,
        trackColor = Color(0xFFE5E7EB), modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp))
    if (list.isCompleted) {
        Text("Completed ${PickListFormat.dateTime(list.completedDate)}${list.completedBy?.let { " by $it" } ?: ""}",
            color = LbGood, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun PickRow(
    item: PickListItemDto,
    readOnly: Boolean,
    onToggle: () -> Unit,
    onEditQty: () -> Unit,
    onMissing: () -> Unit,
    onAddToCart: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(enabled = !readOnly, onClick = onToggle)
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
        Box(
            Modifier.size(28.dp)
                .background(if (item.isChecked) LbGood else Color.White, CircleShape)
                .border(2.dp, when { item.isChecked -> LbGood; item.isMissing -> LbBad; else -> LbFaint }, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (item.isChecked) Icon(Icons.Filled.Check, contentDescription = "Picked", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.itemName ?: item.itemCode, color = if (item.isChecked) LbMuted else LbText, fontSize = 15.sp,
                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null)
            Text(listOfNotNull(item.itemCode, item.upc?.takeIf { it.isNotBlank() }).joinToString(" · "),
                color = LbFaint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            if (item.isMissing) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Missing", color = LbBad, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.background(LbBad.copy(alpha = 0.1f), FieldShape).padding(horizontal = 6.dp, vertical = 2.dp))
                    if (!readOnly) {
                        OutlinedButton(onClick = onAddToCart, shape = FieldShape, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.padding(start = 8.dp).height(30.dp)) {
                            Icon(Icons.Filled.AddShoppingCart, null, tint = LbActive, modifier = Modifier.size(16.dp))
                            Text(" Add to cart", color = LbActive, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        Text(if (item.qty > 1 || item.pickedQty in 1 until item.qty) "${item.pickedQty}/${item.qty}" else "×${item.qty}",
            color = if (item.isChecked) LbGood else LbText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, style = TabularNumbers,
            modifier = Modifier.clickable(enabled = !readOnly, onClick = onEditQty).padding(horizontal = 8.dp, vertical = 6.dp))
        if (!readOnly) {
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = LbFaint)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Set picked qty") }, onClick = { menu = false; onEditQty() })
                    DropdownMenuItem(text = { Text(if (item.isMissing) "Not missing" else "Mark missing") }, onClick = { menu = false; onMissing() })
                    DropdownMenuItem(text = { Text("Add to LB cart") }, onClick = { menu = false; onAddToCart() })
                }
            }
        }
    }
}

@Composable
private fun BottomActions(list: PickListDto, working: Boolean, onComplete: () -> Unit, onReopen: () -> Unit) {
    Surface(color = Color.White, shadowElevation = 6.dp) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (list.isCompleted) {
                OutlinedButton(onClick = onReopen, enabled = !working, shape = FieldShape, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Reopen pick list", color = LbText)
                }
            } else {
                val allDone = list.items.isNotEmpty() && list.items.all { it.isChecked || it.isMissing }
                Button(onClick = onComplete, enabled = !working, shape = FieldShape, modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (allDone) LbGood else LiquorBeeNavy)) {
                    if (working) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    else Text(if (allDone) "Complete pick list ✓" else "Complete pick list")
                }
            }
        }
    }
}
