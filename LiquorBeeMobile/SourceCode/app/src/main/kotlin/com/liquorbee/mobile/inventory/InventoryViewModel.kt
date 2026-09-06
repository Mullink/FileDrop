package com.liquorbee.mobile.inventory

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquorbee.mobile.core.network.ApiService
import com.liquorbee.mobile.core.network.CategorySessionItemRequest
import com.liquorbee.mobile.core.network.CreateCategorySessionRequest
import com.liquorbee.mobile.core.network.CreateNewItemInSessionRequest
import com.liquorbee.mobile.core.network.FinalizeReviewItemRequest
import com.liquorbee.mobile.core.network.InventoryCategoryDto
import com.liquorbee.mobile.core.network.InventoryHistoryDto
import com.liquorbee.mobile.core.network.InventorySessionHeaderDto
import com.liquorbee.mobile.core.network.InventorySessionItemNameDto
import com.liquorbee.mobile.core.network.LogItemInventorySessionRequest
import com.liquorbee.mobile.core.network.ReviewSessionItemDetailDto
import com.liquorbee.mobile.core.network.ReviewSessionItemDto
import com.liquorbee.mobile.core.network.SaveCategorySessionCountsRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

enum class InventoryView { SESSIONS, CATEGORY_SELECT, CATEGORY_ITEMS, CAPTURE, REVIEW }

// Mirrors ReviewSessionItemDetailDto but tracks the field the operator actually edits (finalQty)
// alongside its original server value, so "which rows changed" can be computed on Finalize the
// same way the web app's originalReviewItems/originalFinalQtyMap comparison does.
data class ReviewItemUi(
    val itemId: String,
    val itemName: String,
    val itemCode: String,
    val barcode: String,
    val qtyBefore: Int,
    val qtyAfter: Int,
    val finalQty: Int,
    val originalFinalQty: Int,
    val price: Double,
    val cog: Double,
    val qtyMatched: Int,
    val newItem: Int
)

// Mirrors the web app's CategoryInventoryItem - afterQty null/negative means "not yet counted".
data class CategoryInventoryItemUi(
    val itemId: String,
    val name: String,
    val sku: String,
    val upc: String,
    val beforeQty: Int,
    val afterQty: Int?
)

