package com.liquorbee.mobile.pricecheck

import org.junit.Assert.*
import org.junit.Test

class PriceCheckPricingTest {
    @Test fun markupAndMarginHaveDifferentBases() {
        assertEquals("12.50", priceFromPercentage("10", "25", false))
        assertEquals("12.50", priceFromPercentage("10", "20", true))
        assertEquals("25", pricePercentage("12.50", "10", false))
        assertEquals("20", pricePercentage("12.50", "10", true))
    }
    @Test fun invalidPercentagesCannotProduceASaveablePrice() {
        assertNull(priceFromPercentage("10", "100", true))
        assertNull(priceFromPercentage("10", "-100", false))
        assertNull(priceFromPercentage("0", "20", false))
        assertNull(priceFromPercentage("10", "", false))
        assertNull(priceFromPercentage("NaN", "20", true))
    }
    @Test fun zeroCostHasNoMarkupButStillHasMargin() {
        assertEquals("", pricePercentage("10", "0", false))
        assertEquals("100", pricePercentage("10", "0", true))
        assertEquals("", pricePercentage("0", "0", true))
    }
    @Test fun centsRoundHalfUpAndLossMakingPricesWork() {
        assertEquals("13.33", priceFromPercentage("10", "25", true))
        assertEquals("1.01", priceFromPercentage("1", "0.5", false))
        assertEquals("8.00", priceFromPercentage("10", "-20", false))
        assertEquals("-25", pricePercentage("8", "10", true))
    }
}
