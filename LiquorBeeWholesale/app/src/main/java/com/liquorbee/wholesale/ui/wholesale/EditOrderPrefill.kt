package com.liquorbee.wholesale.ui.wholesale

/** In-memory hand-off from an order's detail screen "Edit Order" action into the Place Order
 * tab - matches the web's editOrderFromManagement, which sets a pendingEditSession and switches
 * to the same Place Order tab rather than editing items in a dedicated modal. Place Order applies
 * this by selecting the matching account, loading its catalog, and pre-filling the cart by
 * matching itemCode against the freshly-loaded catalog (unmatched items are silently dropped,
 * same as the web). Cleared once PlaceOrderFragment consumes it. */
object EditOrderPrefill {
    data class Session(
        val orderId: String,
        val orderNumber: String?,
        val requestId: String?,
        val notes: String?,
        val items: List<Pair<String, Int>> // itemCode to quantity
    )

    private var pending: Session? = null

    fun set(session: Session) { pending = session }

    fun consume(): Session? {
        val result = pending
        pending = null
        return result
    }
}
