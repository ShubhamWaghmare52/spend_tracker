package com.shubham.spendtracker.parser

import com.shubham.spendtracker.data.Transaction

object Categorizer {
    const val INCOME = "Income"
    const val TRANSFERS = "Transfers"
    const val OTHER = "Other"
    const val CASH = "Cash"

    /** Money moved between the user's own accounts; excluded from spent/received totals. */
    const val SELF = "Self transfer"

    private val rules: List<Pair<String, List<String>>> = listOf(
        "Food" to listOf("swiggy", "zomato", "eatsure", "domino", "pizza", "mcdonald", "kfc", "burger", "starbucks",
            "cafe", "restaurant", "hotel", "chai", "bakery", "bekery", "dhaba", "biryani", "haldiram", "tea", "latte", "icecream", "ice cream", "sweet",
            "pohe", "momos", "chicken", "chiken", "bhelpuri", "bhellpuri", "wadewale", "misal", "vadapav"),
        "Groceries" to listOf("blinkit", "zepto", "instamart", "bigbasket", "dmart", "jiomart", "grofers", "kirana",
            "reliance fresh", "more retail", "nature", "milk", "dairy", "vegetable", "dudh",
            "supermart", "star bazaar"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tata cliq", "croma",
            "reliance digital", "decathlon", "ikea", "lifestyle", "westside", "zara", "h&m", "zudio"),
        "Travel" to listOf("uber", "ola", "rapido", "irctc", "redbus", "makemytrip", "goibibo", "indigo", "air india",
            "metro", "cleartrip", "ixigo", "fastag", "yatra", "railway", "airport"),
        "Fuel" to listOf("petrol", "fuel", "hpcl", "bpcl", "indian oil", "iocl", "shell", "nayara"),
        "Bills & Recharge" to listOf("airtel", "jio", "vodafone", "vi prepaid", "bsnl", "electricity", "mseb", "mahadiscom",
            "bescom", "tata power", "adani", "gas", "broadband", "act fibernet", "recharge", "dth", "tatasky", "bill"),
        "Entertainment" to listOf("netflix", "hotstar", "prime video", "spotify", "bookmyshow", "pvr", "inox",
            "youtube", "sonyliv", "zee5", "steam", "playstation", "district"),
        "Health" to listOf("pharma", "medical", "chemist", "apollo", "1mg", "netmeds", "pharmeasy", "hospital",
            "clinic", "diagnostic", "lab", "medplus"),
        "Education" to listOf("udemy", "coursera", "byju", "unacademy", "school", "college", "university", "fees"),
        "Investments" to listOf("zerodha", "groww", "upstox", "mutual fund", "sip", "indmoney", "kuvera", "coin"),
        "Rent" to listOf("rent", "nobroker", "housing", "maintenance", "society"),
    )

    val all: List<String> = rules.map { it.first } + listOf(CASH, TRANSFERS, SELF, INCOME, OTHER)

    // VPAs like 9876543210@ybl or rahul.sharma@okaxis are usually people, not shops.
    private val personVpa = Regex("""^(\d{10}|[a-z]+[._]?[a-z]*\d{0,4})@(ybl|ibl|axl|okaxis|okhdfcbank|okicici|oksbi|paytm|apl|upi)$""")

