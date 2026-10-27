package com.shubham.spendtracker.insights

import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.parser.TxnType
import com.shubham.spendtracker.ui.bankName

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
    fun latest(txns: List<Transaction>): List<AccountBalance> {
        val withBank = txns.mapNotNull { t -> bankName(t.sourceApp)?.let { t to it } }

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
            .mapNotNull { (account, rows) ->
                val sorted = rows.sortedWith(compareBy({ it.timestamp }, { it.id }))
                val readingAt = sorted.indexOfLast { parse(it.rawText) != null }
                if (readingAt < 0) return@mapNotNull null
                val later = sorted.drop(readingAt + 1)
                val change = later.sumOf { if (it.type == TxnType.CREDIT) it.amountPaise else -it.amountPaise }
                AccountBalance(
                    bank = account.first,
                    last4 = account.second,
                    balancePaise = parse(sorted[readingAt].rawText)!! + change,
                    asOf = sorted.last().timestamp,
                    exact = later.isEmpty(),
                )
            }
            .sortedWith(compareBy({ it.bank }, { it.last4 }))
    }
}
