package com.liquorbee.invoicescanner.network

import com.google.gson.JsonObject
import retrofit2.HttpException
import retrofit2.Response

data class ScanOrderReview(val isEditable: Boolean, val batchId: String,
    val reconciliation: ScanReconciliation, val lines: List<ScanLineReview>) {
    val readyForPricing: Boolean get() = lines.isNotEmpty() && lines.none { it.needsReview } &&
        reconciliation.isBalanced && reconciliation.issues.isEmpty()
}
data class ScanLineReview(val lineId: Long, val itemConfirmed: Boolean, val version: String,
    val source: JsonObject, val issues: Map<String,String>, val isMiscCharge: Boolean = false) {
    val needsReview: Boolean get() = issues.isNotEmpty()
}
data class ScanReconciliation(val totals: ScanTotals, val lineTotal: Double?, val calculatedTotal: Double?,
    val difference: Double?, val isBalanced: Boolean, val issues: List<String>)
data class ScanTotals(val invoiceTotal: Double?, val tax: Double?, val fees: Double?, val invoiceDiscount: Double?)
data class ScanItemChoice(val itemId: String, val newItem: ScanNewItem? = null)
data class ScanNewItem(val itemCode: String?, val itemName: String?, val upcCode: String?)
fun Response<Unit>.requireSuccess() { if (!isSuccessful) throw HttpException(this) }
fun apiError(error: Exception): String {
    return if (error is HttpException) {
        val body = error.response()?.errorBody()?.string()?.trim()?.trim('"')
        if (!body.isNullOrBlank() && !body.startsWith("<") && body.length < 2500) body
        else "Request failed (${error.code()}). Please try again."
    } else error.message ?: "Unable to connect. Please try again."
}
fun JsonObject.text(key: String): String = get(key)?.takeUnless { it.isJsonNull }?.asString ?: ""
