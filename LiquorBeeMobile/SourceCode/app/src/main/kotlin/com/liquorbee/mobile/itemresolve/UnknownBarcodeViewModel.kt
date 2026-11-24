package com.liquorbee.mobile.itemresolve

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquorbee.mobile.carts.CatalogRepository
import com.liquorbee.mobile.carts.upcMatches
import com.liquorbee.mobile.core.network.ApiService
import com.liquorbee.mobile.core.network.CreateItemForPORequest
import com.liquorbee.mobile.core.network.ItemCatalogSearchDto
import com.liquorbee.mobile.core.network.ItemMetaDataDto
import com.liquorbee.mobile.core.network.ItemResolveRequest
import com.liquorbee.mobile.core.network.SubCustomerCatalogItemDto
import com.liquorbee.mobile.core.network.UniversalProductDto
import com.liquorbee.mobile.core.network.UniversalStoreItemLink
import com.liquorbee.mobile.core.network.UniversalUpcAction
import com.liquorbee.mobile.core.network.UpdateItemMetaDataRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response

data class LinkCandidate(val item: SubCustomerCatalogItemDto, val reason: String, val meta: ItemMetaDataDto? = null) {
    val hasRealUpc: Boolean get() = !ItemMatching.isMissingUpc(item.upc)
    /** Size written in the item name (ml), e.g. "1750". */
    val nameSize: String? get() = ItemMatching.size(item.itemName.orEmpty())
    /** Size from QuanticInventoryMetaDataReport (ml) - can be wrong, so the name wins when both exist. */
    // Many metadata rows ("Default" source) have no Size yet but their stored name still has one
    // ("MONSTER ENERGY ZERO ULTRA 16oz"), so fall back to that.
    val metaSize: String? get() = meta?.size?.let { ItemMatching.size(it) } ?: meta?.itemName?.let { ItemMatching.size(it) }
    val effectiveSize: String? get() = nameSize ?: metaSize
}

internal const val ONLINE_LOOKUP_UNAVAILABLE = "Online lookup is not available. Contact LiquorBee support."

/** What the scanned barcode turned out to be, from whichever source identified it. */
data class IdentifiedProduct(
    val name: String,
    val source: String,
    val sku: String? = null,
    val size: String? = null,
    val cost: Double? = null,
    val unitsPerCase: Int? = null,
    /** Universal product this barcode belongs to - gets the barcode backfilled once a person confirms it. */
    val universalSku: String? = null,
    val universalHasBarcode: Boolean = false,
    /** Size as the source wrote it ("1750ML", "10/50ML") - keeps multi-packs that [size] (ml) can't express. */
    val sizeLabel: String? = null
) {
    /** Our own LB-12345 universal SKU, the store SKU for anything in the LiquorBee catalog (older long LB- codes aren't used). */
    val lbSku: String? get() = universalSku?.trim()?.uppercase()?.takeIf { Regex("^LB-[0-9]{5}$").matches(it) }
}

/**
 * "Link" choices for one store item: the barcode always gets linked; each looked-up value that
 * differs from the store item can also be applied. Sizes are ml strings ("1750"), shown formatted.
 */
data class LinkOptions(
    val candidate: LinkCandidate,
    val newName: String? = null,
    val applyName: Boolean = false,
    /** Looked-up name without any trailing state code - the code suffix follows the SKU the item ends up with. */
    val baseName: String? = null,
    /** True once the user typed in the name field - stop regenerating the suggestion then. */
    val nameEdited: Boolean = false,
    val newSku: String? = null,
    val applySku: Boolean = false,
    val newSize: String? = null,
    val applySize: Boolean = false,
    val newUnitsPerCase: Int? = null,
    val applyUnits: Boolean = false
) {
    val hasUpdates: Boolean get() = newName != null || newSku != null || newSize != null || newUnitsPerCase != null

    /** SKU the item will have after linking. */
    val finalSku: String? get() = if (newSku != null && applySku) newSku else candidate.item.itemCode
}

/** Store convention is "NAME (SIZE) - CODE"; skip the suffix for placeholder SKUs like MISSING-123. */
fun suggestedItemName(baseName: String, sku: String?): String =
    if (sku.isNullOrBlank() || sku.startsWith("MISSING", ignoreCase = true)) baseName else "$baseName - $sku"

/** "1750" (ml) -> "1.75L", "750" -> "750ml" - the same format the item metadata uses. */
fun formatSizeMl(ml: String): String {
    val v = ml.toIntOrNull() ?: return ml
    return if (v >= 1000) "${(v / 1000.0).toString().removeSuffix(".0")}L" else "${v}ml"
}

