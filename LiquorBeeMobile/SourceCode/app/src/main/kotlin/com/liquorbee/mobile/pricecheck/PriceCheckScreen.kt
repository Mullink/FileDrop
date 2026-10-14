package com.liquorbee.mobile.pricecheck

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquorbee.mobile.core.network.PriceCheckItemDto
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
import com.liquorbee.mobile.core.ui.InterFontFamily
import java.text.NumberFormat
import java.util.Locale

// Flat redesign matching the web Price Check page (price-check.component.html/.css): grey page,
// white cards with a thin border and 8dp radius, grey labels above fields, item name leading
// with codes underneath, one big green price, and plain label/value rows for the details.

private val CardShape = RoundedCornerShape(8.dp)
private val FieldShape = RoundedCornerShape(6.dp)

@Composable
fun PriceCheckScreen(viewModel: PriceCheckViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    if (uiState.editorOpen) {
        PriceCheckEditor(uiState, onNext = viewModel::closeEditor,
            onBack = { viewModel.closeEditor(); onBack() }, onReload = viewModel::editItem, onSave = viewModel::saveItem)
        return
    }
    val upcFocusRequester = remember { FocusRequester() }

    // Every screen open, and again after every Clear, the UPC field should be focused so the
    // iMin hardware scan-trigger keys (which inject the scanned barcode as simulated keystrokes
    // into whichever field currently has focus - see plan notes) land in the right place without
    // the operator having to tap it first.
    LaunchedEffect(uiState.focusRequestToken) {
        upcFocusRequester.requestFocus()
    }

    Scaffold(
        containerColor = LbPage,
        topBar = {
            TopAppBar(
                title = { Text("Price Check", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LiquorBeeNavy,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SearchCard(
                uiState = uiState,
                upcFocusRequester = upcFocusRequester,
                onUpcChanged = viewModel::onUpcChanged,
                onClear = viewModel::clear,
                onSelectNameResult = viewModel::selectNameResult
            )

            uiState.saveMessage?.let { Text(it, color = LbGood) }
            uiState.lookupError?.let { Text(it, color = LbBad) }

            if (uiState.notFound && !uiState.upcLoading) {
                FlatCard {
                    Text(
                        "No matching items. Try another UPC, SKU, or item name.",
                        color = LbMuted,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 28.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (uiState.showAmbiguousDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAmbiguousDialog,
            containerColor = Color.White,
            shape = RoundedCornerShape(12.dp),
            title = {
                Text(
                    "Multiple items match this barcode",
                    color = LbText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                ResultList(
                    items = uiState.ambiguousMatches,
                    onSelect = viewModel::selectAmbiguousMatch
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissAmbiguousDialog) {
                    Text("Cancel", color = LbText)
                }
            }
        )
    }
}

@Composable
private fun SearchCard(
    uiState: PriceCheckUiState,
    upcFocusRequester: FocusRequester,
    onUpcChanged: (String) -> Unit,
    onClear: () -> Unit,
    onSelectNameResult: (PriceCheckItemDto) -> Unit
) {
    FlatCard {
        Column(modifier = Modifier.padding(16.dp)) {
            FlatTextField(
                value = uiState.upcText,
                onValueChange = onUpcChanged,
                placeholder = "",
                keyboardType = KeyboardType.Text,
                loading = uiState.upcLoading,
                onClear = if (uiState.upcText.isNotEmpty()) onClear else null,
                modifier = Modifier.focusRequester(upcFocusRequester)
            )
            Text("Scan UPC or search UPC, item name, or SKU", color = LbMuted,
                fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            if (uiState.nameResults.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ResultList(items = uiState.nameResults, onSelect = onSelectNameResult)
            }
        }
    }
}

@Composable
private fun PriceResultCard(item: PriceCheckItemDto) {
    // The hero (big green) price flips between card/cash based on the store's own pricing
    // setting for this item, same as the web app - the other one is shown as a smaller
    // secondary line rather than hidden entirely.
    val byCard = item.pricingByCardPrice == true
    val heroLabel = if (byCard) "Card Price" else "Cash Price"
    val heroValue = if (byCard) item.cardPrice else item.cashPrice
    val secondaryLabel = if (byCard) "Cash Price" else "Card Price"
    val secondaryValue = if (byCard) item.cashPrice else item.cardPrice

    FlatCard {
        Column(modifier = Modifier.padding(16.dp)) {
            // Item head: name leads, codes and category in small grey underneath.
            Text(item.itemName.orEmpty(), color = LbText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
            val codes = listOfNotNull(
                item.itemCode?.takeIf { it.isNotBlank() }?.let { "SKU: $it" },
                item.barcode?.takeIf { it.isNotBlank() }?.let { "UPC: $it" }
            )
            if (codes.isNotEmpty()) {
                Text(codes.joinToString("    "), color = LbMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
            val category = listOfNotNull(
                item.superCategoryName?.takeIf { it.isNotBlank() },
                item.categoryName?.takeIf { it.isNotBlank() }
            )
            if (category.isNotEmpty()) {
                Text(category.joinToString(" › "), color = LbMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }

            HorizontalDivider(color = LbDivider, modifier = Modifier.padding(top = 16.dp))

            // Price section
            Text(heroLabel, color = LbMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 18.dp))
            Text(
                formatMoney(heroValue),
                color = LbGood,
                fontSize = 42.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 46.sp,
                style = TabularNumbers
            )
            Column(
                modifier = Modifier.padding(top = 10.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (secondaryValue != null) {
                    PriceLine(secondaryLabel) {
                        Text(formatMoney(secondaryValue), color = LbText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, style = TabularNumbers)
                    }
                }
                if (item.msrp != null && item.msrp != heroValue) {
                    PriceLine("MSRP") {
                        Text(
                            formatMoney(item.msrp),
                            color = LbFaint,
                            fontSize = 15.sp,
                            textDecoration = TextDecoration.LineThrough,
                            style = TabularNumbers
                        )
                    }
                }
            }

            DiscountBadge(eligible = item.discountEligible == true)

            // Details: one label/value row each, thin dividers, like the web stat grid on phones.
            Column(modifier = Modifier.padding(top = 18.dp)) {
                HorizontalDivider(color = LbDivider)
                val qty = item.qtyInHand
                StatRow(
                    "Stock",
                    qty?.let { "$it on hand" } ?: "—",
                    valueColor = when {
                        qty == null -> LbText
                        qty > 0 -> LbGood
                        else -> LbBad
                    }
                )
                StatRow("Cost", formatMoney(item.cog))
                StatRow("Markup", formatPercent(item.markupPercent))
                StatRow("Margin", formatPercent(item.marginPercent))
                item.vendorName?.takeIf { it.isNotBlank() }?.let { StatRow("Vendor", it) }
            }
        }
    }
}

@Composable
private fun FlatCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, CardShape)
            .border(1.dp, LbBorder, CardShape)
    ) {
        content()
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = LbMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun FlatTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = LbFaint, fontSize = 16.sp) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search items", tint = LbMuted, modifier = Modifier.size(20.dp)) },
        singleLine = true,
        textStyle = TextStyle(color = LbText, fontSize = 16.sp, fontFamily = InterFontFamily),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = FieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LbFaint,
            unfocusedBorderColor = LbBorder,
            focusedContainerColor = LbPage,
            unfocusedContainerColor = LbPage,
            cursorColor = LbActive
        ),
        trailingIcon = if (loading || onClear != null) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (loading) {
                        CircularProgressIndicator(
                            color = LbActive,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (onClear != null) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = LbFaint, modifier = Modifier.size(20.dp))
                        }
                    } else {
                        Spacer(modifier = Modifier.width(14.dp))
                    }
                }
            }
        } else {
            null
        },
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun ResultList(items: List<PriceCheckItemDto>, onSelect: (PriceCheckItemDto) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState())
    ) {
        items.forEachIndexed { index, result ->
            if (index > 0) HorizontalDivider(color = LbDivider)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clickable { onSelect(result) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        result.itemName.orEmpty(),
                        color = LbText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    result.itemCode?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = LbFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(formatMoney(result.cashPrice), color = LbGood, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = TabularNumbers)
            }
        }
    }
}

@Composable
private fun PriceLine(label: String, value: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, color = LbMuted, fontSize = 13.sp)
        value()
    }
}

@Composable
private fun DiscountBadge(eligible: Boolean) {
    val shape = RoundedCornerShape(999.dp)
    val (bg, border, fg) = if (eligible) {
        Triple(Color(0xFFECFDF3), Color(0xFFB7EBC9), LbGood)
    } else {
        Triple(Color(0xFFFEF2F2), Color(0xFFFECACA), LbBad)
    }
    Text(
        if (eligible) "Discount Eligible" else "Not Discount Eligible",
        color = fg,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .background(bg, shape)
            .border(1.dp, border, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color = LbText) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(label, color = LbMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, style = TabularNumbers)
    }
    HorizontalDivider(color = LbDivider)
}

private val TabularNumbers = TextStyle(fontFeatureSettings = "tnum", fontFamily = InterFontFamily)

private val currencyFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)

private fun formatMoney(value: Double?): String = value?.let { currencyFormat.format(it) } ?: "—"

private fun formatPercent(value: Double?): String = value?.let { "%.1f%%".format(it) } ?: "—"
