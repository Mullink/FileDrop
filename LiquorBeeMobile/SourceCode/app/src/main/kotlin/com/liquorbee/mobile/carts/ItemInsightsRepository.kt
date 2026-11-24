package com.liquorbee.mobile.carts

import com.liquorbee.mobile.core.network.ApiService
import com.liquorbee.mobile.core.network.PricingItemDto
import com.liquorbee.mobile.core.network.ShelfReviewItemDto
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shelf Review tiers, with the web page's rules (shelf-review.ts getTier) and suggested actions. */
enum class ShelfTier(val label: String, val action: String) {
    Dead("Dead Stock", "Discount deeply or return to vendor if eligible"),
    VerySlow("Very Slow", "Run a flash sale to move this out"),
    Slow("Slow", "Bundle into a mix & match deal"),
    Ok("Acceptable", "");

    companion object {
        fun of(row: ShelfReviewItemDto): ShelfTier = when {
            row.daysOfSupplyRemaining >= 9999 || row.sellThruPct == 0.0 -> Dead
            row.daysOfSupplyRemaining >= 180 || row.sellThruPct < 20 -> VerySlow
            row.daysOfSupplyRemaining >= 90 || row.sellThruPct < 40 -> Slow
            else -> Ok
        }
    }
}

data class ItemInsightsState(
    val pricing: Map<String, PricingItemDto> = emptyMap(),
    val shelf: Map<String, ShelfReviewItemDto> = emptyMap(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val pricingAvailable: Boolean = false,
    val shelfAvailable: Boolean = false
) {
    /** Rows are keyed by ItemId and by SKU so either lookup finds them. */
    fun pricingFor(itemId: String?, sku: String?): PricingItemDto? = key(itemId)?.let { pricing[it] } ?: key(sku)?.let { pricing["sku:$it"] }
    fun shelfFor(itemId: String?, sku: String?): ShelfReviewItemDto? = key(itemId)?.let { shelf[it] } ?: key(sku)?.let { shelf["sku:$it"] }
    private fun key(v: String?) = v?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
}

/**
 * Margin Monitoring and Shelf Review data for Scan's item card, loaded once per session (both are
 * whole-store reports) and looked up per scanned item. Either may be unavailable for an account;
 * Scan then just leaves that line out.
 */
class ItemInsightsRepository(private val api: ApiService) {
    private val _state = MutableStateFlow(ItemInsightsState())
    val state: StateFlow<ItemInsightsState> = _state.asStateFlow()
    private val mutex = Mutex()

    suspend fun ensureLoaded(force: Boolean = false) = mutex.withLock {
        if (_state.value.loaded && !force) return@withLock
        _state.value = _state.value.copy(loading = true)
        coroutineScope {
            val pricing = async { runCatching { loadPricing() }.getOrNull() }
            // Same 90-day lookback as the web Shelf Review, but every item rather than only recent receipts,
            // so any scanned item gets a status.
            val shelf = async { runCatching { api.getShelfReview(SHELF_LOOKBACK_DAYS, false).takeIf { it.isSuccessful }?.body() }.getOrNull() }
            val p = pricing.await()
            val s = shelf.await()
            _state.value = ItemInsightsState(
                pricing = p.orEmpty().index({ it.itemId }, { it.itemCode }),
                shelf = s.orEmpty().index({ it.itemId }, { it.sku }),
                loaded = true, loading = false,
                pricingAvailable = p != null, shelfAvailable = s != null
            )
        }
    }

    fun reset() { _state.value = ItemInsightsState() }

    // ABC stores use the Margin Monitoring list; General / Full stores use their markup list instead.
    private suspend fun loadPricing(): List<PricingItemDto>? {
        val abc = api.getItemsForPricing(0)
        if (abc.isSuccessful && !abc.body().isNullOrEmpty()) return abc.body()
        val general = api.getGeneralMarkupItems()
        return if (general.isSuccessful) general.body() else abc.body().takeIf { abc.isSuccessful }
    }

    private fun <T> List<T>.index(id: (T) -> String?, sku: (T) -> String?): Map<String, T> {
        val map = HashMap<String, T>(size * 2)
        forEach { row ->
            sku(row)?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }?.let { map.putIfAbsent("sku:$it", row) }
            id(row)?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }?.let { map[it] = row }
        }
        return map
    }

    private companion object { const val SHELF_LOOKBACK_DAYS = 90 }
}
