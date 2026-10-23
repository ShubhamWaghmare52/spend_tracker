package com.shubham.spendtracker.data

import kotlin.math.abs

/** Decides which outstanding split shares an incoming payment pays back. */
object SplitMatcher {
    // UPI amounts can be rounded by a rupee either way.
    private const val TOLERANCE_PAISE = 100L

    private val titles = setOf("mr", "mrs", "ms", "miss", "dr", "prof", "shri", "smt", "sri")

    private fun tokens(s: String) =
        s.substringBefore('@').lowercase().split(Regex("[^a-z]+")).filter { it.length >= 2 }

    /**
     * Matches on the friend's first name: "Rahul" or "Rahul Sharma" match "RAHUL K SHARMA" and
     * "rahul.k@okaxis", but a shared surname alone ("Amit Sharma", "Sharma Sweets") doesn't.
     * Short names like "Om" work too.
     */
    fun samePerson(person: String, payer: String?): Boolean {
        if (payer.isNullOrBlank()) return false
        // Skip titles: "Dr. Sharma" must match on "sharma", not on every doctor who pays.
        val first = tokens(person).firstOrNull { it !in titles } ?: return false
        return first in tokens(payer)
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