enum class NameStyle(val label: String) { CATALOG("Name (SIZE) - SKU"), PLAIN("Just name") }

/**
 * Catalog size label: uppercase, no spaces ("750ML", "1.75L", "10/50ML"). A plain single size is
 * normalized through ml (so "0.75L" -> "750ML"); null for missing/junk values like "unknown".
 */
fun catalogSizeLabel(raw: String?): String? {
    val s = raw?.uppercase()?.replace(Regex("\\s+"), "")?.removePrefix("(")?.removeSuffix(")") ?: return null
    if (s.isEmpty() || s.none(Char::isDigit)) return null
    if (s.contains('/') || s.endsWith("OZ")) return s // multi-packs and ounce sizes stay as written
    val ml = (if (s.all(Char::isDigit)) s else ItemMatching.size(s))?.toIntOrNull() ?: return null
    if (ml <= 0) return null
    return if (ml >= 1000) "${(ml / 1000.0).toString().removeSuffix(".0")}L" else "${ml}ML"
}

/** True when [name] already shows this size in any spelling (750ML / 750 ml / 0.75L). */
private fun nameHasSize(name: String, label: String): Boolean {
    val compact = name.uppercase().replace(Regex("\\s+"), "")
    if (compact.contains(label)) return true
    if (label.contains('/')) return false
    val wanted = catalogSizeLabel(label) ?: return false
    return Regex("""\d+(?:\.\d+)?(ML|LTR|LT|L|OZ)\b""").findAll(name.uppercase().replace(Regex("(\\d)\\s+(ML|LTR|LT|L|OZ)\\b"), "$1$2"))
        .any { catalogSizeLabel(it.value) == wanted }
}

/** "NAME (750ML) - 19366" -> "NAME": drops a trailing " - SKU" and a trailing "(SIZE)". */
fun plainItemName(name: String, sku: String?): String {
    var n = name.trim()
    if (!sku.isNullOrBlank() && n.endsWith(sku.trim(), ignoreCase = true)) n = n.dropLast(sku.trim().length).trimEnd(' ', '-')
    val paren = Regex("""\s*\(([^()]*)\)\s*$""").find(n)
    if (paren != null && catalogSizeLabel(paren.groupValues[1]) != null) n = n.substring(0, paren.range.first).trimEnd()
    return n
}

/** Catalog convention: "NAME (SIZE) - SKU"; size/SKU each added only when known and not already in the name. */
fun catalogItemName(name: String, sizeRaw: String?, sku: String?): String {
    var n = name.trim()
    val size = catalogSizeLabel(sizeRaw)
    if (size != null && !nameHasSize(n, size)) n = "$n ($size)"
    val code = sku?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("MISSING", ignoreCase = true) }
    if (code != null && !n.contains(code, ignoreCase = true)) n = "$n - $code"
    return n
}

data class CreateItemForm(
    val name: String = "",
    /** Plain product name (no size / SKU suffix) the format is built from; blank = nothing to build. */
    val baseName: String = "",
    /** Size as found (ml string or a label like "10/50ML"). */
    val sizeRaw: String? = null,
    val nameStyle: NameStyle = NameStyle.CATALOG,
    /** The person typed in the name field - stop rebuilding it until they pick a format again. */
    val nameEdited: Boolean = false,
    val sku: String = "",
    /** [sku] is a generated UC-##### placeholder - a real catalog code may still replace it. */
    val skuGenerated: Boolean = false,
    val upc: String = "",
    val price: String = "",
    val cost: String = "",
    val unitsPerCase: String = "1",
    val qtyInHand: String = "0",
    /** Where the pre-filled selling price came from, e.g. "Suggested at your 30% markup". */
    val priceHint: String? = null
) {
    /** Rebuild [name] from the chosen format (after SKU / style changes) unless the person typed their own. */
    fun withGeneratedName(): CreateItemForm {
        if (nameEdited || baseName.isBlank()) return this
        return copy(name = if (nameStyle == NameStyle.PLAIN) baseName else catalogItemName(baseName, sizeRaw, sku))
    }
}

