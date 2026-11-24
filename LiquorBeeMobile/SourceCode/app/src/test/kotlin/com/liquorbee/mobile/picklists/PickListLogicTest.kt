package com.liquorbee.mobile.picklists

import com.liquorbee.mobile.core.network.PickListDto
import com.liquorbee.mobile.core.network.PickListItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PickListLogicTest {
    private val items = listOf(
        PickListItemDto(1, "5036", "GLENLIVET 12", "080432400630", qty = 2, pickedQty = 2, isChecked = true),
        PickListItemDto(2, "9366", "100 PIPERS", "0089000010387", qty = 3, pickedQty = 1),
        PickListItemDto(3, "43258", "PARROT BAY COCONUT", null, qty = 1, isMissing = true)
    )
    private fun state(filter: PickFilter, query: String = "") =
        PickListUiState(loading = false, list = PickListDto(id = 1, items = items), filter = filter, query = query)

    @Test fun filtersSplitToPickMissingAndDone() {
        assertEquals(listOf(2), state(PickFilter.ToPick).visibleItems.map { it.id })
        assertEquals(listOf(3), state(PickFilter.Missing).visibleItems.map { it.id })
        assertEquals(listOf(1), state(PickFilter.Done).visibleItems.map { it.id })
        assertEquals(3, state(PickFilter.All).visibleItems.size)
    }

    @Test fun typedTextNarrowsTheList() {
        assertEquals(listOf(3), state(PickFilter.All, "parrot").visibleItems.map { it.id })
        assertEquals(listOf(1), state(PickFilter.All, "0804324").visibleItems.map { it.id })
    }

    @Test fun serverDatesParseAsUtc() {
        assertNotNull(PickListFormat.parse("2026-11-20T14:30:05.1234567"))
        assertNotNull(PickListFormat.parse("2026-11-20T14:30:05Z"))
        assertNull(PickListFormat.parse("bad"))
        assertEquals("", PickListFormat.dateTime(null))
    }
}
