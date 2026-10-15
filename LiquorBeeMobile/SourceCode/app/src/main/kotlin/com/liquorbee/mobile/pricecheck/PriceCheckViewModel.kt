package com.liquorbee.mobile.pricecheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquorbee.mobile.core.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class PriceCheckUiState(
    val editorOpen: Boolean = false,
    val editorLoading: Boolean = false,
    val editorItem: PriceCheckEditDto? = null,
    val vendors: List<PriceCheckVendorDto> = emptyList(),
    val categories: List<InventoryCategoryDto> = emptyList(),
    val categoryWarning: String? = null,
    val categoriesLoading: Boolean = false,
    val editorError: String? = null,
    val saving: Boolean = false,
    val saveMessage: String? = null,
    val upcText: String = "",
    val upcLoading: Boolean = false,
    val notFound: Boolean = false,
    val lookupError: String? = null,
    val selectedItem: PriceCheckItemDto? = null,
    val ambiguousMatches: List<PriceCheckItemDto> = emptyList(),
    val showAmbiguousDialog: Boolean = false,
    val nameSearchExpanded: Boolean = false,
    val nameQuery: String = "",
    val nameResults: List<PriceCheckItemDto> = emptyList(),
    val nameSearchLoading: Boolean = false,
    val focusRequestToken: Int = 0
)

class PriceCheckViewModel(private val apiService: ApiService) : ViewModel() {
    private val _uiState = MutableStateFlow(PriceCheckUiState())
    val uiState: StateFlow<PriceCheckUiState> = _uiState.asStateFlow()
    private var lookupJob: Job? = null
    private var searchJob: Job? = null
    private var editorJob: Job? = null
    private var activeCategoriesCache: List<InventoryCategoryDto>? = null
    private var categoriesCheckedAt = 0L

