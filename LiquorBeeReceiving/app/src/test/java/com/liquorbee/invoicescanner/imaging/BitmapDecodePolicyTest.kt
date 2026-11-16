package com.liquorbee.invoicescanner.imaging

import org.junit.Assert.*
import org.junit.Test

class BitmapDecodePolicyTest {
    @Test fun normalLetterScanKeepsResolutionButSeventyTwoInchScanIsBounded() {
        assertEquals(1, BitmapDecodePolicy.sampleSize(2550, 3300))
        assertEquals(4, BitmapDecodePolicy.sampleSize(2550, 21600))
        assertEquals(4, BitmapDecodePolicy.sampleSize(21600, 2550))
    }
    @Test fun rejectsUnreadableDimensionsAndBoundsExtremeValuesWithoutOverflow() {
        assertThrows(IllegalArgumentException::class.java) { BitmapDecodePolicy.sampleSize(-1, -1) }
        assertThrows(IllegalArgumentException::class.java) { BitmapDecodePolicy.sampleSize(0, 100) }
        for ((w, h) in listOf(2550 to 21600, 50000 to 65535, Int.MAX_VALUE to Int.MAX_VALUE, 8192 to 8192)) {
            val sample = BitmapDecodePolicy.sampleSize(w, h)
            val decodedW = (w.toLong() + sample - 1) / sample
            val decodedH = (h.toLong() + sample - 1) / sample
            assertTrue(decodedW * decodedH <= BitmapDecodePolicy.MAX_PIXELS)
            assertTrue(decodedW <= BitmapDecodePolicy.MAX_EDGE && decodedH <= BitmapDecodePolicy.MAX_EDGE)
        }
    }
}