    // Paying the card bill from the bank: the card purchases were already counted when made.
    // Paying the card *bill*: "bill"/"dues" next to the card, or money sent "towards" the card.
    // A bare "payment ... on your credit card at X" is a purchase, so "payment" alone doesn't count.
    private val cardBill = Regex(
        """(credit card|\bcc\b)[^.]{0,30}\b(bill|dues)\b|\b(bill|dues)\b[^.]{0,30}(credit card|\bcc\b)|""" +
            """\btowards\b[^.]{0,40}(credit card|\bcc\b)""",
        RegexOption.IGNORE_CASE,
    )
    // ATM/cash withdrawals: "Withdrawn Rs.500 From HDFC Bank Card x5090 At KOLHAPUR BR".
    private val cashWithdrawal = Regex(
        """\bwithdrawn\b.{0,60}?\b(card|atm)\b|\b(atm|cash)\s+(withdrawal|wdl)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val cardSpend = Regex("""\b(spent|purchase|using|used)\b""", RegexOption.IGNORE_CASE)

    /** A bank debit that pays off a credit card bill (counted as moving money, not spending). */
    fun isCardBillPayment(type: TxnType, rawText: String): Boolean =
        type == TxnType.DEBIT && cardBill.containsMatchIn(rawText) && !cardSpend.containsMatchIn(rawText)

    /**
     * [ownPayees] are payees already seen in the user's own self transfers; paying one of them again
     * is a self transfer even when the receiving account sends no credit SMS to pair it with.
     */
    fun categorize(type: TxnType, merchant: String?, rawText: String, ownPayees: Collection<String> = emptyList()): String {
        if (type == TxnType.CREDIT) return INCOME
        if (isCardBillPayment(type, rawText)) return SELF
        if (cashWithdrawal.containsMatchIn(rawText)) return CASH
        if (isOwnPayee(merchant, ownPayees)) return SELF
        // Match the payee only; raw SMS text has words like "current" or "bill" that mislead.
        val haystack = merchant?.lowercase() ?: return OTHER
        rules.firstOrNull { (_, keys) -> keys.any { keyMatches(it, haystack) } }?.let { return it.first }
        return if (looksLikePerson(merchant)) TRANSFERS else OTHER
    }

    // Short keys must be whole words ("chai" must not match "CHAITANYA"); longer ones may prefix ("olacabs").
    private fun keyMatches(key: String, haystack: String): Boolean {
        val k = Regex.escape(key.trim())
        val re = if (key.trim().length <= 4) """\b$k\b""" else """\b$k"""
        return Regex(re).containsMatchIn(haystack)
    }

    private val personName = Regex("""^[A-Za-z]+(?: [A-Za-z]+){1,3}$""")
    private val businessWords = setOf(
        "ltd", "limited", "pvt", "private", "llp", "store", "stores", "mart", "enterprise", "enterprises", "trader",
        "traders", "trading", "services", "solutions", "agency", "agencies", "hotel", "cafe", "restaurant", "medical",
        "pharma", "bank", "finance", "payment", "payments", "india", "technologies", "tech", "food", "foods", "super",
        "bazaar", "shop", "shope", "shoppe", "centre", "center", "co", "university", "school", "college", "sweets",
        "auto", "corner", "motors", "garage", "general", "provision", "provisions", "jewellers", "jewelers",
        "electrical", "electricals", "hardware", "mobile", "mobiles", "collection", "emporium", "works", "industries",
        "trust", "ministries", "bhandar", "kendra", "seva", "studio", "salon", "br", "branch", "agro", "movies",
        "stall", "hub", "king", "fund", "welfare", "mahila", "sahakari",
    )

    // "M S SHEETAL FOODS", "M/S ANKIT …": Messrs, i.e. a firm.
    private val messrs = Regex("""^m\s*/?\s*s\b""", RegexOption.IGNORE_CASE)

    // Google Pay's personal handles (name@okaxis etc.); shops mostly use other handles.
    private val personalHandle = Regex("""^[a-z][a-z0-9._-]*@ok(axis|hdfcbank|icici|sbi)$""")

    /** Whether a payee looks like a person ("RAHUL SHARMA", rahul.s@okaxis) rather than a business. */
    fun looksLikePerson(merchant: String?): Boolean {
        val m = merchant?.trim() ?: return false
        if ('@' in m) return personVpa.matches(m.lowercase()) || personalHandle.matches(m.lowercase())
        if (!personName.matches(m) || messrs.containsMatchIn(m)) return false
        val words = m.lowercase().split(' ')
        if (words.any { it in businessWords }) return false
        // Banks cut long payee names short ("SHREE DHANLAXMI SWEE", "… DUDH KENDR"): a last word that
        // starts a business word is taken as one.
        val last = words.last()
        return last.length < 4 || businessWords.none { it.length > last.length && it.startsWith(last) }
    }

    // A failed UPI payment's reversal reuses the original ref, so it pairs up like a self transfer.
    private val reversal = Regex("""\b(revers\w*|refund\w*)\b""", RegexOption.IGNORE_CASE)

    /**
     * Payees of the user's own self transfers. Only person-like payees count, and reversal pairs are
     * skipped, so a shop or friend whose failed payment was refunded isn't taken for the user.
     */
    fun ownPayees(selfRows: List<Transaction>): List<String> {
        val reversedRefs = selfRows.filter { it.upiRef != null && reversal.containsMatchIn(it.rawText) }.mapNotNull { it.upiRef }.toSet()
        return selfRows.filter { it.upiRef !in reversedRefs && looksLikePerson(it.merchant) }.mapNotNull { it.merchant }.distinct()
    }

    /**
     * Whether [merchant] is one of [ownPayees]. VPAs match across Google Pay's "-2"/"-3" handles for the
     * same user and across banks cutting the handle short ("name-2@okaxi"); names must match exactly.
     */
    fun isOwnPayee(merchant: String?, ownPayees: Collection<String>): Boolean {
        if (merchant == null || ownPayees.isEmpty()) return false
        val vpa = vpaParts(merchant)
        if (vpa == null) {
            val key = merchantKey(merchant) ?: return false
            return ownPayees.any { '@' !in it && merchantKey(it) == key }
        }
        return ownPayees.any { own ->
            val (user, handle) = vpaParts(own) ?: return@any false
            user == vpa.first && (handle.startsWith(vpa.second) || vpa.second.startsWith(handle))
        }
    }

    // "name-2@okaxis" → ("name", "okaxis"). Masked VPAs ("*am52@okaxis") can't be matched.
    private fun vpaParts(vpa: String): Pair<String, String>? {
        val m = vpa.trim().lowercase()
        val at = m.indexOf('@')
        if (at <= 0 || at == m.lastIndex || '*' in m || ' ' in m) return null
        return m.substring(0, at).replace(Regex("""-\d+$"""), "") to m.substring(at + 1)
    }

    /** Key used to remember a user's category choice for a merchant. */
    fun merchantKey(merchant: String?): String? =
        merchant?.lowercase()?.replace(Regex("""[^a-z0-9@]"""), "")?.takeIf { it.length >= 2 }
}
