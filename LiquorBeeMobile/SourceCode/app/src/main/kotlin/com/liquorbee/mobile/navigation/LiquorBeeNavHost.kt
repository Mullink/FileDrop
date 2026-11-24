package com.liquorbee.mobile.navigation

import android.app.Activity
import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.liquorbee.mobile.LiquorBeeApplication
import com.liquorbee.mobile.auth.LoginScreen
import com.liquorbee.mobile.auth.LoginViewModel
import com.liquorbee.mobile.core.di.AppViewModelFactory
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.liquorbee.mobile.lbcart.LbCartScreen
import com.liquorbee.mobile.lbcart.LbCartViewModel
import com.liquorbee.mobile.wholesale.WholesaleOrderScreen
import com.liquorbee.mobile.wholesale.WholesaleOrderViewModel
import com.liquorbee.mobile.wholesale.WholesaleScreen
import com.liquorbee.mobile.wholesale.WholesaleViewModel
import com.liquorbee.mobile.carts.ScanScreen
import com.liquorbee.mobile.carts.ScanViewModel
import com.liquorbee.mobile.home.HomeScreen
import com.liquorbee.mobile.picklists.PickListScreen
import com.liquorbee.mobile.picklists.PickListViewModel
import com.liquorbee.mobile.picklists.PickListsScreen
import com.liquorbee.mobile.picklists.PickListsViewModel
import com.liquorbee.mobile.home.HomeViewModel
import com.liquorbee.mobile.inventory.InventoryScreen
import com.liquorbee.mobile.inventory.InventoryViewModel
import com.liquorbee.mobile.labelprinting.LabelPrintingScreen
import com.liquorbee.mobile.labelprinting.LabelPrintingViewModel
import com.liquorbee.mobile.pricecheck.PriceCheckScreen
import com.liquorbee.mobile.core.ui.ScanMode
import com.liquorbee.mobile.core.ui.ScanModePrefs
import com.liquorbee.mobile.pricecheck.PriceCheckViewModel
import com.liquorbee.mobile.scaninvoice.ScanInvoiceScreen
import com.liquorbee.mobile.scaninvoice.ScanInvoiceViewModel

private const val ROUTE_LOGIN = "login"
private const val ROUTE_HOME = "home"
private const val ROUTE_PRICE_CHECK = "price-check"
private const val ROUTE_LABEL_PRINTING = "label-printing"
private const val ROUTE_INVENTORY = "pos-inventory-module"
private const val ROUTE_SCAN_INVOICE = "scan-invoice-ocr"
private const val ROUTE_SCAN = "scan"
// Optional ?q= is a barcode scanned before Scan opened (e.g. on Home) - searched on arrival.
private const val ROUTE_SCAN_PATTERN = "scan?q={q}"
private const val ROUTE_WHOLESALE = "wholesale"
private const val ROUTE_WHOLESALE_ORDER = "wholesale-order/{orderId}"
private const val ROUTE_LB_CART = "lb-cart"
private const val ROUTE_PICK_LISTS = "pick-lists"
private const val ROUTE_PICK_LIST = "pick-list/{id}"
private const val ROUTE_PLACEHOLDER = "placeholder/{tool}"
private const val PENDING_SCAN = "pendingScan"

// Human-readable titles for the Tools rows, keyed by the same route slugs the web app already
// uses (label-printing, price-check, pos-inventory-module, scan-invoice-ocr) - see HomeScreen.
private val TOOL_TITLES = mapOf(
    "label-printing" to "Label Printing",
    "price-check" to "Price Check",
    "pos-inventory-module" to "Inventory Module",
    "scan-invoice-ocr" to "Scan Invoice"
)

