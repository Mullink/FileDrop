package com.liquorbee.mobile.carts

import com.liquorbee.mobile.core.network.ApiService
import com.liquorbee.mobile.core.network.SubCustomerCatalogItemDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The store's item catalog (SubCustomers/GetCatalog - the same endpoint the Wholesale app's Place
 * Order uses), cached in memory for the session because it can be 10k-30k rows. The General
 * Customer catalog drives scanning/search; a linked account's catalog is only fetched to price
 * an item added to that account's cart.
 */
class CatalogRepository(private val api: ApiService) {
    private val mutex = Mutex()
    private var customerId: Int? = null
    private var general: List<SubCustomerCatalogItemDto>? = null
    private val accountCatalogs = mutableMapOf<String, Map<String, SubCustomerCatalogItemDto>>()
    private var accounts: List<WholesaleAccount>? = null

    private suspend fun currentCustomerId(): Int {
        customerId?.let { return it }
        val response = api.getCurrentCustomerId()
        val id = response.body()
        if (!response.isSuccessful || id == null) error("Could not identify your store.")
        customerId = id
        return id
    }

    suspend fun generalCatalog(forceRefresh: Boolean = false): List<SubCustomerCatalogItemDto> = mutex.withLock {
        if (!forceRefresh) general?.let { return it }
        val response = api.getOrderCatalog(currentCustomerId(), "")
        if (!response.isSuccessful) error("Couldn't load your items. Check your connection and try again.")
        val items = response.body().orEmpty().filter { !it.itemCode.isNullOrBlank() }
        general = items
        items
    }

    /** Linked (status "Linked") wholesale accounts, for the long-press account picker. */
    suspend fun linkedAccounts(): List<WholesaleAccount> {
        accounts?.let { return it }
        val response = api.getManagementView()
        if (!response.isSuccessful) error("Couldn't load your wholesale accounts. Try again.")
        val result = response.body()?.requests.orEmpty()
            .filter { it.status == "Linked" && it.requestId.isNotBlank() }
            .map {
                WholesaleAccount(
                    key = "req:${it.requestId}",
                    label = it.toBusinessName ?: it.recipientBusinessName ?: it.toEmailAddress ?: "(Unnamed account)",
                    requestId = it.requestId,
                    email = it.toEmailAddress,
                    customerId = it.fromId,
                    priceListId = it.priceListId.orEmpty()
                )
            }
            .sortedBy { it.label.lowercase() }
        accounts = result
        return result
    }

    /**
     * Unit price for an item in a given wholesale cart: retail price for General Customer, the
     * account's negotiated price list price (subCustomerPrice) for a linked account - the same
     * rule the Wholesale app and web Place Order use.
     */
    suspend fun priceFor(account: WholesaleAccount, item: SubCustomerCatalogItemDto): Double {
        if (account.isGeneral) return item.retailPrice
        val code = item.itemCode ?: return item.retailPrice
        val catalog = mutex.withLock {
            accountCatalogs[account.key] ?: run {
                val id = if (account.customerId != 0) account.customerId else currentCustomerId()
                val response = api.getOrderCatalog(id, account.priceListId)
                if (!response.isSuccessful) error("Couldn't load ${account.label}'s prices. Try again.")
                response.body().orEmpty().filter { it.itemCode != null }.associateBy { it.itemCode!! }
                    .also { accountCatalogs[account.key] = it }
            }
        }
        val match = catalog[code] ?: error("${item.itemName ?: code} isn't in ${account.label}'s price list.")
        return match.subCustomerPrice
    }

    private var metadata: ItemMetaIndex? = null

    /**
     * Per-item size / units per case from QuanticInventoryMetaDataReport. Best effort: an empty
     * index if it can't load, so callers fall back to reading the size from the item name.
     */
    suspend fun itemMetadata(): ItemMetaIndex {
        metadata?.let { return it }
        val rows = runCatching { api.getItemMetaDataList().takeIf { it.isSuccessful }?.body() }.getOrNull()
            ?: return ItemMetaIndex(emptyList())
        return ItemMetaIndex(rows).also { metadata = it }
    }

    /** Call after changing an item's size/units so the next lookup re-reads them. */
    fun invalidateMetadata() { metadata = null }

    private val reserve = mutableMapOf<String, Pair<Long, Int>>()

    /**
     * Units on order from the distributor that haven't shipped, for one item code. Cached for a
     * minute so re-scanning the same item doesn't refetch. Null when unavailable (e.g. a server
     * without the endpoint) - callers just hide the value.
     */
    suspend fun qtyOnReserve(code: String): Int? {
        val now = System.currentTimeMillis()
        synchronized(reserve) { reserve[code]?.takeIf { now - it.first < 60_000 }?.let { return it.second } }
        val qty = runCatching { api.getQtyOnReserve(code) }.getOrNull()
            ?.takeIf { it.isSuccessful }?.body()?.qtyOnReserve ?: return null
        synchronized(reserve) { reserve[code] = now to qty }
        return qty
    }

    private var market: String? = null

    /** The store's two-letter state ("MS", "AL", ...) - decides whether the LB cart can check out. */
    suspend fun market(): String {
        market?.let { return it }
        val response = api.getCustomerState()
        if (!response.isSuccessful) error("Couldn't load your store settings. Try again.")
        val value = response.body()?.string().orEmpty().trim().trim('"').uppercase()
        market = value
        return value
    }

    fun clear() {
        metadata = null
        synchronized(reserve) { reserve.clear() }
        market = null
        customerId = null
        general = null
        accounts = null
        accountCatalogs.clear()
    }
}
