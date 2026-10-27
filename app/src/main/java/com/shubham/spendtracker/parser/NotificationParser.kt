package com.shubham.spendtracker.parser

/** Parses "Paid ₹250 to Swiggy" style notifications from UPI apps. */
object NotificationParser {
    val supportedApps = mapOf(
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "com.phonepe.app" to "PhonePe",
        "net.one97.paytm" to "Paytm",
        "in.org.npci.upiapp" to "BHIM",
        "in.amazon.mShop.android.shopping" to "Amazon Pay",
        "com.dreamplug.androidapp" to "CRED",
        "money.super.payments" to "super.money",
        "com.mobikwik_new" to "MobiKwik",
        "com.freecharge.android" to "Freecharge",
        "com.csam.icici.bank.imobile" to "iMobile",
        "com.snapwork.hdfc" to "HDFC Bank",
        "com.sbi.lotusintouch" to "YONO SBI",
        "com.axis.mobile" to "Axis Mobile",
        "com.msf.kbank.mobile" to "Kotak",
    )

    private val ignore = Regex(
        """\b(request(?:ed|s)?|reminder|offer|cashback|reward|scratch|failed|declined|pending|due|""" +
            """recharge now|expir\w*|win|bill is|autopay set|collect|upcoming|scheduled)\b""",
        RegexOption.IGNORE_CASE,
    )

    // "paid you" sits with credits: it starts where "paid" does, and earlier groups win ties.
    private val credit = Regex("""\b(received|sent you|paid you|credited|refund(?:ed)?|added to)\b""", RegexOption.IGNORE_CASE)
    private val debit = Regex("""\b(paid|sent|payment (?:of|to)|debited|transferred|spent)\b""", RegexOption.IGNORE_CASE)

    // "Rahul Sharma sent you ₹500", "RAHUL DESHMUKH paid you ₹500.00", "You've received a payment. Neha has paid you ₹33".
    private val senderFirst = Regex("""(?:^|\.\s)([A-Za-z][A-Za-z '&\-]{1,40}?) (?:has )?(?:sent|paid) you""", RegexOption.IGNORE_CASE)

    // Google Pay's call to action, which would otherwise read as a payee ("to view").
    private val tapToView = Regex("""\s*\btap to view(?: details)?\.?""", RegexOption.IGNORE_CASE)

    fun parse(packageName: String, title: String?, text: String?, timestamp: Long): ParsedTxn? {
        val app = supportedApps[packageName] ?: return null
        val combined = listOfNotNull(title, text).joinToString(". ").replace(Regex("""\s+"""), " ").trim()
        return parseText(app, combined, timestamp)
    }

    /** Parses a notification's joined title and text, as stored in a payment's raw text. */
    fun parseText(app: String, combined: String, timestamp: Long): ParsedTxn? {
        if (combined.isEmpty() || ignore.containsMatchIn(combined)) return null
        val body = combined.replace(tapToView, "")

        val type = ParseUtils.earliest(body, TxnType.CREDIT to credit, TxnType.DEBIT to debit) ?: return null
        val amount = ParseUtils.findAmountPaise(body) ?: return null

        val merchant = if (type == TxnType.DEBIT) {
            ParseUtils.findParty(body, listOf("to", "at"))
        } else {
            senderFirst.find(body)?.groupValues?.get(1)?.trim() ?: ParseUtils.findParty(body, listOf("from", "by"))
        }

        return ParsedTxn(
            amountPaise = amount,
            type = type,
            merchant = merchant,
            accountLast4 = null,
            upiRef = ParseUtils.findRef(body),
            timestamp = timestamp,
            source = TxnSource.NOTIFICATION,
            sourceApp = app,
            rawText = combined,
        )
    }
}
