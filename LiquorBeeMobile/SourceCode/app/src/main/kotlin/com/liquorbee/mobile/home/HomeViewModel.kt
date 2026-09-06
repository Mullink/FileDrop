package com.liquorbee.mobile.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquorbee.mobile.core.network.ApiService
import com.liquorbee.mobile.core.network.DashboardSummaryDto
import com.liquorbee.mobile.core.network.TopSellersDto
import com.liquorbee.mobile.core.update.UpdateChecker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val UPDATE_CHECK_INTERVAL_MS = 5 * 60 * 1000L

data class HomeUiState(
    val showScanInvoice: Boolean = false,
    val showSalesDashboard: Boolean = true,
    val topSellers: TopSellersDto? = null,
    val topSellersLoading: Boolean = true,
    val dashboardSummary: DashboardSummaryDto? = null,
    val dashboardLoading: Boolean = true,
    val storeName: String? = null,
    // Only ever populated with something the dialog should actually show - a dismissed-for-this-
    // version UpdateAvailable, or any UpToDate/CheckFailed result from a background poll, never
    // reaches the UI (see checkForUpdate's announce/wasDismissed gating).
    val updateDialogResult: UpdateChecker.Result? = null
)

class HomeViewModel(
    private val apiService: ApiService,
    private val appContext: Context,
    val onLogout: () -> Unit,
    // Read once at construction time (see AppViewModelFactory.consumeJustLoggedIn) - whether to
    // play the post-login splash animation on this particular Home screen instance.
    val playLoginSplash: Boolean = false
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadToolsGating()
        loadDashboardData()
        loadStoreName()

        // Auto-checks every 5 minutes while Home is alive - same pattern as
        // LiquorBeeInvoiceScannerAndroid's HomeActivity. announce=false so this never interrupts
        // with an "Up to Date"/"Couldn't Check" dialog, only a genuine new update.
        viewModelScope.launch {
            while (isActive) {
                checkForUpdate(announce = false)
                delay(UPDATE_CHECK_INTERVAL_MS)
            }
        }
    }

    // announce=true (the manual "Check for Updates" menu item) always surfaces a result, even
    // Up to Date/Couldn't Check. The automatic background poll (announce=false) only ever shows a
    // dialog for a genuinely new update the user hasn't already dismissed (see
    // UpdateChecker.wasDismissed) - it never nags about being up to date.
    fun checkForUpdate(announce: Boolean) {
        viewModelScope.launch {
            when (val result = UpdateChecker.check(appContext)) {
                is UpdateChecker.Result.UpdateAvailable -> {
                    if (announce || !UpdateChecker.wasDismissed(appContext, result.latestVersionCode)) {
                        _uiState.value = _uiState.value.copy(updateDialogResult = result)
                    }
                }
                else -> if (announce) {
                    _uiState.value = _uiState.value.copy(updateDialogResult = result)
                }
            }
        }
    }

    fun dismissUpdateDialog(rememberDismissal: Boolean) {
        val result = _uiState.value.updateDialogResult
        if (rememberDismissal && result is UpdateChecker.Result.UpdateAvailable) {
            UpdateChecker.markDismissed(appContext, result.latestVersionCode)
        }
        _uiState.value = _uiState.value.copy(updateDialogResult = null)
    }

    private fun loadStoreName() {
        viewModelScope.launch {
            val storeName = runCatching { apiService.getAccountSettings() }
                .getOrNull()?.takeIf { it.isSuccessful }?.body()?.storeName
            _uiState.value = _uiState.value.copy(storeName = storeName)
        }
    }

    // Same gates the web home page uses (Login/GetCanSeeInvoiceOcrScan, Login/GetHideSalesDashboard)
    // so this app's Tools list and Sales Dashboard visibility always match the web app for a given
    // customer, with zero app-side configuration to keep in sync.
    private fun loadToolsGating() {
        viewModelScope.launch {
            // Matches web's ocrScanVisible getter (pos-purchase-orders.ts) exactly: gated only on
            // GetCanSeeInvoiceOcrScan. Web also ANDs a feature-profile check, but that fails open
            // to true when the feature isn't configured for a given State/ProductScope - it's not
            // an independent flag mobile can meaningfully re-check, so it's left out here rather
            // than adding a mismatched gate. GetIsHumanAutomationOn is a red herring: on web it only
            // hides the separate "Scanned Invoices" link, never this button - do not AND it in here.
            val canSeeOcrScan = runCatching { apiService.canSeeInvoiceOcrScan() }
                .getOrNull()?.takeIf { it.isSuccessful }?.body() == true
            _uiState.value = _uiState.value.copy(showScanInvoice = canSeeOcrScan)

            runCatching { apiService.getHideSalesDashboard() }
                .onSuccess { response ->
                    if (response.isSuccessful) {
                        _uiState.value = _uiState.value.copy(showSalesDashboard = response.body() != true)
                    }
                }
        }
    }

    private fun loadDashboardData() {
        viewModelScope.launch {
            val customerId = runCatching { apiService.getCurrentCustomerId() }
                .getOrNull()
                ?.takeIf { it.isSuccessful }
                ?.body()

            if (customerId == null) {
                _uiState.value = _uiState.value.copy(topSellersLoading = false, dashboardLoading = false)
                return@launch
            }

            runCatching { apiService.getLastWeekTopSellers(customerId) }
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        topSellers = if (response.isSuccessful) response.body() else null,
                        topSellersLoading = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(topSellersLoading = false)
                }

            runCatching { apiService.getQuanticDashboard(customerId) }
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        dashboardSummary = if (response.isSuccessful) response.body()?.summary else null,
                        dashboardLoading = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(dashboardLoading = false)
                }
        }
    }
}
