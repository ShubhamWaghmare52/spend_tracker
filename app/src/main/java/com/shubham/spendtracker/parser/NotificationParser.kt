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
            """recharge now|expir\w*|win|bill is|autopay set|collect)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val credit = Regex("""\b(received|sent you|credited|refund(?:ed)?|added to)\b""", RegexOption.IGNORE_CASE)
    private val debit = Regex("""\b(paid|sent|payment (?:of|to)|debited|transferred|spent)\b""", RegexOption.IGNORE_CASE)

    private val senderFirst = Regex("""^([A-Za-z][A-Za-z .'&\-]{1,40}?) sent you""", RegexOption.IGNORE_CASE)

    fun parse(packageName: String, title: String?, text: String?, timestamp: Long): ParsedTxn? {
        val app = supportedApps[packageName] ?: return null
        val combined = listOfNotNull(title, text).joinToString(". ").replace(Regex("""\s+"""), " ").trim()
        if (combined.isEmpty() || ignore.containsMatchIn(combined)) return null

        val type = ParseUtils.earliest(combined, TxnType.CREDIT to credit, TxnType.DEBIT to debit) ?: return null
        val amount = ParseUtils.findAmountPaise(combined) ?: return null

        val merchant = if (type == TxnType.DEBIT) {
            ParseUtils.findParty(combined, listOf("to", "at"))
        } else {
            senderFirst.find(combined)?.groupValues?.get(1)?.trim() ?: ParseUtils.findParty(combined, listOf("from", "by"))
        }

        return ParsedTxn(
            amountPaise = amount,
            type = type,
            merchant = merchant,
            accountLast4 = null,
            upiRef = ParseUtils.findRef(combined),
            timestamp = timestamp,
            source = TxnSource.NOTIFICATION,
            sourceApp = app,
            rawText = combined,
        )
    }
}