data class InventoryUiState(
    val view: InventoryView = InventoryView.SESSIONS,
    val sessionsLoading: Boolean = true,
    val sessions: List<InventorySessionHeaderDto> = emptyList(),
    val addedByName: String = "",
    val showAddedByDialog: Boolean = false,
    val zeroOutCurrentQty: Boolean = false,

    // Category select
    val categoriesLoading: Boolean = false,
    val availableCategories: List<InventoryCategoryDto> = emptyList(),
    val selectedCategoryIds: Set<String> = emptySet(),
    val categorySearchFilter: String = "",
    val newSessionName: String = "",
    val sessionCreating: Boolean = false,

    // Category items
    val activeSessionId: Int? = null,
    val categoryItemsLoading: Boolean = false,
    val categoryItems: List<CategoryInventoryItemUi> = emptyList(),
    val categoryItemsFilter: String = "",
    // Debounced ~250ms after typing settles - see LabelPrintingViewModel for why (a hardware
    // barcode scan into this same field would otherwise fire a full-list re-filter per keystroke).
    val appliedCategoryItemsFilter: String = "",
    val showOnlyUnfilled: Boolean = false,
    val categoryItemsSubmitting: Boolean = false,

    // Capture (full/"scratch" count session)
    val captureSessionId: Int? = null,
    val barcodeText: String = "",
    val captureLookupLoading: Boolean = false,
    val captureNotFound: Boolean = false,
    val capturedItem: InventorySessionItemNameDto? = null,
    val captureAction: String = "plus",
    val captureQtyText: String = "1",
    val captureSubmitting: Boolean = false,
    val showManualSearch: Boolean = false,
    val manualSearchQuery: String = "",
    val manualSearchResults: List<InventorySessionItemNameDto> = emptyList(),
    val showCreateNewItemDialog: Boolean = false,
    val newItemName: String = "",
    val newItemSku: String = "",
    val newItemUpc: String = "",
    val recentHistory: List<InventoryHistoryDto> = emptyList(),
    val showSessionOptionsDialog: Boolean = false,
    val showCancelConfirmDialog: Boolean = false,
    val showFinalizeConfirmDialog: Boolean = false,
    val captureFocusToken: Int = 0,

    // Review grid (UNDER REVIEW = editable, COMPLETED = read-only, PENDING SUBMISSION = summary)
    val reviewSessionId: Int = 0,
    val reviewReadOnly: Boolean = false,
    val reviewPendingSubmission: Boolean = false,
    val reviewLoading: Boolean = false,
    val reviewItems: List<ReviewItemUi> = emptyList(),
    val reviewSearchFilter: String = "",
    val reviewSubmitting: Boolean = false,
    val showFinalizeReviewConfirmDialog: Boolean = false,

    val message: String? = null
) {
    val filteredCategories: List<InventoryCategoryDto>
        get() {
            val q = categorySearchFilter.trim().lowercase()
            return if (q.isEmpty()) availableCategories
            else availableCategories.filter { it.name.orEmpty().lowercase().contains(q) }
        }

    val categoryItemsWithAfterQty: Int
        get() = categoryItems.count { it.afterQty != null && it.afterQty >= 0 }

    val filteredCategoryItems: List<CategoryInventoryItemUi>
        get() {
            var items = categoryItems
            if (showOnlyUnfilled) items = items.filter { it.afterQty == null || it.afterQty < 0 }
            val q = appliedCategoryItemsFilter.trim().lowercase()
            if (q.isNotEmpty()) {
                items = items.filter {
                    it.name.lowercase().contains(q) || it.sku.lowercase().contains(q) || it.upc.lowercase().contains(q)
                }
            }
            return items
        }

    val filteredReviewItems: List<ReviewItemUi>
        get() {
            val q = reviewSearchFilter.trim().lowercase()
            if (q.isEmpty()) return reviewItems
            return reviewItems.filter {
                it.itemName.lowercase().contains(q) || it.itemCode.lowercase().contains(q) || it.barcode.lowercase().contains(q)
            }
        }

    val changedReviewItemCount: Int
        get() = reviewItems.count { it.finalQty != it.originalFinalQty }
}

