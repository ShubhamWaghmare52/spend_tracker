package com.shubham.spendtracker.data

import kotlin.math.abs

/** Decides which outstanding split shares an incoming payment pays back. */
object SplitMatcher {
    // UPI amounts can be rounded by a rupee either way.
    private const val TOLERANCE_PAISE = 100L

    private fun tokens(s: String) =
        s.substringBefore('@').lowercase().split(Regex("[^a-z]+")).filter { it.length >= 3 }.toSet()

    /** "Rahul" matches "RAHUL K SHARMA", "rahul.k@okaxis" and "Rahul Sharma". */
    fun samePerson(person: String, payer: String?): Boolean {
        if (payer.isNullOrBlank()) return false
        val p = tokens(person)
        val q = tokens(payer)
        return p.isNotEmpty() && p.any { it in q }
    }

    /**
     * Shares settled by [credit]: one share of the same amount, or all of that person's
     * outstanding shares when the credit equals their total.
     */
    fun settledBy(credit: Transaction, outstanding: List<Split>): List<Split> {
        val byPerson = outstanding.filter { samePerson(it.person, credit.merchant) }.groupBy { it.person.lowercase() }
        for (shares in byPerson.values) {
            shares.firstOrNull { abs(it.sharePaise - credit.amountPaise) <= TOLERANCE_PAISE }?.let { return listOf(it) }
            if (shares.size > 1 && abs(shares.sumOf { it.sharePaise } - credit.amountPaise) <= TOLERANCE_PAISE) return shares
        }
        return emptyList()
    }
}
