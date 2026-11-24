package com.liquorbee.mobile.picklists

import com.liquorbee.mobile.core.network.EmailPickListRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquorbee.mobile.carts.CartStore
import com.liquorbee.mobile.carts.RestockRepository
import com.liquorbee.mobile.carts.upcMatches
import com.liquorbee.mobile.core.network.ApiService
import com.liquorbee.mobile.core.network.PickItemRequest
import com.liquorbee.mobile.core.network.PickListDto
import com.liquorbee.mobile.core.network.PickListItemDto
import com.liquorbee.mobile.core.network.PickListSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PickFilter(val label: String) { ToPick("To pick"), Missing("Missing"), Done("Done"), All("All") }

data class PickMessage(val text: String, val isError: Boolean)

data class PickListUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val list: PickListDto? = null,
    val query: String = "",
    val filter: PickFilter = PickFilter.ToPick,
    val message: PickMessage? = null,
    val working: Boolean = false,
    /** Item whose missing qty is being added to the LB cart. */
    val cartFor: PickListItemDto? = null,
    val focusToken: Int = 0,
    // ---- Email the list ----
    val emailOpen: Boolean = false,
    val emailTo: String = "",
    val emailNote: String = "",
    val emailSending: Boolean = false,
    val emailError: String? = null
) {
    val visibleItems: List<PickListItemDto>
        get() {
            val items = list?.items.orEmpty()
            val q = query.trim()
            val byText = if (q.isEmpty()) items else items.filter {
                it.itemName?.contains(q, true) == true || it.itemCode.contains(q, true) || it.upc?.contains(q, true) == true
            }
            return when (filter) {
                PickFilter.ToPick -> byText.filter { !it.isChecked && !it.isMissing }
                PickFilter.Missing -> byText.filter { it.isMissing }
                PickFilter.Done -> byText.filter { it.isChecked }
                PickFilter.All -> byText
            }
        }
}

/**
 * One pick list as a checklist. Scan an item to pick one more of it (checked once the full qty is
 * picked), or tap to check/uncheck. Items that aren't on the shelf can be marked missing and
 * added to the LB shopping cart.
 */
