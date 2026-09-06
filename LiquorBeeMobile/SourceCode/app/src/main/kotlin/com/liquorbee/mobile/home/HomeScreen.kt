package com.liquorbee.mobile.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liquorbee.mobile.core.network.DashboardSummaryDto
import com.liquorbee.mobile.core.network.TopSellersDto
import com.liquorbee.mobile.core.update.UpdateChecker
import com.liquorbee.mobile.core.ui.LiquorBeeBlue
import com.liquorbee.mobile.core.ui.LiquorBeeNavy
import com.liquorbee.mobile.core.ui.LiquorBeeNavyLight
import com.liquorbee.mobile.core.ui.LoginSplashOverlay
import java.text.NumberFormat
import java.util.Locale

private data class ToolItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val route: String, val accent: Color)

private val GoldRank = Color(0xFFD4AF37)
private val SilverRank = Color(0xFFA8A8B3)
private val BronzeRank = Color(0xFFB08D57)
private val PositiveGreen = Color(0xFF2ECC71)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenTool: (route: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    // Read once - viewModel.playLoginSplash itself never changes after construction, but this
    // local copy is what actually gets flipped off once the animation finishes.
    var showSplash by remember { mutableStateOf(viewModel.playLoginSplash) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Liquor Bee", fontWeight = FontWeight.Bold)
                            // Matches LiquorBeeInvoiceScannerAndroid's HomeActivity.textSyncStatus:
                            // green check when current, red warning when a newer build exists,
                            // tappable to force an immediate re-check either way.
                            val status = uiState.updateStatus
                            if (status != null) {
                                val (label, color) = when (status) {
                                    is UpdateChecker.Result.UpdateAvailable -> "⚠ Update available" to Color(0xFFC62828)
                                    UpdateChecker.Result.UpToDate -> "✓ Up to date" to Color(0xFF2E7D32)
                                    UpdateChecker.Result.CheckFailed -> "" to Color.Transparent
                                }
                                if (label.isNotEmpty()) {
                                    Text(
                                        label,
                                        color = color,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.clickable { viewModel.checkForUpdate(announce = true) }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = LiquorBeeNavy,
                        titleContentColor = LiquorBeeBlue,
                        actionIconContentColor = Color.White
                    ),
                    actions = {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.AccountCircle, contentDescription = "Profile")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            // Store name is a non-interactive header row, not a nav link - "Log Out"
                            // stays the only actionable item here per spec.
                            uiState.storeName?.let { storeName ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            storeName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    },
                                    enabled = false,
                                    onClick = {}
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Check for Updates") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.checkForUpdate(announce = true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Log Out") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.onLogout()
                                }
                            )
                        }
                    }
                )
            },
            containerColor = Color(0xFFF3F5FA)
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    Text("Tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = LiquorBeeNavy)
                }

                item {
                    val tools = buildList {
                        add(ToolItem("Label Printing", Icons.Filled.Print, "label-printing", Color(0xFF3BB2FF)))
                        add(ToolItem("Price Check", Icons.Filled.Sell, "price-check", Color(0xFF2ECC71)))
                        add(ToolItem("Inventory Module", Icons.Filled.Inventory2, "pos-inventory-module", Color(0xFFFF9F43)))
                        if (uiState.showScanInvoice) {
                            add(ToolItem("Scan Invoice", Icons.Filled.DocumentScanner, "scan-invoice-ocr", Color(0xFFB07CFF)))
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column {
                            tools.forEach { tool ->
                                ListItem(
                                    headlineContent = { Text(tool.label, fontWeight = FontWeight.SemiBold) },
                                    leadingContent = {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(tool.accent.copy(alpha = 0.15f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(tool.icon, contentDescription = null, tint = tool.accent)
                                        }
                                    },
                                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.LightGray) },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.clickable { onOpenTool(tool.route) }
                                )
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

        if (showSplash) {
            LoginSplashOverlay(onFinished = { showSplash = false })
        }

        when (uiState.updateDialogResult) {
            is UpdateChecker.Result.UpdateAvailable -> {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissUpdateDialog(rememberDismissal = false) },
                    title = { Text("Update Available") },
                    text = { Text("A newer version of this app is available. Please download and install it.") },
                    confirmButton = {
                        TextButton(onClick = {
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
                AlertDialog(
                    onDismissRequest = { viewModel.dismissUpdateDialog(rememberDismissal = false) },
                    title = { Text("Up to Date") },
                    text = { Text("You're running the latest version.") },
                    confirmButton = {
                        TextButton(onClick = { viewModel.dismissUpdateDialog(rememberDismissal = false) }) { Text("OK") }
                    }
                )
            }
            UpdateChecker.Result.CheckFailed -> {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissUpdateDialog(rememberDismissal = false) },
                    title = { Text("Couldn't Check") },
                    text = { Text("Couldn't check for an update right now - check your connection and try again.") },
                    confirmButton = {
                        TextButton(onClick = { viewModel.dismissUpdateDialog(rememberDismissal = false) }) { Text("OK") }
                    }
                )
            }
            null -> {}
        }
    }
}

@Composable
private fun SalesDashboardCard(summary: DashboardSummaryDto?, loading: Boolean) {
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    fun fmt(v: Double?): String = currency.format(v ?: 0.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(LiquorBeeNavy, LiquorBeeNavyLight, LiquorBeeBlue)))
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.InsertChart, contentDescription = null, tint = LiquorBeeBlue)
                    Spacer(modifier = Modifier.padding(start = 8.dp))
                    Text("Sales Dashboard", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                }

                when {
                    loading -> CenteredBox { CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp), color = Color.White) }
                    summary == null -> Text("No data available yet.", modifier = Modifier.padding(top = 12.dp), color = Color.White.copy(alpha = 0.7f))
                    else -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Net Total Today", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                        Text(
                            fmt(summary.netTotal),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineMedium
                        )

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
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TopSellersCard(topSellers: TopSellersDto?, loading: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).background(Color(0xFFFFF3CD), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Leaderboard, contentDescription = null, tint = GoldRank)
                }
                Spacer(modifier = Modifier.padding(start = 10.dp))
                Text("Last Week Top Sellers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = LiquorBeeNavy)
            }

            when {
                loading -> CenteredBox { CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp)) }
                topSellers == null || topSellers.items.isEmpty() ->
                    Text("No data available.", modifier = Modifier.padding(top = 12.dp), color = Color.Gray)
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
private fun TopSellerRow(rank: Int, name: String, sold: Int) {
    val rankColor = when (rank) {
        1 -> GoldRank
        2 -> SilverRank
        3 -> BronzeRank
        else -> LiquorBeeBlue
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(28.dp).background(rankColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("$rank", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(modifier = Modifier.padding(start = 10.dp))
        Text(name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                .background(PositiveGreen.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text("$sold sold", color = PositiveGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) { content() }
}
