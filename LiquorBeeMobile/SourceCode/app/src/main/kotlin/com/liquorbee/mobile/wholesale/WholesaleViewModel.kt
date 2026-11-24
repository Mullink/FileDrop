package com.liquorbee.mobile.wholesale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquorbee.mobile.carts.CartStore
import com.liquorbee.mobile.carts.CatalogRepository
import com.liquorbee.mobile.carts.WholesaleAccount
import com.liquorbee.mobile.core.network.ApiService
import com.liquorbee.mobile.core.network.CreateGeneralOrderDto
import com.liquorbee.mobile.core.network.CreatePickListItem
import com.liquorbee.mobile.core.network.CreatePickListRequest
import com.liquorbee.mobile.core.network.CreateSubCustomerDraftOrderDto
import com.liquorbee.mobile.core.network.PickListSource
import com.liquorbee.mobile.core.network.SubCustomerOrderItemDto
import com.liquorbee.mobile.core.network.WholesaleOrderDto
import com.liquorbee.mobile.core.ui.LbToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Response

enum class WholesaleTab { Cart, Orders }

enum class OrderFilter(val label: String) {
    Today("Today"), Open("Open"), Ready("Ready"), Closed("Closed"), All("All");

    fun matches(order: WholesaleOrderDto, today: String = todayKey()): Boolean = when (this) {
        Today -> orderLocalDay(order.createdDate) == today
        Open -> order.orderStatus == 0 || order.orderStatus == 1
        Ready -> order.orderStatus == 2
        Closed -> order.isClosed
        All -> true
    }
}

/** Today on this phone, yyyy-MM-dd. */
internal fun todayKey(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

/** Order dates come from the server in UTC (PlacedAt); null if unparseable. */
internal fun parseOrderDate(raw: String?): java.util.Date? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        // minSdk 24 without desugaring: java.time isn't available everywhere, so SimpleDateFormat.
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(raw.take(19))
    }.getOrNull()
}

/** The phone-local day an order was placed, yyyy-MM-dd. */
internal fun orderLocalDay(raw: String?): String? =
    parseOrderDate(raw)?.let { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(it) }

data class WholesaleUiState(
    val tab: WholesaleTab = WholesaleTab.Cart,
    val accounts: List<WholesaleAccount> = emptyList(),
    val accountsLoading: Boolean = false,
    val accountPickerOpen: Boolean = false,
    val confirmSubmit: Boolean = false,
    val confirmClear: Boolean = false,
    val notes: String = "",
    /** Also build a pick list from the order's lines when it's placed. */
    val buildPickList: Boolean = true,
    val working: Boolean = false,
    val error: String? = null,
    val toast: LbToast? = null,
    // ---- Orders ----
    val orders: List<WholesaleOrderDto> = emptyList(),
    val ordersLoading: Boolean = false,
    val ordersLoaded: Boolean = false,
    val ordersError: String? = null,
    val filter: OrderFilter = OrderFilter.Today
) {
    val filteredOrders: List<WholesaleOrderDto> get() { val today = todayKey(); return orders.filter { filter.matches(it, today) } }
}

