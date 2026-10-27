package com.shubham.spendtracker.insights

import com.shubham.spendtracker.data.LowBalance
import com.shubham.spendtracker.data.ManualPayment
import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.parser.Deduper
import com.shubham.spendtracker.parser.ParsedTxn
import com.shubham.spendtracker.parser.TxnSource
import com.shubham.spendtracker.parser.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BalancesTest {
    private var nextId = 1L

    private fun txn(
        ts: Long, type: TxnType, paise: Long, sender: String, raw: String, last4: String? = null,
        source: TxnSource = TxnSource.SMS,
    ) = Transaction(
        id = nextId++, amountPaise = paise, type = type, merchant = null, accountLast4 = last4, source = source,
        sourceApp = sender, timestamp = ts, upiRef = null, category = "Other", rawText = raw,
    )

    @Test fun parsesEachBanksBalance() {
        assertEquals(51240L, Balances.parse("Rs.8.33 Dr. from A/C XXXXXX7421 and Cr. to x@okaxis. Ref:500000000001. AvlBal:Rs512.40(2026:01:10 09:30:00). Not you? Call 18005700/5000-BOB"))
        assertEquals(1234567L, Balances.parse("Dear BOB UPI User: Your account is credited with INR 2000.00 on 2026-01-11 10:00:00 AM by UPI Ref No 500000000002; AvlBal: Rs12345.67 - BOB"))
        assertEquals(432150L, Balances.parse("Rs.4100 transferred from A/c ...7421 to:CMS/LANDTTW/2026. Total Bal:Rs.4321.50CR. Avlbl Amt:Rs.4321.50(03-01-2026 12:00:00) - Bank of Baroda"))
        assertEquals(2150025L, Balances.parse("UPDATE: INR 4,100.00 debited from HDFC Bank XX3156 on 03-JAN-26. Info: ACH D- L&TFINANCELIMITED-TXXXXXXXXXX1234. Avl bal:INR 21,500.25"))
        assertEquals(180L, Balances.parse("Withdrawn Rs.500 From HDFC Bank Card x6608 At PUNE BR On 2026-01-12:16:00:00 Bal Rs.1.8 Not You?"))
        assertEquals(123400L, Balances.parse("Rs 200 debited from A/c XX12. Available Balance is Rs. 1,234.00"))
    }

    @Test fun ignoresCreditCardLimitsAndMessagesWithoutBalance() {
        assertNull(Balances.parse("Rs.2,340.50 spent on your SBI Credit Card ending 9876 at AMAZON on 12/01/25. Avl Lmt Rs.45,000.00"))
        assertNull(Balances.parse("Rs.250 spent on HDFC Bank Credit Card xx1234. Available balance: Rs 10,000"))
        assertNull(Balances.parse("Sent Rs.200.00 From HDFC Bank A/C *3156 To AMIT KUMAR PATIL On 12/01/26 Ref 500000000003 Not You?"))
        assertNull(Balances.parse("You've received a payment. Neha has paid you ₹33.33"))
    }

    @Test fun latestReadingPerAccountWithUnnumberedAndCardAlertsResolved() {
        val rows = listOf(
            txn(1, TxnType.DEBIT, 5000, "JX-BOBSMS-S", "Rs.50.00 Dr. from A/C XXXXXX7421 and Cr. to a@okaxis. AvlBal:Rs300.00(…)", "7421"),
            // BOB credit alerts carry no account number.
            txn(2, TxnType.CREDIT, 3334, "JK-BOBSMS-S + Google Pay", "Dear BOB UPI User: Your account is credited with INR 33.34; AvlBal: Rs333.34 - BOB"),
            txn(3, TxnType.DEBIT, 410000, "AD-HDFCBK-S", "INR 4,100.00 debited from HDFC Bank XX3156. Avl bal:INR 2,400.00", "3156"),
            // Debit card spend on the same HDFC account.
            txn(4, TxnType.DEBIT, 239900, "AD-HDFCBK-S", "Spent Rs.2399 From HDFC Bank Card x6608 At ANTHROPIC* CLAUDE SUB Bal Rs.950.50", "6608"),
            // A UPI app's notification alone isn't tied to a bank.
            txn(5, TxnType.DEBIT, 10000, "Google Pay", "You paid ₹100 to Someone", source = TxnSource.NOTIFICATION),
        )
        val b = Balances.latest(rows)
        assertEquals(2, b.size)
        assertEquals(AccountBalance("Bank of Baroda", "7421", 33334, 2, exact = true), b[0])
        assertEquals(AccountBalance("HDFC Bank", "3156", 95050, 4, exact = true), b[1])
    }

    @Test fun laterPaymentsWithoutBalanceAreApplied() {
        val rows = listOf(
            txn(1, TxnType.DEBIT, 410000, "AD-HDFCBK-S", "INR 4,100.00 debited from HDFC Bank XX3156. Avl bal:INR 10,000.00", "3156"),
            txn(2, TxnType.DEBIT, 20000, "AD-HDFCBK-T", "Sent Rs.200.00 From HDFC Bank A/C *3156 To AMIT Ref 1", "3156"),
            txn(3, TxnType.CREDIT, 50000, "VM-HDFCBK-S", "Credit Alert! Rs.500.00 credited to HDFC Bank A/c XX3156 from VPA a@okaxis", "3156"),
        )
        val b = Balances.latest(rows).single()
        assertEquals(1_000_000L - 20000 + 50000, b.balancePaise)
        assertEquals(3L, b.asOf)
        assertFalse(b.exact)
    }

    @Test fun accountWithoutAnyBalanceIsLeftOut() {
        val rows = listOf(txn(1, TxnType.DEBIT, 600000, "JD-JIOPBS-S", "Rs. 6000.00 Sent from x0457 on 02-Jan-2026 Info: UPI/DR/500000000004/someone"))
        assertTrue(Balances.latest(rows).isEmpty())
    }

    @Test fun mergingKeepsTheBankSmsText() {
        val notification = txn(1, TxnType.CREDIT, 3333, "Google Pay", "Neha has paid you ₹33.33", source = TxnSource.NOTIFICATION)
        val sms = ParsedTxn(3333, TxnType.CREDIT, null, null, "500000000005", 2, TxnSource.SMS, "JX-BOBSMS-S", "INR 33.33 credited; AvlBal: Rs420.00 - BOB")
        val merged = Deduper.merge(notification, sms)
        assertEquals(sms.rawText, merged.rawText)
        assertEquals(42000L, Balances.latest(listOf(merged)).single().balancePaise)
    }

    @Test fun lowBalanceAlertsOncePerDip() {
        val now = 10L * 24 * 60 * 60 * 1000
        fun bal(paise: Long, asOf: Long = now) = AccountBalance("HDFC Bank", "3156", paise, asOf, exact = true)

        val (first, alerted) = LowBalance.due(listOf(bal(95050)), 100_000, emptySet(), now)
        assertEquals(1, first.size)
        // Still low: no repeat.
        val (second, stillAlerted) = LowBalance.due(listOf(bal(50000)), 100_000, alerted, now)
        assertTrue(second.isEmpty())
        // Back above, then low again: alerts again.
        val (_, cleared) = LowBalance.due(listOf(bal(500_000)), 100_000, stillAlerted, now)
        assertTrue(cleared.isEmpty())
        assertEquals(1, LowBalance.due(listOf(bal(90000)), 100_000, cleared, now).first.size)
        // An old figure doesn't alert, and 0 turns alerts off.
        assertTrue(LowBalance.due(listOf(bal(100, asOf = now - 5L * 24 * 60 * 60 * 1000)), 100_000, emptySet(), now).first.isEmpty())
        assertTrue(LowBalance.due(listOf(bal(100)), 0, emptySet(), now).first.isEmpty())
    }

    // A bank that reports its balance on every SMS, skipping some payments entirely.
    private fun bob(ts: Long, type: TxnType, paise: Long, balance: String) = txn(
        ts, type, paise, "JD-BOBSMS-S",
        if (type == TxnType.DEBIT) "Rs.${paise / 100}.00 Dr. from A/C XXXXXX7421 and Cr. to a@okaxis. AvlBal:Rs$balance(...)"
        else "Dear BOB UPI User: Your account is credited with INR ${paise / 100}.00; AvlBal: Rs$balance - BOB",
        if (type == TxnType.DEBIT) "7421" else null,
    )

    @Test fun balancesThatAddUpHaveNoGap() {
        val rows = listOf(bob(1, TxnType.DEBIT, 10000, "500.00"), bob(2, TxnType.CREDIT, 30000, "800.00"), bob(3, TxnType.DEBIT, 5000, "750.00"))
        assertTrue(Balances.gaps(rows).isEmpty())
    }

    @Test fun paymentWithoutSmsShowsAsGap() {
        // ₹500 → (₹180 paid, no SMS) → +₹300 SMS says ₹620, not ₹800.
        val rows = listOf(bob(10, TxnType.DEBIT, 10000, "500.00"), bob(20, TxnType.CREDIT, 30000, "620.00"))
        val gap = Balances.gaps(rows).single()
        assertEquals(-18000L, gap.amountPaise)
        assertEquals("Bank of Baroda", gap.bank)
        assertEquals("7421", gap.last4)
        assertEquals(10L, gap.from)
        assertEquals(20L, gap.to)

        // Adding the payment on that account, inside the window, closes the gap.
        val added = txn(15, TxnType.DEBIT, 18000, "Bank of Baroda", ManualPayment.RAW_TEXT, "7421", source = TxnSource.MANUAL)
        assertTrue(Balances.gaps(rows + added).isEmpty())
        // Added without an account it can't be placed, but still explains the gap.
        val loose = txn(15, TxnType.DEBIT, 18000, ManualPayment.SOURCE, ManualPayment.RAW_TEXT, source = TxnSource.MANUAL)
        assertTrue(Balances.gaps(rows + loose).isEmpty())
    }

    @Test fun notificationOnlyPaymentExplainsAGap() {
        // A Google Pay credit with no bank SMS: the next balance is higher, but that's this payment.
        val rows = listOf(
            bob(10, TxnType.DEBIT, 10000, "500.00"),
            txn(15, TxnType.CREDIT, 25000, "Google Pay", "RAHUL DESHMUKH paid you ₹250.00", source = TxnSource.NOTIFICATION),
            bob(20, TxnType.DEBIT, 10000, "649.76"),
        )
        assertTrue(Balances.gaps(rows).isEmpty())
    }

    @Test fun smallDifferencesAndOldGapsAreIgnored() {
        val rows = listOf(bob(10, TxnType.DEBIT, 10000, "500.00"), bob(20, TxnType.DEBIT, 10000, "399.40"))
        assertTrue(Balances.gaps(rows).isEmpty())
        // A ₹5 difference is real but too small to suggest.
        assertTrue(Balances.gaps(listOf(bob(10, TxnType.DEBIT, 10000, "500.00"), bob(20, TxnType.DEBIT, 10000, "395.00"))).isEmpty())
        val old = listOf(bob(10, TxnType.DEBIT, 10000, "500.00"), bob(20, TxnType.DEBIT, 10000, "200.00"))
        assertEquals(1, Balances.gaps(old).size)
        assertTrue(Balances.gaps(old, since = 21).isEmpty())
    }

    @Test fun manualPaymentCountsTowardsItsAccountBalance() {
        val rows = listOf(
            bob(10, TxnType.DEBIT, 10000, "500.00"),
            txn(20, TxnType.DEBIT, 24000, "Bank of Baroda", ManualPayment.RAW_TEXT, "7421", source = TxnSource.MANUAL),
        )
        val b = Balances.latest(rows).single()
        assertEquals(26000L, b.balancePaise)
        assertFalse(b.exact)
    }
}
