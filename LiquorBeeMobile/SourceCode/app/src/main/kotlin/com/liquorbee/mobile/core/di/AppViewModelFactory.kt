package com.liquorbee.mobile.core.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.liquorbee.mobile.LiquorBeeApplication
import com.liquorbee.mobile.auth.LoginViewModel
import com.liquorbee.mobile.home.HomeViewModel
import com.liquorbee.mobile.inventory.InventoryViewModel
import com.liquorbee.mobile.labelprinting.LabelPrintingViewModel
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
                PriceCheckViewModel(app.apiService) as T
            modelClass.isAssignableFrom(LabelPrintingViewModel::class.java) ->
                LabelPrintingViewModel(app.apiService) as T
            modelClass.isAssignableFrom(InventoryViewModel::class.java) ->
                InventoryViewModel(app.apiService, app) as T
            modelClass.isAssignableFrom(ScanInvoiceViewModel::class.java) ->
                ScanInvoiceViewModel(app.apiService, app) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
