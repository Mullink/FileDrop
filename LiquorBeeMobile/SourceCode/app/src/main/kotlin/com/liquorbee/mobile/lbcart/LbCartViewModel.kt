package com.liquorbee.mobile.lbcart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquorbee.mobile.carts.CartStore
import com.liquorbee.mobile.carts.CatalogRepository
import com.liquorbee.mobile.carts.LbLine
import com.liquorbee.mobile.carts.LbMarket
import com.liquorbee.mobile.carts.ReorderInfo
import com.liquorbee.mobile.carts.ReorderRepository
import com.liquorbee.mobile.carts.ReorderSettings
import com.liquorbee.mobile.core.network.ApiService
import com.liquorbee.mobile.core.network.SmartProductAddDto
import com.liquorbee.mobile.core.network.StageOrderLineRequest
import com.liquorbee.mobile.core.network.StagedOrderLineDto
import com.liquorbee.mobile.core.ui.LbToast
import com.liquorbee.mobile.wholesale.failureMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LbTab { Suggested, Cart }

/** One staged LB order (Products/GetAllStagedOrdersAsync rows grouped by order). */
data class StagedOrderSummary(val orderId: Int, val name: String, val createdDate: String?, val lines: List<StagedOrderLineDto>) {
    val cases: Int get() = lines.sumOf { it.caseQty }
}

enum class StagedDialog { Stage, Load }

data class LbCartUiState(
    val tab: LbTab = LbTab.Suggested,
    val market: String? = null,
    val restrictedPosUser: Boolean = false,
    val purchaseNow: Boolean = true,
    val search: String = "",
    val confirmSubmit: Boolean = false,
    val confirmClear: Boolean = false,
    val reorderSettingsOpen: Boolean = false,
    val stagedDialog: StagedDialog? = null,
    val stagedOrders: List<StagedOrderSummary> = emptyList(),
    val stagedLoading: Boolean = false,
    val stageTargetId: Int? = null,
    val stageName: String = "",
    val working: Boolean = false,
    val error: String? = null,
    val toast: LbToast? = null
) {
    /** Only Mississippi (MS-ABC-BAILMENT) checks out; everywhere else the LB cart is a plain list. Null = still loading. */
    val canCheckout: Boolean get() = market == LbMarket.MISSISSIPPI
    val isList: Boolean get() = market != null && market != LbMarket.MISSISSIPPI
}

/**
 * The LB Shopping Cart, working like the web Shopping Cart: the forecast / reorder-point report lists
 * what to order with suggested cases, you add cases to the cart, then stage it or send it to LiquorBee.
 */