class InventoryViewModel(
    private val apiService: ApiService,
    appContext: Context
) : ViewModel() {

    private val prefs = appContext.getSharedPreferences("inventory_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    // No distinctUntilChanged, same reasoning as PriceCheckViewModel's UPC flow - a hardware scan
    // of the identical barcode twice in a row (e.g. after Log, or after a Create New Item detour)
    // must still trigger a fresh lookup.
    private val barcodeEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val categoryItemsFilterEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)

    init {
        _uiState.value = _uiState.value.copy(addedByName = prefs.getString(KEY_ADDED_BY, "") ?: "")
        loadSessions()

        @OptIn(kotlinx.coroutines.FlowPreview::class)
        viewModelScope.launch {
            barcodeEvents.debounce(150).collectLatest { barcode -> lookupBarcode(barcode) }
        }

        @OptIn(kotlinx.coroutines.FlowPreview::class)
        viewModelScope.launch {
            categoryItemsFilterEvents.debounce(250).collectLatest { text ->
                _uiState.value = _uiState.value.copy(appliedCategoryItemsFilter = text)
            }
        }
    }

    fun loadSessions() {
        _uiState.value = _uiState.value.copy(sessionsLoading = true)
        viewModelScope.launch {
            val sessions = runCatching { apiService.getInventorySessionHeaderData() }
                .getOrNull()
                ?.takeIf { it.isSuccessful }
                ?.body()
                .orEmpty()
                .sortedByDescending { it.sessionId }
            _uiState.value = _uiState.value.copy(sessions = sessions, sessionsLoading = false)
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    // ---- New session flow ----

    fun onNewSessionTapped() {
        if (_uiState.value.addedByName.isBlank()) {
            _uiState.value = _uiState.value.copy(showAddedByDialog = true)
        } else {
            openCategorySelect()
        }
    }

    fun onAddedByChanged(value: String) {
        _uiState.value = _uiState.value.copy(addedByName = value)
    }

    fun confirmAddedBy() {
        val name = _uiState.value.addedByName.trim()
        if (name.isBlank()) return
        prefs.edit().putString(KEY_ADDED_BY, name).apply()
        _uiState.value = _uiState.value.copy(addedByName = name, showAddedByDialog = false)
        openCategorySelect()
    }

    fun dismissAddedByDialog() {
        _uiState.value = _uiState.value.copy(showAddedByDialog = false)
    }

    private fun openCategorySelect() {
        _uiState.value = _uiState.value.copy(
            view = InventoryView.CATEGORY_SELECT,
            selectedCategoryIds = emptySet(),
            newSessionName = "",
            categorySearchFilter = ""
        )
        if (_uiState.value.availableCategories.isEmpty()) {
            loadCategories()
        }
    }

    fun createScratchSession() {
        val state = _uiState.value
        _uiState.value = state.copy(sessionCreating = true)
        viewModelScope.launch {
            try {
                val response = apiService.createInventorySession(state.zeroOutCurrentQty, state.addedByName)
                val sessionId = response.body()
                if (response.isSuccessful && sessionId != null) {
                    _uiState.value = _uiState.value.copy(sessionCreating = false)
                    openCaptureSession(sessionId)
                } else {
                    // Surface the server's actual reason (same approach as LabelPrintingViewModel.
                    // printSelected) instead of a generic message - swallowing this with
                    // runCatching{}.getOrNull() previously hid the real cause entirely.
                    val serverMessage = runCatching { response.errorBody()?.string() }.getOrNull()?.trim()
                    _uiState.value = _uiState.value.copy(
                        sessionCreating = false,
                        message = "Failed to create session (HTTP ${response.code()})" +
                            if (!serverMessage.isNullOrBlank()) ": $serverMessage" else ". Please try again."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    sessionCreating = false,
                    message = "Failed to create session: ${e.message ?: e::class.simpleName}"
                )
            }
        }
    }

    private fun loadCategories() {
        _uiState.value = _uiState.value.copy(categoriesLoading = true)
        viewModelScope.launch {
            val categories = runCatching { apiService.getInventoryCategories() }
                .getOrNull()
                ?.takeIf { it.isSuccessful }
                ?.body()
                .orEmpty()
            _uiState.value = _uiState.value.copy(availableCategories = categories, categoriesLoading = false)
        }
    }

    fun toggleCategorySelection(categoryId: String) {
        val current = _uiState.value.selectedCategoryIds
        _uiState.value = _uiState.value.copy(
            selectedCategoryIds = if (current.contains(categoryId)) current - categoryId else current + categoryId
        )
    }

    fun selectAllCategories() {
        _uiState.value = _uiState.value.copy(selectedCategoryIds = _uiState.value.availableCategories.map { it.categoryId }.toSet())
    }

    fun clearAllCategories() {
        _uiState.value = _uiState.value.copy(selectedCategoryIds = emptySet())
    }

    fun onCategorySearchChanged(value: String) {
        _uiState.value = _uiState.value.copy(categorySearchFilter = value)
    }

    fun onNewSessionNameChanged(value: String) {
        _uiState.value = _uiState.value.copy(newSessionName = value)
    }

    fun onZeroOutChanged(value: Boolean) {
        _uiState.value = _uiState.value.copy(zeroOutCurrentQty = value)
    }

    fun backToSessions() {
        _uiState.value = _uiState.value.copy(
            view = InventoryView.SESSIONS,
            activeSessionId = null,
            categoryItems = emptyList(),
            selectedCategoryIds = emptySet(),
            categoryItemsFilter = "",
            appliedCategoryItemsFilter = "",
            showOnlyUnfilled = false,
            captureSessionId = null,
            capturedItem = null,
            barcodeText = "",
            showCreateNewItemDialog = false,
            showSessionOptionsDialog = false,
            showCancelConfirmDialog = false,
            showFinalizeConfirmDialog = false,
            reviewItems = emptyList(),
            reviewSearchFilter = "",
            showFinalizeReviewConfirmDialog = false
        )
        loadSessions()
    }

    fun backToCategorySelect() {
        // Matches the web app: the session stays IN PROGRESS server-side, just navigate back -
        // nothing to save here since Category Items has its own explicit Save actions.
        _uiState.value = _uiState.value.copy(view = InventoryView.CATEGORY_SELECT, activeSessionId = null, categoryItems = emptyList())
    }

    fun proceedToLoadCategoryItems() {
        val state = _uiState.value
        if (state.selectedCategoryIds.isEmpty()) return

        _uiState.value = state.copy(sessionCreating = true)
        viewModelScope.launch {
            val request = CreateCategorySessionRequest(
                categoryIds = state.selectedCategoryIds.toList(),
                sessionName = state.newSessionName.trim(),
                createdBy = state.addedByName
            )
            try {
                val response = apiService.createCategorySession(state.zeroOutCurrentQty, request)
                val sessionId = response.body()
                if (response.isSuccessful && sessionId != null) {
                    _uiState.value = _uiState.value.copy(sessionCreating = false)
                    openCategorySessionForEdit(sessionId)
                } else {
                    val serverMessage = runCatching { response.errorBody()?.string() }.getOrNull()?.trim()
                    _uiState.value = _uiState.value.copy(
                        sessionCreating = false,
                        message = "Failed to create session (HTTP ${response.code()})" +
                            if (!serverMessage.isNullOrBlank()) ": $serverMessage" else ". Please try again."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    sessionCreating = false,
                    message = "Failed to create session: ${e.message ?: e::class.simpleName}"
                )
            }
        }
    }

    // ---- Category items ----

    fun openCategorySessionForEdit(sessionId: Int) {
        _uiState.value = _uiState.value.copy(
            view = InventoryView.CATEGORY_ITEMS,
            activeSessionId = sessionId,
            categoryItemsLoading = true,
            categoryItems = emptyList(),
            categoryItemsFilter = "",
            appliedCategoryItemsFilter = "",
            showOnlyUnfilled = false
        )
        viewModelScope.launch {
            val items = runCatching { apiService.getSessionReviewItems(sessionId) }
                .getOrNull()
                ?.takeIf { it.isSuccessful }
                ?.body()
                .orEmpty()
                .map {
                    CategoryInventoryItemUi(
                        itemId = it.itemId,
                        name = it.itemName.orEmpty(),
                        sku = it.itemCode.orEmpty(),
                        upc = it.barcode.orEmpty(),
                        beforeQty = it.qtyBefore,
                        afterQty = if (it.qtyAfter > 0) it.qtyAfter else null
                    )
                }
            _uiState.value = _uiState.value.copy(categoryItems = items, categoryItemsLoading = false)
        }
    }

    fun onCategoryItemsFilterChanged(value: String) {
        _uiState.value = _uiState.value.copy(categoryItemsFilter = value)
        viewModelScope.launch { categoryItemsFilterEvents.emit(value) }
    }

    fun toggleShowOnlyUnfilled() {
        _uiState.value = _uiState.value.copy(showOnlyUnfilled = !_uiState.value.showOnlyUnfilled)
    }

    fun onAfterQtyChanged(itemId: String, value: Int?) {
        _uiState.value = _uiState.value.copy(
            categoryItems = _uiState.value.categoryItems.map { if (it.itemId == itemId) it.copy(afterQty = value) else it }
        )
    }

    fun fillAllToZero() {
        _uiState.value = _uiState.value.copy(
            categoryItems = _uiState.value.categoryItems.map {
                if (it.afterQty == null || it.afterQty < 0) it.copy(afterQty = 0) else it
            }
        )
    }

    fun fillNominal() {
        _uiState.value = _uiState.value.copy(
            categoryItems = _uiState.value.categoryItems.map {
                if (it.afterQty == null || it.afterQty < 0) it.copy(afterQty = it.beforeQty) else it
            }
        )
    }

    fun submitCategoryInventory() {
        saveCategoryCounts(moveToReview = true)
    }

    fun saveCategoryInventoryForLater() {
        val state = _uiState.value
        val itemsWithQty = state.categoryItems.filter { it.afterQty != null && it.afterQty >= 0 }
        if (itemsWithQty.isEmpty() || state.activeSessionId == null) {
            backToSessions()
            return
        }
        saveCategoryCounts(moveToReview = false)
    }

    private fun saveCategoryCounts(moveToReview: Boolean) {
        val state = _uiState.value
        val sessionId = state.activeSessionId ?: return
        val itemsWithQty = state.categoryItems.filter { it.afterQty != null && it.afterQty >= 0 }
        if (itemsWithQty.isEmpty()) {
            _uiState.value = state.copy(message = "No items selected.")
            return
        }

        _uiState.value = state.copy(categoryItemsSubmitting = true)
        viewModelScope.launch {
            val request = SaveCategorySessionCountsRequest(
                sessionId = sessionId,
                items = itemsWithQty.map { CategorySessionItemRequest(it.itemId, it.afterQty!!) },
                moveToReview = moveToReview
            )
            val response = runCatching { apiService.saveCategorySessionCounts(request) }.getOrNull()
            if (response?.isSuccessful == true) {
                backToSessions()
            } else {
                _uiState.value = _uiState.value.copy(categoryItemsSubmitting = false, message = "Failed to save counts. Please try again.")
            }
        }
    }

    // ---- Session dispatch (mirrors the web app's openInvoiceDetails switch) ----

    fun openSession(session: InventorySessionHeaderDto) {
        when (session.status) {
            "IN PROGRESS" -> if (session.isCategorySession) {
                openCategorySessionForEdit(session.sessionId)
            } else {
                openCaptureSession(session.sessionId)
            }
            "UNDER REVIEW" -> openReview(session.sessionId, readOnly = false)
            "COMPLETED" -> openReview(session.sessionId, readOnly = true)
            "PENDING SUBMISSION" -> openReview(session.sessionId, readOnly = true, pendingSubmission = true)
            else -> _uiState.value = _uiState.value.copy(message = "This session (${session.status}) can't be opened here.")
        }
    }

    // ---- Capture (full/"scratch" count session) ----

    fun openCaptureSession(sessionId: Int) {
        _uiState.value = _uiState.value.copy(
            view = InventoryView.CAPTURE,
            captureSessionId = sessionId,
            barcodeText = "",
            capturedItem = null,
            captureNotFound = false,
            captureAction = "plus",
            captureQtyText = "1",
            showManualSearch = false,
            manualSearchQuery = "",
            manualSearchResults = emptyList()
        )
        loadRecentHistory()
    }

    fun onBarcodeChanged(value: String) {
        _uiState.value = _uiState.value.copy(barcodeText = value, captureNotFound = false)
        if (value.isNotBlank()) {
            viewModelScope.launch { barcodeEvents.emit(value) }
        }
    }

    private suspend fun lookupBarcode(barcode: String) {
        val sessionId = _uiState.value.captureSessionId ?: return
        _uiState.value = _uiState.value.copy(captureLookupLoading = true)
        val matches = runCatching { apiService.lookupInventoryItemByBarcode(sessionId, barcode) }
            .getOrNull()
            ?.takeIf { it.isSuccessful }
            ?.body()
            .orEmpty()

        if (matches.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(captureLookupLoading = false, capturedItem = matches.first(), captureNotFound = false)
        } else {
            _uiState.value = _uiState.value.copy(captureLookupLoading = false, capturedItem = null, captureNotFound = true)
            openCreateNewItemDialog(prefillUpc = barcode)
        }
    }

    fun toggleManualSearch() {
        _uiState.value = _uiState.value.copy(
            showManualSearch = !_uiState.value.showManualSearch,
            manualSearchQuery = "",
            manualSearchResults = emptyList()
        )
    }

    fun onManualSearchChanged(value: String) {
        _uiState.value = _uiState.value.copy(manualSearchQuery = value)
        val sessionId = _uiState.value.captureSessionId ?: return
        if (value.isBlank()) {
            _uiState.value = _uiState.value.copy(manualSearchResults = emptyList())
            return
        }
        viewModelScope.launch {
            val results = runCatching { apiService.searchInventoryItemsBySession(sessionId, value) }
                .getOrNull()
                ?.takeIf { it.isSuccessful }
                ?.body()
                .orEmpty()
            _uiState.value = _uiState.value.copy(manualSearchResults = results)
        }
    }

    fun selectItem(item: InventorySessionItemNameDto) {
        _uiState.value = _uiState.value.copy(
            capturedItem = item,
            captureNotFound = false,
            barcodeText = item.barcode ?: item.itemCode ?: "",
            showManualSearch = false,
            manualSearchQuery = "",
            manualSearchResults = emptyList()
        )
    }

    fun onCaptureActionChanged(action: String) {
        _uiState.value = _uiState.value.copy(captureAction = action)
    }

    fun onCaptureQtyChanged(value: String) {
        _uiState.value = _uiState.value.copy(captureQtyText = value)
    }

    fun submitCaptureEntry() {
        val state = _uiState.value
        val item = state.capturedItem
        val sessionId = state.captureSessionId
        val qty = state.captureQtyText.toIntOrNull()
        if (item == null || sessionId == null || qty == null || qty <= 0) {
            _uiState.value = state.copy(message = "Please select an item and enter a valid quantity.")
            return
        }

        _uiState.value = state.copy(captureSubmitting = true)
        viewModelScope.launch {
            val request = LogItemInventorySessionRequest(
                itemId = item.itemId,
                sessionId = sessionId,
                action = state.captureAction,
                quantity = qty,
                addedBy = state.addedByName
            )
            val response = runCatching { apiService.logItemInventorySession(request) }.getOrNull()
            if (response?.isSuccessful == true) {
                _uiState.value = _uiState.value.copy(
                    captureSubmitting = false,
                    capturedItem = null,
                    captureNotFound = false,
                    barcodeText = "",
                    captureAction = "plus",
                    captureQtyText = "1",
                    captureFocusToken = _uiState.value.captureFocusToken + 1,
                    message = "Logged ${item.itemName}."
                )
                loadRecentHistory()
            } else {
                _uiState.value = _uiState.value.copy(captureSubmitting = false, message = "Failed to log inventory. Please try again.")
            }
        }
    }

    private fun loadRecentHistory() {
        val sessionId = _uiState.value.captureSessionId ?: return
        viewModelScope.launch {
            val history = runCatching { apiService.getRecentInventorySubmissions(sessionId, "EVERYONE") }
                .getOrNull()
                ?.takeIf { it.isSuccessful }
                ?.body()
                .orEmpty()
            _uiState.value = _uiState.value.copy(recentHistory = history)
        }
    }

    fun openCreateNewItemDialog(prefillUpc: String = "") {
        _uiState.value = _uiState.value.copy(
            showCreateNewItemDialog = true,
            newItemName = "",
            newItemSku = "",
            newItemUpc = prefillUpc
        )
    }

    fun dismissCreateNewItemDialog() {
        _uiState.value = _uiState.value.copy(showCreateNewItemDialog = false)
    }

    fun onNewItemNameChanged(value: String) {
        _uiState.value = _uiState.value.copy(newItemName = value)
    }

    fun onNewItemSkuChanged(value: String) {
        _uiState.value = _uiState.value.copy(newItemSku = value)
    }

    fun onNewItemUpcChanged(value: String) {
        _uiState.value = _uiState.value.copy(newItemUpc = value)
    }

    fun submitNewItem() {
        val state = _uiState.value
        val sessionId = state.captureSessionId ?: return
        if (state.newItemName.isBlank()) {
            _uiState.value = state.copy(message = "Item Name is required.")
            return
        }

        val name = state.newItemName.trim()
        val sku = state.newItemSku.trim()
        val upc = state.newItemUpc.trim()

        viewModelScope.launch {
            val response = runCatching {
                apiService.createNewItemInSession(CreateNewItemInSessionRequest(sessionId, name, sku, upc))
            }.getOrNull()

            if (response?.isSuccessful == true) {
                _uiState.value = _uiState.value.copy(showCreateNewItemDialog = false, newItemName = "", newItemSku = "", newItemUpc = "")

                // The create endpoint doesn't hand back the new item's server-generated id - look
                // it back up by barcode/SKU (single indexed lookup) or, failing that, by name.
                val lookupCode = upc.ifBlank { sku }
                val matches = if (lookupCode.isNotBlank()) {
                    runCatching { apiService.lookupInventoryItemByBarcode(sessionId, lookupCode) }.getOrNull()?.takeIf { it.isSuccessful }?.body()
                } else {
                    runCatching { apiService.searchInventoryItemsBySession(sessionId, name) }.getOrNull()?.takeIf { it.isSuccessful }?.body()
                }
                matches?.firstOrNull()?.let { selectItem(it) }
            } else {
                _uiState.value = _uiState.value.copy(message = "Failed to create new item. Please try again.")
            }
        }
    }

    fun openSessionOptions() {
        _uiState.value = _uiState.value.copy(showSessionOptionsDialog = true)
    }

    fun dismissSessionOptions() {
        _uiState.value = _uiState.value.copy(showSessionOptionsDialog = false)
    }

    fun openCancelConfirm() {
        _uiState.value = _uiState.value.copy(showSessionOptionsDialog = false, showCancelConfirmDialog = true)
    }

    fun dismissCancelConfirm() {
        _uiState.value = _uiState.value.copy(showCancelConfirmDialog = false)
    }

    fun confirmCancelSession() {
        val sessionId = _uiState.value.captureSessionId ?: return
        viewModelScope.launch {
            runCatching { apiService.submitSessionStatus(sessionId, "CANCELLED") }
            backToSessions()
        }
    }

    fun openFinalizeConfirm() {
        _uiState.value = _uiState.value.copy(showSessionOptionsDialog = false, showFinalizeConfirmDialog = true)
    }

    fun dismissFinalizeConfirm() {
        _uiState.value = _uiState.value.copy(showFinalizeConfirmDialog = false)
    }

    fun confirmFinalizeSession() {
        val sessionId = _uiState.value.captureSessionId ?: return
        viewModelScope.launch {
            val response = runCatching { apiService.submitSessionStatus(sessionId, "COMPLETED") }.getOrNull()
            if (response?.isSuccessful == true) {
                backToSessions()
            } else {
                _uiState.value = _uiState.value.copy(showFinalizeConfirmDialog = false, message = "Failed to finalize session. Please try again.")
            }
        }
    }

    // ---- Review grid (UNDER REVIEW editable / COMPLETED read-only / PENDING SUBMISSION summary) ----

    fun openReview(sessionId: Int, readOnly: Boolean, pendingSubmission: Boolean = false) {
        _uiState.value = _uiState.value.copy(
            view = InventoryView.REVIEW,
            reviewSessionId = sessionId,
            reviewReadOnly = readOnly,
            reviewPendingSubmission = pendingSubmission,
            reviewLoading = true,
            reviewItems = emptyList(),
            reviewSearchFilter = ""
        )
        viewModelScope.launch {
            val items = runCatching { apiService.getSessionReviewItemDetails(sessionId) }
                .getOrNull()
                ?.takeIf { it.isSuccessful }
                ?.body()
                .orEmpty()
                .map {
                    ReviewItemUi(
                        itemId = it.itemId,
                        itemName = it.itemName.orEmpty(),
                        itemCode = it.itemCode.orEmpty(),
                        barcode = it.barcode.orEmpty(),
                        qtyBefore = it.qtyBefore,
                        qtyAfter = it.qtyAfter,
                        finalQty = it.finalQty,
                        originalFinalQty = it.finalQty,
                        price = it.price,
                        cog = it.cog,
                        qtyMatched = it.qtyMatched,
                        newItem = it.newItem
                    )
                }
            _uiState.value = _uiState.value.copy(reviewItems = items, reviewLoading = false)
        }
    }

    fun onReviewSearchChanged(value: String) {
        _uiState.value = _uiState.value.copy(reviewSearchFilter = value)
    }

    fun onFinalQtyChanged(itemId: String, value: Int?) {
        _uiState.value = _uiState.value.copy(
            reviewItems = _uiState.value.reviewItems.map { if (it.itemId == itemId) it.copy(finalQty = value ?: 0) else it }
        )
    }

    fun openFinalizeReviewConfirm() {
        _uiState.value = _uiState.value.copy(showFinalizeReviewConfirmDialog = true)
    }

    fun dismissFinalizeReviewConfirm() {
        _uiState.value = _uiState.value.copy(showFinalizeReviewConfirmDialog = false)
    }

    fun confirmFinalizeReview() {
        val state = _uiState.value
        val changed = state.reviewItems.filter { it.finalQty != it.originalFinalQty }

        // Matches the web app: the backend expects at least one row even when nothing actually
        // changed (a "No Change" placeholder record), rather than an empty array.
        val payload = if (changed.isNotEmpty()) {
            changed.map {
                FinalizeReviewItemRequest(
                    sessionId = state.reviewSessionId, itemId = it.itemId, itemName = it.itemName, itemCode = it.itemCode,
                    barcode = it.barcode, finalQty = it.finalQty, price = it.price, cog = it.cog, counted = true, overWriteQty = true
                )
            }
        } else {
            listOf(
                FinalizeReviewItemRequest(
                    sessionId = state.reviewSessionId, itemId = "0", itemName = "No Change", itemCode = "4444444",
                    barcode = "0000000000", finalQty = 0, price = 0.0, cog = 0.0, counted = true, overWriteQty = true
                )
            )
        }

        _uiState.value = state.copy(reviewSubmitting = true, showFinalizeReviewConfirmDialog = false)
        viewModelScope.launch {
            val response = runCatching { apiService.finalizeSessionItems(overwrite = true, items = payload) }.getOrNull()
            if (response?.isSuccessful == true) {
                backToSessions()
                _uiState.value = _uiState.value.copy(
                    message = "Session moved to Pending Submission. Inventory has NOT been updated yet - open it again and confirm to push quantities to your catalog."
                )
            } else {
                _uiState.value = _uiState.value.copy(reviewSubmitting = false, message = "Failed to save changes. Please try again.")
            }
        }
    }

    fun confirmCompleteSession() {
        val sessionId = _uiState.value.reviewSessionId
        _uiState.value = _uiState.value.copy(reviewSubmitting = true)
        viewModelScope.launch {
            val response = runCatching { apiService.completeSessionStatus(sessionId, updateInventory = true) }.getOrNull()
            if (response?.isSuccessful == true) {
                backToSessions()
                _uiState.value = _uiState.value.copy(message = "Session successfully marked as complete.")
            } else {
                _uiState.value = _uiState.value.copy(reviewSubmitting = false, message = "Failed to mark session as complete. Please try again.")
            }
        }
    }

    private companion object {
        const val KEY_ADDED_BY = "added_by_name"
    }
}
