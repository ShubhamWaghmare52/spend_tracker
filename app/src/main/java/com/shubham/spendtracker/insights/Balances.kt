package com.shubham.spendtracker.insights

import com.shubham.spendtracker.data.ManualPayment
import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.parser.TxnSource
import com.shubham.spendtracker.parser.TxnType
import com.shubham.spendtracker.ui.bankName
import kotlin.math.abs

/**
 * An account's balance from its bank's SMS. When later payments on the account came without a balance
 * (HDFC's UPI alerts don't carry one), they're applied to the last reported figure and [exact] is false.
 */
data class AccountBalance(
    val bank: String,
    val last4: String?,
    val balancePaise: Long,
    /** Time of the latest payment the figure accounts for. */
    val asOf: Long,
    val exact: Boolean,
) {
    val key: String get() = "$bank:${last4.orEmpty()}"
}

/**
 * Money that moved on an account between two balance SMS without any record of it:
 * negative when the balance is lower than expected (a payment out is missing).
 */
data class BalanceGap(val bank: String, val last4: String?, val from: Long, val to: Long, val amountPaise: Long) {
    val key: String get() = "$bank:${last4.orEmpty()}:$from:$to"
}

object Balances {
    // "AvlBal:Rs512.40", "Avl bal:INR 21,500.25", "Avlbl Amt:Rs.4321.50", "Available Balance is Rs. 1,234".
    private val available = Regex(
        """\b(?:avl\.?\s*bal(?:ance)?|avlbal|avlbl\.?\s*amt|avail(?:able)?\.?\s*bal(?:ance)?)\b\s*(?:is\s*)?[:.\-]?\s*""" +
            """(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    // Fallback for "Bal Rs.1.8" and "Total Bal:Rs.4321.50CR".
    private val anyBalance = Regex(
        """\b(?:total\s*)?bal(?:ance)?\b\s*(?:is\s*)?[:.\-]?\s*(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    // A credit card's "available" figure is a spending limit, not money in the bank.
    private val creditCard = Regex("""\bcredit\s*card\b""", RegexOption.IGNORE_CASE)

    // "From HDFC Bank Card x1234": a debit card, which draws on one of the bank's accounts.
    private val viaCard = Regex("""\bcard\b[^.]{0,20}?[xX*]+\d{3,4}""", RegexOption.IGNORE_CASE)

    /** The balance an SMS reports after its payment, in paise. */
    fun parse(rawText: String): Long? {
        if (creditCard.containsMatchIn(rawText)) return null
        val m = available.find(rawText) ?: anyBalance.find(rawText) ?: return null
        return m.groupValues[1].replace(",", "").trimEnd('.').toBigDecimalOrNull()?.movePointRight(2)?.toLong()
    }

    /** Latest balance of every account that has reported one, by bank. */
    fun latest(txns: List<Transaction>): List<AccountBalance> =
        byAccount(txns).mapNotNull { (account, rows) ->
            val readingAt = rows.indexOfLast { parse(it.rawText) != null }
            if (readingAt < 0) return@mapNotNull null
            val later = rows.drop(readingAt + 1)
            AccountBalance(
                bank = account.first,
                last4 = account.second,
                balancePaise = parse(rows[readingAt].rawText)!! + later.sumOf(::signed),
                asOf = rows.last().timestamp,
                exact = later.isEmpty(),
            )
        }.sortedWith(compareBy({ it.bank }, { it.last4 }))

    /**
     * Places where a bank's reported balance doesn't follow from the one before it and the payments
     * recorded in between, i.e. money moved without an SMS or notification. Gaps that payments the app
     * couldn't tie to an account (a UPI app's notification alone) account for are left out. Newest first.
     */
    fun gaps(txns: List<Transaction>, since: Long = 0L): List<BalanceGap> {
        val unattributed = txns.filter { bankOf(it) == null }
        return byAccount(txns).flatMap { (account, rows) ->
            val found = mutableListOf<BalanceGap>()
            var previous: Pair<Long, Long>? = null // time and balance of the last reading
            var moved = 0L
            for (t in rows) {
                if (previous != null) moved += signed(t)
                val reading = parse(t.rawText) ?: continue
                previous?.let { (from, balance) ->
                    val gap = reading - (balance + moved)
                    if (abs(gap) >= MIN_GAP_PAISE && t.timestamp >= since && !explained(gap, unattributed, from, t.timestamp)) {
                        found += BalanceGap(account.first, account.second, from, t.timestamp, gap)
                    }
                }
                previous = t.timestamp to reading
                moved = 0L
            }
            found
        }.sortedByDescending { it.to }
    }

    // Bank figures and our sums can differ by paise (rounded interest, charges); a rupee is noise.
    private const val TOLERANCE_PAISE = 100L

    // Smaller gaps (a stray ₹2 cashback) aren't worth asking about.
    private const val MIN_GAP_PAISE = 1000L

    private fun signed(t: Transaction) = if (t.type == TxnType.CREDIT) t.amountPaise else -t.amountPaise

    private fun explained(gap: Long, unattributed: List<Transaction>, from: Long, to: Long): Boolean {
        val inside = unattributed.filter { it.timestamp in (from + 1)..to }.map(::signed)
        return abs(gap - inside.sum()) < TOLERANCE_PAISE || inside.any { abs(gap - it) < TOLERANCE_PAISE }
    }

    // Payments the user added carry the account's bank by name; everything else by its SMS sender.
    private fun bankOf(t: Transaction): String? =
        if (t.source == TxnSource.MANUAL) t.sourceApp.takeIf { it != ManualPayment.SOURCE } else bankName(t.sourceApp)

    /** Each account's payments in time order, keyed by bank and account number. */
    private fun byAccount(txns: List<Transaction>): Map<Pair<String, String?>, List<Transaction>> {
        val withBank = txns.mapNotNull { t -> bankOf(t)?.let { t to it } }

        // Accounts each bank's SMS name directly ("A/C XXXXXX5678"), as opposed to a card or no number at all.
        val named = withBank
            .filter { (t, _) -> t.accountLast4 != null && !viaCard.containsMatchIn(t.rawText) }
            .groupBy({ it.second }, { it.first.accountLast4!! })
            .mapValues { it.value.toSet() }

        // BOB's credit alerts and HDFC's debit-card alerts don't name the account: when the bank has only
        // one account on record, that's the one.
        fun accountOf(t: Transaction, bank: String): String? {
            val direct = t.accountLast4.takeIf { it != null && !viaCard.containsMatchIn(t.rawText) }
            return direct ?: named[bank]?.singleOrNull() ?: t.accountLast4
        }

        return withBank
            .groupBy({ (t, bank) -> bank to accountOf(t, bank) }, { it.first })
            .filterKeys { (bank, last4) -> last4 != null || named[bank].isNullOrEmpty() }
            .mapValues { (_, rows) -> rows.sortedWith(compareBy({ it.timestamp }, { it.id })) }
    }
}
