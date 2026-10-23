package com.shubham.spendtracker.parser

import com.shubham.spendtracker.data.DailyLimit
import com.shubham.spendtracker.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class SmsParserTest {
    private fun sms(body: String, sender: String = "VM-HDFCBK") = SmsParser.parse(sender, body, 1_000L)

    @Test fun hdfcUpiSent() {
        val t = sms("Sent Rs.250.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 12/01/25\nRef 501234567890\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808")!!
        assertEquals(25000L, t.amountPaise)
        assertEquals(TxnType.DEBIT, t.type)
        assertEquals("SWIGGY", t.merchant)
        assertEquals("1234", t.accountLast4)
        assertEquals("501234567890", t.upiRef)
    }

    @Test fun sbiDebitTrfTo() {
        val t = sms("Dear UPI user A/C X5678 debited by 120.0 on date 12Jan25 trf to ZOMATO Refno 412345678901. If not u? call 1800111109. -SBI", "AD-SBIUPI-S")
        assertEquals(12000L, t!!.amountPaise)
        assertEquals(TxnType.DEBIT, t.type)
        assertEquals("ZOMATO", t.merchant)
        assertEquals("5678", t.accountLast4)
        assertEquals("412345678901", t.upiRef)
    }

    @Test fun iciciDebitedCreditedTo() {
        val t = sms("ICICI Bank Acct XX901 debited for Rs 1,499.00 on 12-Jan-25; uber.rides@icici credited. UPI:501234567891. Call 18002662 for dispute.")!!
        assertEquals(149900L, t.amountPaise)
        assertEquals(TxnType.DEBIT, t.type)
        assertEquals("901", t.accountLast4)
    }

    @Test fun kotakSentToVpa() {
        val t = sms("Sent Rs.100.00 from Kotak Bank AC X4321 to rahul.k@okaxis on 12-01-25.UPI Ref 501234567892. Not you, https://kotak.com/KBANKT/Fraud", "JD-KOTAKB")!!
        assertEquals(10000L, t.amountPaise)
        assertEquals("rahul.k@okaxis", t.merchant)
        assertEquals("4321", t.accountLast4)
    }

    @Test fun cardSpentAt() {
        val t = sms("Rs.2,340.50 spent on your SBI Credit Card ending 9876 at AMAZON on 12/01/25. Avl Lmt Rs.45,000.00", "VK-SBICRD")!!
        assertEquals(234050L, t.amountPaise)
        assertEquals("AMAZON", t.merchant)
        assertEquals("9876", t.accountLast4)
    }

    @Test fun creditFromVpa() {
        val t = sms("Rs.500.00 credited to A/c XX1234 by VPA friend@oksbi on 12-01-25 (UPI Ref No 501234567893)")!!
        assertEquals(TxnType.CREDIT, t.type)
        assertEquals("friend@oksbi", t.merchant)
        assertEquals("501234567893", t.upiRef)
    }

    @Test fun balanceBeforeAmountIsSkipped() {
        val t = sms("Avl Bal Rs 10,000.00. Your A/c XX1234 is debited with INR 75.00 towards CHAI POINT")!!
        assertEquals(7500L, t.amountPaise)
        assertEquals("CHAI POINT", t.merchant)
    }

    @Test fun upiInfoFormat() {
        val t = sms("Rs 349.00 debited from A/c no. XX3456 on 12-01-25. Info: UPI/P2M/501234567894/BLINKIT. Avl bal Rs 2,000")!!
        assertEquals("BLINKIT", t.merchant)
        assertEquals("501234567894", t.upiRef)
    }

    // Formats seen on a real phone (numbers replaced).
    @Test fun jioPaymentsBankStripsNotYou() {
        val t = sms("Rs. 1200.00 Sent from x4321 on 12-Aug-2026 Info: UPI/DR/612345678901/R K SHARMA  Not you? Call 18008907070 - JPBL", "JX-JIOPBS-S")!!
        assertEquals("R K SHARMA", t.merchant)
        assertEquals("612345678901", t.upiRef)
    }

    @Test fun hdfcInfoAndForFields() {
        assertEquals("CHAITANYA TRADING PRIVATE LIMITED", sms("UPDATE: INR 8,000.00 debited from HDFC Bank XX4321 on 12-SEP-26. Info: CHAITANYA TRADING PRIVATE LIMITED Dr - XXXXXXXXXX4321 - CHAITANYA TRADING PRIVATE LIMITED. Avl bal:INR 2,000.00", "JM-HDFCBK-S")!!.merchant)
        assertEquals("ACMEFINANCELIMITED", sms("UPDATE: INR 2,000.00 debited from HDFC Bank XX4321 on 05-OCT-26. Info: ACH D- ACMEFINANCELIMITED-TXXXXXXXXXX1234. Avl bal:INR 9,000.00", "JM-HDFCBK-S")!!.merchant)
        assertEquals("DEBIT CARD ANNUAL FEE-Aug-2026-EPR1234567890123", sms("UPDATE: INR 500 debited from HDFC Bank A/c XX123456 on 01-AUG-26. For: DEBIT CARD ANNUAL FEE-Aug-2026-EPR1234567890123", "VM-HDFCBK-S")!!.merchant)
    }

    @Test fun cardSpendWithStarAndHelplineNumber() {
        val t = sms("Spent Rs.1999 From HDFC Bank Card x4321 At SPOTIFY* PREMIUM On 2026-10-01:10:00:00 Bal Rs.5000.00 Not You? Call 18002586161/SMS BLOCK DC 4321 to 7308080808", "AD-HDFCBK-S")!!
        assertEquals(199900L, t.amountPaise)
        assertEquals("SPOTIFY* PREMIUM", t.merchant)
    }

    @Test fun bobFormats() {
        assertEquals("CHAITANYA TRADING P", sms("Rs.12000 Debited to A/c ...4321 for NEFT to CHAITANYA TRADING P UTR BARBV00000000000. Total Bal:Rs.100.00CR. Avlbl Amt:Rs.100.00(12-09-2026 10:00:00). Not you? Call 18005700-BOB", "JK-BOBTXN-S")!!.merchant)
        assertEquals("CMS/ACMELN/1234", sms("Rs.2000 transferred from A/c ...4321 to:CMS/ACMELN/1234. Total Bal:Rs.100.00CR. Avlbl Amt:Rs.100.00(12-09-2026 10:00:00) - Bank of Baroda", "JK-BOBTXN-S")!!.merchant)
        val credit = sms("Dear BOB UPI User: Your account is credited with INR 500.00 on 2026-09-12 10:00:00 AM by UPI Ref No 612345678901; AvlBal: Rs1000.00 - BOB", "JK-BOBSMS-S")!!
        assertEquals(TxnType.CREDIT, credit.type)
        assertEquals(50000L, credit.amountPaise)
        assertNull(credit.merchant)
    }

    @Test fun bobDrCrSelfTransfer() {
        val t = sms("Rs.2.00 Dr. from A/C XXXXXX5678 and Cr. to someone-3@okaxis. Ref:666111000000. AvlBal:Rs1000.00(2026:01:15 10:00:00). Not you? Call 18005700/5000-BOB", "JK-BOBTXN-S")!!
        assertEquals(200L, t.amountPaise)
        assertEquals(TxnType.DEBIT, t.type)
        assertEquals("someone-3@okaxis", t.merchant)
        assertEquals("5678", t.accountLast4)
        assertEquals("666111000000", t.upiRef)
    }

    @Test fun hdfcCreditAlertUpiRef() {
        val t = sms("Credit Alert!\nRs.2.00 credited to HDFC Bank A/c XX9012 on 15-01-26 from VPA *xy99@okaxis (UPI 666111000000)", "VD-HDFCBK-S")!!
        assertEquals(TxnType.CREDIT, t.type)
        assertEquals("666111000000", t.upiRef)
        assertEquals("*xy99@okaxis", t.merchant)
        assertEquals("9012", t.accountLast4)
    }

    @Test fun balanceSuffixCrIsNotCredit() {
        val t = sms("Rs.2000 transferred from A/c ...4321 to:CMS/ACMELN/1234. Total Bal:Rs.100.00CR. Avlbl Amt:Rs.100.00 - Bank of Baroda", "JK-BOBTXN-S")!!
        assertEquals(TxnType.DEBIT, t.type)
    }

    @Test fun stripsVpaPrefix() {
        assertEquals("*77-1@okaxis", ParseUtils.cleanParty("VPA *77-1@okaxis"))
    }

    @Test fun ignoresPromosAndEmiReminders() {
        assertNull(sms("Dear Customer, Claim your Lifetime Free HDFC Bank Credit Card+Upto Rs.1000 Amazon Voucher. Hurry: https://x.hdfc.bank.in T&C", "VM-HDFCBN-P"))
        assertNull(sms("Your Freedom Credit Card is FREE + Get upto Rs.1000 Voucher Apply https://x HDFC Bank", "VD-HDFCBK-S"))
        assertNull(sms("Amount of Rs. 35000.00 is CREDITED to Beneficiary A/c on 12-09-2026 10:00:00 through NEFT for your UTR No. BARBV00000000000 -Bank of Baroda", "JK-BOBSMS-S"))
        assertEquals("Amazon Pay", sms("Your Apay Wallet balance is debited for INR 250.50. Reference Number is 612345678901. If not you? call 18001234567 - SMS via Juspay", "JK-JUSPAY-S")!!.merchant)
        assertNull(sms("Dear Customer, EMI debit of Rs.2000/- against your loan account T123 will be processed on 05-AUG-2026. Ensure sufficient balance in your bank account XX4321 to avoid penal charges", "JM-ACMEFN-S"))
    }

    @Test fun creditCardBillPaymentsAreNotIncomeOrSpend() {
        // The card company's acknowledgement: ignored entirely.
        assertNull(sms("Payment of Rs.5,000 received towards your HDFC Bank Credit Card ending 1234 on 15-Oct-26. Thank you.", "VM-HDFCBK-S"))
        // The bank's debit paying the bill: recorded, but filed as a transfer of the user's own money.
        val debit = sms("Rs.5000 debited from A/c XX4321 on 15-Oct-26 towards Credit Card bill payment. Ref 612345678901", "VM-HDFCBK-S")!!
        assertEquals(TxnType.DEBIT, debit.type)
        assertEquals("Self transfer", Categorizer.categorize(debit.type, debit.merchant, debit.rawText))
        // A purchase on the card is still spending.
        val spend = sms("Rs.1,200 spent on your SBI Credit Card ending 9876 at AMAZON on 12/01/26.", "VK-SBICRD")!!
        assertEquals("Shopping", Categorizer.categorize(spend.type, spend.merchant, spend.rawText))
    }

    @Test fun cardPurchasesWorded_payment_onYourCard_areStillSpending() {
        for (body in listOf(
            "Rs.1,500 debited: Payment of INR 1,500.00 on your HDFC Bank Credit Card XX1234 at RELIANCE DIGITAL is successful.",
            "Thank you for using your ICICI Credit Card for Rs 350 at PVR CINEMAS. Debited on 12-Oct-26.",
        )) {
            assertFalse(body, Categorizer.isCardBillPayment(TxnType.DEBIT, body))
        }
        assertTrue(Categorizer.isCardBillPayment(TxnType.DEBIT, "Rs 8,000 debited from A/c XX4321 towards your Credit Card ending 1234"))
        assertTrue(Categorizer.isCardBillPayment(TxnType.DEBIT, "INR 3,000 debited for HDFC credit card dues via NetBanking"))
    }

    @Test fun futureRefundsAreIgnoredUntilTheyArrive() {
        assertNull(sms("Refund of Rs 499 will be credited to your A/c XX1234 in 3-5 working days", "AD-HDFCBK-S"))
    }

    @Test fun ignoresOtpPromoAndPersonal() {
        assertNull(sms("123456 is your OTP for txn of Rs 500 at AMAZON on card XX1234"))
        assertNull(sms("Get a pre-approved loan of Rs 5,00,000 on your account. Apply now!"))
        assertNull(sms("Your bill of Rs 799 is due on 15-01-25 for card XX1234"))
        assertNull(sms("Rs 500 sent to you via UPI bro", sender = "+919876543210"))
        assertNull(sms("rahul@okaxis has requested Rs 200 from your A/c via UPI"))
    }
}

