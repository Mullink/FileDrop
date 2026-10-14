package com.liquorbee.mobile.labelprinting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquorbee.mobile.core.network.ApiService
import com.liquorbee.mobile.core.network.InventoryCategoryDto
import com.liquorbee.mobile.core.network.LabelPrintingItemDto
import com.liquorbee.mobile.core.network.PrintLabelRequestItem
import com.liquorbee.mobile.core.network.QueuedLabelItemDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class LabelPrintingUiState(
    val items: List<LabelPrintingItemDto> = emptyList(),
    val loading: Boolean = true,
    val searchText: String = "",
    // What filteredItems actually filters by - only updated ~250ms after typing settles (see
    // LabelPrintingViewModel's debounced searchEvents pipeline). searchText updates immediately so
    // the text field itself never lags, but the (potentially 5-10k row) full-list scan below does
    // NOT re-run on every keystroke - that was the cause of an ANR when a hardware barcode scan
    // fired a dozen+ keystrokes in milliseconds, each one triggering a full re-filter + LazyColumn
    // recomposition synchronously on the main thread.
    val appliedSearchFilter: String = "",
    val qtyOnHandOnly: Boolean = false,
    val selectedFilter: Int = 0,
    val sizeFilter: Int? = null,
    val activeCategoryIds: Set<String> = emptySet(),
    val loadError: String? = null,
    val categoryError: String? = null,
    val selectedItemCodes: Set<String> = emptySet(),
    val syncCatalog: Boolean = true,
    val printing: Boolean = false,
    val queueCount: Int = 0,
    val queueOpen: Boolean = false,
    val queueLoading: Boolean = false,
    val queueItems: List<QueuedLabelItemDto> = emptyList(),
    val queueError: String? = null,
    val removingQueueId: Int? = null,
    val queueRemoveError: String? = null,
    val queuedSuccessfully: Boolean = false,
    val pricingByCardPrice: Boolean = false,
    val showCategoryDialog: Boolean = false,
    val categories: List<InventoryCategoryDto> = emptyList(),
    val categoriesLoading: Boolean = false,
    val selectedCategoryIds: Set<String> = emptySet(),
    val message: String? = null
) {
    // Search + qty filter are applied client-side against whatever list is currently loaded
    // (either a filter-code load or a by-category load), matching the web app's applyFilter -
    // the search box never triggers its own server call.
    val filteredItems: List<LabelPrintingItemDto>
        get() {
            var result = items
            if (qtyOnHandOnly) result = result.filter { it.qtyOnHand > 0 }
            val q = appliedSearchFilter.trim().lowercase()
            if (q.isNotEmpty()) {
                result = result.filter {
                    it.itemCode.lowercase().contains(q) ||
                        it.itemName.orEmpty().lowercase().contains(q) ||
                        it.upc.orEmpty().lowercase().contains(q)
                }
            }
            return result
        }
}

class LabelPrintingViewModel(private val apiService: ApiService) : ViewModel() {

    private val _uiState = MutableStateFlow(LabelPrintingUiState())
    val uiState: StateFlow<LabelPrintingUiState> = _uiState.asStateFlow()

    private var itemsJob: Job? = null
    private var queueJob: Job? = null
    private var requestId = 0

