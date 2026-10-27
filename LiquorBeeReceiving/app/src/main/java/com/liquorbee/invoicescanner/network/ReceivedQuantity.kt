package com.liquorbee.invoicescanner.network

/** Keep the signed stock delta through manual entry, staged edits and JSON serialization. */
fun applyReceivedQuantity(line: PurchaseOrderLineDto, quantity: Int, byUnits: Boolean) {
    val signed = if (line.isReturn && quantity > 0) -quantity else quantity
    if (byUnits) {
        line.shippedUnits = signed
    } else {
        line.shippedCases = signed
        line.shippedUnits = signed * (line.unitsPerCase ?: 1)
    }
}
