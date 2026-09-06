package com.liquorbee.mobile.pricecheck

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.liquorbee.mobile.core.ui.LiquorBeeNavyLight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.liquorbee.mobile.core.network.PriceCheckItemDto
import com.liquorbee.mobile.core.ui.LiquorBeeBlue
import com.liquorbee.mobile.core.ui.LiquorBeeNavy
import java.text.NumberFormat
import java.util.Locale

@Composable
fun PriceCheckScreen(viewModel: PriceCheckViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val upcFocusRequester = remember { FocusRequester() }

    // Every screen open, and again after every Clear, the UPC field should be focused so the
    // iMin hardware scan-trigger keys (which inject the scanned barcode as simulated keystrokes
    // into whichever field currently has focus - see plan notes) land in the right place without
    // the operator having to tap it first.
    LaunchedEffect(uiState.focusRequestToken) {
        upcFocusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Price Check") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LiquorBeeNavy,
                    titleContentColor = LiquorBeeBlue
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = uiState.upcText,
                    onValueChange = viewModel::onUpcChanged,
                    label = { Text("Scan or enter UPC") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(upcFocusRequester)
                )
                IconButton(onClick = viewModel::clear) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear")
                }
            }

            if (uiState.upcLoading) {
                Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }

            if (uiState.notFound) {
                Text(
                    "No item found for that barcode.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            uiState.selectedItem?.let { item ->
                PriceResultCard(item = item, modifier = Modifier.padding(top = 16.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()

            TextButton(
                onClick = viewModel::toggleNameSearch,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Spacer(modifier = Modifier.padding(start = 6.dp))
                Text("Search by Name Instead")
                Spacer(modifier = Modifier.padding(start = 6.dp))
                Icon(if (uiState.nameSearchExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }

            if (uiState.nameSearchExpanded) {
                OutlinedTextField(
                    value = uiState.nameQuery,
                    onValueChange = viewModel::onNameQueryChanged,
                    label = { Text("Item name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (uiState.nameSearchLoading) {
                    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(uiState.nameResults) { result ->
                        ListItem(
                            headlineContent = { Text(result.itemName.orEmpty()) },
                            supportingContent = { Text(result.itemCode.orEmpty()) },
                            modifier = Modifier.clickable { viewModel.selectNameResult(result) }
                        )
                    }
                }
            }
        }
    }

    if (uiState.showAmbiguousDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAmbiguousDialog,
            title = { Text("Multiple items share this barcode") },
            text = {
                LazyColumn {
                    items(uiState.ambiguousMatches) { match ->
                        ListItem(
                            headlineContent = { Text(match.itemName.orEmpty()) },
                            supportingContent = { Text(match.itemCode.orEmpty()) },
                            modifier = Modifier.clickable { viewModel.selectAmbiguousMatch(match) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissAmbiguousDialog) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PriceResultCard(item: PriceCheckItemDto, modifier: Modifier = Modifier) {
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    fun fmt(v: Double?) = if (v != null) currency.format(v) else "—"

    // The "hero" (large) price flips between card/cash based on the store's own pricing setting
    // for this item, same as the web app - the other one is always shown as a smaller secondary
    // line rather than hidden entirely.
    val byCard = item.pricingByCardPrice == true
    val heroLabel = if (byCard) "Card Price" else "Cash Price"
    val heroValue = if (byCard) item.cardPrice else item.cashPrice
    val secondaryLabel = if (byCard) "Cash Price" else "Card Price"
    val secondaryValue = if (byCard) item.cashPrice else item.cardPrice

    val inStock = (item.qtyInHand ?: 0) > 0
    val stockColor = if (inStock) Color(0xFF2ECC71) else Color(0xFFE74C3C)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // Hero header: name/code + the big price, on a navy-to-blue gradient so the number
            // that matters most reads instantly at a glance.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(LiquorBeeNavy, LiquorBeeNavyLight, LiquorBeeBlue)))
                    .padding(20.dp)
            ) {
                Column {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.itemName.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(item.itemCode.orEmpty(), color = Color.White.copy(alpha = 0.7f))
                        }
                        StockBadge(inStock = inStock, color = stockColor)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(heroLabel.uppercase(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                    Text(fmt(heroValue), style = MaterialTheme.typography.headlineLarge, color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold)
                    Text("$secondaryLabel: ${fmt(secondaryValue)}", color = Color.White.copy(alpha = 0.75f))
                }
            }

            Column(modifier = Modifier.padding(20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricPill("Cost", fmt(item.cog), Color(0xFF3BB2FF), Modifier.weight(1f))
                    MetricPill(
                        "Margin",
                        item.marginPercent?.let { "%.1f%%".format(it) } ?: "—",
                        if ((item.marginPercent ?: 0.0) >= 0) Color(0xFF2ECC71) else Color(0xFFE74C3C),
                        Modifier.weight(1f)
                    )
                    MetricPill("Qty on Hand", item.qtyInHand?.toString() ?: "—", stockColor, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                DetailRow("MSRP", fmt(item.msrp))
                DetailRow("Markup %", item.markupPercent?.let { "%.1f%%".format(it) } ?: "—")
                item.vendorName?.let { DetailRow("Vendor", it) }
                item.categoryName?.let { DetailRow("Category", it) }
            }
        }
    }
}

@Composable
private fun StockBadge(inStock: Boolean, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            if (inStock) "IN STOCK" else "OUT OF STOCK",
            color = color,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun MetricPill(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(accent.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        Text(value, color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
