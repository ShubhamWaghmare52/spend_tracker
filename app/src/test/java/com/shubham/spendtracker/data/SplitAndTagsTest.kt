package com.shubham.spendtracker.data

import com.shubham.spendtracker.parser.TxnSource
import com.shubham.spendtracker.parser.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitAndTagsTest {
    private fun credit(from: String?, rupees: Long) = Transaction(
        id = 99, amountPaise = rupees * 100, type = TxnType.CREDIT, merchant = from, accountLast4 = null,
        source = TxnSource.SMS, sourceApp = "X", timestamp = 1, upiRef = null, category = "Income", rawText = "",
    )

    private val outstanding = listOf(
        Split(id = 1, txnId = 10, person = "Rahul", sharePaise = 25000),
        Split(id = 2, txnId = 11, person = "Rahul", sharePaise = 40000),
        Split(id = 3, txnId = 10, person = "Amit", sharePaise = 25000),
    )

    @Test fun namesMatchAcrossFormats() {
        assertTrue(SplitMatcher.samePerson("Rahul", "RAHUL K SHARMA"))
        assertTrue(SplitMatcher.samePerson("rahul sharma", "rahul.k@okaxis"))
        assertFalse(SplitMatcher.samePerson("Rahul", "AMIT KUMAR"))
        assertFalse(SplitMatcher.samePerson("Al", "ALOK")) // whole names only, not prefixes
        assertFalse(SplitMatcher.samePerson("Rahul", null))
    }

    @Test fun sharedSurnameIsNotEnoughButShortNamesWork() {
        assertFalse(SplitMatcher.samePerson("Rahul Sharma", "AMIT SHARMA"))
        assertFalse(SplitMatcher.samePerson("Rahul Sharma", "SHARMA SWEETS"))
        assertTrue(SplitMatcher.samePerson("Om", "OM PRAKASH JOSHI"))
        // Titles are skipped
        assertFalse(SplitMatcher.samePerson("Dr. Sharma", "DR AMIT VERMA"))
        assertTrue(SplitMatcher.samePerson("Dr. Sharma", "SHARMA RAJESH"))
        assertTrue(SplitMatcher.samePerson("Mr Rahul", "rahul.k@okaxis"))
    }

    @Test fun exactShareSettlesThatShare() {
        assertEquals(listOf(1L), SplitMatcher.settledBy(credit("RAHUL K SHARMA", 250), outstanding).map { it.id })
        assertEquals(listOf(3L), SplitMatcher.settledBy(credit("amit.v@okicici", 250), outstanding).map { it.id })
    }

    @Test fun paidTheTotalSettlesEverything() {
        assertEquals(listOf(1L, 2L), SplitMatcher.settledBy(credit("Rahul", 650), outstanding).map { it.id })
    }

    @Test fun wrongAmountOrStrangerSettlesNothing() {
        assertTrue(SplitMatcher.settledBy(credit("Rahul", 999), outstanding).isEmpty())
        assertTrue(SplitMatcher.settledBy(credit("Priya", 250), outstanding).isEmpty())
    }

    @Test fun tagsAreTrimmedAndDeduplicated() {
        // The first spelling of a tag wins.
        assertEquals(listOf("Goa trip", "Work"), Tags.parse(" Goa trip, Work ,goa TRIP,, work"))
        assertEquals("Goa trip,Work", Tags.join(listOf("Goa trip ", "Work", "work")))
        assertNull(Tags.join(listOf(" ", "")))
    }
}
