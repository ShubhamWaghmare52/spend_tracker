package com.shubham.spendtracker.parser

import com.shubham.spendtracker.data.Transaction

/**
 * One payment usually shows up twice: a bank SMS and a UPI-app notification.
 * Those are merged into a single transaction.
 */
object Deduper {
    const val WINDOW_MS = 3 * 60 * 1000L

    fun findDuplicate(new: ParsedTxn, candidates: List<Transaction>): Transaction? {
        // A shared UPI ref with the opposite direction is the other half of a self transfer, not a duplicate.
        new.upiRef?.let { ref -> candidates.firstOrNull { it.upiRef == ref && it.type == new.type }?.let { return it } }
        return candidates.firstOrNull { old ->
            old.amountPaise == new.amountPaise &&
                old.type == new.type &&
                kotlin.math.abs(old.timestamp - new.timestamp) <= WINDOW_MS &&
                // Same source twice is only a duplicate if it is literally the same message
                // (backfill re-reading an SMS, or a notification being re-posted).
                (old.source != new.source || old.rawText == new.rawText)
        }
    }

    /**
     * Rows sharing one UPI ref that include both a debit and a credit of the same amount are
     * money moving between the user's own accounts. Returns those rows, or empty.
     */
    fun selfTransferPair(sameRef: List<Transaction>): List<Transaction> {
        val debits = sameRef.filter { it.type == TxnType.DEBIT }
        val credits = sameRef.filter { it.type == TxnType.CREDIT }
        val matched = debits.filter { d -> credits.any { it.amountPaise == d.amountPaise } }
        if (matched.isEmpty()) return emptyList()
        return matched + credits.filter { c -> matched.any { it.amountPaise == c.amountPaise } }
    }

    /** Fills gaps in [old] with details from [new]; keeps the user's category. */
    fun merge(old: Transaction, new: ParsedTxn): Transaction {
        val betterMerchant = when {
            old.merchant == null -> new.merchant
            new.merchant == null -> old.merchant
            // UPI apps show a friendly name ("Swiggy"); SMS often only has the VPA.
            new.source == TxnSource.NOTIFICATION && '@' in old.merchant -> new.merchant
            else -> old.merchant
        }
        return old.copy(
            merchant = betterMerchant,
            accountLast4 = old.accountLast4 ?: new.accountLast4,
            upiRef = old.upiRef ?: new.upiRef,
            sourceApp = if (new.sourceApp in old.sourceApp) old.sourceApp else "${old.sourceApp} + ${new.sourceApp}",
        )
    }
}
