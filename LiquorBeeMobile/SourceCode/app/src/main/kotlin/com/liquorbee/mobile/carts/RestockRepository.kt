package com.liquorbee.mobile.carts

import com.liquorbee.mobile.core.network.AddRestockItemRequest
import com.liquorbee.mobile.core.network.PickListSource
import com.liquorbee.mobile.core.network.ApiService
import com.liquorbee.mobile.core.network.RestockItemDto
import com.liquorbee.mobile.core.network.SetRestockQtyRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RestockState(
    val items: List<RestockItemDto> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null
) {
    val count: Int get() = items.sumOf { it.qty }
    fun qtyFor(itemCode: String?): Int = items.firstOrNull { it.itemCode == itemCode }?.qty ?: 0
}

/** The store's shared restock list, kept server-side (Restock controller) so every device and the web see the same list. */
class RestockRepository(private val api: ApiService) {
    private val _state = MutableStateFlow(RestockState())
    val state: StateFlow<RestockState> = _state.asStateFlow()

    suspend fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        try {
            val response = api.getRestockItems()
            if (!response.isSuccessful) error(failure(response.code()))
            _state.value = RestockState(items = response.body().orEmpty(), loaded = true)
        } catch (e: Exception) {
            _state.value = _state.value.copy(loading = false, error = e.message ?: "Could not load the restock list.")
            throw e
        }
    }

    suspend fun add(itemCode: String, itemName: String?, upc: String?, qty: Int): RestockItemDto {
        val response = api.addRestockItem(AddRestockItemRequest(itemCode, itemName, upc, qty))
        val row = response.body()
        if (!response.isSuccessful || row == null) error(failure(response.code()))
        upsertLocal(row)
        return row
    }

    suspend fun setQty(id: Int, qty: Int) {
        val response = api.setRestockQty(SetRestockQtyRequest(id, qty))
        if (!response.isSuccessful) error(failure(response.code()))
        if (qty <= 0) removeLocal(id)
        else _state.value = _state.value.copy(items = _state.value.items.map { if (it.id == id) it.copy(qty = qty) else it })
    }

    suspend fun remove(id: Int) {
        val response = api.removeRestockItem(id)
        if (!response.isSuccessful && response.code() != 404) error(failure(response.code()))
        removeLocal(id)
    }

    suspend fun clear() {
        val response = api.clearRestock()
        if (!response.isSuccessful) error(failure(response.code()))
        _state.value = _state.value.copy(items = emptyList())
    }

    /** Id of the store's open Restock pick list, or null when there isn't one yet (the first Restock add creates it). */
    suspend fun openPickListId(): Int? {
        val response = api.getPickLists(status = "open", source = PickListSource.RESTOCK, take = 1)
        if (!response.isSuccessful) error(failure(response.code()))
        return response.body().orEmpty().firstOrNull { it.source.equals(PickListSource.RESTOCK, ignoreCase = true) }?.id
    }

    fun reset() { _state.value = RestockState() }

    private fun upsertLocal(row: RestockItemDto) {
        _state.value = _state.value.copy(items = listOf(row) + _state.value.items.filterNot { it.id == row.id })
    }

    private fun removeLocal(id: Int) {
        _state.value = _state.value.copy(items = _state.value.items.filterNot { it.id == id })
    }

    private fun failure(code: Int) = when (code) {
        404 -> "The restock list isn't available for your store yet."
        401, 403 -> "You don't have permission to change the restock list."
        else -> "Couldn't update the restock list. Try again."
    }
}
