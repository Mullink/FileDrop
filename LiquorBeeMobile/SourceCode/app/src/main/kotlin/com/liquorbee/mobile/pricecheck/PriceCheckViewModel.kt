package com.liquorbee.mobile.pricecheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquorbee.mobile.core.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val SAVED_RETRIES = 4
private const val SAVED_RETRY_MS = 2500L

data class PriceCheckUiState(
    val editorOpen: Boolean = false,
    val editorLoading: Boolean = false,
    val editorItem: PriceCheckEditDto? = null,
    val vendors: List<PriceCheckVendorDto> = emptyList(),
    val categories: List<InventoryCategoryDto> = emptyList(),
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
    val focusRequestToken: Int = 0,
    /** Scan's margin suggestion: typed into the editor's price field (unsaved) when it opens. */
    val pendingPrice: Double? = null,
    /** The edited item's size from the item metadata, for the suggested "NAME (SIZE) - SKU" name. */
    val editorSize: String? = null,
    /** Barcode just created/linked from the not-found screen: never reopen that screen for it. */
    val savedBarcode: String? = null
)

class PriceCheckViewModel(
    private val apiService: ApiService,
    private val catalogRepository: com.liquorbee.mobile.carts.CatalogRepository? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(PriceCheckUiState())
    val uiState: StateFlow<PriceCheckUiState> = _uiState.asStateFlow()
    private var lookupJob: Job? = null
    private var searchJob: Job? = null
    private var editorJob: Job? = null
    /** Set when Scan's "Edit Item" opened the editor straight from the item's ItemId (no lookup). */
    private var directItemId: String? = null

    /** Scan's "Edit Item": open the editor for this ItemId right away, skipping the barcode lookup. */
    fun openForEdit(itemId: String, price: Double? = null) {
        directItemId = itemId
        _uiState.value = _uiState.value.copy(pendingPrice = price?.takeIf { it > 0 })
        loadEditor { itemId }
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
                var items = response.body().orEmpty()
                // UPCs are stored inconsistently (12-digit UPC-A vs 13-digit with a leading 0) and
                // this lookup is exact-match, so retry the other form before calling it not found.
                val trimmed = value.trim()
                val barcodeForms = if (trimmed.length >= 8 && trimmed.all { it.isDigit() })
                    listOf(trimmed, "0$trimmed", trimmed.removePrefix("0")).distinct() else emptyList()
                if (items.isEmpty()) {
                    for (variant in barcodeForms.drop(1)) {
                        val retry = apiService.lookupPriceCheckItemByUpc(variant)
                        if (retry.isSuccessful && !retry.body().isNullOrEmpty()) { items = retry.body().orEmpty(); break }
                    }
                }
                // Price Check's lookup only returns active, inventory-tracked items. An inactive or untracked
                // item is still the store's item: find it through the editor's lookup (which includes them)
                // and open it - the editor shows Inactive and can switch it back on.
                if (items.isEmpty()) {
                    for (variant in barcodeForms) {
                        val editable = runCatching { apiService.lookupEditableItem(variant) }.getOrNull()
                        val found = editable?.takeIf { it.isSuccessful }?.body().orEmpty().filter { !it.itemId.isNullOrBlank() }
                        if (found.isNotEmpty()) { items = found; break }
                    }
                }
                // Short codes: no exact match, so look in the store catalog for a UPC this code is a short
                // form of (UPC-E, or the full UPC with its middle zeros taken out) and open that item.
                if (items.isEmpty() && trimmed.length >= 6 && trimmed.all { it.isDigit() } && catalogRepository != null) {
                    val catalog = runCatching { catalogRepository.generalCatalog() }.getOrDefault(emptyList())
                    val storedUpcs = com.liquorbee.mobile.carts.matchBarcode(catalog, trimmed) { it.upc }
                        .mapNotNull { it.upc?.trim()?.takeIf { u -> u.isNotEmpty() } }.distinct()
                    for (upc in storedUpcs.take(3)) {
                        val editable = runCatching { apiService.lookupEditableItem(upc) }.getOrNull()
                        items = items + editable?.takeIf { it.isSuccessful }?.body().orEmpty().filter { !it.itemId.isNullOrBlank() }
                    }
                    items = items.distinctBy { it.itemId }
                }
                when (items.size) {
                    0 -> {
                        val search = apiService.searchPriceCheckItemsByName(value.trim())
                        if (!search.isSuccessful) error("Search failed")
                        // Same for typed names/SKUs: fall back to the editor's search, which includes inactive items.
                        val results = search.body().orEmpty().ifEmpty {
                            runCatching { apiService.searchEditableItems(value.trim()) }.getOrNull()
                                ?.takeIf { it.isSuccessful }?.body().orEmpty().filter { !it.itemId.isNullOrBlank() }
                        }
                        // Just created/linked: the item list can trail the POS by a few seconds, so retry
                        // rather than reporting a missing item that was just saved.
                        if (results.isEmpty() && trimmed == _uiState.value.savedBarcode && savedRetries < SAVED_RETRIES) {
                            savedRetries++
                            delay(SAVED_RETRY_MS)
                            onUpcChanged(value)
                            return@launch
                        }
                        val saved = results.isEmpty() && trimmed == _uiState.value.savedBarcode
                        _uiState.value = _uiState.value.copy(upcLoading = false, nameResults = results, notFound = results.isEmpty(),
                            saveMessage = if (saved) "Item saved. It shows up here once the POS sync catches up - scan it again in a minute." else _uiState.value.saveMessage)
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
        val item = _uiState.value.selectedItem
        if (item == null) { directItemId?.let { id -> loadEditor { id } }; return }
        loadEditor {
                item.itemId?.takeIf { it.isNotBlank() } ?: run {
                    val barcode = item.barcode?.takeIf { it.isNotBlank() }
                        ?: error("This item can't be edited from the phone. Edit it in your POS.")
                    val lookup = apiService.lookupEditableItem(barcode)
                    if (!lookup.isSuccessful) {
                        error(if (lookup.code() == 404) "Item editing isn't available for your store yet."
                            else "Could not load this item for editing. Please try again.")
                    }
                    val matches = lookup.body().orEmpty().filter {
                        it.barcode == barcode && it.itemCode == item.itemCode && !it.itemId.isNullOrBlank()
                    }
                    matches.singleOrNull()?.itemId
                        ?: error("More than one item matches. Search by name or SKU to pick the right one.")
                }
        }
    }

    private fun loadEditor(resolveId: suspend () -> String) {
        if (_uiState.value.editorLoading || _uiState.value.saving) return
        editorJob?.cancel()
        _uiState.value = _uiState.value.copy(editorOpen = true, editorLoading = true, editorItem = null, editorError = null, saveMessage = null)
        editorJob = viewModelScope.launch {
            try {
                val id = resolveId()
                coroutineScope {
                    val itemResponse = async { apiService.getPriceCheckEdit(id) }
                    val vendorResponse = async { apiService.getPriceCheckVendors() }
                    val categoryResponse = async { apiService.getPriceCheckCategories() }
                    val editable = itemResponse.await()
                    val vendors = vendorResponse.await()
                    val categories = categoryResponse.await()
                    if (!editable.isSuccessful || !vendors.isSuccessful || !categories.isSuccessful)
                        error("Could not load item details. Please try again.")
                    val loaded = editable.body() ?: error("No item details returned.")
                    _uiState.value = _uiState.value.copy(editorItem = loaded,
                        vendors = vendors.body().orEmpty(), categories = categories.body().orEmpty())
                    loadEditorSize(loaded)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(editorError = e.message ?: "Could not load item.") }
            finally { _uiState.value = _uiState.value.copy(editorLoading = false) }
        }
    }

    /** Best effort, off the editor's critical path: no size just means the suggestion leaves size out. */
    private fun loadEditorSize(item: PriceCheckEditDto) {
        val repo = catalogRepository ?: return
        viewModelScope.launch {
            val size = runCatching {
                repo.itemMetadata().find(SubCustomerCatalogItemDto(itemId = item.itemId, itemName = item.name, upc = item.upc))?.size
            }.getOrNull()
            if (_uiState.value.editorItem?.itemId == item.itemId) _uiState.value = _uiState.value.copy(editorSize = size)
        }
    }

    private var savedRetries = 0

    /** The not-found screen just created or linked this barcode: look it up (with retries) and open it. */
    fun onBarcodeSaved(barcode: String) {
        savedRetries = 0
        _uiState.value = _uiState.value.copy(savedBarcode = barcode.trim())
        onUpcChanged(barcode)
    }

    fun closeEditor() {
        if (_uiState.value.saving) return
        editorJob?.cancel(); lookupJob?.cancel(); searchJob?.cancel()
        _uiState.value = PriceCheckUiState(focusRequestToken = _uiState.value.focusRequestToken + 1)
    }

    fun saveItem(item: PriceCheckEditDto) {
        // A saved suggestion must not be typed in again when the editor reloads the saved item.
        _uiState.value = _uiState.value.copy(pendingPrice = null)
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
