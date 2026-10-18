package com.liquorbee.invoicescanner.ui

/** Page views retain the original objects; edits remain part of the complete invoice. */
data class InvoicePage<T>(val number: Int, val count: Int, val start: Int, val total: Int, val items: List<T>) {
    val description: String get() = if(total==0) "No items match this filter" else "Items ${start+1}–${start+items.size} of $total • Page $number of $count"
}
fun <T> invoicePage(items: List<T>, requestedPage: Int, pageSize: Int = 20): InvoicePage<T> {
    require(pageSize > 0)
    val count=maxOf(1,(items.size+pageSize-1)/pageSize)
    val page=requestedPage.coerceIn(1,count)
    val start=(page-1)*pageSize
    return InvoicePage(page,count,start,items.size,items.subList(start,minOf(start+pageSize,items.size)))
}
