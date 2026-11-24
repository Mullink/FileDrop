package com.liquorbee.mobile.home

import com.liquorbee.mobile.core.ui.LbActive
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.liquorbee.mobile.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquorbee.mobile.core.network.DashboardSummaryDto
import com.liquorbee.mobile.core.network.TopSellersDto
import com.liquorbee.mobile.core.update.UpdateChecker
import com.liquorbee.mobile.core.ui.LiquorBeeBlue
import com.liquorbee.mobile.core.ui.LiquorBeeNavy
import com.liquorbee.mobile.core.ui.LbPage
import com.liquorbee.mobile.core.ui.LbBorder
import com.liquorbee.mobile.core.ui.LbText
import com.liquorbee.mobile.core.ui.LbMuted
import com.liquorbee.mobile.core.ui.LbGood
import java.text.NumberFormat
import java.util.Locale

private data class ToolItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val route: String, val accent: Color,
    val badge: String? = null)

private val PositiveGreen = Color(0xFF2ECC71)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenTool: (route: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    val carts by (context.applicationContext as com.liquorbee.mobile.LiquorBeeApplication).cartStore.state.collectAsState()
    val wholesaleEnabled by (context.applicationContext as com.liquorbee.mobile.LiquorBeeApplication).wholesaleAccess.enabled.collectAsState()
    var scanMode by remember { mutableStateOf(com.liquorbee.mobile.core.ui.ScanModePrefs.get(context)) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                            Image(painterResource(R.drawable.liquorbee_nav_logo), contentDescription = "Liquor Bee",
                                modifier = Modifier.height(48.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = LiquorBeeNavy,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    actions = {
                            // Matches LiquorBeeInvoiceScannerAndroid's HomeActivity.textSyncStatus:
                            // green check when current, red warning when a newer build exists,
                            // tappable to force an immediate re-check either way.
                            val status = uiState.updateStatus
                            if (status != null) {
                                val (label, color) = when (status) {
                                    is UpdateChecker.Result.UpdateAvailable -> "⚠ Update available" to Color(0xFFFDBA74)
                                    UpdateChecker.Result.UpToDate -> "✓ Up to date" to Color(0xFF86EFAC)
                                    UpdateChecker.Result.CheckFailed -> "" to Color.Transparent
                                }
                                if (label.isNotEmpty()) {
                                    Text(
                                        label,
                                        color = color,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        modifier = Modifier.clickable { viewModel.checkForUpdate(announce = true) }.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.AccountCircle, contentDescription = "Profile")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.width(280.dp).background(Color.White)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(42.dp).background(LiquorBeeNavy, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Storefront, null, tint = Color(0xFF3BB2FF))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("YOUR STORE", fontSize = 10.sp, color = LbMuted, fontWeight = FontWeight.Medium)
                                    Text(uiState.storeName?.takeIf { it.isNotBlank() } ?: "My store",
                                        fontSize = 18.sp, lineHeight = 23.sp, color = LbText, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            HorizontalDivider(color = LbBorder, modifier = Modifier.padding(horizontal = 16.dp))
                            DropdownMenuItem(
                                text = { Text("Check for Updates", fontSize = 14.sp, color = LbText) },
                                leadingIcon = { Icon(Icons.Filled.SystemUpdate, null, tint = LiquorBeeNavy) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.checkForUpdate(announce = true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Log Out", fontSize = 14.sp, color = Color(0xFFB42318)) },
                                leadingIcon = { Icon(Icons.Filled.Logout, null, tint = Color(0xFFB42318)) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.onLogout()
                                }
                            )
                        }
                    }
                )
            },
            containerColor = LbPage
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Scan is the centre of the app: the hero opens it in the chosen mode (switchable right here).
                item {
                    ScanHero(mode = scanMode, onMode = {
                        scanMode = it; com.liquorbee.mobile.core.ui.ScanModePrefs.set(context, it)
                    }, onOpen = { onOpenTool("scan") })
                }

                item {
                    val tools = buildList {
                        if (wholesaleEnabled) add(ToolItem("Wholesale", Icons.Filled.Storefront, "wholesale", Color(0xFF006EF8),
                            badge = carts.wholesaleCount.takeIf { it > 0 }?.let { "$it" }))
                        add(ToolItem("LB Cart", Icons.Filled.ShoppingCart, "lb-cart", Color(0xFFB45309),
                            badge = carts.lbCount.takeIf { it > 0 }?.let { "$it" }))
                        add(ToolItem("Pick Lists", Icons.Filled.Checklist, "pick-lists", Color(0xFF006EF8)))
                        add(ToolItem("Label Printing", Icons.Filled.Print, "label-printing", Color(0xFF006EF8)))
                        add(ToolItem("Inventory Module", Icons.Filled.Inventory2, "pos-inventory-module", Color(0xFFC2410C)))
                        if (uiState.showScanInvoice) {
                            add(ToolItem("Scan Invoice", Icons.Filled.DocumentScanner, "scan-invoice-ocr", Color(0xFF7C3AED)))
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        tools.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { tool ->
                                    Card(
                                        onClick = { onOpenTool(tool.route) },
                                        modifier = Modifier.weight(1f).height(92.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, LbBorder),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                    ) {
                                        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                                Box(Modifier.size(32.dp).background(tool.accent.copy(alpha = 0.10f), RoundedCornerShape(6.dp)),
                                                    contentAlignment = Alignment.Center) {
                                                    Icon(tool.icon, contentDescription = null, tint = tool.accent, modifier = Modifier.size(18.dp))
                                                }
                                                Spacer(Modifier.weight(1f))
                                                // Items waiting in that cart.
                                                tool.badge?.let {
                                                    Text(it, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.background(tool.accent, RoundedCornerShape(10.dp))
                                                            .padding(horizontal = 8.dp, vertical = 2.dp))
                                                }
                                            }
                                            Text(tool.label, color = LbText, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1)
                                        }
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Sales Dashboard first - the figure store staff check most often - then Top Sellers.
                if (uiState.showSalesDashboard) {
                    item {
                        SalesDashboardCard(summary = uiState.dashboardSummary, loading = uiState.dashboardLoading)
                    }
                }

                item {
                    TopSellersCard(topSellers = uiState.topSellers, loading = uiState.topSellersLoading)
                }
            }
        }

        when (uiState.updateDialogResult) {
            is UpdateChecker.Result.UpdateAvailable -> {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissUpdateDialog(rememberDismissal = false) },
                    containerColor = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    icon = { UpdateStatusIcon(Icons.Filled.SystemUpdate, Color(0xFF3BB2FF)) },
                    title = { Text("Update Available", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = LbText) },
                    text = { Text("A new version of Liquor Bee is ready. Download it to get the latest improvements.", color = LbMuted, textAlign = TextAlign.Center) },
                    confirmButton = {
                        Button(shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = LiquorBeeNavy), onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.APK_DOWNLOAD_URL)))
                            viewModel.dismissUpdateDialog(rememberDismissal = false)
                        }) { Text("Download") }
                    },
                    dismissButton = {
                        // Remembered across app restarts (see UpdateChecker.markDismissed) - stops
                        // the background poll from re-nagging about THIS version; a genuinely newer
                        // version still gets its own fresh popup.
                        TextButton(onClick = { viewModel.dismissUpdateDialog(rememberDismissal = true) }) { Text("Later") }
                    }
                )
            }
            UpdateChecker.Result.UpToDate -> {
                val dismiss = { viewModel.dismissUpdateDialog(rememberDismissal = false) }
                Dialog(onDismissRequest = dismiss) {
                    Surface(color = Color.White, shape = RoundedCornerShape(12.dp)) {
                        Box(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 32.dp, bottom = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                UpdateStatusIcon(Icons.Filled.CheckCircle, LbGood)
                                Text("Up to Date", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = LbText)
                                Text("You’re running the latest version of Liquor Bee.", color = LbMuted, textAlign = TextAlign.Center)
                                Button(shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = LiquorBeeNavy),
                                    onClick = dismiss) { Text("Done") }
                            }
                            IconButton(onClick = dismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                                Icon(Icons.Filled.Close, contentDescription = "Close", tint = LbMuted)
                            }
                        }
                    }
                }
            }
            UpdateChecker.Result.CheckFailed -> {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissUpdateDialog(rememberDismissal = false) },
                    containerColor = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    icon = { UpdateStatusIcon(Icons.Filled.CloudOff, Color(0xFFB42318)) },
                    title = { Text("Couldn't Check", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = LbText) },
                    text = { Text("We couldn’t check for updates. Check your connection and try again.", color = LbMuted, textAlign = TextAlign.Center) },
                    confirmButton = {
                        Button(shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = LiquorBeeNavy),
                            onClick = { viewModel.dismissUpdateDialog(rememberDismissal = false) }) { Text("Done") }
                    }
                )
            }
            null -> {}
        }
    }
}

