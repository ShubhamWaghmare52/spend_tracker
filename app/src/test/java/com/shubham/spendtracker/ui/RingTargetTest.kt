package com.shubham.spendtracker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RingTargetTest {
    private val wed = LocalDate.of(2026, 10, 21) // Wednesday

    @Test fun todayKeepsTheDailyLimitWithoutPace() {
        val t = ringTarget(Period.TODAY, 50_000, wed)
        assertEquals(50_000L, t.limit)
        assertNull(t.pace)
    }

    @Test fun weekIsSevenDailyLimitsPacedFromMonday() {
        val t = ringTarget(Period.WEEK, 50_000, wed)
        assertEquals(350_000L, t.limit)
        assertEquals(3f / 7, t.pace!!, 1e-6f)
        assertEquals(1f / 7, ringTarget(Period.WEEK, 50_000, LocalDate.of(2026, 10, 19)).pace!!, 1e-6f)
        assertEquals(1f, ringTarget(Period.WEEK, 50_000, LocalDate.of(2026, 10, 25)).pace!!, 1e-6f)
    }

    @Test fun monthUsesItsOwnLength() {
        val oct = ringTarget(Period.MONTH, 50_000, LocalDate.of(2026, 10, 25))
        assertEquals(50_000L * 31, oct.limit)
        assertEquals(25f / 31, oct.pace!!, 1e-6f)
        val feb = ringTarget(Period.MONTH, 50_000, LocalDate.of(2028, 2, 1)) // leap year
        assertEquals(50_000L * 29, feb.limit)
        assertEquals(1f / 29, feb.pace!!, 1e-6f)
    }

    @Test fun noLimitStaysZero() {
        assertEquals(0L, ringTarget(Period.MONTH, 0, wed).limit)
    }
}
