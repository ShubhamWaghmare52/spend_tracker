package com.shubham.spendtracker.insights

import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.parser.TxnSource
import com.shubham.spendtracker.parser.TxnType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class MonthlySummaryTest {
    private fun t(date: String, rupees: Long, category: String, type: TxnType = TxnType.DEBIT) = Transaction(
        amountPaise = rupees * 100, type = type, merchant = "m", accountLast4 = null, source = TxnSource.SMS,
        sourceApp = "X", upiRef = null, category = category, rawText = "",
        timestamp = LocalDate.parse(date).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
    )

    @Test fun otherDoesNotHeadlineWhenRealCategoriesExist() {
        val txns = listOf(t("2026-10-02", 9_000, "Other"), t("2026-10-03", 500, "Food"))
        assertEquals("Food", MonthlySummary.summarize(txns, YearMonth.of(2026, 10), emptyMap(), ZoneOffset.UTC).topCategory)
    }

    @Test fun summarisesMonthAgainstPreviousAndBudgets() {
        val txns = listOf(
            t("2026-09-10", 1000, "Food"),
            t("2026-10-02", 3000, "Food"),
            t("2026-10-15", 1500, "Shopping"),
            t("2026-10-20", 9000, "Self transfer"), // excluded
            t("2026-10-21", 5000, "Income", TxnType.CREDIT), // excluded
            t("2026-11-01", 700, "Food"), // next month
        )
        val s = MonthlySummary.summarize(txns, YearMonth.of(2026, 10), mapOf("Food" to 250000L, "Shopping" to 500000L), ZoneOffset.UTC)
        assertEquals(450000L, s.spentPaise)
        assertEquals(100000L, s.previousPaise)
        assertEquals(2, s.payments)
        assertEquals("Food", s.topCategory)
        assertEquals(listOf("Food" to 50000L), s.overBudget)
    }
}
