package com.liquorbee.mobile.carts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquorbee.mobile.core.network.SubCustomerCatalogItemDto
import com.liquorbee.mobile.itemresolve.ItemMatching
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScanMessage(val text: String, val isError: Boolean)

/**
 * POS UPCs are stored inconsistently (e.g. "0096749800858" with an extra leading zero, or
 * 12-digit UPC-A, sometimes without the check digit), while scanners send UPC-A/EAN as read.
 * Compare digits only with leading zeros ignored, and also accept a missing trailing check digit
 * on either side.
 */
internal fun upcMatches(stored: String?, scanned: String): Boolean {
    if (stored.isNullOrBlank()) return false
    if (stored.trim().equals(scanned.trim(), ignoreCase = true)) return true
    val a = stored.filter { it.isDigit() }.trimStart('0')
    val b = scanned.filter { it.isDigit() }.trimStart('0')
    if (a.length < 6 || b.length < 6) return false
    // Only compare digit-only values - "MISSING-10001290" style codes shouldn't match by digits.
    if (stored.trim().any { !it.isDigit() } || scanned.trim().any { !it.isDigit() }) return false
    return a == b || a == b.dropLast(1) || b == a.dropLast(1)
}

/** Digits only, leading zeros dropped, and every run of 3+ zeros removed: "87000007093" -> "877093". */
internal fun squeezeZeros(code: String): String =
    code.filter { it.isDigit() }.trimStart('0').replace(Regex("0{3,}"), "")

/**
 * A standard UPC-E short code (6 digits, or 8 with number system and check digit) expanded to its
 * UPC-A form without the check digit, e.g. "08770903" -> "08700000709". Null when it isn't UPC-E shaped.
 */
internal fun upcEToUpcA(code: String): String? {
    val d = code.trim()
    if (d.any { !it.isDigit() }) return null
    val (ns, body) = when (d.length) {
        6 -> "0" to d
        8 -> if (d[0] == '0' || d[0] == '1') d[0].toString() to d.substring(1, 7) else return null
        else -> return null
    }
    val (a, b, c, e, f, last) = body.map { it }.let { Sextet(it[0], it[1], it[2], it[3], it[4], it[5]) }
    val core = when (last) {
        '0', '1', '2' -> "$a$b${last}0000$c$e$f"
        '3' -> "$a$b${c}00000$e$f"
        '4' -> "$a$b$c${e}00000$f"
        else -> "$a$b$c$e${f}0000$last"
    }
    return ns + core
}
private data class Sextet(val a: Char, val b: Char, val c: Char, val d: Char, val e: Char, val f: Char)

/**
 * Fallback for short codes, used only after an exact UPC match found nothing: the scanned code is a
 * UPC-E that expands to the stored UPC, or equals the stored UPC with its middle zeros taken out
 * ("877093" for "87000007093"), with or without the check digit. Digit-only codes, scanned shorter than stored.
 */
internal fun shortCodeMatches(stored: String?, scanned: String): Boolean {
    val st = stored?.trim().orEmpty()
    val sc = scanned.trim()
    if (st.isEmpty() || sc.isEmpty() || st.any { !it.isDigit() } || sc.any { !it.isDigit() }) return false
    upcEToUpcA(sc)?.let { if (upcMatches(st, it)) return true }
    if (sc.trimStart('0').length >= st.trimStart('0').length) return false
    val short = squeezeZeros(sc)
    if (short.length < 4) return false
    val full = squeezeZeros(st)
    val fullNoCheck = squeezeZeros(st.dropLast(1))
    return short == full || short == fullNoCheck
}

/** Exact UPC matches first; only when there are none, short-code matches. */
internal fun <T> matchBarcode(items: List<T>, code: String, upc: (T) -> String?): List<T> {
    val exact = items.filter { upcMatches(upc(it), code) }
    if (exact.isNotEmpty() || code.trim().any { !it.isDigit() }) return exact
    return items.filter { shortCodeMatches(upc(it), code) }
}

data class ScanUiState(
    val catalogLoading: Boolean = true,
    val catalogError: String? = null,
    val query: String = "",
    val searching: Boolean = false,
    val results: List<SubCustomerCatalogItemDto> = emptyList(),
    val noResults: Boolean = false,
    val selected: SubCustomerCatalogItemDto? = null,
    val busy: Boolean = false,
    val restockBusy: Boolean = false,
    val message: ScanMessage? = null,
    val accountPickerOpen: Boolean = false,
    val accountsLoading: Boolean = false,
    val accounts: List<WholesaleAccount> = emptyList(),
    val accountsError: String? = null,
    val unknownBarcode: String? = null,
    val lbCanCheckout: Boolean = true,
    /** The barcode just linked to the selected item through the unknown-barcode flow. */
    val linkedBarcode: String? = null,
    val focusRequestToken: Int = 0
)

