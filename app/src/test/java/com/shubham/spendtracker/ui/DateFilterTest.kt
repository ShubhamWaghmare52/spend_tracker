package com.shubham.spendtracker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class DateFilterTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) =
        LocalDate.of(y, m, d).atTime(h, min).atZone(ist).toInstant().toEpochMilli()

    @Test fun allTimeHasNoWindow() {
        assertNull(DateFilter.AllTime.window(ist))
    }

    @Test fun monthCoversFirstToLastMinute() {
        val w = DateFilter.Month(YearMonth.of(2026, 11)).window(ist)!!
        assertTrue(at(2026, 11, 1) in w)
        assertTrue(at(2026, 11, 30, 23, 59) in w)
        assertFalse(at(2026, 10, 31, 23, 59) in w)
        assertFalse(at(2026, 12, 1) in w)
    }

    @Test fun rangeIncludesBothEnds() {
        val w = DateFilter.Range(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12)).window(ist)!!
        assertTrue(at(2026, 10, 5) in w)
        assertTrue(at(2026, 10, 12, 23, 59) in w)
        assertFalse(at(2026, 10, 4, 23, 59) in w)
        assertFalse(at(2026, 10, 13) in w)
    }

    @Test fun labels() {
        assertEquals("All time", DateFilter.AllTime.label())
        assertEquals("November 2026", DateFilter.Month(YearMonth.of(2026, 11)).label())
        assertEquals("5 Oct – 12 Oct 2026", DateFilter.Range(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12)).label())
        assertEquals("28 Dec 2025 – 3 Jan 2026", DateFilter.Range(LocalDate.of(2025, 12, 28), LocalDate.of(2026, 1, 3)).label())
        assertEquals("5 Oct 2026", DateFilter.Range(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5)).label())
    }

    @Test fun survivesSaving() {
        listOf(
            DateFilter.AllTime,
            DateFilter.Month(YearMonth.of(2026, 3)),
            DateFilter.Range(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 15)),
        ).forEach { assertEquals(it, DateFilter.decode(it.encode())) }
        assertEquals(DateFilter.AllTime, DateFilter.decode("garbage"))
    }
}