data class UnknownBarcodeState(
    val barcode: String = "",
    val loading: Boolean = false,
    val identified: IdentifiedProduct? = null,
    val alreadyInPos: SubCustomerCatalogItemDto? = null,
    val candidates: List<LinkCandidate> = emptyList(),
    /** The universal catalog is reachable for this login (the endpoints are admin-gated today). */
    val universalAvailable: Boolean = false,
    val onlineLookupRunning: Boolean = false,
    val onlineLookupMessage: String? = null,
    /** An online lookup already ran for this barcode (so the button offers to try again). */
    val onlineLookupTried: Boolean = false,
    val manualQuery: String = "",
    val manualResults: List<LinkCandidate> = emptyList(),
    val creating: Boolean = false,
    val form: CreateItemForm = CreateItemForm(),
    val linkOptions: LinkOptions? = null,
    val working: Boolean = false,
    val error: String? = null,
    /** Partial-success message from the last link (e.g. size couldn't be saved). */
    val linkNote: String? = null,
    /** Set once the barcode was linked or a new item created - the caller re-runs its lookup. */
    val resolvedBarcode: String? = null
)

const val SOURCE_STATE_CATALOG = "LiquorBee catalog"
const val SOURCE_UNIVERSAL = "LiquorBee catalog"
const val SOURCE_ONLINE = "online lookup"

/** How long the first automatic online lookup keeps the spinner up before showing the "not found" page. */
private const val AUTO_LOOKUP_SPINNER_MS = 4000L

/**
 * A scanned barcode matched nothing in the store. Identify it, then offer to link it to the
 * store's existing item or create a new one:
 *  1. Master LiquorBee catalog (Products/GetItemCatalogSearch - the web manual PO's Create Item search)
 *     and the universal product catalog (Items/Resolve, read-only) are checked together.
 *  2. If neither knows it, the online lookup (Items/Resolve with allowLookup: a paid web lookup that may
 *     add the product to the universal catalog) starts automatically in the background. The server skips
 *     barcodes that recently failed a lookup unless the person taps "Try again online".
 *  3. Store items that are probably the same product but lack this UPC (same state code, or a
 *     close name + same size) are offered for linking (Price Check edit save with the new UPC).
 *  4. Once a person links or creates, the barcode is backfilled onto the universal product.
 */
