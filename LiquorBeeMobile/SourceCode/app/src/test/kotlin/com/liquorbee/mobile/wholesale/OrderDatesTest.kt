package com.liquorbee.mobile.wholesale

import com.liquorbee.mobile.core.network.WholesaleOrderDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class OrderDatesTest {
    private lateinit var saved: TimeZone

    @Before fun central() { saved = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("America/Chicago")) }
    @After fun restore() { TimeZone.setDefault(saved) }

    @Test fun utcEveningOrderCountsAsTheLocalDay() {
        // 1:30 AM UTC on the 24th is 7:30 PM on the 23rd in Central time.
        assertEquals("2026-11-23", orderLocalDay("2026-11-24T01:30:00"))
        assertEquals("2026-11-23", orderLocalDay("2026-11-23T15:00:00.123Z"))
        assertNull(orderLocalDay(null))
        assertNull(orderLocalDay("garbage"))
    }

    @Test fun todayFilter() {
        val order = WholesaleOrderDto(orderId = "1", createdDate = "2026-11-24T01:30:00")
        assertTrue(OrderFilter.Today.matches(order, today = "2026-11-23"))
        assertFalse(OrderFilter.Today.matches(order, today = "2026-11-24"))
        assertTrue(OrderFilter.Open.matches(order))
    }
}
