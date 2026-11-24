package com.liquorbee.mobile.itemresolve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogItemNameTest {
    @Test fun sizeLabels() {
        assertEquals("750ML", catalogSizeLabel("750"))
        assertEquals("750ML", catalogSizeLabel("0.75 L"))
        assertEquals("1.75L", catalogSizeLabel("1750ML"))
        assertEquals("10/50ML", catalogSizeLabel("10/50 ml"))
        assertNull(catalogSizeLabel("unknown"))
        assertNull(catalogSizeLabel(""))
    }

    @Test fun addsSizeAndSku() {
        assertEquals("TITOS HANDMADE VODKA (1.75L) - 1234", catalogItemName("TITOS HANDMADE VODKA", "1750", "1234"))
        assertEquals("Soju - 885235", catalogItemName("Soju", null, "885235"))
        assertEquals("Soju - 885235", catalogItemName("Soju", "unknown", "885235"))
        assertEquals("Soju", catalogItemName("Soju", null, "MISSING-12"))
    }

    @Test fun skipsWhatTheNameAlreadyHas() {
        assertEquals("ABSOLUT 750ML - 99", catalogItemName("ABSOLUT 750ML", "0.75L", "99"))
        assertEquals("ABSOLUT 0.75 L - 99", catalogItemName("ABSOLUT 0.75 L", "750", "99"))
        assertEquals("ABSOLUT (750ML) - 99", catalogItemName("ABSOLUT", "750", "99").let { catalogItemName(it, "750", "99") })
    }

    @Test fun plainNameStripsSuffixes() {
        assertEquals("OLD KENTUCKY TAVERN (PET)", plainItemName("OLD KENTUCKY TAVERN (PET) (750ml) - 19366", "19366"))
        assertEquals("OLD KENTUCKY TAVERN (PET) (750ML) - 19366",
            catalogItemName(plainItemName("OLD KENTUCKY TAVERN (PET) (750ml) - 19366", "19366"), "750", "19366"))
        assertEquals("Soju", plainItemName("Soju", null))
    }
}
