package com.liquorbee.mobile.carts

import android.content.Context
import com.liquorbee.mobile.core.network.ApiService
import com.liquorbee.mobile.core.network.ReorderCartConfigDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** How "needs reorder" is decided: the web Shopping Cart's N WEEK forecast, or the item's standard reorder point. */
enum class ReorderMode { Forecast, ReorderPoint }

data class ReorderSettings(val mode: ReorderMode = ReorderMode.Forecast, val weeks: Int = 1) {
    val label: String get() = when (mode) {
        ReorderMode.Forecast -> if (weeks == 1) "1-week forecast" else "$weeks-week forecast"
        ReorderMode.ReorderPoint -> "Reorder point"
    }
}

/** One item's reorder signal from the report. [needsReorder] drives the badge; the rest explains it. */
data class ReorderInfo(
    val needsReorder: Boolean,
    val mode: ReorderMode,
    val weeks: Int,
    val totalQty: Int?,
    val qtyOnHand: Int?,
    /** Forecast: units sold over the window. */
    val sales: Int?,
    /** Forecast: total qty minus sales (negative = short). Reorder point: on hand minus reorder point. */
    val deficit: Int?,
    val suggestedCases: Int?,
    val reorderPoint: Int?,
    // Report row details for the LB shopping cart's list (any may be missing - columns follow the
    // store's report column settings).
    val code: String = "",
    val description: String? = null,
    val unitsPerCase: Int? = null,
    val availableCases: Int? = null,
    val caseCost: Double? = null,
    val casesInLb: Int? = null
) {
    /** Cases to order: the report's suggestion, else enough cases to cover the shortfall. */
    val casesToOrder: Int get() {
        if (!needsReorder) return 0
        suggestedCases?.takeIf { it > 0 }?.let { return it }
        val short = -(deficit ?: 0)
        val upc = unitsPerCase?.takeIf { it > 0 } ?: return 1
        return maxOf(1, (short + upc - 1) / upc)
    }

    val summary: String get() = when (mode) {
        ReorderMode.Forecast -> {
            val window = if (weeks == 1) "1 wk" else "$weeks wks"
            if (needsReorder) buildString {
                append("Reorder · sold ${sales ?: 0} in $window, have ${totalQty ?: 0}")
                suggestedCases?.takeIf { it > 0 }?.let { append(" · suggest $it cs") }
            } else "Stock OK · sold ${sales ?: 0} in $window, have ${totalQty ?: 0}"
        }
        ReorderMode.ReorderPoint -> when {
            (reorderPoint ?: 0) <= 0 -> "No reorder point set"
            needsReorder -> "Reorder · ${qtyOnHand ?: 0} on hand, reorder point $reorderPoint"
            else -> "Stock OK · ${qtyOnHand ?: 0} on hand, reorder point $reorderPoint"
        }
    }
}