private const val MAX_QTY = 99999

// UPC-E/EAN-8, UPC-A, EAN-13, GTIN-14 - auto-trigger "not found" only for complete barcodes.
private val BARCODE_LENGTHS = setOf(8, 12, 13, 14)
private const val RESOLVED_RETRIES = 5
private const val RESOLVED_RETRY_MS = 2500L

/**
 * The Scan screen: find one item (hardware scan or search) and send it to the wholesale cart
 * (General Customer by default, or a linked account via long-press), the LB cart, or the restock
 * list - or price-check it.
 */
class ScanViewModel(
    private val catalogRepository: CatalogRepository,
    private val cartStore: CartStore,
    private val restockRepository: RestockRepository,
    private val reorderRepository: ReorderRepository,
    private val insightsRepository: ItemInsightsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()
    val carts = cartStore.state
    val restock = restockRepository.state
    val reorder = reorderRepository.state
    val insights = insightsRepository.state

    private var catalog: List<SubCustomerCatalogItemDto> = emptyList()
    private var searchJob: Job? = null
    // A scan that arrived while the catalog was still loading - searched as soon as it's ready,
    // so the first scan is never lost.
    private var pendingSubmit = false

    init {
        loadCatalog()
        viewModelScope.launch { runCatching { reorderRepository.ensureLoaded() } }
        viewModelScope.launch { runCatching { insightsRepository.ensureLoaded() } }
        if (!restockRepository.state.value.loaded) {
            viewModelScope.launch { runCatching { restockRepository.refresh() } }
        }
        viewModelScope.launch {
            val market = runCatching { catalogRepository.market() }.getOrNull()
            _uiState.value = _uiState.value.copy(lbCanCheckout = market == null || LbMarket.canCheckout(market))
        }
    }

    fun loadCatalog(forceRefresh: Boolean = false) {
        _uiState.value = _uiState.value.copy(catalogLoading = true, catalogError = null)
        viewModelScope.launch {
            try {
                catalog = catalogRepository.generalCatalog(forceRefresh)
                _uiState.value = _uiState.value.copy(catalogLoading = false, focusRequestToken = _uiState.value.focusRequestToken + 1)
                val waiting = _uiState.value.query.trim()
                if (waiting.isNotEmpty()) {
                    val fromEnter = pendingSubmit || ItemMatching.looksLikeBarcode(waiting)
                    pendingSubmit = false
                    _uiState.value = _uiState.value.copy(searching = true)
                    searchJob?.cancel()
                    searchJob = viewModelScope.launch { runSearch(waiting, fromEnter) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _uiState.value = _uiState.value.copy(catalogLoading = false,
                    catalogError = e.message ?: "Could not load items. Check your connection and try again.")
            }
        }
    }

    // Scanners type the barcode into the focused field. Once typing pauses, an exact UPC match
    // (long enough to be a real barcode) selects the item; anything else lists matches to tap.
    fun onQueryChanged(value: String) {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(query = value, searching = value.isNotBlank(), noResults = false,
            results = if (value.isBlank()) emptyList() else _uiState.value.results)
        if (value.isBlank()) return
        if (_uiState.value.catalogLoading) return // searched once the catalog arrives
        searchJob = viewModelScope.launch {
            delay(250)
            runSearch(value, fromEnter = false)
        }
    }

    fun onSearchSubmitted() {
        val value = _uiState.value.query
        if (value.isBlank()) return
        if (_uiState.value.catalogLoading) { pendingSubmit = true; return }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(value, fromEnter = true) }
    }

    /** A barcode scanned somewhere else (e.g. on Home) that opened this screen. */
    fun scanFromOutside(code: String) {
        val value = code.trim()
        if (value.isEmpty()) return
        _uiState.value = _uiState.value.copy(query = value, searching = true, noResults = false, unknownBarcode = null)
        onSearchSubmitted()
    }

    private suspend fun runSearch(raw: String, fromEnter: Boolean, offerUnknown: Boolean = true) {
        val q = raw.trim()
        val (exact, matches) = withContext(Dispatchers.Default) {
            val upcHits = matchBarcode(catalog, q) { it.upc }.distinctBy { it.itemCode }
            val codeHits = catalog.filter { it.itemCode?.trim().equals(q, ignoreCase = true) }.distinctBy { it.itemCode }
            val contains = catalog.asSequence().filter {
                it.itemName?.contains(q, ignoreCase = true) == true ||
                    it.itemCode?.contains(q, ignoreCase = true) == true ||
                    it.upc?.contains(q, ignoreCase = true) == true
            }.distinctBy { it.itemCode to it.upc }.take(60).toList()
            val exact = when {
                upcHits.isNotEmpty() && (fromEnter || q.length >= 6) -> upcHits
                fromEnter -> codeHits
                else -> emptyList()
            }
            exact to contains
        }
        if (exact.size == 1) {
            select(exact.first())
        } else if (offerUnknown && exact.isEmpty() && matches.isEmpty() && ItemMatching.looksLikeBarcode(q) && (fromEnter || q.length in BARCODE_LENGTHS)) {
            // A real barcode that isn't in the store: offer to link it to an existing item or create one.
            _uiState.value = _uiState.value.copy(query = "", searching = false, results = emptyList(), noResults = false,
                unknownBarcode = q, message = null)
        } else {
            val shown = if (exact.size > 1) exact else matches
            _uiState.value = _uiState.value.copy(searching = false, results = shown, noResults = shown.isEmpty(),
                message = if (shown.isEmpty() && fromEnter) ScanMessage("No item found for \"$q\".", true) else _uiState.value.message)
        }
    }

    fun dismissUnknown() {
        _uiState.value = _uiState.value.copy(unknownBarcode = null, focusRequestToken = _uiState.value.focusRequestToken + 1)
    }

    /** The barcode was just linked or created - reload the (already refreshed) catalog and select it. */
    fun onUnknownResolved(barcode: String, note: String? = null) {
        _uiState.value = _uiState.value.copy(unknownBarcode = null)
        viewModelScope.launch {
            // The item exists now, but the store catalog can trail the POS by a few seconds after a create:
            // retry for a while instead of reopening "Item Not Found" for an item that was just saved.
            for (attempt in 0 until RESOLVED_RETRIES) {
                if (attempt > 0) delay(RESOLVED_RETRY_MS)
                catalog = runCatching { catalogRepository.generalCatalog(forceRefresh = attempt > 0) }.getOrDefault(catalog)
                runSearch(barcode, fromEnter = true, offerUnknown = false)
                if (_uiState.value.selected != null) {
                    _uiState.value = _uiState.value.copy(message = if (note != null) ScanMessage(note, true)
                        else ScanMessage("✓ Barcode linked - item ready", false),
                        linkedBarcode = if (note == null) barcode else null)
                    return@launch
                }
            }
            _uiState.value = _uiState.value.copy(searching = false, results = emptyList(), noResults = false,
                message = ScanMessage("✓ Item saved. It shows up here once the POS sync catches up - scan it again in a minute.", false))
        }
    }

    fun select(item: SubCustomerCatalogItemDto) {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(selected = item, query = "", searching = false,
            results = emptyList(), noResults = false, message = null, linkedBarcode = null,
            focusRequestToken = _uiState.value.focusRequestToken + 1)
    }

    fun clearQuery() {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(query = "", searching = false, results = emptyList(), noResults = false,
            focusRequestToken = _uiState.value.focusRequestToken + 1)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selected = null, message = null, linkedBarcode = null,
            focusRequestToken = _uiState.value.focusRequestToken + 1)
    }

    fun refocus() { _uiState.value = _uiState.value.copy(focusRequestToken = _uiState.value.focusRequestToken + 1) }

    private fun itemName(item: SubCustomerCatalogItemDto) = item.itemName ?: item.itemCode ?: "Item"

    // ---- Each list's tile: tap adds one, the stepper sets that list's quantity directly. ----

    fun addToWholesale() {
        val snapshot = cartStore.state.value
        setWholesaleQty(snapshot.wholesaleQty(snapshot.currentAccount.key, _uiState.value.selected?.itemCode) + 1, announce = true)
    }

    fun setWholesaleQty(qty: Int, announce: Boolean = false) {
        val item = _uiState.value.selected ?: return
        val code = item.itemCode ?: return
        val account = cartStore.state.value.currentAccount
        val current = cartStore.state.value.wholesaleQty(account.key, code)
        val target = qty.coerceIn(0, MAX_QTY)
        if (target == current) return
        if (current > 0) {
            cartStore.setWholesaleQty(account.key, code, target)
            if (announce) done("✓ $target in Wholesale · ${account.label}", false)
            return
        }
        if (_uiState.value.busy) return
        // First add for this account: look up its price (a linked account's price list) once.
        _uiState.value = _uiState.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                val price = catalogRepository.priceFor(account, item)
                cartStore.addWholesale(account, OrderLine(code, item.itemName, item.upc, price, target))
                done("✓ $target added to Wholesale · ${account.label}", false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { done(e.message ?: "Could not add to the wholesale cart.", true) }
        }
    }

    fun addToLb() = setLbQty(cartStore.state.value.lbQty(_uiState.value.selected?.itemCode) + 1, announce = true)

    fun setLbQty(cases: Int, announce: Boolean = false) {
        val item = _uiState.value.selected ?: return
        val code = item.itemCode ?: return
        val current = cartStore.state.value.lbQty(code)
        val target = cases.coerceIn(0, MAX_QTY)
        if (target == current) return
        if (current == 0) cartStore.addLb(code, item.itemName, item.upc, target) else cartStore.setLbQty(code, target)
        if (announce) done("✓ $target ${if (target == 1) "case" else "cases"} on ${if (_uiState.value.lbCanCheckout) "LB Cart" else "LB List"}", false)
    }

    fun addToRestock() = setRestockQty(restockRepository.state.value.qtyFor(_uiState.value.selected?.itemCode) + 1, announce = true)

    fun setRestockQty(qty: Int, announce: Boolean = false) {
        val item = _uiState.value.selected ?: return
        val code = item.itemCode ?: return
        if (_uiState.value.restockBusy) return
        val row = restockRepository.state.value.items.firstOrNull { it.itemCode == code }
        val current = row?.qty ?: 0
        val target = qty.coerceIn(0, MAX_QTY)
        if (target == current) return
        _uiState.value = _uiState.value.copy(restockBusy = true, message = null)
        viewModelScope.launch {
            try {
                if (row == null) restockRepository.add(code, item.itemName, item.upc, target)
                else restockRepository.setQty(row.id, target)
                _uiState.value = _uiState.value.copy(restockBusy = false)
                if (announce) done("✓ $target on Restock", false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _uiState.value = _uiState.value.copy(restockBusy = false)
                done(e.message ?: "Could not update the restock list.", true)
            }
        }
    }

    private fun done(text: String, isError: Boolean) {
        _uiState.value = _uiState.value.copy(busy = false,
            message = ScanMessage(text, isError), focusRequestToken = _uiState.value.focusRequestToken + 1)
    }

    fun saveReorderSettings(settings: ReorderSettings) {
        viewModelScope.launch { runCatching { reorderRepository.updateSettings(settings) } }
    }

    fun reloadReorder() {
        viewModelScope.launch { runCatching { reorderRepository.ensureLoaded(force = true) } }
    }

    /** Long-press Restock: open the store's open Restock pick list. */
    fun openRestockPickList(onOpen: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                val id = restockRepository.openPickListId()
                if (id != null) onOpen(id)
                else done("Restock list is empty - tap Restock to add this item.", false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { done(e.message ?: "Could not open the restock pick list.", true) }
        }
    }

    /** Long-press Wholesale: choose who the wholesale order is being built for. Remembered. */
    fun chooseAccount(account: WholesaleAccount) {
        cartStore.setDefaultAccount(account)
        _uiState.value = _uiState.value.copy(accountPickerOpen = false,
            message = ScanMessage("Wholesale now builds the order for ${account.label}", false),
            focusRequestToken = _uiState.value.focusRequestToken + 1)
    }

    fun openAccountPicker() {
        _uiState.value = _uiState.value.copy(accountPickerOpen = true, accountsError = null,
            accountsLoading = _uiState.value.accounts.isEmpty())
        if (_uiState.value.accounts.isNotEmpty()) return
        viewModelScope.launch {
            try {
                val accounts = catalogRepository.linkedAccounts()
                _uiState.value = _uiState.value.copy(accountsLoading = false, accounts = accounts)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _uiState.value = _uiState.value.copy(accountsLoading = false, accountsError = e.message ?: "Could not load accounts.")
            }
        }
    }

    fun dismissAccountPicker() {
        _uiState.value = _uiState.value.copy(accountPickerOpen = false, focusRequestToken = _uiState.value.focusRequestToken + 1)
    }
}