@Composable
fun LiquorBeeNavHost(app: LiquorBeeApplication) {
    val navController = rememberNavController()
    val factory = AppViewModelFactory(app)
    val isLoggedIn by app.isLoggedIn.collectAsState()
    // Tracks the value isLoggedIn had on the PREVIOUS check, seeded to the current value so the
    // very first LaunchedEffect firing (on initial composition) is a no-op - NavHost's own
    // startDestination below already reflects that initial value correctly. Only a genuine flip
    // afterward (login success, explicit logout, or a 401 firing AuthInterceptor.onUnauthorized
    // from anywhere) should force navigation.
    var previousLoggedIn by remember { mutableStateOf(isLoggedIn) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn != previousLoggedIn) {
            previousLoggedIn = isLoggedIn
            val target = if (isLoggedIn) ROUTE_HOME else ROUTE_LOGIN
            navController.navigate(target) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // NavHost's own automatic back handling is disabled below in favor of this single handler,
    // which owns 100% of back-press behavior for the whole graph. NavHost's built-in handling is
    // keyed off backQueue.size and re-registers per composition; a rapid second back press (e.g.
    // right after returning from a Tool screen to Home) can land while that internal state is
    // between updates, popping the graph's last remaining entry (Home) instead of falling through
    // to finish the Activity - the result is nothing left to compose (a blank window matching the
    // theme's background) with the Activity still alive, requiring a force-restart. A single,
    // stable, always-registered handler removes the race entirely: pop through the NavController
    // ourselves when there's somewhere to go back to, otherwise finish the Activity directly.
    val activity = LocalContext.current as? Activity
    LaunchedEffect(navController) { navController.enableOnBackPressed(false) }
    BackHandler(enabled = true) {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else {
            activity?.finish()
        }
    }

    // A hardware scan on any screen that doesn't use scans itself goes through Scan: back to the Scan
    // screen already open underneath (looking the code up there), or a new Scan screen searching for it.
    // Screens that scan for their own job keep their scans: Scan, Pick List (checks items off), Price
    // Check, Label Printing, Inventory and Scan Invoice.
    val currentEntry by navController.currentBackStackEntryAsState()
    val route = currentEntry?.destination?.route
    val routesScans = route != null && (route == ROUTE_HOME || route == ROUTE_WHOLESALE || route == ROUTE_WHOLESALE_ORDER ||
        route == ROUTE_LB_CART || route == ROUTE_PICK_LISTS || route == ROUTE_PLACEHOLDER)
    val context = androidx.compose.ui.platform.LocalContext.current
    // Item edits mode (the default): a scan from Home opens Price Check, straight to the item's editor.
    val openItemEdits: (String) -> Unit = { code ->
        navController.navigate("$ROUTE_PRICE_CHECK?q=${Uri.encode(code)}") { launchSingleTop = true }
    }
    val openScan: (String) -> Unit = openScan@{ code ->
        if (route == ROUTE_HOME && ScanModePrefs.get(context) == ScanMode.ItemEdits) { openItemEdits(code); return@openScan }
        val scanEntry = runCatching { navController.getBackStackEntry(ROUTE_SCAN_PATTERN) }.getOrNull()
        if (scanEntry != null) {
            // A unique token so scanning the same code twice still runs twice.
            scanEntry.savedStateHandle[PENDING_SCAN] = "${System.nanoTime()}|$code"
            navController.popBackStack(ROUTE_SCAN_PATTERN, inclusive = false)
        } else {
            navController.navigate("$ROUTE_SCAN?q=${Uri.encode(code)}") { launchSingleTop = true }
        }
    }
    if (isLoggedIn && routesScans) {
        com.liquorbee.mobile.core.ui.ScanCapture(key = currentEntry?.id ?: "", onScan = openScan)
    }

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) ROUTE_HOME else ROUTE_LOGIN,
        enterTransition = { fadeIn(tween(180)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(180)) },
        popExitTransition = { fadeOut(tween(180)) }
    ) {
        composable(ROUTE_LOGIN) {
            val viewModel: LoginViewModel = viewModel(factory = factory)
            LoginScreen(viewModel = viewModel)
        }

        composable(ROUTE_HOME) {
            val viewModel: HomeViewModel = viewModel(factory = factory)
            HomeScreen(
                viewModel = viewModel,
                onOpenTool = { route ->
                    when (route) {
                        // Home's Scan card opens whichever mode was used last (Item edits by default).
                        ROUTE_SCAN -> navController.navigate(if (ScanModePrefs.get(context) == ScanMode.ItemEdits) ROUTE_PRICE_CHECK else ROUTE_SCAN)
                        ROUTE_PRICE_CHECK, ROUTE_LABEL_PRINTING, ROUTE_INVENTORY, ROUTE_SCAN_INVOICE, ROUTE_WHOLESALE, ROUTE_LB_CART, ROUTE_PICK_LISTS -> navController.navigate(route)
                        else -> if (route.startsWith("$ROUTE_SCAN?")) {
                            if (ScanModePrefs.get(context) == ScanMode.ItemEdits) openItemEdits(Uri.decode(route.substringAfter("q=")))
                            else navController.navigate(route) { launchSingleTop = true }
                        }
                            else navController.navigate("placeholder/$route")
                    }
                }
            )
        }

        // Optional ?q= pre-fills the lookup (used by Scan's "Price Check" action); plain
        // "price-check" from Home still matches since q has a default.
        composable(
            "$ROUTE_PRICE_CHECK?q={q}&itemId={itemId}&price={price}",
            arguments = listOf(
                navArgument("q") { type = NavType.StringType; defaultValue = "" },
                navArgument("itemId") { type = NavType.StringType; defaultValue = "" },
                navArgument("price") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val viewModel: PriceCheckViewModel = viewModel(factory = factory)
            val prefill = backStackEntry.arguments?.getString("q").orEmpty()
            // Scan's "Edit Item" passes the ItemId: open the editor immediately, and finishing it goes back to Scan.
            val itemId = backStackEntry.arguments?.getString("itemId").orEmpty()
            LaunchedEffect(backStackEntry.id) {
                val s = viewModel.uiState.value
                if (s.editorOpen || s.upcText.isNotEmpty()) return@LaunchedEffect
                if (itemId.isNotBlank()) viewModel.openForEdit(itemId, backStackEntry.arguments?.getString("price")?.toDoubleOrNull())
                else if (prefill.isNotBlank()) viewModel.onUpcChanged(prefill)
            }
            PriceCheckScreen(viewModel = viewModel, onBack = { navController.popBackStack() },
                returnAfterEdit = itemId.isNotBlank(),
                // Opened for one item from Scan's Edit Item: no mode switch, just edit and go back.
                onSwitchToStock = if (itemId.isNotBlank()) null else { q ->
                    ScanModePrefs.set(context, ScanMode.StockMgmt)
                    navController.navigate("$ROUTE_SCAN?q=${Uri.encode(q)}") {
                        popUpTo(backStackEntry.destination.id) { inclusive = true }
                        launchSingleTop = true
                    }
                })
        }

        composable(ROUTE_LABEL_PRINTING) {
            val viewModel: LabelPrintingViewModel = viewModel(factory = factory)
            LabelPrintingScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(ROUTE_INVENTORY) {
            val viewModel: InventoryViewModel = viewModel(factory = factory)
            InventoryScreen(viewModel = viewModel, onExit = { navController.popBackStack() })
        }

        composable(ROUTE_SCAN_INVOICE) {
            val viewModel: ScanInvoiceViewModel = viewModel(factory = factory)
            ScanInvoiceScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(
            ROUTE_SCAN_PATTERN,
            arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" })
        ) { backStackEntry ->
            val viewModel: ScanViewModel = viewModel(factory = factory)
            // Search the scanned code once; rememberSaveable keeps rotation/back from re-running it.
            val q = backStackEntry.arguments?.getString("q").orEmpty()
            var consumed by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(q) {
                if (!consumed && q.isNotBlank()) { consumed = true; viewModel.scanFromOutside(q) }
            }
            // A scan made on another screen that returned here (see openScan above).
            val pending by backStackEntry.savedStateHandle.getStateFlow(PENDING_SCAN, "").collectAsState()
            androidx.compose.runtime.LaunchedEffect(pending) {
                val code = pending.substringAfter('|', "")
                if (code.isNotBlank()) {
                    backStackEntry.savedStateHandle[PENDING_SCAN] = ""
                    viewModel.scanFromOutside(code)
                }
            }
            ScanScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenWholesale = { navController.navigate(ROUTE_WHOLESALE) { launchSingleTop = true } },
                onOpenLbCart = { navController.navigate(ROUTE_LB_CART) { launchSingleTop = true } },
                onPriceCheck = { q -> navController.navigate("$ROUTE_PRICE_CHECK?q=${Uri.encode(q)}") },
                onEditItem = { itemId, q, price ->
                    navController.navigate("$ROUTE_PRICE_CHECK?q=${Uri.encode(q)}&itemId=${Uri.encode(itemId.orEmpty())}&price=${price ?: ""}")
                },
                onOpenPickList = { id -> navController.navigate("pick-list/$id") },
                onSwitchToItemEdits = { q ->
                    ScanModePrefs.set(context, ScanMode.ItemEdits)
                    navController.navigate("$ROUTE_PRICE_CHECK?q=${Uri.encode(q)}") {
                        popUpTo(backStackEntry.destination.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        // Wholesale and the LB cart are opened from Scan; their Scan button goes back to that same
        // Scan screen rather than stacking another.
        val backToScan: () -> Unit = {
            if (!navController.popBackStack(ROUTE_SCAN_PATTERN, inclusive = false)) navController.navigate(ROUTE_SCAN)
        }

        composable(ROUTE_WHOLESALE) {
            val viewModel: WholesaleViewModel = viewModel(factory = factory)
            WholesaleScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenScan = backToScan,
                onOpenOrder = { id -> navController.navigate("wholesale-order/${Uri.encode(id)}") }
            )
        }

        composable(ROUTE_WHOLESALE_ORDER, arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId").orEmpty()
            val viewModel: WholesaleOrderViewModel = viewModel(key = "wholesale-order-$orderId", factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = WholesaleOrderViewModel(app.apiService, app.catalogRepository, orderId) as T
            })
            WholesaleOrderScreen(viewModel = viewModel, onBack = { navController.popBackStack() },
                onOpenPickList = { id -> navController.navigate("pick-list/$id") })
        }

        composable(ROUTE_LB_CART) {
            val viewModel: LbCartViewModel = viewModel(factory = factory)
            LbCartScreen(viewModel = viewModel, onBack = { navController.popBackStack() }, onOpenScan = backToScan)
        }

        composable(ROUTE_PICK_LISTS) {
            val viewModel: PickListsViewModel = viewModel(factory = factory)
            PickListsScreen(viewModel = viewModel, onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate("pick-list/$id") })
        }

        composable(ROUTE_PICK_LIST, arguments = listOf(navArgument("id") { type = NavType.IntType })) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("id") ?: 0
            val app = LocalContext.current.applicationContext as LiquorBeeApplication
            val viewModel: PickListViewModel = viewModel(key = "pick-list-$id", factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    PickListViewModel(app.apiService, app.cartStore, app.restockRepository, id,
                        app.getSharedPreferences("liquorbee_pick_lists", android.content.Context.MODE_PRIVATE)) as T
            })
            PickListScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(ROUTE_PLACEHOLDER) { backStackEntry ->
            val tool = backStackEntry.arguments?.getString("tool").orEmpty()
            PlaceholderScreen(
                title = TOOL_TITLES[tool] ?: tool,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