class NotificationParserTest {
    private val gpay = "com.google.android.apps.nbu.paisa.user"

    @Test fun gpayPaid() {
        val t = NotificationParser.parse(gpay, "₹250 paid to Swiggy", "Payment successful", 1L)!!
        assertEquals(25000L, t.amountPaise)
        assertEquals(TxnType.DEBIT, t.type)
        assertEquals("Swiggy", t.merchant)
        assertEquals("Google Pay", t.sourceApp)
    }

    @Test fun phonepePaid() {
        val t = NotificationParser.parse("com.phonepe.app", "Paid ₹1,200 to Uber India", null, 1L)!!
        assertEquals(120000L, t.amountPaise)
        assertEquals("Uber India", t.merchant)
    }

    @Test fun receivedMoney() {
        val t = NotificationParser.parse(gpay, "Rahul Sharma sent you ₹500", "Tap to view", 1L)!!
        assertEquals(TxnType.CREDIT, t.type)
        assertEquals("Rahul Sharma", t.merchant)
    }

    @Test fun ignoresRequestsAndUnknownApps() {
        assertNull(NotificationParser.parse(gpay, "Rahul requested ₹200", "Pay now", 1L))
        assertNull(NotificationParser.parse(gpay, "You won ₹25 cashback", null, 1L))
        assertNull(NotificationParser.parse("com.whatsapp", "Paid ₹250 to Swiggy", null, 1L))
    }
}