    private val searchEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)

    init {
        loadPricingSettings()
        loadItems()
        startQueuePolling()

        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            searchEvents.debounce(250).collectLatest { text ->
                _uiState.value = _uiState.value.copy(appliedSearchFilter = text)
            }
        }
    }

    private fun loadPricingSettings() {
        viewModelScope.launch {
            runCatching { apiService.getCardPricingSettings() }
                .getOrNull()
                ?.takeIf { it.isSuccessful }
                ?.body()
                ?.let { settings ->
                    _uiState.value = _uiState.value.copy(pricingByCardPrice = settings.pricingByCardPrice)
                }
        }
    }

    fun loadItems() {
        itemsJob?.cancel()
        val currentRequest = ++requestId
        val filters = _uiState.value
        _uiState.value = filters.copy(loading = true, loadError = null, items = emptyList(), selectedItemCodes = emptySet())
        itemsJob = viewModelScope.launch {
            try {
                val results = coroutineScope {
                    val requests = labelFilterCodes(filters.selectedFilter, filters.sizeFilter, filters.activeCategoryIds).map { code ->
                        async {
                            val response = apiService.getAllItemsForPrinting(code)
                            if (!response.isSuccessful) error("Filter request failed")
                            response.body() ?: error("No filter response")
                        }
                    }.toMutableList()
                    if (filters.activeCategoryIds.isNotEmpty()) requests += async {
                        val response = apiService.getItemsForPrintingByCategories(filters.activeCategoryIds.toList())
                        if (!response.isSuccessful) error("Category request failed")
                        response.body() ?: error("No category response")
                    }
                    requests.awaitAll()
                }
                if (currentRequest == requestId) _uiState.value = _uiState.value.copy(
                    items = intersectLabelResults(results), loading = false)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (currentRequest == requestId) _uiState.value = _uiState.value.copy(loading = false,
                    loadError = "Could not load items. Please try again.")
            }
        }
    }

    fun applyFilters(list: Int, size: Int?, inStock: Boolean, categories: Set<String>) {
        if (_uiState.value.printing) return
        _uiState.value = _uiState.value.copy(selectedFilter = list, sizeFilter = size,
            qtyOnHandOnly = inStock, activeCategoryIds = categories)
        loadItems()
    }

    fun ensureCategories() {
        if (_uiState.value.categories.isEmpty() && !_uiState.value.categoriesLoading) loadCategories()
    }

    fun onFilterSelected(filterCode: Int) {
        _uiState.value = _uiState.value.copy(selectedFilter = filterCode)
        loadItems()
    }

    fun onSearchChanged(value: String) {
        _uiState.value = _uiState.value.copy(searchText = value)
        viewModelScope.launch { searchEvents.emit(value) }
    }

    fun toggleQtyOnHandOnly() {
        _uiState.value = _uiState.value.copy(qtyOnHandOnly = !_uiState.value.qtyOnHandOnly)
    }

    fun onSyncToggled(value: Boolean) {
        _uiState.value = _uiState.value.copy(syncCatalog = value)
    }

    fun toggleItemSelection(itemCode: String) {
        val current = _uiState.value.selectedItemCodes
        _uiState.value = _uiState.value.copy(
            selectedItemCodes = if (current.contains(itemCode)) current - itemCode else current + itemCode
        )
    }

    fun selectAllVisible() {
        val visibleCodes = _uiState.value.filteredItems.map { it.itemCode }.toSet()
        _uiState.value = _uiState.value.copy(selectedItemCodes = _uiState.value.selectedItemCodes + visibleCodes)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedItemCodes = emptySet())
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null, queuedSuccessfully = false)
    }

    fun openQueue() {
        if (_uiState.value.removingQueueId != null) {
            _uiState.value = _uiState.value.copy(queueOpen = true)
            return
        }
        queueJob?.cancel()
        _uiState.value = _uiState.value.copy(queueOpen = true, queueLoading = true, queueError = null)
        queueJob = viewModelScope.launch {
            try {
                val response = apiService.getQueuedItems()
                if (!response.isSuccessful) error("Queue unavailable")
                val items = response.body() ?: error("Missing queue response")
                _uiState.value = _uiState.value.copy(queueItems = items, queueLoading = false)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                _uiState.value = _uiState.value.copy(queueLoading = false, queueError = "Could not load the print queue. Please try again.")
            }
        }
    }

    fun closeQueue() {
        queueJob?.cancel()
        _uiState.value = _uiState.value.copy(queueOpen = false, queueLoading = false)
    }

    fun removeQueuedItem(id: Int) {
        if (_uiState.value.removingQueueId != null || _uiState.value.queueLoading) return
        _uiState.value = _uiState.value.copy(removingQueueId = id, queueRemoveError = null)
        viewModelScope.launch {
            try {
                val response = apiService.removeQueuedItem(id)
                response.body()?.close()
                if (!response.isSuccessful) error("Remove failed")
                _uiState.value = _uiState.value.copy(
                    queueItems = _uiState.value.queueItems.filterNot { it.id == id })
                val count = apiService.getPrintQueueCount()
                if (count.isSuccessful) count.body()?.let {
                    _uiState.value = _uiState.value.copy(queueCount = it)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                _uiState.value = _uiState.value.copy(queueRemoveError = "Could not update the queue. Refresh and try again.")
            } finally {
                _uiState.value = _uiState.value.copy(removingQueueId = null)
            }
        }
    }

    fun printSelected() {
        val state = _uiState.value
        if (state.printing || state.loading || state.loadError != null) return
        val toPrint = state.items.filter { state.selectedItemCodes.contains(it.itemCode) }
        if (toPrint.isEmpty()) {
            _uiState.value = state.copy(message = "No items selected.")
            return
        }

        _uiState.value = state.copy(printing = true, message = null, queuedSuccessfully = false)
        viewModelScope.launch {
            val payload = toPrint.map {
                PrintLabelRequestItem(
                    itemCode = it.itemCode,
                    itemName = it.itemName,
                    displayName = it.itemName,
                    isSelected = true,
                    upc = it.upc,
                    sync = state.syncCatalog
                )
            }
            try {
                val response = apiService.printLabels(payload)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        printing = false,
                        selectedItemCodes = emptySet(),
                        queuedSuccessfully = true,
                        message = "Successfully queued. Your labels will print soon."
                    )
                    loadItems()
                } else {
                    // Surface the server's actual reason (same approach as LoginViewModel) rather
                    // than a generic message - a 500 here almost always carries a real exception
                    // message worth seeing instead of guessing blind.
                    val serverMessage = runCatching { response.errorBody()?.string() }.getOrNull()?.trim()
                    _uiState.value = _uiState.value.copy(
                        printing = false,
                        message = "Failed to process labels (HTTP ${response.code()})" +
                            if (!serverMessage.isNullOrBlank()) ": $serverMessage" else ". Please try again."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    printing = false,
                    message = "Failed to process labels: ${e.message ?: e::class.simpleName}"
                )
            }
        }
    }

    // ---- Print by Category ----

    fun openCategoryDialog() {
        _uiState.value = _uiState.value.copy(showCategoryDialog = true, selectedCategoryIds = emptySet())
        if (_uiState.value.categories.isEmpty()) {
            loadCategories()
        }
    }

    fun dismissCategoryDialog() {
        _uiState.value = _uiState.value.copy(showCategoryDialog = false)
    }

    private fun loadCategories() {
        _uiState.value = _uiState.value.copy(categoriesLoading = true, categoryError = null)
        viewModelScope.launch {
            try {
                val response = apiService.getInventoryCategories()
                if (!response.isSuccessful) error("Categories unavailable")
                _uiState.value = _uiState.value.copy(categories = response.body().orEmpty(), categoriesLoading = false)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _uiState.value = _uiState.value.copy(categoriesLoading = false, categoryError = "Could not load categories.") }
        }
    }

    fun toggleCategorySelection(categoryId: String) {
        val current = _uiState.value.selectedCategoryIds
        _uiState.value = _uiState.value.copy(
            selectedCategoryIds = if (current.contains(categoryId)) current - categoryId else current + categoryId
        )
    }

    fun loadItemsByCategory() {
        val state = _uiState.value
        _uiState.value = state.copy(showCategoryDialog = false)
        applyFilters(state.selectedFilter, state.sizeFilter, state.qtyOnHandOnly, state.selectedCategoryIds)
    }

    // ---- Print queue polling ----

    private fun startQueuePolling() {
        viewModelScope.launch {
            while (true) {
                val count = runCatching { apiService.getPrintQueueCount() }
                    .getOrNull()
                    ?.takeIf { it.isSuccessful }
                    ?.body()
                if (count != null) {
                    _uiState.value = _uiState.value.copy(queueCount = count)
                }
                delay(15_000)
            }
        }
    }
}
