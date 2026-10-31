package com.shubham.spendtracker.parser

import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.insights.Balances
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A payment SMS carries two times: when the bank sent it (what a live SMS broadcast gives) and when the
 * phone received it (what an inbox re-scan gives). A backup made from live rows, restored after a fresh
 * install that already re-read the inbox, used to bring every such payment in a second time.
 */
class DuplicateCopiesTest {
    private val sms = "Rs.50.00 Dr. from A/C XXXXXX1234 and Cr. to shop@okbank. Ref:612345678901. AvlBal:Rs226.43"
    private val sent = 1_793_198_719_000L
    private val received = sent + 4_484L

    private fun row(id: Long, at: Long, text: String = sms, ref: String? = "612345678901", amount: Long = 5000,
                    type: TxnType = TxnType.DEBIT, source: TxnSource = TxnSource.SMS, app: String = "JX-BOBSMS-S",
                    category: String = "Other", note: String? = null) =
        Transaction(id = id, amountPaise = amount, type = type, merchant = "shop@okbank", accountLast4 = "1234",
            source = source, sourceApp = app, timestamp = at, upiRef = ref, category = category, rawText = text, note = note)

    @Test fun sameSmsReadLiveAndFromInboxIsOnePayment() {
        assertTrue(Deduper.sameRecord(row(1, received), row(2, sent)))
        assertTrue(Deduper.sameRecord(row(1, received, ref = null), row(2, sent, ref = null)))
    }

    @Test fun differentPaymentsStayApart() {
        // Same amount a minute later is another payment (its own ref and message).
        val other = row(2, sent + 60_000, text = sms.replace("612345678901", "612345678902"), ref = "612345678902")
        assertFalse(Deduper.sameRecord(row(1, sent), other))
        // Two payments added by hand with the same amount and wording are both real.
        val manual = { id: Long, at: Long -> row(id, at, text = "Added by you", ref = null, source = TxnSource.MANUAL, app = "Bank of Baroda") }
        assertFalse(Deduper.sameRecord(manual(1, sent), manual(2, sent + 30_000)))
        // A self transfer's debit and credit share a ref but are two rows.
        assertFalse(Deduper.sameRecord(row(1, sent), row(2, sent, type = TxnType.CREDIT)))
    }

    @Test fun repairKeepsTheCopyWithTheUsersDetails() {
        val rescanned = row(1, received)
        val restored = row(2, sent, category = "Shopping", app = "JX-BOBSMS-S + Google Pay")
        val noted = row(3, sent + 1, note = "birthday gift")
        assertEquals(listOf(rescanned), Deduper.copies(listOf(rescanned, restored)).map { it.second })
        val removed = Deduper.copies(listOf(rescanned, restored, noted)).map { it.second.id }.sorted()
        assertEquals(listOf(1L, 2L), removed)
    }

    @Test fun repairKeepsRowsWithSplits() {
        val rescanned = row(1, received)
        val restored = row(2, sent)
        assertEquals(listOf(2L), Deduper.copies(listOf(rescanned, restored)) { it.id == 1L }.map { it.second.id })
    }

    @Test fun noGapFromAPaymentCountedTwice() {
        val earlier = row(10, sent - 86_400_000, text = "Rs.100.00 Cr. to A/C XXXXXX1234. AvlBal:Rs276.43", ref = "611111111111",
            amount = 10000, type = TxnType.CREDIT)
        val doubled = listOf(earlier, row(11, received), row(12, sent))
        assertEquals(1, Balances.gaps(doubled).size) // the bug, as seen on the phone: "₹50 more", same minute
        val removed = Deduper.copies(doubled).map { it.second }.toSet()
        assertTrue(Balances.gaps(doubled - removed).isEmpty())
    }
}