data class ReorderState(
    val settings: ReorderSettings = ReorderSettings(),
    val config: ReorderCartConfigDto? = null,
    val items: Map<String, ReorderInfo> = emptyMap(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val reportDate: String? = null,
    /** False outside Mississippi (or for inventory-category accounts): no reorder check, no staged orders. */
    val supported: Boolean = true
) {
    fun infoFor(code: String?): ReorderInfo? = code?.trim()?.uppercase()?.let { items[it] }
    /** Items the current setting says to reorder, most short first (the web report's order). */
    val reorderList: List<ReorderInfo> get() = items.values.filter { it.needsReorder }.sortedWith(compareBy<ReorderInfo> { it.deficit ?: 0 }.thenByDescending { it.sales ?: 0 })
    val canUseReorderPoint: Boolean get() = supported && config?.addReorderReportInCart == true
    val canStageOrders: Boolean get() = supported && config != null
    val weekOptions: List<Int> get() = (1..8).toList() + if (config?.enableTwelveWeekForecast == true) listOf(12) else emptyList()
}

/**
 * The web Shopping Cart's all-data report, loaded once per setting and kept in memory so Scan and the
 * LB cart can show whether each item needs reordering. The setting (forecast weeks or reorder point)
 * is remembered on the device.
 */
class ReorderRepository(
    context: Context,
    private val api: ApiService,
    private val catalogRepository: CatalogRepository
) {
    private val prefs = context.getSharedPreferences("liquorbee_reorder", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(ReorderState(settings = loadSettings()))
    val state: StateFlow<ReorderState> = _state.asStateFlow()
    private val mutex = Mutex()

    private fun loadSettings(): ReorderSettings {
        val mode = runCatching { ReorderMode.valueOf(prefs.getString(KEY_MODE, null) ?: "") }.getOrDefault(ReorderMode.Forecast)
        return ReorderSettings(mode, prefs.getInt(KEY_WEEKS, 1).coerceIn(1, 12))
    }

    /** Loads the report for the current setting unless it's already loaded (or [force]). */
    suspend fun ensureLoaded(force: Boolean = false) = mutex.withLock {
        if (_state.value.loaded && !force) return@withLock
        _state.value = _state.value.copy(loading = true, error = null)
        try {
            val config = _state.value.config ?: api.getReorderCartConfig().let { r ->
                if (!r.isSuccessful) error("Couldn't load reorder options. Try again.")
                r.body() ?: ReorderCartConfigDto()
            }
            // Saved prefs, not state: a load already in flight must not undo a setting saved meanwhile.
            val market = runCatching { catalogRepository.market() }.getOrNull()
            if (market != LbMarket.MISSISSIPPI || config.useInventoryCategories) {
                _state.value = _state.value.copy(config = config, items = emptyMap(), loaded = true, loading = false,
                    error = null, supported = false)
                return@withLock
            }
            var settings = loadSettings()
            // A saved reorder-point setting from an account that can't run that report falls back to forecast.
            if (settings.mode == ReorderMode.ReorderPoint && !config.addReorderReportInCart) {
                settings = settings.copy(mode = ReorderMode.Forecast)
            }
            if (settings.weeks == 12 && !config.enableTwelveWeekForecast) settings = settings.copy(weeks = 8)
            val date = reportDate()
            val vendor = if (settings.mode == ReorderMode.ReorderPoint) "MS-ABC-BAILMENT-REORDERPOINT" else "MS-ABC-BAILMENT"
            val weeksOut = if (settings.mode == ReorderMode.ReorderPoint) 0 else settings.weeks
            val response = api.getDynamicSalesReport(weeksOut, date, true, vendor)
            if (!response.isSuccessful) error("Couldn't load reorder info. Try again.")
            val items = HashMap<String, ReorderInfo>()
            response.body().orEmpty().forEach { el ->
                val row = runCatching { el.jsonObject }.getOrNull() ?: return@forEach
                val code = row.text("Code")?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return@forEach
                items[code] = parse(row, settings)
            }
            _state.value = _state.value.copy(settings = settings, config = config, items = items, loaded = true,
                loading = false, error = null, reportDate = date, supported = true)
        } catch (e: Exception) {
            _state.value = _state.value.copy(loading = false, error = e.message ?: "Could not load the reorder report.")
            throw e
        }
    }

    /** Saves the setting and reloads the report for it. */
    suspend fun updateSettings(settings: ReorderSettings) {
        prefs.edit().putString(KEY_MODE, settings.mode.name).putInt(KEY_WEEKS, settings.weeks).apply()
        _state.value = _state.value.copy(settings = settings, loaded = false, items = emptyMap())
        ensureLoaded(force = true)
    }

    fun reset() { _state.value = ReorderState(settings = loadSettings()) }

    /** Last POS sync date like the web cart (MM/DD/YYYY -> MM-dd-yyyy), or today when there isn't one. */
    private suspend fun reportDate(): String {
        val raw = runCatching { api.getLastSyncDate().takeIf { it.isSuccessful }?.body()?.string() }.getOrNull()
            ?.trim()?.trim('"')
        val m = raw?.let { Regex("""^(\d{1,2})/(\d{1,2})/(\d{4})$""").find(it) }
        return if (m != null) {
            val (mm, dd, yyyy) = m.destructured
            "${mm.padStart(2, '0')}-${dd.padStart(2, '0')}-$yyyy"
        } else SimpleDateFormat("MM-dd-yyyy", Locale.US).format(Date())
    }

    private fun parse(row: JsonObject, settings: ReorderSettings): ReorderInfo {
        val totalQty = row.int("TotalQty")
        val onHand = row.int("QtyOnHand")
        val reorderPoint = row.int("ReorderPoint")
        val info = parseSignal(row, settings, totalQty, onHand, reorderPoint)
        return info.copy(code = row.text("Code")?.trim().orEmpty(), description = row.text("Description"),
            unitsPerCase = row.int("UnitsPerCase"), availableCases = row.int("AvailableCases"),
            caseCost = row.dbl("AbcCaseCost") ?: row.dbl("CaseCost"), casesInLb = row.int("CasesinLB"))
    }

    private fun parseSignal(row: JsonObject, settings: ReorderSettings, totalQty: Int?, onHand: Int?, reorderPoint: Int?): ReorderInfo {
        return if (settings.mode == ReorderMode.ReorderPoint) {
            val deficit = row.int("ReorderPointDeficit") ?: if (reorderPoint != null && onHand != null) onHand - reorderPoint else null
            // Same rule as the web REORDERPOINT report: listed when on hand is below the reorder point.
            ReorderInfo(needsReorder = (reorderPoint ?: 0) > 0 && (deficit ?: 0) < 0, mode = settings.mode, weeks = 0,
                totalQty = totalQty, qtyOnHand = onHand, sales = null, deficit = deficit, suggestedCases = null, reorderPoint = reorderPoint)
        } else {
            val word = WEEK_WORDS[settings.weeks] ?: "One"
            val sales = row.int("${word}WeeksSales")
            val deficit = row.int("${word}WeekSalesDeficit") ?: if (totalQty != null && sales != null) totalQty - sales else null
            // TotalQty is only returned when it's a visible column in the store's report settings; the
            // deficit and sales columns always are, and deficit = total - sales.
            val total = totalQty ?: if (deficit != null && sales != null) deficit + sales else null
            // Same rule as the stored procs' forecast filter: what sold in the window would use up everything on hand + on order.
            ReorderInfo(needsReorder = (sales ?: 0) > 0 && (deficit ?: 0) <= 0, mode = settings.mode, weeks = settings.weeks,
                totalQty = total, qtyOnHand = onHand, sales = sales, deficit = deficit,
                suggestedCases = row.int("${word}WeekSuggestedCases"), reorderPoint = reorderPoint)
        }
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] ?: entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value)
            ?.let { it as? JsonPrimitive }?.contentOrNull

    private fun JsonObject.dbl(key: String): Double? {
        val p = (this[key] ?: entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value) as? JsonPrimitive ?: return null
        return p.doubleOrNull ?: p.contentOrNull?.toDoubleOrNull()
    }

    private fun JsonObject.int(key: String): Int? {
        val p = (this[key] ?: entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value) as? JsonPrimitive ?: return null
        return (p.doubleOrNull ?: p.contentOrNull?.toDoubleOrNull())?.let { Math.round(it).toInt() }
    }

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_WEEKS = "weeks"
        val WEEK_WORDS = mapOf(1 to "One", 2 to "Two", 3 to "Three", 4 to "Four", 5 to "Five", 6 to "Six",
            7 to "Seven", 8 to "Eight", 12 to "Twelve")
    }
}