class DedupeAndCategoryTest {
    private fun stored(source: TxnSource, merchant: String?, ts: Long = 0L, ref: String? = null) = Transaction(
        id = 1, amountPaise = 25000, type = TxnType.DEBIT, merchant = merchant, accountLast4 = "1234",
        source = source, sourceApp = "VM-HDFCBK", timestamp = ts, upiRef = ref, category = "Food", rawText = "x",
    )

    private fun parsed(source: TxnSource, merchant: String?, ts: Long, raw: String = "y") = ParsedTxn(
        25000, TxnType.DEBIT, merchant, null, null, ts, source, "Google Pay", raw,
    )

    @Test fun smsAndNotificationMerge() {
        val old = stored(TxnSource.SMS, "swiggy@icici")
        val new = parsed(TxnSource.NOTIFICATION, "Swiggy", 60_000)
        val dup = Deduper.findDuplicate(new, listOf(old))
        assertNotNull(dup)
        val merged = Deduper.merge(dup!!, new)
        assertEquals("Swiggy", merged.merchant)
        assertEquals("Food", merged.category)
        assertEquals("VM-HDFCBK + Google Pay", merged.sourceApp)
    }

    @Test fun sameRefOppositeDirectionIsNotMergedButIsSelfTransfer() {
        val credit = stored(TxnSource.SMS, "*xy99@okaxis", ref = "666111000000").copy(type = TxnType.CREDIT, amountPaise = 200)
        val debitParsed = ParsedTxn(200, TxnType.DEBIT, "x@okaxis", "5678", "666111000000", 0, TxnSource.SMS, "JK-BOBTXN-S", "z")
        assertNull(Deduper.findDuplicate(debitParsed, listOf(credit)))

        val debit = credit.copy(id = 2, type = TxnType.DEBIT)
        assertEquals(2, Deduper.selfTransferPair(listOf(credit, debit)).size)
        assertEquals(0, Deduper.selfTransferPair(listOf(credit)).size)
        assertEquals(0, Deduper.selfTransferPair(listOf(credit, debit.copy(amountPaise = 999))).size)
    }

