package com.liquorbee.mobile.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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

private data class ToolItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val route: String, val accent: Color)

private val PositiveGreen = Color(0xFF2ECC71)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenTool: (route: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

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
                item {
                    val tools = buildList {
                        add(ToolItem("Label Printing", Icons.Filled.Print, "label-printing", Color(0xFF3BB2FF)))
                        add(ToolItem("Price Check", Icons.Filled.Sell, "price-check", Color(0xFF2ECC71)))
                        add(ToolItem("Inventory Module", Icons.Filled.Inventory2, "pos-inventory-module", Color(0xFFFF9F43)))
                        if (uiState.showScanInvoice) {
                            add(ToolItem("Scan Invoice", Icons.Filled.DocumentScanner, "scan-invoice-ocr", Color(0xFFB07CFF)))
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        tools.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { tool ->
                                    Card(
                                        onClick = { onOpenTool(tool.route) },
                                        modifier = Modifier.weight(1f).aspectRatio(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = LiquorBeeNavy),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                    ) {
                                        Column(Modifier.fillMaxSize().padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                                            Icon(tool.icon, contentDescription = null, tint = Color(0xFF3BB2FF), modifier = Modifier.size(36.dp))
                                            Text(tool.label, color = Color.White, fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
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
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        colors = CardDefaults.cardColors(containerColor = LiquorBeeNavy, contentColor = Color.White),
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
                    loading -> CenteredBox { CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp), color = Color(0xFF3BB2FF)) }
                    summary == null -> Text("No data available yet.", modifier = Modifier.padding(top = 12.dp), color = Color(0xFFCBD5E1))
                    else -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Net Total Today", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center)
                            Text(
                                fmt(summary.netTotal),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
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
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, color = Color(0xFFCBD5E1), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TopSellersCard(topSellers: TopSellersDto?, loading: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        colors = CardDefaults.cardColors(containerColor = LiquorBeeNavy, contentColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        HomeCardHeader("Last Week Top Sellers", Icons.Filled.Leaderboard)
        Column(modifier = Modifier.padding(16.dp)) {
            when {
                loading -> CenteredBox { CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp), color = Color(0xFF3BB2FF)) }
                topSellers == null || topSellers.items.isEmpty() ->
                    Text("No data available.", modifier = Modifier.padding(top = 12.dp), color = Color(0xFFCBD5E1))
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
    Row(Modifier.fillMaxWidth().background(LiquorBeeNavy).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = Color(0xFF3BB2FF), modifier = Modifier.size(28.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
}

@Composable
private fun TopSellerRow(rank: Int, name: String, sold: Int) {
    Column(Modifier.fillMaxWidth().padding(bottom = if (rank <= 3) 4.dp else 0.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .background(if (rank <= 3) Color.White.copy(alpha = 0.035f) else Color.Transparent, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (rank <= 3) {
                Icon(Icons.Filled.MilitaryTech, contentDescription = "Rank $rank",
                    tint = when (rank) { 1 -> Color(0xFFF5C542); 2 -> Color(0xFFC9D1DC); else -> Color(0xFFD08B4E) },
                    modifier = Modifier.size(26.dp))
            } else {
                Box(Modifier.size(24.dp).border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                    Text("$rank", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(name, color = Color.White, modifier = Modifier.weight(1f),
                fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("$sold", color = Color(0xFF3BB2FF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("sold", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
            }
        }
        if (rank in 4..9) HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) { content() }
}
