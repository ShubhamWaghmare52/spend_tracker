package com.shubham.spendtracker.insights

import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.parser.TxnSource
import com.shubham.spendtracker.parser.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class UnusualPaymentsTest {
    private val zone = ZoneOffset.UTC
    private var nextId = 1L

    private fun t(merchant: String?, rupees: Long, at: String, category: String = "Food") = Transaction(
        id = nextId++, amountPaise = rupees * 100, type = TxnType.DEBIT, merchant = merchant, accountLast4 = null,
        source = TxnSource.SMS, sourceApp = "X", upiRef = null, category = category, rawText = "",
        timestamp = LocalDateTime.parse(at).toInstant(zone).toEpochMilli(),
    )

    private val swiggyHistory = listOf(
        t("Swiggy", 300, "2026-10-01T13:00"), t("Swiggy", 350, "2026-10-05T13:00"), t("Swiggy", 280, "2026-10-09T13:00"),
    )

    @Test fun normalPaymentIsFine() {
        assertNull(UnusualPayments.check(t("Swiggy", 420, "2026-10-12T13:00"), swiggyHistory, zone))
    }

    @Test fun muchBiggerThanUsualAtMerchant() {
        val r = UnusualPayments.check(t("Swiggy", 4_000, "2026-10-12T13:00"), swiggyHistory, zone)
        assertTrue(r is Unusual.BigForMerchant)
        assertEquals(30000L, (r as Unusual.BigForMerchant).typicalPaise)
    }

    @Test fun largeFirstPaymentToSomeoneNew() {
        assertEquals(Unusual.LargeToNewPayee, UnusualPayments.check(t("UNKNOWN TRADERS", 9_000, "2026-10-12T13:00"), swiggyHistory, zone))
        assertNull(UnusualPayments.check(t("NEW CAFE", 600, "2026-10-12T13:00"), swiggyHistory, zone))
    }

    @Test fun lateNight() {
        assertEquals(Unusual.LateNight(2), UnusualPayments.check(t(null, 2_500, "2026-10-12T02:14"), swiggyHistory, zone))
        assertNull(UnusualPayments.check(t(null, 500, "2026-10-12T02:14"), swiggyHistory, zone))
    }

    @Test fun selfTransfersAndIncomeAreIgnored() {
        assertNull(UnusualPayments.check(t("me@okaxis", 50_000, "2026-10-12T02:00", "Self transfer"), swiggyHistory, zone))
        assertNull(UnusualPayments.check(t("Boss", 50_000, "2026-10-12T02:00").copy(type = TxnType.CREDIT), swiggyHistory, zone))
    }
}
