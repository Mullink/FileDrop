package com.liquorbee.mobile.carts

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val GENERAL_ACCOUNT_KEY = "general"

/** Who a wholesale cart is for: the General Customer (retail) or one linked sub-customer account. */
@Serializable
data class WholesaleAccount(
    val key: String,
    val label: String,
    val requestId: String? = null,
    val email: String? = null,
    val customerId: Int = 0,
    val priceListId: String = ""
) {
    val isGeneral: Boolean get() = key == GENERAL_ACCOUNT_KEY

    companion object {
        val General = WholesaleAccount(key = GENERAL_ACCOUNT_KEY, label = "General Customer")
    }
}

@Serializable
data class OrderLine(
    val itemCode: String,
    val itemName: String? = null,
    val upc: String? = null,
    val unitPrice: Double = 0.0,
    val qty: Int
) {
    val lineTotal: Double get() = unitPrice * qty
}

@Serializable
data class WholesaleCart(val account: WholesaleAccount, val lines: List<OrderLine> = emptyList()) {
    val count: Int get() = lines.sumOf { it.qty }
    val total: Double get() = lines.sumOf { it.lineTotal }
}

/** One LiquorBee (state ordering) cart line, in cases. `code` is the item's SKU/state code. */
@Serializable
data class LbLine(val code: String, val itemName: String? = null, val upc: String? = null, val cases: Int)

@Serializable
data class CartsSnapshot(
    val wholesale: List<WholesaleCart> = emptyList(),
    val lb: List<LbLine> = emptyList(),
    /** Who Scan's Wholesale button builds an order for (long-press to change). Null = General Customer. */
    val defaultAccount: WholesaleAccount? = null
) {
    val currentAccount: WholesaleAccount get() = defaultAccount ?: WholesaleAccount.General
    fun wholesaleQty(accountKey: String, itemCode: String?): Int =
        wholesaleCart(accountKey)?.lines?.firstOrNull { it.itemCode == itemCode }?.qty ?: 0
    fun lbQty(itemCode: String?): Int = lb.firstOrNull { it.code == itemCode }?.cases ?: 0

    fun wholesaleCart(key: String): WholesaleCart? = wholesale.firstOrNull { it.account.key == key }
    val wholesaleCount: Int get() = wholesale.sumOf { it.count }
    val lbCount: Int get() = lb.sumOf { it.cases }
}

/**
 * The wholesale and LB carts, kept on the device (survives app restarts) until they're submitted.
 * Single app-wide instance so the Scan screen and the Carts screen always see the same carts.
 * Lines added most recently move to the top of their cart.
 */
class CartStore(context: Context) {
    private val prefs = context.getSharedPreferences("liquorbee_carts", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<CartsSnapshot> = _state.asStateFlow()

    private fun load(): CartsSnapshot = try {
        prefs.getString(KEY, null)?.let { json.decodeFromString(CartsSnapshot.serializer(), it) } ?: CartsSnapshot()
    } catch (_: Exception) { CartsSnapshot() }

    @Synchronized
    private fun update(transform: (CartsSnapshot) -> CartsSnapshot) {
        val next = transform(_state.value)
        // Drop empty non-general carts so the account chips only show carts with something in them.
        val cleaned = next.copy(wholesale = next.wholesale.filter { it.lines.isNotEmpty() || it.account.isGeneral })
        _state.value = cleaned
        prefs.edit().putString(KEY, json.encodeToString(CartsSnapshot.serializer(), cleaned)).apply()
    }

    fun addWholesale(account: WholesaleAccount, line: OrderLine) = update { s ->
        val carts = s.wholesale.toMutableList()
        val index = carts.indexOfFirst { it.account.key == account.key }
        val cart = if (index >= 0) carts[index] else WholesaleCart(account)
        val existing = cart.lines.firstOrNull { it.itemCode == line.itemCode }
        val merged = existing?.copy(qty = existing.qty + line.qty, unitPrice = line.unitPrice) ?: line
        val updated = cart.copy(lines = listOf(merged) + cart.lines.filterNot { it.itemCode == line.itemCode })
        if (index >= 0) carts[index] = updated else carts.add(updated)
        s.copy(wholesale = carts)
    }

    fun setWholesaleQty(accountKey: String, itemCode: String, qty: Int) = update { s ->
        s.copy(wholesale = s.wholesale.map { cart ->
            if (cart.account.key != accountKey) cart
            else cart.copy(lines = if (qty <= 0) cart.lines.filterNot { it.itemCode == itemCode }
                else cart.lines.map { if (it.itemCode == itemCode) it.copy(qty = qty) else it })
        })
    }

    fun clearWholesale(accountKey: String) = update { s ->
        s.copy(wholesale = s.wholesale.map { if (it.account.key == accountKey) it.copy(lines = emptyList()) else it })
    }

    fun addLb(code: String, itemName: String?, upc: String?, cases: Int) = update { s ->
        val existing = s.lb.firstOrNull { it.code == code }
        val merged = existing?.copy(cases = existing.cases + cases) ?: LbLine(code, itemName, upc, cases)
        s.copy(lb = listOf(merged) + s.lb.filterNot { it.code == code })
    }

    fun setLbQty(code: String, cases: Int) = update { s ->
        s.copy(lb = if (cases <= 0) s.lb.filterNot { it.code == code } else s.lb.map { if (it.code == code) it.copy(cases = cases) else it })
    }

    fun clearLb() = update { it.copy(lb = emptyList()) }

    /** Loading a staged order replaces the LB cart, like the web Shopping Cart's "Start from". */
    fun replaceLb(lines: List<LbLine>) = update { it.copy(lb = lines) }

    fun clearAll() = update { CartsSnapshot(defaultAccount = it.defaultAccount) }

    fun setDefaultAccount(account: WholesaleAccount) = update { it.copy(defaultAccount = account.takeUnless { a -> a.isGeneral }) }

    private companion object { const val KEY = "carts_v1" }
}
