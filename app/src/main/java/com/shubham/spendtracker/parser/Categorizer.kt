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

    fun categorize(type: TxnType, merchant: String?, rawText: String): String {
        if (type == TxnType.CREDIT) return INCOME
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

    /** Key used to remember a user's category choice for a merchant. */
    fun merchantKey(merchant: String?): String? =
        merchant?.lowercase()?.replace(Regex("""[^a-z0-9@]"""), "")?.takeIf { it.length >= 2 }
}
