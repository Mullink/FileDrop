package com.liquorbee.mobile.labelprinting

import com.liquorbee.mobile.core.network.LabelPrintingItemDto

internal fun labelFilterCodes(list: Int, size: Int?, categories: Set<String>): List<Int> = buildList {
    if (list != 0 || (size == null && categories.isEmpty())) add(list)
    size?.let { add(it) }
}

internal fun intersectLabelResults(results: List<List<LabelPrintingItemDto>>): List<LabelPrintingItemDto> {
    fun key(item: LabelPrintingItemDto) = Triple(item.itemCode, item.upc.orEmpty(), item.caseSizeRatio ?: 0.0)
    val remaining = results.drop(1).map { rows -> rows.map(::key).toSet() }
    return results.firstOrNull().orEmpty().filter { item -> remaining.all { key(item) in it } }
}
