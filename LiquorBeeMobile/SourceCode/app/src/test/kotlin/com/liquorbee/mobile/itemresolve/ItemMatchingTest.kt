package com.liquorbee.mobile.itemresolve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemMatchingTest {
    private fun s(a: String, aCode: String?, b: String, bCode: String?) =
        ItemMatching.score(ItemMatching.normalize(a, aCode), ItemMatching.normalize(b, bCode))

    @Test fun sizes() {
        assertEquals("750", ItemMatching.size("100 PIPERS (750ml) - 9366"))
        assertEquals("1750", ItemMatching.size("100 PIPERS 1.75L 9368"))
        assertEquals(null, ItemMatching.size("CARD FEE"))
        assertEquals("750", ItemMatching.size("PARROT BAY COCONUT 750"))
        assertEquals("1750", ItemMatching.size("TITOS HANDMADE VODKA 1.75"))
    }

    @Test fun bareSizeMismatchIsExcluded() =
        assertTrue(s("PARROT BAY COCONUT 90 PROOF (1.75L) - 43258", "43258", "PARROT BAY COCONUT 750", "MISSING-1000488") < ItemMatching.CANDIDATE_THRESHOLD)

    @Test fun sameItemDifferentFormatting() =
        assertTrue(s("100 PIPERS (1.75L) - 9368", "9368", "100 PIPERS 1.75L 9368", "9368") > 0.9)

    @Test fun differentSizeIsExcluded() =
        assertTrue(s("100 PIPERS (750ml) - 9366", "9366", "100 PIPERS 1.75L 9368", "9368") < ItemMatching.CANDIDATE_THRESHOLD)

    // Real case: master catalog name vs. a store item that was typed with the brand owner prefix
    // and never had its UPC scanned in.
    @Test fun extraBrandPrefixStillMatches() =
        assertTrue(s("PARROT BAY COCONUT 90 PROOF (1.75L) - 43258", "43258",
            "CAPTAIN MORGAN PARROT BAY COCONUT 1.75L", "MISSING-1000299") >= ItemMatching.CANDIDATE_THRESHOLD)

    // "100 PIPERS" with no size in the name, but metadata says 750ml - a 1.75L scan must not match it.
    @Test fun metadataSizeIsUsedWhenNameHasNone() {
        val scan = ItemMatching.normalize("100 PIPERS (1.75L)")
        val noSizeName = ItemMatching.normalize("100 PIPERS", null, fallbackSize = "750ml")
        assertTrue(ItemMatching.score(scan, noSizeName) < ItemMatching.CANDIDATE_THRESHOLD)
        val nameWins = ItemMatching.normalize("100 PIPERS 1.75L", null, fallbackSize = "750ml")
        assertEquals("1750", nameWins.size)
    }

    @Test fun suggestedNameFollowsSku() {
        assertEquals("PARROT BAY COCONUT 90 PROOF (1.75L) - 43258", suggestedItemName("PARROT BAY COCONUT 90 PROOF (1.75L)", "43258"))
        assertEquals("PARROT BAY COCONUT 90 PROOF (1.75L)", suggestedItemName("PARROT BAY COCONUT 90 PROOF (1.75L)", "MISSING-1000299"))
        assertEquals("1.75L", formatSizeMl("1750"))
        assertEquals("750ml", formatSizeMl("750"))
    }

    @Test fun singleGenericWordDoesNotMatch() =
        assertTrue(s("TITO'S HANDMADE VODKA (750ml)", null, "VODKA 750ML", null) < ItemMatching.CANDIDATE_THRESHOLD)

    @Test fun sameBrandDifferentFlavorScoresLower() {
        val coconut = s("PARROT BAY COCONUT (1.75L)", null, "PARROT BAY COCONUT (1.75L) - 43408", "43408")
        val pineapple = s("PARROT BAY COCONUT (1.75L)", null, "PARROT BAY PINEAPPLE (1.75L)", null)
        assertTrue(coconut > pineapple)
    }

    @Test fun unrelatedItemsScoreLow() =
        assertTrue(s("OLD KENTUCKY TAVERN (PET) (750ml) - 19366", "19366", "SALT RIVER SAUVIGNON BLANC (750ml) - 949366", "949366") < 0.2)

    @Test fun abbreviationsGetPartialCredit() =
        assertTrue(s("JOSH CABERNET SAUVIGNON (750ml)", null, "JOSH CAB SAUV 750ML", null) >= 0.5)

    @Test fun missingUpc() {
        assertTrue(ItemMatching.isMissingUpc("MISSING-10001290"))
        assertTrue(ItemMatching.isMissingUpc(""))
        assertFalse(ItemMatching.isMissingUpc("0089000010387"))
    }

    @Test fun barcodeDetection() {
        assertTrue(ItemMatching.looksLikeBarcode("070847037545"))
        assertFalse(ItemMatching.looksLikeBarcode("9366"))
        assertFalse(ItemMatching.looksLikeBarcode("JOSH CAB"))
    }
}
