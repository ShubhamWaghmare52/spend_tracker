package com.shubham.spendtracker.insights

import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.parser.TxnSource
import com.shubham.spendtracker.parser.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class SavingsTest {
    private fun t(date: String, rupees: Long, type: TxnType, category: String = "Food") = Transaction(
        amountPaise = rupees * 100, type = type, merchant = null, accountLast4 = null, source = TxnSource.SMS,
        sourceApp = "X", upiRef = null, category = category, rawText = "",
        timestamp = LocalDate.parse(date).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
    )

    @Test fun sixMonthsOldestFirstWithRates() {
        val txns = listOf(
            t("2026-10-01", 50_000, TxnType.CREDIT, "Income"),
            t("2026-10-05", 30_000, TxnType.DEBIT),
            t("2026-10-06", 9_000, TxnType.DEBIT, "Self transfer"), // ignored
            t("2026-09-10", 10_000, TxnType.DEBIT),
        )
        val months = Savings.lastMonths(txns, YearMonth.of(2026, 10), 6, ZoneOffset.UTC)
        assertEquals(6, months.size)
        assertEquals(YearMonth.of(2026, 5), months.first().month)
        val oct = months.last()
        assertEquals(5_000_000L, oct.incomePaise)
        assertEquals(3_000_000L, oct.spentPaise)
        assertEquals(40, oct.savingsRate)
        assertNull(months[4].savingsRate) // September: spending but no income
        assertEquals(-1_000_000L, months[4].savedPaise)
    }
}
