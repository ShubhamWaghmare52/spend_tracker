package com.shubham.spendtracker.ui

import com.shubham.spendtracker.insights.BalanceGap
import com.shubham.spendtracker.parser.TxnType
import org.junit.Assert.assertEquals
import org.junit.Test

class AddPaymentTest {
    @Test fun amountKeepsDigitsAndTwoDecimals() {
        assertEquals("240", cleanAmount("240"))
        assertEquals("33.33", cleanAmount("33.339"))
        assertEquals("1200.5", cleanAmount("1,200.5"))
        assertEquals("0.5", cleanAmount(".5"))
        assertEquals("12.34", cleanAmount("12.3.4"))
        assertEquals("", cleanAmount("abc"))
    }

    @Test fun gapPrefillsTheMissingPaymentJustBeforeTheSms() {
        val hour = 60 * 60 * 1000L
        val out = paymentFor(BalanceGap("Bank of Baroda", "7421", from = 0, to = 10 * hour, amountPaise = -20667))
        assertEquals(TxnType.DEBIT, out.type)
        assertEquals(20667L, out.amountPaise)
        assertEquals(10 * hour - 60_000, out.timestamp)
        assertEquals("Bank of Baroda", out.bank)
        assertEquals("7421", out.last4)

        // Money in, and a window too short to step back a minute: the middle of it.
        val inGap = paymentFor(BalanceGap("HDFC Bank", "3156", from = 0, to = 60_000, amountPaise = 3333))
        assertEquals(TxnType.CREDIT, inGap.type)
        assertEquals(30_000L, inGap.timestamp)
    }
}
