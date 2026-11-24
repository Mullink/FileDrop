package com.liquorbee.mobile.carts

/**
 * LB (state ordering) checkout, staged orders and the reorder check only support Mississippi
 * (MS-ABC-BAILMENT) for now. Every other market, Alabama included, keeps the LB items as a plain list.
 */
object LbMarket {
    const val MISSISSIPPI = "MS"
    const val ALABAMA = "AL"

    fun canCheckout(market: String): Boolean = market == MISSISSIPPI

    // Mississippi "Purchase now" needs at least this many cases in the cart (web: checkForPurchaseButton).
    const val MS_PURCHASE_NOW_MIN_CASES = 10

    val DATE_PREFERENCES = listOf("SoonestAvailable" to "Soonest available", "Tomorrow" to "Tomorrow", "DayOfWeek" to "Specific day")
    val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val FALLBACKS = listOf("NextClosest" to "Use the next closest date", "ThrowException" to "Don't submit")
}