    @Test fun twoRealSmsPaymentsAreKept() {
        assertNull(Deduper.findDuplicate(parsed(TxnSource.SMS, "X", 30_000), listOf(stored(TxnSource.SMS, "X"))))
    }

    @Test fun outsideWindowIsNotDuplicate() {
        assertNull(Deduper.findDuplicate(parsed(TxnSource.NOTIFICATION, "X", 10 * 60_000), listOf(stored(TxnSource.SMS, "X"))))
    }

    @Test fun dailyLimitLevels() {
        assertEquals(DailyLimit.Level.OK, DailyLimit.level(79_900, 100_000))
        assertEquals(DailyLimit.Level.NEAR, DailyLimit.level(80_000, 100_000))
        assertEquals(DailyLimit.Level.OVER, DailyLimit.level(100_000, 100_000))
        assertEquals(DailyLimit.Level.OK, DailyLimit.level(500_000, 0))
    }

    @Test fun peopleVsBusinesses() {
        assertEquals(true, Categorizer.looksLikePerson("RAHUL KUMAR SHARMA"))
        assertEquals(true, Categorizer.looksLikePerson("rahul.k@okaxis"))
        assertEquals(true, Categorizer.looksLikePerson("9876543210@ybl"))
        assertEquals(false, Categorizer.looksLikePerson("CHAITANYA TRADING PRIVATE LIMITED"))
        assertEquals(false, Categorizer.looksLikePerson("DELUX BAKERY AND CAFE"))
        assertEquals(false, Categorizer.looksLikePerson("swiggy@icici"))
        assertEquals(false, Categorizer.looksLikePerson(null))
    }

    @Test fun categories() {
        assertEquals("Food", Categorizer.categorize(TxnType.DEBIT, "SWIGGY", ""))
        assertEquals("Travel", Categorizer.categorize(TxnType.DEBIT, "uber.rides@icici", ""))
        assertEquals("Transfers", Categorizer.categorize(TxnType.DEBIT, "9876543210@ybl", ""))
        assertEquals("Other", Categorizer.categorize(TxnType.DEBIT, "Coca-Cola Store", "current account"))
        assertEquals("Other", Categorizer.categorize(TxnType.DEBIT, "CHAITANYA TRADING P", ""))
        assertEquals("Food", Categorizer.categorize(TxnType.DEBIT, "Chai Point", ""))
        assertEquals("Other", Categorizer.categorize(TxnType.DEBIT, null, "paid at hotel swiggy"))
        assertEquals("Income", Categorizer.categorize(TxnType.CREDIT, "anyone", ""))
    }
}
