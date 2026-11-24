package com.liquorbee.mobile.picklists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquorbee.mobile.core.network.ApiService
import com.liquorbee.mobile.core.network.PickListSummaryDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PickListStatus(val api: String, val label: String) {
    Open("open", "Open"), Completed("completed", "Completed"), All("all", "All")
}

/** How far back to look. Defaults to the last 30 days so the screen opens on recent, unfinished work. */
enum class PickListRange(val days: Int?, val label: String) {
    Week(7, "7 days"), Month(30, "30 days"), Any(null, "Any time")
}

data class PickListsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val lists: List<PickListSummaryDto> = emptyList(),
    val status: PickListStatus = PickListStatus.Open,
    val source: String? = null,
    val range: PickListRange = PickListRange.Month,
    val search: String = ""
)

/** The Pick Lists section: every list for the store (Restock, wholesale orders, Enterprise lists) with filters. */
class PickListsViewModel(private val api: ApiService) : ViewModel() {
    private val _uiState = MutableStateFlow(PickListsUiState())
    val uiState: StateFlow<PickListsUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init { refresh() }

    fun refresh(debounce: Boolean = false) {
        loadJob?.cancel()
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        val s = _uiState.value
        loadJob = viewModelScope.launch {
            if (debounce) delay(300)
            try {
                val response = api.getPickLists(status = s.status.api, source = s.source,
                    search = s.search.trim().ifBlank { null }, days = s.range.days)
                if (!response.isSuccessful) error(failure(response.code()))
                _uiState.value = _uiState.value.copy(loading = false, lists = response.body().orEmpty())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _uiState.value = _uiState.value.copy(loading = false, error = e.message ?: "Could not load pick lists.")
            }
        }
    }

    fun setStatus(status: PickListStatus) { _uiState.value = _uiState.value.copy(status = status); refresh() }
    fun setSource(source: String?) { _uiState.value = _uiState.value.copy(source = source); refresh() }
    fun setRange(range: PickListRange) { _uiState.value = _uiState.value.copy(range = range); refresh() }
    fun setSearch(text: String) { _uiState.value = _uiState.value.copy(search = text); refresh(debounce = true) }
}

internal fun failure(code: Int): String = when (code) {
    401, 403 -> "Your session has expired. Sign in again."
    404 -> "That pick list no longer exists."
    409 -> "This pick list is completed. Reopen it to make changes."
    else -> "Something went wrong. Try again."
}
