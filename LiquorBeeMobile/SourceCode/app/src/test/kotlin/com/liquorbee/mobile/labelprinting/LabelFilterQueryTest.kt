package com.liquorbee.mobile.labelprinting

import com.liquorbee.mobile.core.network.LabelPrintingItemDto
import org.junit.Assert.*
import org.junit.Test

class LabelFilterQueryTest {
    @Test fun unfilteredLoadsAllButCategoryOnlyDoesNot() {
        assertEquals(listOf(0), labelFilterCodes(0, null, emptySet()))
        assertEquals(emptyList<Int>(), labelFilterCodes(0, null, setOf("category")))
        assertEquals(listOf(-4009), labelFilterCodes(0, -4009, setOf("category")))
        assertEquals(listOf(7, -4009), labelFilterCodes(7, -4009, setOf("category")))
    }
    @Test fun combinedFiltersRequireEveryResultAndPreserveRowOrder() {
        val a = LabelPrintingItemDto("a", upc = "1")
        val b = LabelPrintingItemDto("b", upc = "2")
        val c = LabelPrintingItemDto("c", upc = "3")
        assertEquals(listOf(b), intersectLabelResults(listOf(listOf(c, b, a), listOf(a, b), listOf(b, c))))
        assertTrue(intersectLabelResults(listOf(listOf(a), emptyList())).isEmpty())
    }
    @Test fun matchingDoesNotConfuseCaseRowsOrDifferentBarcodes() {
        val unit = LabelPrintingItemDto("a", upc = "1")
        val case = unit.copy(caseSizeRatio = 12.0)
        val other = unit.copy(upc = "2")
        assertEquals(listOf(case), intersectLabelResults(listOf(listOf(unit, case, other), listOf(case))))
    }
    @Test fun stockAndSearchApplyTogetherAfterServerFilters() {
        val rows = listOf(LabelPrintingItemDto("ABC", itemName = "Whiskey", upc = "123", qtyOnHand = 3),
            LabelPrintingItemDto("DEF", itemName = "Whiskey", upc = "456", qtyOnHand = 0))
        assertEquals(1, LabelPrintingUiState(items = rows, qtyOnHandOnly = true, appliedSearchFilter = "whiskey").filteredItems.size)
        assertEquals(listOf(rows[0]), LabelPrintingUiState(items = rows, appliedSearchFilter = "abc").filteredItems)
        assertEquals(listOf(rows[1]), LabelPrintingUiState(items = rows, appliedSearchFilter = "456").filteredItems)
    }
}