class LbCartViewModel(
    private val api: ApiService,
    private val cartStore: CartStore,
    private val catalogRepository: CatalogRepository,
    private val reorderRepository: ReorderRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(LbCartUiState())
    val uiState: StateFlow<LbCartUiState> = _uiState.asStateFlow()
    val carts = cartStore.state
    val reorder = reorderRepository.state

    init {
        viewModelScope.launch {
            val market = runCatching { catalogRepository.market() }.getOrNull()
            // Outside Mississippi there's no report, so open straight on the list.
            _uiState.value = _uiState.value.copy(market = market,
                tab = if (market == LbMarket.MISSISSIPPI && cartStore.state.value.lb.isEmpty()) LbTab.Suggested else LbTab.Cart)
            if (market == LbMarket.MISSISSIPPI) {
                val restricted = runCatching { api.getRestrictedPosUser().body() == true }.getOrDefault(false)
                _uiState.value = _uiState.value.copy(restrictedPosUser = restricted)
            }
        }
        viewModelScope.launch { runCatching { reorderRepository.ensureLoaded() } }
    }

    fun selectTab(tab: LbTab) { _uiState.value = _uiState.value.copy(tab = tab, error = null) }
    fun onSearch(v: String) { _uiState.value = _uiState.value.copy(search = v) }
    fun toastShown() { _uiState.value = _uiState.value.copy(toast = null) }
    fun setPurchaseNow(v: Boolean) { _uiState.value = _uiState.value.copy(purchaseNow = v) }

    // ---- reorder setting (shared with Scan, remembered on the device) ----

    fun openReorderSettings() { _uiState.value = _uiState.value.copy(reorderSettingsOpen = true) }
    fun closeReorderSettings() { _uiState.value = _uiState.value.copy(reorderSettingsOpen = false) }
    fun saveReorderSettings(settings: ReorderSettings) {
        closeReorderSettings()
        viewModelScope.launch { runCatching { reorderRepository.updateSettings(settings) } }
    }
    fun reloadReorder() { viewModelScope.launch { runCatching { reorderRepository.ensureLoaded(force = true) } } }

    // ---- cart lines ----

    fun setCases(info: ReorderInfo, cases: Int) {
        val code = info.code.ifBlank { return }
        val current = cartStore.state.value.lbQty(code)
        val target = cases.coerceIn(0, 999)
        if (current == 0 && target > 0) cartStore.addLb(code, info.description, null, target) else cartStore.setLbQty(code, target)
    }

    fun setLineCases(code: String, cases: Int) = cartStore.setLbQty(code, cases.coerceIn(0, 999))

    /** "Add suggested": every flagged item not already in the cart, at its suggested cases. */
    fun addAllSuggested() {
        val inCart = cartStore.state.value.lb.map { it.code.uppercase() }.toSet()
        val toAdd = reorderRepository.state.value.reorderList.filter { it.code.isNotBlank() && it.code.uppercase() !in inCart }
        toAdd.forEach { cartStore.addLb(it.code, it.description, null, it.casesToOrder) }
        _uiState.value = _uiState.value.copy(toast = LbToast(if (toAdd.isEmpty()) "Every suggested item is already in the cart."
            else "Added ${toAdd.size} suggested ${if (toAdd.size == 1) "item" else "items"}."))
    }

    fun askSubmit() { _uiState.value = _uiState.value.copy(confirmSubmit = true, error = null) }
    fun askClear() { _uiState.value = _uiState.value.copy(confirmClear = true) }
    fun dismissConfirm() { if (!_uiState.value.working) _uiState.value = _uiState.value.copy(confirmSubmit = false, confirmClear = false, error = null) }
    fun clearCart() { cartStore.clearLb(); _uiState.value = _uiState.value.copy(confirmClear = false) }

    /** Mississippi checkout, the same AddSmartItems call the web Shopping Cart makes. */
    fun submit() {
        val lines = cartStore.state.value.lb
        val s = _uiState.value
        if (lines.isEmpty() || s.working) return
        if (!s.canCheckout) { _uiState.value = s.copy(error = "LB checkout is only available for MS-ABC-BAILMENT. This is a list only."); return }
        val totalCases = lines.sumOf { it.cases }
        // "Purchase now" only with 10+ cases (restricted POS users are always purchase-now).
        val purchaseNow = s.restrictedPosUser || (s.purchaseNow && totalCases >= LbMarket.MS_PURCHASE_NOW_MIN_CASES)
        work {
            val response = api.addSmartItems(lines.map {
                SmartProductAddDto(code = it.code, desiredQty = it.cases, minQty = it.cases, maxQty = it.cases, purchaseNow = purchaseNow)
            })
            if (!response.isSuccessful) error(response.failureMessage("Could not send to LiquorBee"))
            cartStore.clearLb()
            _uiState.value = _uiState.value.copy(working = false, confirmSubmit = false,
                toast = LbToast("Sent ${lines.size} ${if (lines.size == 1) "item" else "items"} ($totalCases cases) to LiquorBee."))
        }
    }

    // ---- staged orders (web Shopping Cart's "Stage Order" / "Start from") ----

    fun openStagedDialog(which: StagedDialog) {
        _uiState.value = _uiState.value.copy(stagedDialog = which, stageTargetId = null, stageName = "", error = null)
        _uiState.value = _uiState.value.copy(stagedLoading = true)
        viewModelScope.launch {
            try {
                val response = api.getStagedOrders()
                if (!response.isSuccessful) error(response.failureMessage("Could not load staged orders"))
                val orders = response.body().orEmpty().groupBy { it.orderId }.map { (id, rows) ->
                    StagedOrderSummary(id, rows.first().orderName?.takeIf { it.isNotBlank() } ?: "Order $id", rows.first().createdDate,
                        rows.filter { !it.code.isNullOrBlank() && it.caseQty > 0 })
                }.sortedByDescending { it.orderId }
                _uiState.value = _uiState.value.copy(stagedLoading = false, stagedOrders = orders)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(stagedLoading = false, error = e.message ?: "Could not load staged orders.") }
        }
    }

    fun dismissStagedDialog() { if (!_uiState.value.working) _uiState.value = _uiState.value.copy(stagedDialog = null, error = null) }
    fun setStageTarget(orderId: Int?) { _uiState.value = _uiState.value.copy(stageTargetId = orderId, error = null) }
    fun setStageName(name: String) { _uiState.value = _uiState.value.copy(stageName = name, error = null) }

    fun stageOrder() {
        val lines = cartStore.state.value.lb
        val s = _uiState.value
        if (lines.isEmpty()) return
        val target = s.stagedOrders.firstOrNull { it.orderId == s.stageTargetId }
        val name = target?.name ?: s.stageName.trim()
        if (target == null && name.isBlank()) { _uiState.value = s.copy(error = "Name the new order, or pick one to add to."); return }
        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        val body = lines.map { StageOrderLineRequest(orderId = target?.orderId ?: 0, orderName = name, code = it.code,
            caseQty = it.cases, name = it.itemName ?: it.code, createdDate = now) }
        work {
            val response = if (target == null) api.createStagedOrder(body) else api.addToStagedOrder(body)
            if (!response.isSuccessful) error(response.failureMessage("Could not stage the order"))
            cartStore.clearLb()
            _uiState.value = _uiState.value.copy(stagedDialog = null, working = false, error = null,
                toast = LbToast(if (target == null) "Staged as “$name”." else "Added to “$name”."))
        }
    }

    fun loadStagedOrder(order: StagedOrderSummary) {
        cartStore.replaceLb(order.lines.map { LbLine(code = it.code.orEmpty(), itemName = it.name, upc = null, cases = it.caseQty) })
        _uiState.value = _uiState.value.copy(stagedDialog = null, error = null, tab = LbTab.Cart,
            toast = LbToast("Loaded “${order.name}”."))
    }

    private fun work(block: suspend () -> Unit) {
        if (_uiState.value.working) return
        _uiState.value = _uiState.value.copy(working = true, error = null)
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(working = false, error = e.message ?: "Something went wrong. Try again.") }
        }
    }
}
