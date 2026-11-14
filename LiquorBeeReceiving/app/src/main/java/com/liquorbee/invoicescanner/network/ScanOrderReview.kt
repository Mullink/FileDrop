package com.liquorbee.invoicescanner.network

import com.google.gson.JsonObject
import retrofit2.HttpException
import retrofit2.Response

data class ScanOrderReview(val isEditable: Boolean, val batchId: String,
    val reconciliation: ScanReconciliation, val lines: List<ScanLineReview>) {
    val readyForPricing: Boolean get() = lines.isNotEmpty() && lines.none { it.needsReview }
}
data class ScanLineReview(val lineId: Long, val itemConfirmed: Boolean, val version: String,
    val source: JsonObject, val issues: Map<String,String>, val isMiscCharge: Boolean = false, val unitEdit: ScanUnitCorrection? = null,
    val itemId:String?=null,val itemCode:String?=null,val itemName:String?=null,val itemUpc:String?=null,
    val isNewItem:Boolean=false,val canEditIdentifiers:Boolean?=null,val isReturn:Boolean=false) {
    val needsReview: Boolean get() = issues.isNotEmpty()
}
data class ScanReconciliation(val totals: ScanTotals, val lineTotal: Double?, val calculatedTotal: Double?,
    val difference: Double?, val isBalanced: Boolean, val issues: List<String>)
data class ScanTotals(val invoiceTotal: Double?, val tax: Double?, val fees: Double?, val invoiceDiscount: Double?)
data class ScanItemRemoval(val version: String)
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

data class ScanCorrectionSave(val lineId:Long,val version:String,val unitEdit:ScanUnitCorrection?,
    val selectedItemId:String?=null,val barcode:String?=null,val sku:String?=null)
fun ScanLineReview.confirmChoice(fallback:PurchaseOrderLineDto?):ScanItemChoice? {
    if(isMiscCharge)return null
    val id=itemId ?: fallback?.itemId
    val upc=itemUpc ?: fallback?.upcCode
    if(upc.isNullOrBlank())return null
    if(!id.isNullOrBlank())return ScanItemChoice(id)
    val code=itemCode ?: fallback?.itemCode;val name=itemName ?: fallback?.itemName
    return if(!code.isNullOrBlank() && !name.isNullOrBlank())ScanItemChoice("",ScanNewItem(code,name,upc)) else null
}
