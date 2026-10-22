package com.shubham.spendtracker.parser

/** Parses Indian bank transaction alerts (UPI, card, netbanking, ATM). */
object SmsParser {
    private val ignore = Regex(
        """\b(otp|one time password|verification code|will be debited|is due|due on|due date|minimum amount|""" +
            """requested|request|collect|failed|declined|unsuccessful|pre-approved|apply now|offer|emi of|""" +
            """has been registered|mandate|will be processed|ensure sufficient|penal|voucher|claim|unlock|""" +
            """congrat\w*|lifetime free|get card|beneficiary)\b""",
        RegexOption.IGNORE_CASE,
    )

    // DLT sender IDs end in -P for promotional, -S service, -T transactional, -G government.
    private val promoSender = Regex("""-P$""", RegexOption.IGNORE_CASE)

    // A real alert mentions some account instrument.
    private val bankContext = Regex("""\b(a/?c|acct|account|card|upi|vpa|bank|imps|neft|rtgs|atm|wallet)\b""", RegexOption.IGNORE_CASE)

    // Bare "debit"/"credit" are left out: they appear in "Credit Card" and "Debit Card" ads.
    // "Dr."/"Cr." are BOB's shorthand ("Rs.2.00 Dr. from A/C … and Cr. to …"); \b keeps "100.00CR" balances out.
    private val debit = Regex("""\b(debited|spent|paid|sent|withdrawn|purchase|transferred|dr)\b""", RegexOption.IGNORE_CASE)
    private val credit = Regex("""\b(credited|received|deposited|refund(?:ed)?|credit alert|cr)\b""", RegexOption.IGNORE_CASE)

    private val account = Regex(
        """(?:a/?c|acct|account|card)\s*(?:no\.?)?\s*(?:ending\s*(?:with|in)?\s*)?[xX*.\s]*(\d{3,6})\b""",
        RegexOption.IGNORE_CASE,
    )

    private val apayWallet = Regex("""\bapay wallet\b""", RegexOption.IGNORE_CASE)

    // Personal phone numbers send chat SMS, not bank alerts.
    private val phoneSender = Regex("""^\+?\d{10,13}$""")

    fun parse(sender: String, body: String, timestamp: Long): ParsedTxn? {
        val text = body.replace(Regex("""\s+"""), " ").trim()
        if (phoneSender.matches(sender.trim()) || promoSender.containsMatchIn(sender.trim())) return null
        if (ignore.containsMatchIn(text) || !bankContext.containsMatchIn(text)) return null

        val type = ParseUtils.earliest(text, TxnType.DEBIT to debit, TxnType.CREDIT to credit) ?: return null
        val amount = ParseUtils.findAmountPaise(text) ?: return null

        val info = ParseUtils.findUpiInfo(text)
        val preps = if (type == TxnType.DEBIT) listOf("to", "at", "towards", "trf to") else listOf("from", "by")
        val merchant = info?.second ?: ParseUtils.findParty(text, preps) ?: ParseUtils.findInfoParty(text)
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
}
