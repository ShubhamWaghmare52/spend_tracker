package com.shubham.spendtracker.parser

enum class TxnType { DEBIT, CREDIT }

enum class TxnSource { SMS, NOTIFICATION, MANUAL }

/** A payment extracted from an SMS or notification, before it is stored. */
data class ParsedTxn(
    val amountPaise: Long,
    val type: TxnType,
    val merchant: String?,
    val accountLast4: String?,
    val upiRef: String?,
    val timestamp: Long,
    val source: TxnSource,
    val sourceApp: String,
    val rawText: String,
)

/** Regex helpers shared by the SMS and notification parsers. */
internal object ParseUtils {
    private val amountRegex =
        Regex("""(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

    // Amounts preceded by these words are balances/limits, not the payment itself.
    private val notPaymentAmount = Regex("""(bal|balance|limit|avl|available)[^0-9]{0,12}$""", RegexOption.IGNORE_CASE)

    fun findAmountPaise(text: String): Long? {
        for (m in amountRegex.findAll(text)) {
            val before = text.substring(maxOf(0, m.range.first - 20), m.range.first)
            if (notPaymentAmount.containsMatchIn(before)) continue
            val value = m.groupValues[1].replace(",", "").trimEnd('.')
            val paise = value.toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: continue
            if (paise > 0) return paise
        }
        // SBI style: "debited by 120.0" with no currency marker.
        return bareAmount.find(text)?.groupValues?.get(1)?.replace(",", "")
            ?.toBigDecimalOrNull()?.movePointRight(2)?.toLong()?.takeIf { it > 0 }
    }

    private val bareAmount =
        Regex("""\b(?:debited|credited)\s+(?:by|for|with)\s+([0-9][0-9,]*(?:\.[0-9]{1,2})?)\b""", RegexOption.IGNORE_CASE)

    /** Returns the keyword group whose earliest match comes first in [text]. */
    fun earliest(text: String, vararg groups: Pair<TxnType, Regex>): TxnType? =
        groups.mapNotNull { (type, re) -> re.find(text)?.let { type to it.range.first } }
            .minByOrNull { it.second }?.first

    private val stopAhead =
        """(?=\s+(?:on|ref|refno|upi|via|using|for|avl|if|not|txn|thru|dated|at|of|with|is|has|was|from|by|utr|total|bal|dr|cr|call)\b|\s*[,;()|]|\.\s|\.?$|\s+\d{2}[-/])"""

    private val skipCandidate = Regex(
        """^(?:your\b|a/?c\b|ac\b|acct|account|card|you\b|xx|\*|x\d|\d{1,2}[-/]|the\b|bank\b|upi\b|imps\b|neft\b|rtgs\b|\d+$)""",
        RegexOption.IGNORE_CASE,
    )

    // Trailing noise banks append after the payee name.
    private val partyNoise = Regex(
        """\s*(?:\bnot you\b|\butr\b|\bcall\b|\bavl\b|\btotal bal|\s(?:dr|cr)\s*-|-\s*t?x{3,}|\bx{4,}).*$""",
        RegexOption.IGNORE_CASE,
    )

    fun cleanParty(raw: String): String? =
        raw.replace(partyNoise, "").replace(Regex("""^(?:ach\s+[dc]-|vpa)\s*""", RegexOption.IGNORE_CASE), "")
            .trim().trimEnd('.', '-', ':').trim()
            // Masked VPAs like "*77-1@okaxis" are still a usable payee.
            .takeIf { it.length >= 2 && ('@' in it || !skipCandidate.containsMatchIn(it)) }

    /** Finds the payee/payer after one of [prepositions], e.g. "to SWIGGY on …". */
    fun findParty(text: String, prepositions: List<String>): String? {
        val re = Regex(
            // A whole VPA first: merchant VPAs like "shopname.eazypay@icici" run past the 40-char name limit.
            """\b(?:${prepositions.joinToString("|")})[\s:]+(?:vpa\s+|upi\s+id\s+|merchant\s+)?""" +
                """([A-Za-z0-9][A-Za-z0-9._\-]{1,63}@[A-Za-z0-9]{2,20}|[A-Za-z0-9][A-Za-z0-9 .&'_@*/\-]{1,40}?)$stopAhead""",
            RegexOption.IGNORE_CASE,
        )
        return re.findAll(text).firstNotNullOfOrNull { cleanParty(it.groupValues[1]) }
    }

    // HDFC style: "Info: CHAITANYA TRADING PRIVATE LIMITED Dr - XXXX…" or "For: DEBIT CARD ANNUAL FEE-…".
    private val infoField = Regex("""\b(?:info|for)\s*:\s*([A-Za-z][^|\n]{1,140}?)(?=\.\s|\.?\s*\||\.?$|\s+avl\b)""", RegexOption.IGNORE_CASE)

    fun findInfoParty(text: String): String? = infoField.find(text)?.groupValues?.get(1)?.let(::cleanParty)

    private val upiInfo = Regex("""upi/(?:p2m|p2a|cr|dr)?/?(\d{9,16})/([^/\n.|]+)""", RegexOption.IGNORE_CASE)
    private val refRegex = Regex(
        """(?:ref(?:erence)?\.?\s*(?:no\.?|number|#|id)?|refno|rrn|utr|\bupi)[\s:.\-]*(\d{6,16})""",
        RegexOption.IGNORE_CASE,
    )

    fun findUpiInfo(text: String): Pair<String, String?>? =
        upiInfo.find(text)?.let { it.groupValues[1] to cleanParty(it.groupValues[2]) }

    fun findRef(text: String): String? = refRegex.find(text)?.groupValues?.get(1)
}