/** Wholesale: build an order for the General Customer or a linked account, and see the store's orders. */
class WholesaleViewModel(
    private val api: ApiService,
    private val cartStore: CartStore,
    private val catalogRepository: CatalogRepository,
    private val access: WholesaleAccess
) : ViewModel() {
    private val _uiState = MutableStateFlow(WholesaleUiState())
    val uiState: StateFlow<WholesaleUiState> = _uiState.asStateFlow()
    val carts = cartStore.state

    init {
        // Orders paint from the list saved on this device (no network), then refresh in the
        // background while the Cart tab is showing, so the Orders tab is ready when opened.
        access.savedOrders()?.let { saved ->
            _uiState.value = _uiState.value.copy(orders = saved, ordersLoaded = true)
        }
        loadOrders()
    }

    fun selectTab(tab: WholesaleTab) {
        _uiState.value = _uiState.value.copy(tab = tab, error = null)
        if (tab == WholesaleTab.Orders && !_uiState.value.ordersLoaded && !_uiState.value.ordersLoading) loadOrders()
    }

    fun setFilter(filter: OrderFilter) { _uiState.value = _uiState.value.copy(filter = filter) }
    fun toastShown() { _uiState.value = _uiState.value.copy(toast = null) }

    // ---- who the order is for (same choice as Scan's long-press on Wholesale) ----

    fun openAccountPicker() {
        _uiState.value = _uiState.value.copy(accountPickerOpen = true)
        if (_uiState.value.accounts.isNotEmpty()) return
        _uiState.value = _uiState.value.copy(accountsLoading = true)
        viewModelScope.launch {
            val accounts = runCatching { catalogRepository.linkedAccounts() }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(accounts = accounts, accountsLoading = false)
        }
    }

    fun dismissAccountPicker() { _uiState.value = _uiState.value.copy(accountPickerOpen = false) }

    fun chooseAccount(account: WholesaleAccount) {
        cartStore.setDefaultAccount(account)
        _uiState.value = _uiState.value.copy(accountPickerOpen = false)
    }

    // ---- cart ----

    fun setQty(itemCode: String, qty: Int) = cartStore.setWholesaleQty(cartStore.state.value.currentAccount.key, itemCode, qty)
    fun onNotesChanged(v: String) { _uiState.value = _uiState.value.copy(notes = v) }
    fun setBuildPickList(v: Boolean) { _uiState.value = _uiState.value.copy(buildPickList = v) }
    fun askSubmit() { _uiState.value = _uiState.value.copy(confirmSubmit = true, error = null) }
    fun askClear() { _uiState.value = _uiState.value.copy(confirmClear = true) }
    fun dismissConfirm() { if (!_uiState.value.working) _uiState.value = _uiState.value.copy(confirmSubmit = false, confirmClear = false, error = null) }

    fun clearCart() {
        cartStore.clearWholesale(cartStore.state.value.currentAccount.key)
        _uiState.value = _uiState.value.copy(confirmClear = false)
    }

    fun submit() {
        val snapshot = cartStore.state.value
        val cart = snapshot.wholesaleCart(snapshot.currentAccount.key) ?: return
        if (cart.lines.isEmpty() || _uiState.value.working) return
        val notes = _uiState.value.notes.trim().ifBlank { null }
        val items = cart.lines.map { SubCustomerOrderItemDto(it.itemCode, it.qty, it.unitPrice) }
        _uiState.value = _uiState.value.copy(working = true, error = null)
        viewModelScope.launch {
            try {
                val account = cart.account
                val response = if (account.isGeneral) api.createGeneralOrder(CreateGeneralOrderDto(notes = notes, items = items))
                else api.createDraftOrder(CreateSubCustomerDraftOrderDto(
                    requestId = account.requestId ?: error("This account is missing its link id."),
                    subCustomerEmail = account.email, notes = notes, items = items))
                if (!response.isSuccessful) error(response.failureMessage("Order could not be created"))
                val placed = response.body()
                val label = placed?.orderNumber?.takeIf { it.isNotBlank() } ?: placed?.orderId
                cartStore.clearWholesale(account.key)
                // The order is already placed; a pick-list failure must not look like the order failed.
                val pickNote = if (!_uiState.value.buildPickList) "" else try {
                    val created = api.createPickList(CreatePickListRequest(
                        name = wholesalePickListName(label, account.label), source = PickListSource.WHOLESALE,
                        sourceRef = label?.takeIf { it.isNotBlank() }, notes = notes,
                        items = cart.lines.map { CreatePickListItem(it.itemCode, it.itemName, it.upc, it.qty) }))
                    if (created.isSuccessful) " Pick list created." else " The pick list couldn't be created."
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { " The pick list couldn't be created." }
                val sent = if (label.isNullOrBlank()) "Order placed for ${account.label}." else "Order $label placed for ${account.label}."
                _uiState.value = _uiState.value.copy(working = false, confirmSubmit = false, notes = "",
                    toast = LbToast(sent + pickNote), tab = WholesaleTab.Orders, filter = OrderFilter.Today)
                loadOrders()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(working = false, error = e.message ?: "Order could not be created.") }
        }
    }

    // ---- orders ----

    private var ordersJob: Job? = null

    /**
     * Three steps, each replacing the last: the list saved on this device (instant, set in init),
     * the server's last synced list (no POS round trip), then the live POS list, which can take a while.
     */
    fun loadOrders() {
        ordersJob?.cancel()
        _uiState.value = _uiState.value.copy(ordersLoading = true, ordersError = null)
        ordersJob = viewModelScope.launch {
            try {
                val cached = api.getWholesaleOrders(cached = true)
                val rows = cached.body()
                if (cached.isSuccessful && rows != null && _uiState.value.ordersLoading) {
                    // Stored rows carry no POS balance; keep a known one until the live list lands.
                    val known = _uiState.value.orders.associateBy { it.orderId }
                    val merged = rows.map { r -> known[r.orderId]?.takeIf { r.balance == 0.0 }?.let { r.copy(balance = it.balance) } ?: r }
                    _uiState.value = _uiState.value.copy(orders = merged.sortedByDescending { it.createdDate.orEmpty() }, ordersLoaded = true)
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            try {
                val response = api.getWholesaleOrders()
                if (!response.isSuccessful) error(response.failureMessage("Could not load orders"))
                val orders = response.body().orEmpty().sortedByDescending { it.createdDate.orEmpty() }
                _uiState.value = _uiState.value.copy(orders = orders, ordersLoading = false, ordersLoaded = true)
                access.saveOrders(orders)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                // Keep a cached list on screen; only a first load with nothing to show is an error.
                _uiState.value = _uiState.value.copy(ordersLoading = false, ordersError = e.message ?: "Could not load orders.",
                    toast = if (_uiState.value.ordersLoaded) LbToast("Couldn't refresh from the POS. Showing the last synced list.", isError = true) else null)
            }
        }
    }
}

/** Name shared by the pick list built at checkout and one sent from the order view, so each can find the other. */
internal fun wholesalePickListName(orderLabel: String?, customer: String?): String =
    listOfNotNull("Wholesale", orderLabel?.takeIf { it.isNotBlank() }?.let { "#$it" }, customer?.takeIf { it.isNotBlank() })
        .joinToString(" · ")

internal fun Response<*>.failureMessage(fallback: String): String {
    val body = runCatching { errorBody()?.string()?.trim()?.take(300) }.getOrNull()
    return if (!body.isNullOrBlank() && !body.startsWith("<")) body else "$fallback. Try again."
}
