package com.shubham.spendtracker.parser

/** Parses Indian bank transaction alerts (UPI, card, netbanking, ATM). */
object SmsParser {
    private val ignore = Regex(
        """\b(otp|one time password|verification code|will be debited|will be deducted|will be credited|to be credited|will be refunded|is due|due on|due date|minimum amount|""" +
            """requested|request|collect|failed|declined|unsuccessful|pre-approved|apply now|offer|emi of|""" +
            """has been registered|will be processed|ensure sufficient|penal|voucher|claim|unlock|""" +
            """congrat\w*|lifetime free|get card|beneficiary)\b""",
        RegexOption.IGNORE_CASE,
    )

    // The card company confirming a bill payment: the money left the user's bank (already
    // recorded there) and moved to their card. It's neither income nor new spending.
    private val cardBillAck = Regex(
        """\b(?:received|credited)\b[^.]{0,40}\btowards\b[^.]{0,30}\b(credit card|card (account|ending)|cc)\b""",
        RegexOption.IGNORE_CASE,
    )

    // Setting up, changing or ending an autopay or UPI mandate moves no money by itself
    // ("UPI-Mandate for Rs.199 is successfully created", "revoked a mandate on …"). Its amount is a limit.
    // An executed mandate payment ("UPI Mandate: Sent Rs.1,450 … To ACME HOME LOANS") is a real debit.
    private val mandateSetup = Regex(
        """\b(?:e-?mandate|mandate|auto ?pay|standing instruction)\b[^.]{0,80}?\b(?:created|registered|activated|set ?up|revoked|cancell?ed|paused|modified|declined)\b""" +
            """|\b(?:revoked|cancell?ed|created|registered|activated)\b[^.]{0,30}?\b(?:mandate|auto ?pay)\b""",
        RegexOption.IGNORE_CASE,
    )

    // Except for the first charge some banks take while setting one up (BOB card autopay):
    // "An initial amount of Rs. 499.00 has been debited from your account. StreamFlix can initiate …".
    private val initialDebit = Regex(
        """\binitial amount of\s*(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:has been\s+)?debited""",
        RegexOption.IGNORE_CASE,
    )
    private val autopayPayee = Regex("""\b([A-Za-z][A-Za-z0-9 &'\-]{1,40}?)\s+can initiate\b""", RegexOption.IGNORE_CASE)

    // DLT sender IDs end in -P for promotional, -S service, -T transactional, -G government.
    private val promoSender = Regex("""-P$""", RegexOption.IGNORE_CASE)

    // A real alert mentions some account instrument.
    private val bankContext = Regex("""\b(a/?c|acct|account|card|upi|vpa|bank|imps|neft|rtgs|atm|wallet|cc)\b""", RegexOption.IGNORE_CASE)

    // Bare "debit"/"credit" count only right before an amount (IPPB: "A/C X1234 Debit Rs.250.00 for UPI to …",
    // SBI: "has a debit by transfer of Rs 2,000", India Post: "CREDIT with amount Rs. 1550"):
    // on their own they appear in "Credit Card" and "Debit Card" ads.
    // "Dr."/"Cr." are BOB's shorthand ("Rs.2.00 Dr. from A/C … and Cr. to …"); \b keeps "100.00CR" balances out.
    private const val AMOUNT_AHEAD =
        """(?=\s*(?:of\s+|with\s+(?:amount\s+)?|by\s+(?:transfer\s+)?(?:of\s+)?)?(?:rs\.?|inr|₹)\s*:?\s*\d)"""
    private val debit = Regex("""\b(debited|deducted|spent|paid|sent|withdrawn|purchase|transferred|dr|debit$AMOUNT_AHEAD)\b""", RegexOption.IGNORE_CASE)
    private val credit = Regex("""\b(credited|received|deposited|refund(?:ed)?|credit alert|cr|credit$AMOUNT_AHEAD)\b""", RegexOption.IGNORE_CASE)

    private val account = Regex(
        """(?:a/?c|acct|account|card|\bcc)\s*(?:no\.?)?\s*(?:ending\s*(?:with|in)?\s*)?[xXnN*.\s]*(\d{3,6})\b""",
        RegexOption.IGNORE_CASE,
    )

    private val apayWallet = Regex("""\bapay wallet\b""", RegexOption.IGNORE_CASE)

    // Personal phone numbers send chat SMS, not bank alerts.
    private val phoneSender = Regex("""^\+?\d{10,13}$""")

    fun parse(sender: String, body: String, timestamp: Long): ParsedTxn? {
        val text = body.replace(Regex("""\s+"""), " ").trim()
        if (phoneSender.matches(sender.trim()) || promoSender.containsMatchIn(sender.trim())) return null
        if (ignore.containsMatchIn(text) || cardBillAck.containsMatchIn(text) || !bankContext.containsMatchIn(text)) return null
        if (mandateSetup.containsMatchIn(text)) return initialCharge(sender, body, text, timestamp)

        val type = ParseUtils.earliest(text, TxnType.DEBIT to debit, TxnType.CREDIT to credit) ?: return null
        val amount = ParseUtils.findAmountPaise(text) ?: return null

        // Line breaks end a name ("UPI/P2A/…/PRIYA S\nAxis Bank"), so read UPI details from the original lines.
        val info = ParseUtils.findUpiInfo(body) ?: ParseUtils.findUpiInfo(text)
        val preps = if (type == TxnType.DEBIT) listOf("to", "at", "towards", "trf to") else listOf("from", "by")
        val merchant = info?.second ?: ParseUtils.findParty(text, preps)
            ?: (if (type == TxnType.DEBIT) ParseUtils.findCreditedParty(text) ?: ParseUtils.findCardOnParty(text) else null)
            ?: ParseUtils.findInfoParty(text)
            ?: if (apayWallet.containsMatchIn(text)) "Amazon Pay" else null

        return ParsedTxn(
            amountPaise = amount,
            type = type,
            merchant = merchant,
            accountLast4 = account.find(text)?.groupValues?.get(1)?.takeLast(4),
            upiRef = info?.first ?: ParseUtils.findRef(text),
            timestamp = timestamp,
            source = TxnSource.SMS,
            sourceApp = sender.trim(),
            rawText = body,
        )
    }

    /** The first charge taken while an autopay was set up, or null when none was (or it was ₹0). */
    private fun initialCharge(sender: String, body: String, text: String, timestamp: Long): ParsedTxn? {
        val paise = initialDebit.find(text)?.groupValues?.get(1)?.replace(",", "")
            ?.toBigDecimalOrNull()?.movePointRight(2)?.toLong()?.takeIf { it > 0 } ?: return null
        return ParsedTxn(
            amountPaise = paise,
            type = TxnType.DEBIT,
            merchant = autopayPayee.find(text)?.groupValues?.get(1)?.let(ParseUtils::cleanParty),
            accountLast4 = account.find(text)?.groupValues?.get(1)?.takeLast(4),
            upiRef = null,
            timestamp = timestamp,
            source = TxnSource.SMS,
            sourceApp = sender.trim(),
            rawText = body,
        )
    }
}
