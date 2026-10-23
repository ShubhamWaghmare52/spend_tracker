package com.shubham.spendtracker.parser

object Categorizer {
    const val INCOME = "Income"
    const val TRANSFERS = "Transfers"
    const val OTHER = "Other"

    /** Money moved between the user's own accounts; excluded from spent/received totals. */
    const val SELF = "Self transfer"

    private val rules: List<Pair<String, List<String>>> = listOf(
        "Food" to listOf("swiggy", "zomato", "eatsure", "domino", "pizza", "mcdonald", "kfc", "burger", "starbucks",
            "cafe", "restaurant", "hotel", "chai", "bakery", "dhaba", "biryani", "haldiram"),
        "Groceries" to listOf("blinkit", "zepto", "instamart", "bigbasket", "dmart", "jiomart", "grofers", "kirana",
            "reliance fresh", "more retail", "nature", "milk", "dairy", "vegetable"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tata cliq", "croma",
            "reliance digital", "decathlon", "ikea", "lifestyle", "westside", "zara", "h&m"),
        "Travel" to listOf("uber", "ola", "rapido", "irctc", "redbus", "makemytrip", "goibibo", "indigo", "air india",
            "metro", "cleartrip", "ixigo", "fastag", "yatra", "railway", "airport"),
        "Fuel" to listOf("petrol", "fuel", "hpcl", "bpcl", "indian oil", "iocl", "shell", "nayara"),
        "Bills & Recharge" to listOf("airtel", "jio", "vodafone", "vi prepaid", "bsnl", "electricity", "mseb", "mahadiscom",
            "bescom", "tata power", "adani", "gas", "broadband", "act fibernet", "recharge", "dth", "tatasky", "bill"),
        "Entertainment" to listOf("netflix", "hotstar", "prime video", "spotify", "bookmyshow", "pvr", "inox",
            "youtube", "sonyliv", "zee5", "steam", "playstation"),
        "Health" to listOf("pharma", "medical", "chemist", "apollo", "1mg", "netmeds", "pharmeasy", "hospital",
            "clinic", "diagnostic", "lab", "medplus"),
        "Education" to listOf("udemy", "coursera", "byju", "unacademy", "school", "college", "university", "fees"),
        "Investments" to listOf("zerodha", "groww", "upstox", "mutual fund", "sip", "indmoney", "kuvera", "coin"),
        "Rent" to listOf("rent", "nobroker", "housing", "maintenance", "society"),
    )

    val all: List<String> = rules.map { it.first } + listOf(TRANSFERS, SELF, INCOME, OTHER)

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
    private val cardSpend = Regex("""\b(spent|purchase|using|used)\b""", RegexOption.IGNORE_CASE)

    /** A bank debit that pays off a credit card bill (counted as moving money, not spending). */
    fun isCardBillPayment(type: TxnType, rawText: String): Boolean =
        type == TxnType.DEBIT && cardBill.containsMatchIn(rawText) && !cardSpend.containsMatchIn(rawText)

    fun categorize(type: TxnType, merchant: String?, rawText: String): String {
        if (type == TxnType.CREDIT) return INCOME
        if (isCardBillPayment(type, rawText)) return SELF
        // Match the payee only; raw SMS text has words like "current" or "bill" that mislead.
        val haystack = merchant?.lowercase() ?: return OTHER
        rules.firstOrNull { (_, keys) -> keys.any { keyMatches(it, haystack) } }?.let { return it.first }
        val m = merchant?.lowercase()?.trim()
        if (m != null && personVpa.matches(m)) return TRANSFERS
        return OTHER
    }

    // Short keys must be whole words ("chai" must not match "CHAITANYA"); longer ones may prefix ("olacabs").
    private fun keyMatches(key: String, haystack: String): Boolean {
        val k = Regex.escape(key.trim())
        val re = if (key.trim().length <= 4) """\b$k\b""" else """\b$k"""
        return Regex(re).containsMatchIn(haystack)
    }

    private val personName = Regex("""^[A-Za-z]+(?: [A-Za-z]+){1,3}$""")
    private val businessWords = Regex(
        """\b(ltd|limited|pvt|private|llp|stores?|mart|enterprises?|traders?|trading|services|solutions|agency|hotel|""" +
            """cafe|restaurant|medical|pharma|bank|finance|payments?|india|technologies|tech|foods?|super|bazaar|shop|""" +
            """centre|center|co|university|school|college)\b""",
        RegexOption.IGNORE_CASE,
    )

    // Google Pay's personal handles (name@okaxis etc.); shops mostly use other handles.
    private val personalHandle = Regex("""^[a-z][a-z0-9._-]*@ok(axis|hdfcbank|icici|sbi)$""")

    /** Whether a payee looks like a person ("RAHUL SHARMA", rahul.s@okaxis) rather than a business. */
    fun looksLikePerson(merchant: String?): Boolean {
        val m = merchant?.trim() ?: return false
        if ('@' in m) return personVpa.matches(m.lowercase()) || personalHandle.matches(m.lowercase())
        return personName.matches(m) && !businessWords.containsMatchIn(m)
    }

    /** Key used to remember a user's category choice for a merchant. */
    fun merchantKey(merchant: String?): String? =
        merchant?.lowercase()?.replace(Regex("""[^a-z0-9@]"""), "")?.takeIf { it.length >= 2 }
}
