package com.liquorbee.mobile.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.liquorbee.mobile.LiquorBeeApplication
import com.liquorbee.mobile.auth.LoginScreen
import com.liquorbee.mobile.auth.LoginViewModel
import com.liquorbee.mobile.core.di.AppViewModelFactory
import com.liquorbee.mobile.home.HomeScreen
import com.liquorbee.mobile.home.HomeViewModel
import com.liquorbee.mobile.inventory.InventoryScreen
import com.liquorbee.mobile.inventory.InventoryViewModel
import com.liquorbee.mobile.labelprinting.LabelPrintingScreen
import com.liquorbee.mobile.labelprinting.LabelPrintingViewModel
import com.liquorbee.mobile.pricecheck.PriceCheckScreen
import com.liquorbee.mobile.pricecheck.PriceCheckViewModel
import com.liquorbee.mobile.scaninvoice.ScanInvoiceScreen
import com.liquorbee.mobile.scaninvoice.ScanInvoiceViewModel

private const val ROUTE_LOGIN = "login"
private const val ROUTE_HOME = "home"
private const val ROUTE_PRICE_CHECK = "price-check"
private const val ROUTE_LABEL_PRINTING = "label-printing"
private const val ROUTE_INVENTORY = "pos-inventory-module"
private const val ROUTE_SCAN_INVOICE = "scan-invoice-ocr"
private const val ROUTE_PLACEHOLDER = "placeholder/{tool}"

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

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) ROUTE_HOME else ROUTE_LOGIN
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
                        ROUTE_PRICE_CHECK, ROUTE_LABEL_PRINTING, ROUTE_INVENTORY, ROUTE_SCAN_INVOICE -> navController.navigate(route)
                        else -> navController.navigate("placeholder/$route")
                    }
                }
            )
        }

        composable(ROUTE_PRICE_CHECK) {
            val viewModel: PriceCheckViewModel = viewModel(factory = factory)
            PriceCheckScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
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

        composable(ROUTE_PLACEHOLDER) { backStackEntry ->
            val tool = backStackEntry.arguments?.getString("tool").orEmpty()
            PlaceholderScreen(
                title = TOOL_TITLES[tool] ?: tool,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

