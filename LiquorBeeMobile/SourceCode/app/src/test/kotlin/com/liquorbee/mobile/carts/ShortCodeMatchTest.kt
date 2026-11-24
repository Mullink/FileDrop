package com.liquorbee.mobile.carts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortCodeMatchTest {
    @Test fun middleZerosRemoved() {
        assertEquals("877093", squeezeZeros("87000007093"))
        assertTrue(shortCodeMatches("87000007093", "877093"))
        assertTrue(shortCodeMatches("087000007093", "877093"))
        // Without the check digit on the short code.
        assertTrue(shortCodeMatches("87000007093", "87709"))
    }

    @Test fun upcEExpands() {
        assertEquals("08700000709", upcEToUpcA("08770903"))
        assertTrue(shortCodeMatches("087000007093", "08770903"))
        assertEquals("01230000045", upcEToUpcA("123453"))
    }

    @Test fun exactWinsAndShortIsOnlyAFallback() {
        val items = listOf("87000007093", "877093")
        assertEquals(listOf("877093"), matchBarcode(items, "877093") { it })
        assertEquals(listOf("87000007093"), matchBarcode(listOf("87000007093", "12345678901"), "877093") { it })
    }

    @Test fun doesNotMatchUnrelatedOrNonDigitCodes() {
        assertFalse(shortCodeMatches("87000007093", "877094"))
        assertFalse(shortCodeMatches("MISSING-1000", "1000"))
        assertFalse(shortCodeMatches("87000007093", "87000007093X"))
        assertFalse(shortCodeMatches("123", "12"))
    }
}
