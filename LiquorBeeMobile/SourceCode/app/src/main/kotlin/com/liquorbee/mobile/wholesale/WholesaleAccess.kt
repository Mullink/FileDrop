package com.liquorbee.mobile.wholesale

import android.content.Context
import com.liquorbee.mobile.core.network.ApiService
import com.liquorbee.mobile.core.network.WholesaleOrderDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Whether this store sells wholesale - the same switch as the web's Wholesale Manager menu
 * (LiquorBeePosCustomers.WholesaleManagerEnabled via Profile/GetPosCustomerSettings).
 * The last answer is kept on the device so Home/Scan show the right tiles instantly; it's
 * re-checked each time Home opens. Unknown (never checked) counts as off.
 *
 * Also keeps the store's last orders list on the device so the Orders tab paints before any
 * network call returns.
 */
class WholesaleAccess(context: Context, private val api: ApiService) {
    private val prefs = context.getSharedPreferences("liquorbee_wholesale", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val ordersSerializer = ListSerializer(WholesaleOrderDto.serializer())

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    suspend fun refresh() {
        val response = runCatching { api.getPosCustomerSettings() }.getOrNull() ?: return
        if (!response.isSuccessful) return
        val on = response.body()?.wholesaleSettings?.wholesaleManagerEnabled == true
        _enabled.value = on
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
    }

    fun savedOrders(): List<WholesaleOrderDto>? = try {
        prefs.getString(KEY_ORDERS, null)?.let { json.decodeFromString(ordersSerializer, it) }
    } catch (_: Exception) { null }

    fun saveOrders(orders: List<WholesaleOrderDto>) {
        // Headers only, capped so the prefs file stays small.
        val rows = orders.take(MAX_SAVED_ORDERS).map { it.copy(items = null) }
        prefs.edit().putString(KEY_ORDERS, json.encodeToString(ordersSerializer, rows)).apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
        _enabled.value = false
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_ORDERS = "orders"
        const val MAX_SAVED_ORDERS = 300
    }
}
