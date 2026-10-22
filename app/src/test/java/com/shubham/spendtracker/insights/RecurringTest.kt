package com.shubham.spendtracker.insights

import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.parser.TxnSource
import com.shubham.spendtracker.parser.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class RecurringTest {
    private val zone = ZoneOffset.UTC

    private fun pay(merchant: String, date: String, rupees: Long, category: String = "Bills & Recharge") = Transaction(
        amountPaise = rupees * 100, type = TxnType.DEBIT, merchant = merchant, accountLast4 = null,
        source = TxnSource.SMS, sourceApp = "X", upiRef = null, category = category, rawText = "",
        timestamp = LocalDate.parse(date).atTime(10, 0).toInstant(zone).toEpochMilli(),
    )

    @Test fun monthlyRechargeIsFoundWithNextDate() {
        val txns = listOf(pay("Airtel", "2026-08-05", 399), pay("Airtel", "2026-09-04", 399), pay("Airtel", "2026-10-05", 399))
        val r = RecurringDetector.detect(txns, LocalDate.parse("2026-10-20"), zone).single()
        assertEquals("Airtel", r.merchant)
        assertEquals(39900L, r.typicalPaise)
        assertEquals(LocalDate.parse("2026-11-05"), r.nextDue)
        assertEquals(16, r.daysUntilDue(LocalDate.parse("2026-10-20")))
    }

    @Test fun billsThatVaryALittleStillCount() {
        val txns = listOf(pay("MSEB", "2026-08-10", 1200), pay("MSEB", "2026-09-10", 1350))
        assertEquals(1, RecurringDetector.detect(txns, LocalDate.parse("2026-09-20"), zone).size)
    }

    @Test fun irregularOrOneOffPaymentsAreIgnored() {
        val txns = listOf(
            pay("Swiggy", "2026-10-01", 250, "Food"), pay("Swiggy", "2026-10-03", 300, "Food"), // too close
            pay("Amazon", "2026-08-01", 500, "Shopping"), pay("Amazon", "2026-09-01", 4000, "Shopping"), // amounts differ
            pay("Zomato", "2026-10-01", 250, "Food"), // only once
        )
        assertTrue(RecurringDetector.detect(txns, LocalDate.parse("2026-10-10"), zone).isEmpty())
    }

    @Test fun stoppedSubscriptionsDropOff() {
        val txns = listOf(pay("Netflix", "2026-06-01", 199), pay("Netflix", "2026-07-01", 199))
        assertTrue(RecurringDetector.detect(txns, LocalDate.parse("2026-10-01"), zone).isEmpty())
    }

    @Test fun renamedPayeeStillCountsAsPaid() {
        val txns = listOf(
            pay("AIRTEL PAYMENTS BANK LIMI", "2026-08-21", 1439),
            pay("AIRTEL PAYMENTS BANK LIMI", "2026-09-21", 1439),
            pay("Airtel", "2026-10-21", 1439),
        )
        val r = RecurringDetector.detect(txns, LocalDate.parse("2026-10-23"), zone).single()
        assertEquals(LocalDate.parse("2026-10-21"), r.lastPaid)
        assertEquals(LocalDate.parse("2026-11-21"), r.nextDue)
    }

    @Test fun twoDifferentBillsFromOneCompanyStaySeparate() {
        val txns = listOf(
            pay("Airtel", "2026-08-05", 399), pay("Airtel", "2026-09-05", 399),
            pay("Airtel Broadband", "2026-08-10", 999), pay("Airtel Broadband", "2026-09-10", 999),
        )
        assertEquals(2, RecurringDetector.detect(txns, LocalDate.parse("2026-09-20"), zone).size)
    }

    @Test fun selfTransfersAreNotRecurringSpend() {
        val txns = listOf(pay("me@okaxis", "2026-09-01", 5000, "Self transfer"), pay("me@okaxis", "2026-10-01", 5000, "Self transfer"))
        assertTrue(RecurringDetector.detect(txns, LocalDate.parse("2026-10-05"), zone).isEmpty())
    }
}
