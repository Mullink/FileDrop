package com.liquorbee.mobile.pricecheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquorbee.mobile.core.network.ApiService
import com.liquorbee.mobile.core.network.PriceCheckItemDto
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class PriceCheckUiState(
    val upcText: String = "",
    val upcLoading: Boolean = false,
    val notFound: Boolean = false,
    val selectedItem: PriceCheckItemDto? = null,
    val ambiguousMatches: List<PriceCheckItemDto> = emptyList(),
    val showAmbiguousDialog: Boolean = false,
    val nameSearchExpanded: Boolean = false,
    val nameQuery: String = "",
    val nameResults: List<PriceCheckItemDto> = emptyList(),
    val nameSearchLoading: Boolean = false,
    // Bumped on every Clear so the screen can re-request focus on the UPC field (a plain
    // StateFlow value flip isn't enough to distinguish "clear tapped again" from "no-op").
    val focusRequestToken: Int = 0
)

@OptIn(FlowPreview::class)
class PriceCheckViewModel(private val apiService: ApiService) : ViewModel() {

    private val _uiState = MutableStateFlow(PriceCheckUiState())
    val uiState: StateFlow<PriceCheckUiState> = _uiState.asStateFlow()

    // Plain SharedFlow (not StateFlow) for the UPC pipeline specifically because it must NOT
    // dedupe consecutive identical values - the web app's price-check page deliberately allows
    // re-scanning the exact same barcode twice in a row to re-trigger a lookup (e.g. after
    // Clear, or if the first scan's result was dismissed) rather than silently no-op'ing.
    private val upcEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val nameQueryEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)

    init {
        viewModelScope.launch {
            upcEvents.debounce(150).collectLatest { upc -> lookupUpc(upc) }
        }
        viewModelScope.launch {
            // Name search DOES dedupe - unlike a barcode scan, retyping the same text isn't a
            // meaningful new user action.
            nameQueryEvents.debounce(300).distinctUntilChanged().collectLatest { query -> searchByName(query) }
        }
    }

    fun onUpcChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            upcText = value,
            notFound = false,
            selectedItem = null
        )
        if (value.isNotBlank()) {
            viewModelScope.launch { upcEvents.emit(value) }
        }
    }

    private suspend fun lookupUpc(upc: String) {
        _uiState.value = _uiState.value.copy(upcLoading = true, notFound = false)
        val items = runCatching { apiService.lookupPriceCheckItemByUpc(upc) }
            .getOrNull()
            ?.takeIf { it.isSuccessful }
            ?.body()
            .orEmpty()

        _uiState.value = when {
            items.isEmpty() -> _uiState.value.copy(upcLoading = false, notFound = true, selectedItem = null)
            items.size == 1 -> _uiState.value.copy(upcLoading = false, notFound = false, selectedItem = items.first())
            else -> _uiState.value.copy(
                upcLoading = false,
                notFound = false,
                ambiguousMatches = items,
                showAmbiguousDialog = true
            )
        }
    }

    fun selectAmbiguousMatch(item: PriceCheckItemDto) {
        _uiState.value = _uiState.value.copy(selectedItem = item, showAmbiguousDialog = false)
    }

    fun dismissAmbiguousDialog() {
        // Closing WITHOUT picking clears the stale candidate list, matching the web app - so
        // reopening later (e.g. after a fresh scan) never shows an outdated set of matches.
        _uiState.value = _uiState.value.copy(showAmbiguousDialog = false, ambiguousMatches = emptyList())
    }

    fun toggleNameSearch() {
        _uiState.value = _uiState.value.copy(nameSearchExpanded = !_uiState.value.nameSearchExpanded)
    }

    fun onNameQueryChanged(value: String) {
        _uiState.value = _uiState.value.copy(nameQuery = value)
        if (value.isNotBlank()) {
            viewModelScope.launch { nameQueryEvents.emit(value) }
        } else {
            _uiState.value = _uiState.value.copy(nameResults = emptyList())
        }
    }

    private suspend fun searchByName(query: String) {
        _uiState.value = _uiState.value.copy(nameSearchLoading = true)
        val items = runCatching { apiService.searchPriceCheckItemsByName(query) }
            .getOrNull()
            ?.takeIf { it.isSuccessful }
            ?.body()
            .orEmpty()
        _uiState.value = _uiState.value.copy(nameSearchLoading = false, nameResults = items)
    }

    fun selectNameResult(item: PriceCheckItemDto) {
        _uiState.value = _uiState.value.copy(
            selectedItem = item,
            nameSearchExpanded = false,
            nameQuery = "",
            nameResults = emptyList()
        )
    }

    fun clear() {
        _uiState.value = PriceCheckUiState(focusRequestToken = _uiState.value.focusRequestToken + 1)
    }
}
