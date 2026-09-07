package com.liquorbee.wholesale.ui.wholesale

/** In-memory hand-off from POS Customers' "Send request" action to the Send Requests tab -
 * matches the web's prefillFromQuanticCustomer, which only prefills the form and switches tabs
 * (there's no dedicated "convert POS customer to wholesale link" endpoint - it's the same
 * CreateRequest flow as any other invitation). Cleared once SendRequestsFragment consumes it. */
object SendRequestPrefill {
    var businessName: String? = null
    var email: String? = null
    var priceListId: String? = null

    fun consume(): Triple<String?, String?, String?>? {
        if (businessName == null && email == null && priceListId == null) return null
        val result = Triple(businessName, email, priceListId)
        businessName = null
        email = null
        priceListId = null
        return result
    }
}
