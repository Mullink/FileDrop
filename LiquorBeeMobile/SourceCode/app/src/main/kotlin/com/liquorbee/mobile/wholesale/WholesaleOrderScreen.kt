package com.liquorbee.mobile.wholesale

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquorbee.mobile.carts.TabularNumbers
import com.liquorbee.mobile.carts.money
import com.liquorbee.mobile.core.network.WholesaleOrderDto
import com.liquorbee.mobile.core.ui.LbBorder
import com.liquorbee.mobile.core.ui.LbEmpty
import com.liquorbee.mobile.core.ui.LbFaint
import com.liquorbee.mobile.core.ui.LbFooter
import com.liquorbee.mobile.core.ui.LbMuted
import com.liquorbee.mobile.core.ui.LbPanel
import com.liquorbee.mobile.core.ui.LbPanelShape
import com.liquorbee.mobile.core.ui.LbPrimaryButton
import com.liquorbee.mobile.core.ui.LbScreen
import com.liquorbee.mobile.core.ui.LbSecondaryButton
import com.liquorbee.mobile.core.ui.LbSectionTitle
import com.liquorbee.mobile.core.ui.LbText
import androidx.compose.foundation.layout.navigationBarsPadding

@Composable
fun WholesaleOrderScreen(viewModel: WholesaleOrderViewModel, onBack: () -> Unit, onOpenPickList: (Int) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val order = uiState.order

    LbScreen(
        title = order?.label?.takeIf { it.isNotBlank() }?.let { "Order #$it" } ?: "Order",
        onBack = onBack,
        bottomBar = {
            if (order != null) Column(Modifier.fillMaxWidth().background(LbFooter)) {
                HorizontalDivider(color = LbBorder)
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    val listId = uiState.pickListId
                    if (listId != null) LbSecondaryButton("Open pick list", onClick = { onOpenPickList(listId) }, modifier = Modifier.fillMaxWidth())
                    else LbPrimaryButton("Send to pick list", onClick = { viewModel.sendToPickList {} }, loading = uiState.sending,
                        enabled = !order.items.isNullOrEmpty(), modifier = Modifier.fillMaxWidth())
                }
            }
        },
        toast = uiState.toast,
        onToastShown = viewModel::toastShown
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.loading && order == null -> LbEmpty("Loading order…", loading = true)
                order == null -> LbEmpty("Couldn't load the order", uiState.error) { LbSecondaryButton("Try again", onClick = viewModel::load) }
                else -> OrderBody(order)
            }
        }
    }
}

@Composable
private fun OrderBody(order: WholesaleOrderDto) {
    val items = order.items.orEmpty()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item {
            LbPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(order.customerName?.takeIf { it.isNotBlank() } ?: "General Customer", color = LbText, fontSize = 16.sp,
                            fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        shortDate(order.createdDate)?.let { Text("Placed $it", color = LbMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)) }
                    }
                    StatusPill(order)
                }
                // Bordered three-column summary strip, like the web order drawer.
                Row(Modifier.fillMaxWidth().padding(top = 12.dp).height(IntrinsicSize.Min).border(1.dp, LbBorder, LbPanelShape)) {
                    SummaryCell("Items", "${items.size}", Modifier.weight(1f))
                    Box(Modifier.width(1.dp).fillMaxHeight().background(LbBorder))
                    SummaryCell("Units", "${items.sumOf { Math.round(it.quantity).toInt() }}", Modifier.weight(1f))
                    Box(Modifier.width(1.dp).fillMaxHeight().background(LbBorder))
                    SummaryCell("Total", money(order.total), Modifier.weight(1f))
                }
                order.notes?.takeIf { it.isNotBlank() }?.let {
                    Text("Notes", color = LbMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                    Text(it, color = LbText, fontSize = 14.sp)
                }
            }
        }
        item { LbSectionTitle("Items", trailing = itemCount(items.size)) }
        if (items.isEmpty()) item { Text("No items on this order.", color = LbMuted, fontSize = 14.sp) }
        items(items) { line ->
            LbPanel(padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(line.itemName ?: line.itemCode ?: "Item", color = LbText, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(line.itemCode, line.size, "${money(line.price)} each").joinToString("  ·  "),
                            color = LbFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("× ${fmtQty(line.quantity)}", color = LbText, fontSize = 15.sp, fontWeight = FontWeight.Medium, style = TabularNumbers)
                        Text(money(line.price * line.quantity), color = LbMuted, fontSize = 12.sp, style = TabularNumbers)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = LbMuted, fontSize = 12.sp)
        Text(value, color = LbText, fontSize = 15.sp, fontWeight = FontWeight.Medium, style = TabularNumbers, maxLines = 1)
    }
}

private fun fmtQty(q: Double) = if (q % 1.0 == 0.0) q.toLong().toString() else q.toString()