class UnknownBarcodeViewModel(
    private val api: ApiService,
    private val catalogRepository: CatalogRepository
) : ViewModel() {
    private val _state = MutableStateFlow(UnknownBarcodeState())
    val state: StateFlow<UnknownBarcodeState> = _state.asStateFlow()
    private var manualJob: Job? = null
    private var startJob: Job? = null
    private var lookupJob: Job? = null

    fun start(barcode: String) {
        val s = _state.value
        if (s.barcode == barcode && s.resolvedBarcode == null && (s.loading || s.identified != null || s.candidates.isNotEmpty())) return
        startJob?.cancel(); lookupJob?.cancel()
        _state.value = UnknownBarcodeState(barcode = barcode, loading = true)
        startJob = viewModelScope.launch {
            try {
                // Price Check reads a different item table than the order catalog - the item may
                // already carry this UPC in the POS, so say so instead of offering a duplicate.
                val existing = runCatching { catalogRepository.generalCatalog() }.getOrDefault(emptyList())
                    .let { com.liquorbee.mobile.carts.matchBarcode(it, barcode) { c -> c.upc }.firstOrNull() }
                val (stateMatch, universal) = coroutineScope {
                    val a = async { lookupStateCatalog(barcode) }
                    val b = async { resolveUniversal(ItemResolveRequest(upc = barcode)) }
                    a.await() to b.await()
                }
                var identified = when {
                    stateMatch != null -> IdentifiedProduct(
                        name = stateMatch.itemName.orEmpty(), source = SOURCE_STATE_CATALOG, sku = stateMatch.sku,
                        size = ItemMatching.size(stateMatch.itemName.orEmpty()), cost = stateMatch.cost, unitsPerCase = stateMatch.unitsPerCase,
                        universalSku = universal.product?.universalSku, universalHasBarcode = universal.product?.hasBarcode(barcode) == true)
                    universal.product != null -> universal.product.toIdentified(SOURCE_UNIVERSAL, barcode)
                    else -> null
                }
                // Known to the state catalog but not yet by barcode in the universal catalog: find the
                // universal product by name so a confirmed link/create can backfill its barcode.
                if (identified != null && identified.universalSku == null && universal.available && stateMatch != null) {
                    val byName = resolveUniversal(ItemResolveRequest(name = cleanName(stateMatch.itemName, stateMatch.sku), unitsPerCase = stateMatch.unitsPerCase))
                    byName.product?.let { identified = identified!!.copy(universalSku = it.universalSku, universalHasBarcode = it.hasBarcode(barcode)) }
                }
                val candidates = if (existing != null) emptyList() else findCandidates(identified)
                // Nothing knows this barcode: search the web right away instead of waiting for a tap. Keep the
                // spinner up for the first few seconds so a quick hit goes straight to Create Item instead of
                // flashing the "not found" page; a slow lookup shows that page so they can link/create meanwhile.
                val autoLookup = identified == null && existing == null && universal.available
                _state.value = _state.value.copy(loading = autoLookup, identified = identified, candidates = candidates,
                    alreadyInPos = existing, universalAvailable = universal.available)
                if (autoLookup) {
                    lookupOnline()
                    delay(AUTO_LOOKUP_SPINNER_MS)
                    if (_state.value.loading && _state.value.barcode == barcode) _state.value = _state.value.copy(loading = false)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Could not look up this barcode.")
            }
        }
    }

    // The catalog search is a substring match on stored 12-digit UPCs, so search with leading
    // zeros stripped (covers 12/13-digit scans), then without the check digit, and only accept
    // results whose barcode really is this one.
    private suspend fun lookupStateCatalog(barcode: String): ItemCatalogSearchDto? {
        val digits = barcode.filter { it.isDigit() }.trimStart('0')
        val terms = listOf(digits, digits.dropLast(1)).filter { it.length >= 7 }.distinct()
        for (term in terms) {
            val response = runCatching { api.getItemCatalogSearch(term) }.getOrNull() ?: continue
            if (!response.isSuccessful) continue
            val hit = response.body().orEmpty().firstOrNull { upcMatches(it.barcode, barcode) }
            if (hit != null) return hit
        }
        return null
    }

    private data class UniversalResult(val available: Boolean, val product: UniversalProductDto?, val status: String? = null,
        val reason: String? = null, val method: String? = null)

    /** Items/Resolve; any failure (403 for non-admin logins today, bad facts, network) = no universal data. */
    private suspend fun resolveUniversal(request: ItemResolveRequest): UniversalResult {
        val response = runCatching { api.resolveItem(request) }.getOrNull() ?: return UniversalResult(false, null)
        if (response.code() == 401 || response.code() == 403) return UniversalResult(false, null)
        if (!response.isSuccessful) return UniversalResult(true, null, reason = response.failure("Lookup failed"))
        val body = response.body() ?: return UniversalResult(true, null)
        // "Matched" = already in the catalog; "Created" = the online lookup just added it.
        val usable = body.status == "Matched" || body.status == "Created"
        return UniversalResult(true, if (usable) body.product else null, body.status, body.reason, body.method)
    }

    private fun UniversalProductDto.hasBarcode(barcode: String) = barcodes.any { upcMatches(it, barcode) }

    private fun UniversalProductDto.toIdentified(source: String, barcode: String) = IdentifiedProduct(
        name = name.orEmpty(), source = source, size = size?.let { ItemMatching.size(it) }, unitsPerCase = unitsPerCase,
        universalSku = universalSku, universalHasBarcode = hasBarcode(barcode), sizeLabel = size)

    // "PARROT BAY COCONUT 90 PROOF (1.75L) - 43258" -> drop the trailing state code for name matching.
    private fun cleanName(name: String?, code: String?): String {
        val n = name.orEmpty().trim()
        return if (!code.isNullOrBlank()) n.removeSuffix(code).trimEnd(' ', '-') else n
    }

    /** "Look up online" - paid web lookup via Items/Resolve(allowLookup); may add the product to the universal catalog. */
    /** Search the web for this barcode. Runs automatically; [force] is the "Try again online" button. */
    fun lookupOnline(force: Boolean = false) {
        val barcode = _state.value.barcode
        if (_state.value.onlineLookupRunning) return
        _state.value = _state.value.copy(onlineLookupRunning = true, onlineLookupMessage = null, error = null)
        lookupJob = viewModelScope.launch {
            val result = resolveUniversal(ItemResolveRequest(upc = barcode, allowLookup = true, forceLookup = force))
            if (result.product != null) {
                val identified = result.product.toIdentified(SOURCE_ONLINE, barcode)
                val candidates = findCandidates(identified)
                val s = _state.value
                _state.value = s.copy(loading = false, onlineLookupRunning = false, onlineLookupTried = true, identified = identified, candidates = candidates)
                when {
                    // The universal catalog work stays behind the scenes: go straight to adding it to the store,
                    // pre-filled. Back from the form still shows any similar store items to link instead.
                    !s.creating && s.linkOptions == null && !s.working -> openCreate()
                    // Already filling in the form by hand: fill only what's still blank.
                    s.creating -> {
                        val typedName = s.form.nameEdited && s.form.name.isNotBlank()
                        _state.value = _state.value.copy(form = s.form.copy(
                            baseName = if (typedName) s.form.baseName else plainItemName(identified.name, identified.sku),
                            sizeRaw = s.form.sizeRaw ?: identified.sizeLabel ?: identified.size,
                            nameEdited = typedName,
                            sku = if (s.form.sku.isBlank() || s.form.skuGenerated) identified.sku ?: identified.lbSku ?: s.form.sku else s.form.sku,
                            skuGenerated = s.form.skuGenerated && identified.sku == null && identified.lbSku == null,
                            cost = s.form.cost.ifBlank { identified.cost?.let { "%.2f".format(it) }.orEmpty() },
                            unitsPerCase = if (s.form.unitsPerCase.isBlank() || s.form.unitsPerCase == "1")
                                (identified.unitsPerCase ?: 1).toString() else s.form.unitsPerCase).withGeneratedName())
                        loadCreateDefaults(identified)
                    }
                    // Mid-link: leave it alone; the result shows on the main screen when they come back.
                    else -> Unit
                }
            } else {
                val message = when {
                    result.method == "RecentMiss" -> "This barcode couldn't be found online recently. Link it to one of your items or create it."
                    result.status == "LookupUnavailable" -> ONLINE_LOOKUP_UNAVAILABLE
                    else -> "Couldn't identify this barcode online. Link it to one of your items or create it."
                }
                _state.value = _state.value.copy(loading = false, onlineLookupRunning = false, onlineLookupTried = true, onlineLookupMessage = message)
            }
        }
    }

    private suspend fun findCandidates(identified: IdentifiedProduct?): List<LinkCandidate> {
        if (identified == null || identified.name.isBlank()) return emptyList()
        val catalog = runCatching { catalogRepository.generalCatalog() }.getOrDefault(emptyList())
        val meta = catalogRepository.itemMetadata()
        return withContext(Dispatchers.Default) {
            val sku = identified.sku?.trim().orEmpty()
            val sameCode = if (sku.isBlank()) emptyList() else catalog
                .filter { it.itemCode?.trim().equals(sku, ignoreCase = true) }
                .map { LinkCandidate(it, "Same item code", meta.find(it)) } // label matched in UnknownBarcodeSheet
            // Universal sizes come as "1750ML" - fold the known size into the name so matching sees it.
            val target = ItemMatching.normalize(
                identified.name + (identified.size?.takeIf { ItemMatching.size(identified.name) == null }?.let { " ${it}ML" } ?: ""), identified.sku)
            val byName = catalog.asSequence()
                .filter { c -> sameCode.none { it.item.itemCode == c.itemCode } }
                .map { item ->
                    val m = meta.find(item)
                    val fallback = m?.size?.takeIf { ItemMatching.size(it) != null } ?: m?.itemName
                    item to ItemMatching.score(target, ItemMatching.normalize(item.itemName.orEmpty(), item.itemCode, fallback))
                }
                .filter { it.second >= ItemMatching.CANDIDATE_THRESHOLD }
                // Items with no real UPC are the likeliest "it was just never scanned in" matches.
                .sortedByDescending { it.second + if (ItemMatching.isMissingUpc(it.first.upc)) 0.1 else 0.0 }
                .take(5)
                .map { LinkCandidate(it.first, "Similar name", meta.find(it.first)) }
                .toList()
            (sameCode + byName).distinctBy { it.item.itemCode to it.item.upc }
        }
    }

    fun onManualQuery(value: String) {
        manualJob?.cancel()
        _state.value = _state.value.copy(manualQuery = value, manualResults = if (value.isBlank()) emptyList() else _state.value.manualResults)
        if (value.trim().length < 2) return
        manualJob = viewModelScope.launch {
            delay(250)
            val catalog = catalogRepository.generalCatalog()
            val meta = catalogRepository.itemMetadata()
            val q = value.trim()
            val results = withContext(Dispatchers.Default) {
                catalog.asSequence()
                    .filter { it.itemName?.contains(q, true) == true || it.itemCode?.contains(q, true) == true }
                    .sortedBy { if (ItemMatching.isMissingUpc(it.upc)) 0 else 1 }
                    .distinctBy { it.itemCode to it.upc }
                    .take(30).map { LinkCandidate(it, "Search result", meta.find(it)) }.toList()
            }
            _state.value = _state.value.copy(manualResults = results)
        }
    }

    // ---- Link ----

    /**
     * Open the link choices. Each looked-up value (name, size, units per case) that differs from
     * the store item is offered as an update, pre-checked; the barcode replacement warning shows
     * there too when the item already has a real UPC.
     */
    fun requestLink(candidate: LinkCandidate) {
        val p = _state.value.identified
        val currentUnits = candidate.meta?.unitsPerCase?.takeIf { it > 0 }
        val currentSku = candidate.item.itemCode?.trim().orEmpty()
        // The state code from the catalog - restoring it matters for the LB cart, which orders by SKU.
        val newSku = p?.sku?.trim()?.takeIf { it.isNotEmpty() && !it.equals(currentSku, ignoreCase = true) }
        val applySku = newSku != null && currentSku.startsWith("MISSING", ignoreCase = true)
        val baseName = p?.name?.let { cleanName(it, p.sku) }?.trim()?.takeIf { it.isNotEmpty() }
        val skuForName = if (applySku) newSku else currentSku
        val newName = baseName?.let { suggestedItemName(it, skuForName) }
            ?.takeIf { !it.equals(candidate.item.itemName?.trim(), ignoreCase = true) }
        val newSize = p?.size?.takeIf { it != candidate.effectiveSize }
        val newUnits = p?.unitsPerCase?.takeIf { it > 0 && it != currentUnits }
        _state.value = _state.value.copy(error = null, linkOptions = LinkOptions(
            candidate = candidate,
            newName = newName, applyName = newName != null, baseName = baseName,
            newSku = newSku, applySku = applySku,
            newSize = newSize, applySize = newSize != null,
            newUnitsPerCase = newUnits, applyUnits = newUnits != null))
    }

    fun updateLinkOptions(options: LinkOptions) {
        // Keep the suggested name's code suffix in step with the SKU choice until the user edits the name.
        val o = options
        val synced = if (!o.nameEdited && o.baseName != null && o.newName != null)
            o.copy(newName = suggestedItemName(o.baseName, o.finalSku)) else o
        _state.value = _state.value.copy(linkOptions = synced)
    }

    fun dismissLinkOptions() { if (!_state.value.working) _state.value = _state.value.copy(linkOptions = null) }

    /** Link the barcode; with [applyUpdates], also apply the checked looked-up values. */
    fun confirmLink(applyUpdates: Boolean) {
        val options = _state.value.linkOptions ?: return
        val candidate = options.candidate
        val barcode = _state.value.barcode
        val code = candidate.item.itemCode ?: return
        val newName = options.newName?.trim()?.takeIf { applyUpdates && options.applyName && it.isNotEmpty() }
        val newSku = options.newSku?.takeIf { applyUpdates && options.applySku }
        val newSize = options.newSize?.takeIf { applyUpdates && options.applySize }
        val newUnits = options.newUnitsPerCase?.takeIf { applyUpdates && options.applyUnits }
        _state.value = _state.value.copy(working = true, error = null)
        viewModelScope.launch {
            try {
                val search = api.searchEditableItems(code)
                if (!search.isSuccessful) error(search.failure("Could not find this item to update"))
                val matches = search.body().orEmpty().filter { it.itemCode.equals(code, true) && !it.itemId.isNullOrBlank() }
                val chosen = matches.firstOrNull { (it.barcode ?: "") == (candidate.item.upc ?: "") }
                    ?: matches.singleOrNull()
                    ?: error(if (matches.isEmpty()) "This item can't be edited from the phone yet."
                        else "More than one item uses code $code. Link the UPC from Price Check instead.")
                val edit = api.getPriceCheckEdit(chosen.itemId!!)
                val current = edit.body()
                if (!edit.isSuccessful || current == null) error(edit.failure("Could not load this item"))
                // One Quantic save for the barcode and (optionally) the name and SKU. The server
                // rejects a SKU another item already uses, and that message is shown as-is.
                val saved = api.savePriceCheckEdit(current.copy(upc = barcode, name = newName ?: current.name, sku = newSku ?: current.sku))
                if (!saved.isSuccessful) error(saved.failure("Could not save the UPC"))

                // Size / units per case live in the item metadata, not on the Quantic item. The
                // endpoint needs both a non-empty size and units > 0, so fill whichever wasn't
                // changed from what's already known; skip (and say so) if that's not possible.
                var note: String? = null
                if (newSize != null || newUnits != null) {
                    val size = (newSize ?: candidate.effectiveSize)?.let { formatSizeMl(it) }
                    val units = newUnits ?: candidate.meta?.unitsPerCase?.takeIf { it > 0 }
                    if (size == null || units == null) {
                        note = "Barcode linked. Size and units per case weren't saved - both are needed and one is unknown."
                    } else {
                        val meta = runCatching { api.updateItemMetaData(UpdateItemMetaDataRequest(chosen.itemId!!, units, size)) }.getOrNull()
                        if (meta?.isSuccessful != true) note = "Barcode linked, but size and units per case couldn't be saved."
                        catalogRepository.invalidateMetadata()
                    }
                }
                if (note != null) _state.value = _state.value.copy(linkNote = note)
                finish(barcode, "Linked to store item ${candidate.item.itemName ?: code} from a LiquorBee Mobile scan")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = _state.value.copy(working = false, error = e.message ?: "Could not link this UPC.") }
        }
    }

    // ---- Create ----

    fun openCreate() {
        val p = _state.value.identified
        _state.value = _state.value.copy(creating = true, error = null, form = CreateItemForm(
            name = p?.name.orEmpty(),
            baseName = p?.let { plainItemName(it.name, it.sku) }.orEmpty(),
            sizeRaw = p?.sizeLabel ?: p?.size,
            sku = p?.sku ?: p?.lbSku.orEmpty(),
            upc = _state.value.barcode,
            cost = p?.cost?.let { "%.2f".format(it) }.orEmpty(),
            unitsPerCase = (p?.unitsPerCase ?: 1).toString()
        ).withGeneratedName())
        loadCreateDefaults(p)
    }

    private var defaultsJob: Job? = null

    /**
     * Server pre-fill: the state price book SKU and unit cost (also for universal-catalog matches, via the code
     * other stores in this state use) and a selling price at the store's desired markup. Only blank fields
     * (or the default "1" units per case) are filled, so nothing the person already typed is replaced.
     */
    private fun loadCreateDefaults(p: IdentifiedProduct?) {
        defaultsJob?.cancel()
        defaultsJob = viewModelScope.launch {
            val d = runCatching { api.getCreateItemDefaults(p?.sku?.takeIf { it.isNotBlank() }, p?.universalSku, p?.cost?.takeIf { it > 0 }) }
                .getOrNull()?.takeIf { it.isSuccessful }?.body()
            if (d == null) { ensureSku(); return@launch }
            // A code borrowed from other stores is no good if this store already uses it for another item,
            // and never replaces our own LB- universal SKU.
            val catalogSku = d.sku?.trim()?.takeIf { it.isNotEmpty() && (d.skuSource == "Request" || (p?.lbSku == null && !storeUsesSku(it))) }
            val s = _state.value
            if (!s.creating) return@launch
            val f = s.form
            val takeCatalogSku = catalogSku != null && (f.sku.isBlank() || f.skuGenerated)
            val price = d.suggestedPrice?.takeIf { it > 0 && f.price.isBlank() }
            _state.value = s.copy(form = f.copy(
                sku = if (takeCatalogSku) catalogSku!! else f.sku,
                skuGenerated = f.skuGenerated && !takeCatalogSku,
                cost = f.cost.ifBlank { d.unitCost?.let { "%.2f".format(it) }.orEmpty() },
                unitsPerCase = if (f.unitsPerCase.isBlank() || f.unitsPerCase == "1") (d.unitsPerCase ?: f.unitsPerCase.toIntOrNull() ?: 1).toString() else f.unitsPerCase,
                price = price?.let { "%.2f".format(it) } ?: f.price,
                priceHint = if (price != null) "Suggested at your ${d.markupPercent?.let { "%.0f".format(it) } ?: ""}% markup" else f.priceHint
            ).withGeneratedName())
            ensureSku()
        }
    }

    private suspend fun storeUsesSku(sku: String): Boolean =
        runCatching { catalogRepository.generalCatalog() }.getOrDefault(emptyList())
            .any { it.itemCode?.trim().equals(sku, ignoreCase = true) }

    /**
     * Fill a blank SKU (or replace a generated one): the product's LB- universal SKU when it's in our catalog
     * and this store doesn't already use it, else a generated UC-##### code.
     */
    private suspend fun ensureSku() {
        val s = _state.value
        if (!s.creating) return
        val lb = s.identified?.lbSku
        if (lb != null && (s.form.sku.isBlank() || s.form.skuGenerated || s.form.sku == lb)) {
            if (!storeUsesSku(lb)) {
                val now = _state.value
                if (now.creating && now.form.sku != lb) _state.value = now.copy(form = now.form.copy(sku = lb, skuGenerated = false).withGeneratedName())
                return
            }
            if (s.form.sku == lb) { generateSku(); return } // this store already has an item with that LB SKU
        }
        if (s.form.sku.isBlank()) generateSku()
    }

    /** UC- plus 5 random digits that no item in this store already uses. Also the SKU field's refresh button. */
    fun generateSku() {
        viewModelScope.launch {
            val used = runCatching { catalogRepository.generalCatalog() }.getOrDefault(emptyList())
                .mapNotNull { it.itemCode?.trim()?.uppercase() }.toHashSet()
            val code = generateSequence { "UC-%05d".format(kotlin.random.Random.nextInt(0, 100000)) }
                .take(200).firstOrNull { it !in used } ?: return@launch
            val s = _state.value
            if (!s.creating) return@launch
            _state.value = s.copy(form = s.form.copy(sku = code, skuGenerated = true).withGeneratedName())
        }
    }

    fun closeCreate() { _state.value = _state.value.copy(creating = false, error = null) }

    /** Any field change; the name follows the chosen format (e.g. a new SKU) until the person types their own. */
    fun updateForm(form: CreateItemForm) { _state.value = _state.value.copy(form = form.withGeneratedName()) }

    /** Typing in the name field: keep exactly what they typed (clearing it goes back to the format). */
    fun onNameTyped(value: String) {
        val f = _state.value.form
        _state.value = _state.value.copy(form = if (value.isBlank()) f.copy(name = "", nameEdited = false).withGeneratedName()
            else f.copy(name = value, nameEdited = true))
    }

    /** Picking a format rebuilds the name from the product, replacing any hand edits. */
    fun setNameStyle(style: NameStyle) {
        val f = _state.value.form
        val base = f.baseName.ifBlank { plainItemName(f.name, f.sku.ifBlank { null }) }
        _state.value = _state.value.copy(form = f.copy(nameStyle = style, nameEdited = false, baseName = base).withGeneratedName())
    }

    fun create() {
        val f = _state.value.form
        val price = f.price.toDoubleOrNull()
        val units = f.unitsPerCase.toIntOrNull()
        val message = when {
            f.name.isBlank() -> "Item name is required."
            f.sku.isBlank() -> "SKU is required."
            f.upc.isBlank() -> "UPC / Barcode is required."
            price == null || price <= 0 -> "Selling price must be greater than zero."
            (f.cost.toDoubleOrNull() ?: 0.0) <= 0 -> "Unit cost must be greater than zero."
            units == null || units <= 0 -> "Units per case must be greater than zero."
            else -> null
        }
        if (message != null) { _state.value = _state.value.copy(error = message); return }
        _state.value = _state.value.copy(working = true, error = null)
        viewModelScope.launch {
            try {
                val response = api.createItemForManualPO(CreateItemForPORequest(
                    name = f.name.trim(), sku = f.sku.trim(), upc = f.upc.trim(),
                    unitsPerCase = units!!, unitCost = f.cost.toDoubleOrNull() ?: 0.0,
                    price = price!!, msrp = price, qtyInHand = f.qtyInHand.toIntOrNull() ?: 0))
                if (!response.isSuccessful) error(response.failure("Could not create the item"))
                finish(f.upc.trim(), "New store item ${f.name.trim()} created from a LiquorBee Mobile scan", createdSku = f.sku.trim())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = _state.value.copy(working = false, error = e.message ?: "Could not create the item.") }
        }
    }

    private suspend fun finish(barcode: String, note: String, createdSku: String? = null) {
        // A person just confirmed what this barcode is - backfill it onto the universal product.
        // Best effort: the store change already succeeded, so a failure here must not surface.
        val p = _state.value.identified
        if (p?.universalSku != null && !p.universalHasBarcode && upcMatches(barcode, _state.value.barcode)) {
            runCatching { api.saveUniversalUpc(p.universalSku, UniversalUpcAction(upc = barcode, note = note.take(1000))) }
        }
        // A store item created from a universal product is mapped to it (found server-side by barcode + SKU).
        if (createdSku != null && p?.universalSku != null) {
            runCatching { api.linkUniversalStoreItem(p.universalSku, UniversalStoreItemLink(upc = barcode, sku = createdSku, note = note.take(1000))) }
        }
        // Pull the fresh catalog so the caller's re-lookup finds the linked/created item.
        runCatching { catalogRepository.generalCatalog(forceRefresh = true) }
        _state.value = _state.value.copy(working = false, creating = false, resolvedBarcode = barcode)
    }

    fun reset() {
        startJob?.cancel(); manualJob?.cancel(); lookupJob?.cancel()
        _state.value = UnknownBarcodeState()
    }

    private fun Response<*>.failure(fallback: String): String {
        val body = runCatching { errorBody()?.string()?.trim()?.take(300) }.getOrNull()
        return if (!body.isNullOrBlank() && !body.startsWith("<")) body else "$fallback. Try again."
    }
}
