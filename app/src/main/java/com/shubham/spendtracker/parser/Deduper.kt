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
     * Whether two stored rows are one payment. A payment SMS carries two times: when the bank sent it
     * (what a live SMS broadcast reports) and when the phone received it (what an inbox re-scan reads),
     * seconds apart, so a copy can't be recognised by its time alone. The same message text or UPI ref,
     * the same amount and direction, within [WINDOW_MS], is the same payment. Payments added by hand all
     * read "Added by you", so for those only an identical time counts.
     */
    fun sameRecord(a: Transaction, b: Transaction): Boolean {
        if (a.type != b.type || a.amountPaise != b.amountPaise) return false
        if (a.timestamp == b.timestamp) return true
        if (kotlin.math.abs(a.timestamp - b.timestamp) > WINDOW_MS) return false
        if (a.upiRef != null && a.upiRef == b.upiRef) return true
        return a.source != TxnSource.MANUAL && b.source != TxnSource.MANUAL && a.rawText == b.rawText
    }

    /**
     * Copies of the same payment among stored rows, as (kept, removed) pairs. The kept copy is the one
     * carrying the user's note, tags or splits; otherwise one already merged with a UPI app's details;
     * otherwise the one stored last (a restored backup row, with the user's own category).
     */
    fun copies(rows: List<Transaction>, hasSplits: (Transaction) -> Boolean = { false }): List<Pair<Transaction, Transaction>> {
        val rank = compareBy<Transaction>(
            { it.note != null || it.tags != null || hasSplits(it) },
            { " + " in it.sourceApp },
            { it.id },
        )
        // Group each row with the earlier copies it matches (rows are in time order, so a copy is near).
        val sorted = rows.sortedWith(compareBy({ it.timestamp }, { it.id }))
        val groups = mutableListOf<MutableList<Transaction>>()
        for (row in sorted) {
            val group = groups.lastOrNull { g -> g.any { sameRecord(it, row) } }
            if (group != null) group += row else groups += mutableListOf(row)
        }
        return groups.filter { it.size > 1 }.flatMap { group ->
            val kept = group.maxWith(rank)
            group.filter { it !== kept }.map { kept to it }
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
            // The bank's SMS is the fuller record (account, balance); keep it over a UPI app's notification.
            rawText = if (old.source == TxnSource.NOTIFICATION && new.source == TxnSource.SMS) new.rawText else old.rawText,
        )
    }
}
