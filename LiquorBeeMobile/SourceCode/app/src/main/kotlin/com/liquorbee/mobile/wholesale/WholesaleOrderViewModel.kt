package com.liquorbee.mobile.wholesale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquorbee.mobile.core.network.ApiService
import com.liquorbee.mobile.core.network.CreatePickListItem
import com.liquorbee.mobile.core.network.CreatePickListRequest
import com.liquorbee.mobile.core.network.PickListSource
import com.liquorbee.mobile.core.network.WholesaleOrderDto
import com.liquorbee.mobile.core.ui.LbToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WholesaleOrderUiState(
    val order: WholesaleOrderDto? = null,
    val loading: Boolean = true,
    val error: String? = null,
    /** The order's existing Wholesale pick list, if one was already made (at checkout or from here). */
    val pickListId: Int? = null,
    val sending: Boolean = false,
    val toast: LbToast? = null
)

/** One wholesale order with its lines; can be sent to Pick Lists. */
class WholesaleOrderViewModel(
    private val api: ApiService,
    private val catalogRepository: com.liquorbee.mobile.carts.CatalogRepository,
    private val orderId: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(WholesaleOrderUiState())
    val uiState: StateFlow<WholesaleOrderUiState> = _uiState.asStateFlow()

    init { load() }

    fun toastShown() { _uiState.value = _uiState.value.copy(toast = null) }

    fun load() {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val response = api.getWholesaleOrderDetail(orderId)
                if (!response.isSuccessful) error(response.failureMessage("Could not load the order"))
                val order = response.body() ?: error("Order not found.")
                _uiState.value = _uiState.value.copy(order = order, loading = false, pickListId = findPickList(order))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(loading = false, error = e.message ?: "Could not load the order.") }
        }
    }

    private suspend fun findPickList(order: WholesaleOrderDto): Int? = runCatching {
        val label = order.label.takeIf { it.isNotBlank() } ?: return null
        api.getPickLists(status = "all", source = PickListSource.WHOLESALE, search = label).body().orEmpty()
            .firstOrNull { it.sourceRef.equals(label, ignoreCase = true) }?.id
    }.getOrNull()

    fun sendToPickList(onCreated: (Int) -> Unit) {
        val order = _uiState.value.order ?: return
        if (_uiState.value.sending) return
        _uiState.value = _uiState.value.copy(sending = true)
        viewModelScope.launch {
            try {
                // Order lines are keyed by POS ItemId and only sometimes carry the SKU; the store catalog
                // fills in SKU + UPC so the pick list can be checked off by scanning.
                val byItemId = runCatching { catalogRepository.generalCatalog() }.getOrDefault(emptyList())
                    .filter { !it.itemId.isNullOrBlank() }.associateBy { it.itemId!!.trim() }
                val lines = order.items.orEmpty().mapNotNull { line ->
                    val match = line.itemId?.trim()?.let { byItemId[it] }
                    val code = line.itemCode?.takeIf { it.isNotBlank() } ?: match?.itemCode?.takeIf { it.isNotBlank() }
                        ?: line.itemId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val qty = Math.round(line.quantity).toInt().takeIf { it > 0 } ?: return@mapNotNull null
                    CreatePickListItem(code, line.itemName ?: match?.itemName, match?.upc?.takeIf { it.isNotBlank() }, qty)
                }
                if (lines.isEmpty()) error("This order has no items to pick.")
                val response = api.createPickList(CreatePickListRequest(
                    name = wholesalePickListName(order.label, order.customerName), source = PickListSource.WHOLESALE,
                    sourceRef = order.label.takeIf { it.isNotBlank() }, notes = order.notes, items = lines))
                val created = response.body()
                if (!response.isSuccessful || created == null) error(response.failureMessage("Could not create the pick list"))
                _uiState.value = _uiState.value.copy(sending = false, pickListId = created.id, toast = LbToast("Pick list created."))
                onCreated(created.id)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(sending = false, toast = LbToast(e.message ?: "Could not create the pick list.", isError = true)) }
        }
    }
}
