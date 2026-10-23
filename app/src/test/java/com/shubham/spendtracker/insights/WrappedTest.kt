package com.shubham.spendtracker.insights

import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.parser.TxnSource
import com.shubham.spendtracker.parser.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class WrappedTest {
    private fun t(date: String, rupees: Long, merchant: String?, category: String, type: TxnType = TxnType.DEBIT) = Transaction(
        amountPaise = rupees * 100, type = type, merchant = merchant, accountLast4 = null, source = TxnSource.SMS,
        sourceApp = "X", upiRef = null, category = category, rawText = "",
        timestamp = LocalDate.parse(date).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
    )

    @Test fun picksTheHighlights() {
        val txns = listOf(
            t("2026-10-03", 300, "Swiggy", "Food"), // Saturday
            t("2026-10-10", 250, "Swiggy", "Food"), // Saturday
            t("2026-10-17", 280, "Swiggy", "Food"), // Saturday
            t("2026-10-06", 2_000, "Amazon", "Shopping"), // Tuesday
            t("2026-10-01", 10_000, null, "Income", TxnType.CREDIT),
            t("2026-10-08", 5_000, "me@okaxis", "Self transfer"), // ignored
            t("2026-09-30", 9_999, "Old", "Shopping"), // other month
        )
        val w = Wrapped.compute(txns, YearMonth.of(2026, 10), ZoneOffset.UTC)!!
        assertEquals(283000L, w.spentPaise)
        assertEquals(4, w.payments)
        assertEquals("Shopping", w.topCategory)
        assertEquals("Amazon", w.topMerchant)
        assertEquals("Swiggy", w.mostVisited)
        assertEquals(3, w.visits)
        assertEquals(200000L, w.biggest.amountPaise)
        assertEquals(DayOfWeek.TUESDAY, w.priciestWeekday)
        assertEquals(71, w.savingsRate)
    }

    @Test fun emptyMonthHasNoStory() {
        assertNull(Wrapped.compute(emptyList(), YearMonth.of(2026, 10), ZoneOffset.UTC))
    }

    @Test fun recapsLastMonthDuringFirstWeek() {
        assertEquals(YearMonth.of(2026, 10), Wrapped.monthFor(LocalDate.parse("2026-11-03")))
        assertEquals(YearMonth.of(2026, 11), Wrapped.monthFor(LocalDate.parse("2026-11-15")))
    }
}
