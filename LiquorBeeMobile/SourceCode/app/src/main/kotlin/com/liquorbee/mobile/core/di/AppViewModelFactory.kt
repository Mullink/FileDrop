package com.liquorbee.mobile.core.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.liquorbee.mobile.LiquorBeeApplication
import com.liquorbee.mobile.auth.LoginViewModel
import com.liquorbee.mobile.lbcart.LbCartViewModel
import com.liquorbee.mobile.wholesale.WholesaleViewModel
import com.liquorbee.mobile.carts.ScanViewModel
import com.liquorbee.mobile.itemresolve.UnknownBarcodeViewModel
import com.liquorbee.mobile.home.HomeViewModel
import com.liquorbee.mobile.inventory.InventoryViewModel
import com.liquorbee.mobile.labelprinting.LabelPrintingViewModel
import com.liquorbee.mobile.picklists.PickListsViewModel
import com.liquorbee.mobile.pricecheck.PriceCheckViewModel
import com.liquorbee.mobile.scaninvoice.ScanInvoiceViewModel

class AppViewModelFactory(
    private val app: LiquorBeeApplication
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return when {
            modelClass.isAssignableFrom(LoginViewModel::class.java) ->
                LoginViewModel(app.apiService, app.tokenStore, onLoggedIn = { app.signIn() }) as T
            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(app.apiService, app, onLogout = { app.signOut() }, playLoginSplash = app.consumeJustLoggedIn()) as T
            modelClass.isAssignableFrom(PriceCheckViewModel::class.java) ->
                PriceCheckViewModel(app.apiService, app.catalogRepository) as T
            modelClass.isAssignableFrom(LabelPrintingViewModel::class.java) ->
                LabelPrintingViewModel(app.apiService) as T
            modelClass.isAssignableFrom(InventoryViewModel::class.java) ->
                InventoryViewModel(app.apiService, app) as T
            modelClass.isAssignableFrom(UnknownBarcodeViewModel::class.java) ->
                UnknownBarcodeViewModel(app.apiService, app.catalogRepository) as T
            modelClass.isAssignableFrom(ScanViewModel::class.java) ->
                ScanViewModel(app.catalogRepository, app.cartStore, app.restockRepository, app.reorderRepository, app.itemInsightsRepository) as T
            modelClass.isAssignableFrom(WholesaleViewModel::class.java) ->
                WholesaleViewModel(app.apiService, app.cartStore, app.catalogRepository, app.wholesaleAccess) as T
            modelClass.isAssignableFrom(LbCartViewModel::class.java) ->
                LbCartViewModel(app.apiService, app.cartStore, app.catalogRepository, app.reorderRepository) as T
            modelClass.isAssignableFrom(PickListsViewModel::class.java) ->
                PickListsViewModel(app.apiService) as T
            modelClass.isAssignableFrom(ScanInvoiceViewModel::class.java) ->
                ScanInvoiceViewModel(app.apiService, app) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
