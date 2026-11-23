package com.shubham.spendtracker.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** The capture log says why a message wasn't a payment, without repeating any of its text. */
class SkipReasonTest {
    private val gpay = "com.google.android.apps.nbu.paisa.user"

    @Test fun notificationReasons() {
        assertEquals("no text", NotificationParser.skipReason(null, null))
        assertEquals("skipped: \"scratch\"", NotificationParser.skipReason("You won a scratch card", "Tap to view"))
        assertEquals("skipped: \"cashback\"", NotificationParser.skipReason("₹15 cashback", "Paid to Swiggy"))
        assertEquals("no payment wording", NotificationParser.skipReason("Your bill of ₹499", "Tap to view"))
        assertEquals("no amount", NotificationParser.skipReason("Payment sent", "To Rahul"))
    }

    @Test fun notificationReasonAgreesWithParser() {
        // Every message given a reason really is skipped.
        listOf("You won a scratch card" to "Tap", "Your bill of ₹499" to "Tap", "Payment sent" to "To Rahul")
            .forEach { (title, text) -> assertNull(NotificationParser.parse(gpay, title, text, 1L)) }
    }

    @Test fun smsReasons() {
        assertEquals("promotional sender", SmsParser.skipReason("BP-HDFCBN-P", "Get a credit card, Rs 0 fee"))
        assertEquals("personal number", SmsParser.skipReason("919800000000", "Sent you Rs 50 on UPI"))
        assertEquals("skipped: \"otp\"", SmsParser.skipReason("JK-BOBSMS-S", "OTP for txn of Rs 500 on A/c X1234 is 123456"))
        assertEquals("no account mentioned", SmsParser.skipReason("JZ-JIOINF-S", "You have used 90% of your daily data"))
        assertEquals("no amount", SmsParser.skipReason("JK-BOBSMS-S", "Your A/c X1234 has been debited. Call 1800 if not you"))
    }

    @Test fun reasonNeverContainsMessageText() {
        val reason = NotificationParser.skipReason("Rahul Sharma", "Payment of hello pending")
        assertFalse("Rahul" in reason)
    }
}
