package com.shubham.spendtracker.data

import com.shubham.spendtracker.parser.TxnSource
import com.shubham.spendtracker.parser.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.ZoneId

class CsvBackupTest {
    private val rows = listOf(
        Transaction(
            id = 7, amountPaise = 25050, type = TxnType.DEBIT, merchant = "Swiggy, Koramangala",
            accountLast4 = "1234", source = TxnSource.SMS, sourceApp = "AD-HDFCBK-S + Google Pay",
            timestamp = 1_760_000_000_000, upiRef = "612345678901", category = "Food",
            rawText = "Sent Rs.250.50\nFrom HDFC Bank A/C *1234\nTo \"SWIGGY\"",
        ),
        Transaction(
            id = 8, amountPaise = 100000, type = TxnType.CREDIT, merchant = null, accountLast4 = null,
            source = TxnSource.NOTIFICATION, sourceApp = "Google Pay", timestamp = 1_760_000_100_000,
            upiRef = null, category = "Income", rawText = "Rahul sent you ₹1,000",
        ),
    )

    @Test fun roundTripKeepsEverythingButIds() {
        val back = CsvBackup.read(CsvBackup.write(rows, ZoneId.of("Asia/Kolkata")))
        assertEquals(rows.map { it.copy(id = 0) }, back)
    }

    @Test fun headerIsHumanReadable() {
        val csv = CsvBackup.write(rows, ZoneId.of("Asia/Kolkata"))
        assertEquals(CsvBackup.HEADER.joinToString(","), csv.lineSequence().first())
        assert(csv.contains("250.50")) { "amount should be in rupees" }
    }

    @Test fun rejectsOtherFiles() {
        assertThrows(IllegalArgumentException::class.java) { CsvBackup.read("name,phone\nA,1\n") }
        assertThrows(IllegalArgumentException::class.java) { CsvBackup.read("") }
    }
}
