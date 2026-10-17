package com.liquorbee.mobile.pricecheck

import com.liquorbee.mobile.core.network.PriceCheckEditDto
import org.junit.Assert.*
import org.junit.Test

class PriceCheckDraftTest {
    private val item = PriceCheckEditDto("item", "revision", "Name", "00123", "00456", 10.0, 5.0, 3, "vendor",
        description = "Description", categoryId = "old-category")

    @Test fun categorySelectionSendsCategoryItemIdAndFlagsAreIntegers() {
        val draft = PriceCheckDraft.from(item).copy(categoryId = "category-item-123", active = false, tracking = false, description = "Changed description")
        assertTrue(draft.isDirty(item))
        val request = draft.request(item)
        assertEquals("category-item-123", request.categoryId)
        assertEquals(0, request.status)
        assertEquals(0, request.trackInventory)
        assertEquals("Name", request.name)
        assertEquals("Changed description", request.description)
        assertEquals("00123", request.upc)
        assertEquals("00456", request.sku)
    }
    @Test fun savedItemResetsDirtyStateAndCanBeReactivated() {
        val saved = item.copy(status = 0, trackInventory = 0)
        val draft = PriceCheckDraft.from(saved)
        assertFalse(draft.isDirty(saved))
        val request = draft.copy(active = true, tracking = true).request(saved)
        assertEquals(1, request.status)
        assertEquals(1, request.trackInventory)
    }
    @Test fun invalidEditsCannotProduceASaveRequest() {
        assertNotNull(PriceCheckDraft.from(item).copy(quantity = "3.5").error(item))
        assertNotNull(PriceCheckDraft.from(item).copy(price = "NaN").error(item))
        assertNotNull(PriceCheckDraft.from(item).copy(description = "a".repeat(2001)).error(item))
        assertNotNull(PriceCheckDraft.from(item).withPercent("100", true).error(item))
    }
    @Test fun pricingEditsRecalculateBothPercentages() {
        val draft = PriceCheckDraft.from(item).withPricing(price = "12.5", cost = "10")
        assertEquals("25", draft.markup)
        assertEquals("20", draft.margin)
        assertEquals("12.50", draft.withPercent("20", true).price)
    }
    @Test fun adjustmentUsesCurrentDraftAndRequiresNormalSave() {
        val original = PriceCheckDraft.from(item)
        val edited = original.copy(quantity = "10")
        val added = edited.copy(quantity = adjustedQuantity(edited.quantity, "4", false)!!)
        val subtracted = added.copy(quantity = adjustedQuantity(added.quantity, "6", true)!!)
        assertEquals("3", original.quantity)
        assertEquals("14", added.quantity)
        assertEquals("8", subtracted.quantity)
        assertTrue(subtracted.isDirty(item))
        assertEquals(8, subtracted.request(item).quantity)
    }
    @Test fun quantityAdjustmentRejectsInvalidAmountsAndOverflow() {
        for (amount in listOf("", "0", "-2", "1.5", "abc", "2147483648")) {
            assertNull(adjustedQuantity("10", amount, false))
        }
        assertNull(adjustedQuantity("", "2", false))
        assertNull(adjustedQuantity("2147483647", "1", false))
        assertNull(adjustedQuantity("-2147483648", "1", true))
        assertEquals("0", adjustedQuantity("2", "2", true))
        assertEquals("-1", adjustedQuantity("2", "3", true))
    }
}