    private suspend fun activeCategories(): List<InventoryCategoryDto> {
        val now = android.os.SystemClock.elapsedRealtime()
        activeCategoriesCache?.let { if (now - categoriesCheckedAt < 300_000) return it }
        val response = apiService.getPriceCheckCategories()
        if (!response.isSuccessful) error("Could not load categories. Please try again.")
        val categories = response.body() ?: error("No categories returned.")
        val limit = Semaphore(4)
        val checked = coroutineScope {
            categories.distinctBy { it.categoryId }.map { category -> async {
                limit.withPermit {
                    try {
                    // Categories are Quantic items; their item ID is exposed as categoryId.
                    val details = apiService.getPriceCheckEdit(category.categoryId)
                    if (!details.isSuccessful) error("Could not check category status. Please try again.")
                    val item = details.body() ?: error("No category details returned.")
                    if (item.itemId != category.categoryId) error("Category details did not match.")
                    category to item.status
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { category to null }
                }
            } }.awaitAll()
        }
        val active = checked.filter { it.second == 1 }.map { it.first }
        val unavailable = checked.count { it.second == null }
        _uiState.value = _uiState.value.copy(categoryWarning = if (unavailable > 0)
            "$unavailable categories could not be checked and are hidden. Reload to try again." else null)
        if (unavailable == 0) {
            activeCategoriesCache = active
            categoriesCheckedAt = android.os.SystemClock.elapsedRealtime()
        }
        return active
    }

    fun onUpcChanged(value: String) {
        if (_uiState.value.editorOpen) return
        lookupJob?.cancel()
        _uiState.value = _uiState.value.copy(upcText = value, notFound = false, lookupError = null,
            upcLoading = value.isNotBlank(), nameResults = emptyList(), selectedItem = null, ambiguousMatches = emptyList(), showAmbiguousDialog = false)
        if (value.isBlank()) return
        lookupJob = viewModelScope.launch {
            delay(300)
            try {
                val response = apiService.lookupPriceCheckItemByUpc(value.trim())
                if (!response.isSuccessful) error("Lookup failed")
                val items = response.body().orEmpty()
                when (items.size) {
                    0 -> {
                        val search = apiService.searchPriceCheckItemsByName(value.trim())
                        if (!search.isSuccessful) error("Search failed")
                        val results = search.body().orEmpty()
                        _uiState.value = _uiState.value.copy(upcLoading = false, nameResults = results, notFound = results.isEmpty())
                    }
                    1 -> selectItem(items.first())
                    else -> _uiState.value = _uiState.value.copy(upcLoading = false, ambiguousMatches = items, showAmbiguousDialog = true)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _uiState.value = _uiState.value.copy(upcLoading = false, lookupError = "Could not look up this item. Check your connection and try again.") }
        }
    }

    private fun selectItem(item: PriceCheckItemDto) {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(selectedItem = item, showAmbiguousDialog = false,
            ambiguousMatches = emptyList(), nameSearchExpanded = false, nameQuery = "", nameResults = emptyList(),
            upcLoading = false, nameSearchLoading = false, lookupError = null, notFound = false)
        editItem()
    }

    fun selectAmbiguousMatch(item: PriceCheckItemDto) {
        if (_uiState.value.editorOpen) return
        lookupJob?.cancel()
        selectItem(item)
    }

    fun dismissAmbiguousDialog() {
        _uiState.value = _uiState.value.copy(showAmbiguousDialog = false, ambiguousMatches = emptyList())
    }

    fun toggleNameSearch() { _uiState.value = _uiState.value.copy(nameSearchExpanded = !_uiState.value.nameSearchExpanded) }

    fun onNameQueryChanged(value: String) {
        if (_uiState.value.editorOpen) return
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(nameQuery = value, nameResults = emptyList(), nameSearchLoading = value.isNotBlank(), lookupError = null)
        if (value.isBlank()) return
        searchJob = viewModelScope.launch {
            delay(300)
            try {
                val response = apiService.searchPriceCheckItemsByName(value.trim())
                if (!response.isSuccessful) error("Search failed")
                _uiState.value = _uiState.value.copy(nameSearchLoading = false, nameResults = response.body().orEmpty())
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _uiState.value = _uiState.value.copy(nameSearchLoading = false, lookupError = "Could not search items. Please try again.") }
        }
    }

    fun selectNameResult(item: PriceCheckItemDto) {
        if (_uiState.value.editorOpen) return
        lookupJob?.cancel()
        selectItem(item)
    }

    fun clear() {
        if (_uiState.value.editorOpen) return
        lookupJob?.cancel(); searchJob?.cancel()
        _uiState.value = PriceCheckUiState(focusRequestToken = _uiState.value.focusRequestToken + 1)
    }

    fun editItem() {
        val item = _uiState.value.selectedItem ?: return
        if (_uiState.value.editorLoading || _uiState.value.saving) return
        editorJob?.cancel()
        _uiState.value = _uiState.value.copy(editorOpen = true, editorLoading = true, editorItem = null, editorError = null, saveMessage = null)
        editorJob = viewModelScope.launch {
            try {
                val id = item.itemId?.takeIf { it.isNotBlank() } ?: run {
                    val barcode = item.barcode?.takeIf { it.isNotBlank() }
                        ?: error("This search result has no item ID or barcode. Update the server to enable editing.")
                    val lookup = apiService.lookupEditableItem(barcode)
                    if (!lookup.isSuccessful) {
                        error(if (lookup.code() == 404) "Item editing is not available on this server yet. The updated backend must be deployed."
                            else "Could not load this item for editing. Please try again.")
                    }
                    val matches = lookup.body().orEmpty().filter {
                        it.barcode == barcode && it.itemCode == item.itemCode && !it.itemId.isNullOrBlank()
                    }
                    matches.singleOrNull()?.itemId
                        ?: error("Could not uniquely identify this item for editing. Update the server to include item IDs in search results.")
                }
                coroutineScope {
                    val itemResponse = async { apiService.getPriceCheckEdit(id) }
                    val vendorResponse = async { apiService.getPriceCheckVendors() }
                    val editable = itemResponse.await()
                    val vendors = vendorResponse.await()
                    if (!editable.isSuccessful || !vendors.isSuccessful)
                        error("Could not load item details. Please try again.")
                    _uiState.value = _uiState.value.copy(editorItem = editable.body() ?: error("No item details returned."),
                        vendors = vendors.body().orEmpty(), editorLoading = false,
                        categories = emptyList(), categoriesLoading = true, categoryWarning = null)
                    try {
                        val categories = activeCategories()
                        _uiState.value = _uiState.value.copy(categories = categories)
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) {
                        _uiState.value = _uiState.value.copy(categoryWarning = "Could not check categories. Reload to try again.")
                    } finally {
                        _uiState.value = _uiState.value.copy(categoriesLoading = false)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(editorError = e.message ?: "Could not load item.") }
            finally { _uiState.value = _uiState.value.copy(editorLoading = false) }
        }
    }

    fun closeEditor() {
        if (_uiState.value.saving) return
        editorJob?.cancel(); lookupJob?.cancel(); searchJob?.cancel()
        _uiState.value = PriceCheckUiState(focusRequestToken = _uiState.value.focusRequestToken + 1)
    }

    fun saveItem(item: PriceCheckEditDto) {
        if (_uiState.value.saving || item.itemId != _uiState.value.editorItem?.itemId) return
        _uiState.value = _uiState.value.copy(saving = true, editorError = null, saveMessage = null)
        viewModelScope.launch {
            try {
                val response = apiService.savePriceCheckEdit(item)
                if (!response.isSuccessful) {
                    val message = when (response.code()) {
                        400, 409 -> response.errorBody()?.string()?.take(500)
                        401, 403 -> "You do not have permission to update this item."
                        else -> "The save could not be confirmed. Reload the item before retrying."
                    }
                    error(message ?: "Could not save this item.")
                }
                val saved = response.body() ?: error("The save could not be confirmed. Reload the item.")
                _uiState.value = _uiState.value.copy(editorItem = saved, editorOpen = true,
                    upcText = saved.upc, nameResults = emptyList(), ambiguousMatches = emptyList(), saveMessage = "Changes saved")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(editorError = e.message ?: "Could not save. Your edits are still here.") }
            finally { _uiState.value = _uiState.value.copy(saving = false) }
        }
    }
}
