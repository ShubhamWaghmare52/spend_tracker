package com.shubham.spendtracker.data

import com.shubham.spendtracker.parser.Categorizer
import com.shubham.spendtracker.parser.Deduper
import com.shubham.spendtracker.parser.ParsedTxn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TransactionRepository(private val dao: TransactionDao) {
    // SMS receiver, notification listener and backfill can all fire at once.
    private val ingestLock = Mutex()

    val transactions: Flow<List<Transaction>> = dao.observeAll()

    suspend fun ingest(parsed: ParsedTxn) = ingestLock.withLock {
        val candidates = dao.candidates(
            from = parsed.timestamp - Deduper.WINDOW_MS,
            to = parsed.timestamp + Deduper.WINDOW_MS,
            upiRef = parsed.upiRef,
        )
        val dup = Deduper.findDuplicate(parsed, candidates)
        if (dup != null) {
            val merged = Deduper.merge(dup, parsed)
            if (merged != dup) dao.update(merged)
        } else {
            insertNew(parsed)
        }
        linkSelfTransfer(parsed.upiRef)
    }

    /** Debit in one of the user's accounts + credit in another with the same UPI ref → both are self transfers. */
    private suspend fun linkSelfTransfer(upiRef: String?) {
        if (upiRef == null) return
        Deduper.selfTransferPair(dao.byUpiRef(upiRef))
            .filter { it.category != Categorizer.SELF }
            .forEach { dao.update(it.copy(category = Categorizer.SELF)) }
    }

    private suspend fun insertNew(parsed: ParsedTxn) {
        val remembered = Categorizer.merchantKey(parsed.merchant)?.let { dao.merchantCategory(it) }
        dao.insert(
            Transaction(
                amountPaise = parsed.amountPaise,
                type = parsed.type,
                merchant = parsed.merchant,
                accountLast4 = parsed.accountLast4,
                source = parsed.source,
                sourceApp = parsed.sourceApp,
                timestamp = parsed.timestamp,
                upiRef = parsed.upiRef,
                category = remembered ?: Categorizer.categorize(parsed.type, parsed.merchant, parsed.rawText),
                rawText = parsed.rawText,
            ),
        )
    }

    /** Re-categorizes this payment and every past/future payment to the same merchant. */
    suspend fun setCategory(txn: Transaction, category: String) {
        val key = Categorizer.merchantKey(txn.merchant)
        if (txn.merchant != null && key != null) {
            dao.saveMerchantCategory(MerchantCategory(key, category))
            dao.setCategoryForMerchant(txn.merchant, category)
        } else {
            dao.update(txn.copy(category = category))
        }
    }

    suspend fun delete(txn: Transaction) = dao.delete(txn)

    /** Money spent since [from], not counting transfers between the user's own accounts. */
    suspend fun spentSince(from: Long): Long = dao.spentSince(from, Categorizer.SELF)

    /** Drops SMS-derived rows so an improved parser can re-import them. Category choices are kept. */
    suspend fun clearSmsImports() = ingestLock.withLock { dao.deleteSmsImports() }
}