/**
 * The Scan hero: what the scanner will do (Item edits or Stock mgmt, switchable here) and a big target to start.
 */
@Composable
private fun ScanHero(mode: com.liquorbee.mobile.core.ui.ScanMode, onMode: (com.liquorbee.mobile.core.ui.ScanMode) -> Unit, onOpen: () -> Unit) {
    val itemEdits = mode == com.liquorbee.mobile.core.ui.ScanMode.ItemEdits
    val glow = Color(0xFF3BB2FF)
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(LiquorBeeNavy, Color(0xFF0B2F66), Color(0xFF0B4A8F))))
            .clickable(onClick = onOpen)
    ) {
        // Oversized faint scanner mark for depth.
        Icon(Icons.Filled.QrCodeScanner, null, tint = Color.White.copy(alpha = 0.05f),
            modifier = Modifier.size(170.dp).align(Alignment.TopEnd).offset(x = 40.dp, y = (-24).dp))
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(58.dp)
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(glow, Color(0xFF006EF8))), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Scan", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (itemEdits) "Scan an item to change its price or details"
                        else "Scan an item to add it to a cart or Restock",
                        color = Color(0xFFCBD5E1), fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                // Mode switch: the same choice as the toggle at the top of Scan.
                Row(Modifier.weight(1f).background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp)).padding(3.dp)) {
                    com.liquorbee.mobile.core.ui.ScanMode.values().forEach { m ->
                        val on = m == mode
                        Box(Modifier.weight(1f).height(36.dp)
                            .background(if (on) Color.White else Color.Transparent, RoundedCornerShape(20.dp))
                            .clickable { onMode(m) }, contentAlignment = Alignment.Center) {
                            Text(m.label, color = if (on) LiquorBeeNavy else Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(42.dp).background(glow, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Start scanning", tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

@Composable
private fun UpdateStatusIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Box(Modifier.size(56.dp).background(tint.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun SalesDashboardCard(summary: DashboardSummaryDto?, loading: Boolean) {
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    fun fmt(v: Double?): String = currency.format(v ?: 0.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, LbBorder),
        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = LbText),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        HomeCardHeader("Sales Dashboard", Icons.Filled.InsertChart)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column {
                when {
                    loading -> CenteredBox { CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp), color = LbActive) }
                    summary == null -> Text("No data available yet.", modifier = Modifier.padding(top = 12.dp), color = LbMuted)
                    else -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Net Total Today", color = LbMuted, style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center)
                            Text(
                                fmt(summary.netTotal),
                                color = LbText,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatPill("Gross", fmt(summary.grossSale), Modifier.weight(1f))
                            StatPill("Cash", fmt(summary.netCashSale), Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatPill("Credit", fmt(summary.netCreditSale), Modifier.weight(1f))
                            StatPill("Tax", fmt(summary.taxCollected), Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(LbPage, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, color = LbMuted, style = MaterialTheme.typography.labelSmall)
        Text(value, color = LbText, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TopSellersCard(topSellers: TopSellersDto?, loading: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, LbBorder),
        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = LbText),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        HomeCardHeader("Last Week Top Sellers", Icons.Filled.Leaderboard)
        Column(modifier = Modifier.padding(16.dp)) {
            when {
                loading -> CenteredBox { CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp), color = LbActive) }
                topSellers == null || topSellers.items.isEmpty() ->
                    Text("No data available.", modifier = Modifier.padding(top = 12.dp), color = LbMuted)
                else -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    topSellers.items.take(10).forEachIndexed { index, item ->
                        TopSellerRow(rank = index + 1, name = item.name.orEmpty(), sold = item.totalSold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCardHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = LbActive, modifier = Modifier.size(22.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = LbText)
    }
    HorizontalDivider(color = LbBorder)
}

@Composable
private fun TopSellerRow(rank: Int, name: String, sold: Int) {
    Column(Modifier.fillMaxWidth().padding(bottom = if (rank <= 3) 4.dp else 0.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .background(if (rank <= 3) LbPage else Color.Transparent, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (rank <= 3) {
                Icon(Icons.Filled.MilitaryTech, contentDescription = "Rank $rank",
                    tint = when (rank) { 1 -> Color(0xFFF5C542); 2 -> Color(0xFF9CA3AF); else -> Color(0xFFD08B4E) },
                    modifier = Modifier.size(26.dp))
            } else {
                Box(Modifier.size(24.dp).border(1.dp, LbBorder, CircleShape), contentAlignment = Alignment.Center) {
                    Text("$rank", color = LbMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
            Text(name, color = LbText, modifier = Modifier.weight(1f),
                fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("$sold", color = LbActive, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("sold", color = LbMuted, fontSize = 11.5.sp)
            }
        }
        if (rank in 4..9) HorizontalDivider(color = LbBorder)
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) { content() }
}


