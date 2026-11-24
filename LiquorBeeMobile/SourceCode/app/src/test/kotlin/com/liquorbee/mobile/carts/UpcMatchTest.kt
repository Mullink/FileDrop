package com.liquorbee.mobile.carts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpcMatchTest {
    @Test fun exactMatch() = assertTrue(upcMatches("082896002740", "082896002740"))
    @Test fun extraLeadingZeroInCatalog() = assertTrue(upcMatches("0096749800858", "096749800858"))
    @Test fun ean13ScanOfUpcA() = assertTrue(upcMatches("082896002740", "0082896002740"))
    @Test fun catalogMissingCheckDigit() = assertTrue(upcMatches("08289600274", "082896002740"))
    @Test fun differentItems() = assertFalse(upcMatches("0096749800858", "096749008452"))
    @Test fun shortCodesDoNotFuzzyMatch() = assertFalse(upcMatches("9368", "09368"))
    @Test fun nonDigitCodesOnlyMatchExactly() = assertFalse(upcMatches("MISSING-10001290", "10001290"))
    @Test fun blankStored() = assertFalse(upcMatches(null, "082896002740"))
}