class PickListViewModel(
    private val api: ApiService,
    private val cartStore: CartStore,
    private val restockRepository: RestockRepository,
    private val listId: Int,
    /** Remembers the last recipient so the next email is one tap. */
    private val prefs: android.content.SharedPreferences? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(PickListUiState())
    val uiState: StateFlow<PickListUiState> = _uiState.asStateFlow()
    val carts = cartStore.state

    init { load() }

    fun load() {
        _uiState.value = _uiState.value.copy(loading = _uiState.value.list == null, error = null)
        viewModelScope.launch {
            try {
                val response = api.getPickList(listId)
                val list = response.body()
                if (!response.isSuccessful || list == null) error(failure(response.code()))
                _uiState.value = _uiState.value.copy(loading = false, list = list,
                    filter = if (list.isCompleted) PickFilter.All else _uiState.value.filter,
                    focusToken = _uiState.value.focusToken + 1)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(loading = false, error = e.message ?: "Could not load the pick list.") }
        }
    }

    fun setFilter(filter: PickFilter) { _uiState.value = _uiState.value.copy(filter = filter, focusToken = _uiState.value.focusToken + 1) }
    fun onQuery(value: String) { _uiState.value = _uiState.value.copy(query = value) }
    fun clearQuery() { _uiState.value = _uiState.value.copy(query = "", focusToken = _uiState.value.focusToken + 1) }
    fun refocus() { _uiState.value = _uiState.value.copy(focusToken = _uiState.value.focusToken + 1) }

    /** Enter / scanner: a UPC or SKU on this list picks one more of it. Anything else just filters. */
    fun onSubmit() {
        val code = _uiState.value.query.trim()
        if (code.isEmpty()) return
        val items = _uiState.value.list?.items.orEmpty()
        val hit = com.liquorbee.mobile.carts.matchBarcode(items, code) { it.upc }.firstOrNull() ?: items.firstOrNull { it.itemCode.equals(code, ignoreCase = true) }
        if (hit == null) {
            val visible = _uiState.value.visibleItems
            if (visible.isEmpty()) say("\"$code\" isn't on this pick list.", true)
            _uiState.value = _uiState.value.copy(query = if (visible.isEmpty()) "" else code, focusToken = _uiState.value.focusToken + 1)
            return
        }
        _uiState.value = _uiState.value.copy(query = "", focusToken = _uiState.value.focusToken + 1)
        if (hit.pickedQty >= hit.qty) { say("✓ ${name(hit)} is already picked (${hit.qty}).", false); return }
        val next = hit.pickedQty + 1
        setPicked(hit, next, isMissing = false,
            note = if (next >= hit.qty) "✓ Picked ${name(hit)}" else "${name(hit)}: $next of ${hit.qty} picked")
    }

    /** Tap: check the whole qty, or uncheck. */
    fun toggle(item: PickListItemDto) {
        if (item.isChecked) setPicked(item, 0, isMissing = false, note = null)
        else setPicked(item, item.qty, isMissing = false, note = null)
    }

    fun setPickedQty(item: PickListItemDto, qty: Int) = setPicked(item, qty.coerceIn(0, item.qty), isMissing = item.isMissing && qty < item.qty, note = null)

    fun markMissing(item: PickListItemDto, missing: Boolean) =
        setPicked(item, if (missing) item.pickedQty.coerceAtMost(item.qty - 1).coerceAtLeast(0) else item.pickedQty, isMissing = missing,
            note = if (missing) "${name(item)} marked missing" else null)

    private fun setPicked(item: PickListItemDto, pickedQty: Int, isMissing: Boolean, note: String?) {
        val list = _uiState.value.list ?: return
        if (list.isCompleted) { say("This pick list is completed. Reopen it to make changes.", true); return }
        val optimistic = item.copy(pickedQty = pickedQty, isMissing = isMissing, isChecked = pickedQty >= item.qty)
        replace(optimistic)
        note?.let { say(it, false) }
        viewModelScope.launch {
            try {
                val response = api.pickItem(list.id, item.id, PickItemRequest(pickedQty, isMissing))
                val saved = response.body()
                if (!response.isSuccessful || saved == null) error(failure(response.code()))
                replace(saved)
                if (list.source == PickListSource.RESTOCK) runCatching { restockRepository.refresh() }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                replace(item)
                say(e.message ?: "Could not save. Try again.", true)
            }
        }
    }

    private fun replace(item: PickListItemDto) {
        val list = _uiState.value.list ?: return
        val items = list.items.map { if (it.id == item.id) item else it }
        _uiState.value = _uiState.value.copy(list = list.copy(items = items,
            checkedCount = items.count { it.isChecked }, missingCount = items.count { it.isMissing },
            pickedQty = items.sumOf { it.pickedQty.coerceAtMost(it.qty) }))
    }

    // ---- Missing item -> LB shopping cart ----

    fun openAddToCart(item: PickListItemDto) { _uiState.value = _uiState.value.copy(cartFor = item) }
    fun dismissAddToCart() { _uiState.value = _uiState.value.copy(cartFor = null, focusToken = _uiState.value.focusToken + 1) }

    fun addToCart(cases: Int) {
        val item = _uiState.value.cartFor ?: return
        _uiState.value = _uiState.value.copy(cartFor = null)
        if (cases <= 0) return
        cartStore.addLb(item.itemCode, item.itemName, item.upc, cases)
        if (!item.isMissing) markMissing(item, true)
        say("✓ $cases ${if (cases == 1) "case" else "cases"} of ${name(item)} added to the LB cart", false)
        refocus()
    }

    // ---- Whole list ----

    fun complete() = listAction { id -> api.completePickList(id).also { if (!it.isSuccessful) error(failure(it.code())) } ; "Pick list completed" }
    fun reopen() = listAction { id -> api.reopenPickList(id).also { if (!it.isSuccessful) error(failure(it.code())) } ; "Pick list reopened" }

    private fun listAction(block: suspend (Int) -> String) {
        val list = _uiState.value.list ?: return
        _uiState.value = _uiState.value.copy(working = true)
        viewModelScope.launch {
            try {
                val text = block(list.id)
                _uiState.value = _uiState.value.copy(working = false)
                if (list.source == PickListSource.RESTOCK) runCatching { restockRepository.refresh() }
                load()
                say(text, false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _uiState.value = _uiState.value.copy(working = false)
                say(e.message ?: "Could not update the pick list.", true)
            }
        }
    }

    fun openEmail() {
        _uiState.value = _uiState.value.copy(emailOpen = true, emailError = null, emailNote = "",
            emailTo = _uiState.value.emailTo.ifBlank { prefs?.getString(KEY_LAST_EMAIL, null).orEmpty() })
    }

    fun dismissEmail() {
        if (_uiState.value.emailSending) return
        _uiState.value = _uiState.value.copy(emailOpen = false, emailError = null, focusToken = _uiState.value.focusToken + 1)
    }

    fun onEmailTo(v: String) { _uiState.value = _uiState.value.copy(emailTo = v, emailError = null) }
    fun onEmailNote(v: String) { _uiState.value = _uiState.value.copy(emailNote = v) }

    fun sendEmail() {
        val s = _uiState.value
        val list = s.list ?: return
        val to = s.emailTo.trim()
        if (s.emailSending) return
        if (to.isEmpty()) { _uiState.value = s.copy(emailError = "Enter an email address."); return }
        _uiState.value = s.copy(emailSending = true, emailError = null)
        viewModelScope.launch {
            try {
                val response = api.emailPickList(list.id, EmailPickListRequest(to, s.emailNote.trim().ifBlank { null }))
                if (!response.isSuccessful) {
                    // 400 carries the server's reason (e.g. a bad address).
                    val body = runCatching { response.errorBody()?.string()?.trim()?.take(200) }.getOrNull()
                    error(if (response.code() == 400 && !body.isNullOrBlank()) body else failure(response.code()))
                }
                prefs?.edit()?.putString(KEY_LAST_EMAIL, to)?.apply()
                _uiState.value = _uiState.value.copy(emailSending = false, emailOpen = false, focusToken = _uiState.value.focusToken + 1)
                say("Pick list emailed to $to", false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(emailSending = false, emailError = e.message ?: "Could not send the email.") }
        }
    }

    private companion object { const val KEY_LAST_EMAIL = "pick_list_last_email" }

    private fun say(text: String, isError: Boolean) { _uiState.value = _uiState.value.copy(message = PickMessage(text, isError)) }
    private fun name(item: PickListItemDto) = item.itemName ?: item.itemCode
}
