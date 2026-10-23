package com.shubham.spendtracker.data

import com.shubham.spendtracker.parser.Categorizer
import com.shubham.spendtracker.parser.Deduper
import com.shubham.spendtracker.parser.ParsedTxn
import com.shubham.spendtracker.parser.TxnType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TransactionRepository(private val dao: TransactionDao) {
    // SMS receiver, notification listener and backfill can all fire at once.
    private val ingestLock = Mutex()

    val transactions: Flow<List<Transaction>> = dao.observeAll()
    val splits: Flow<List<Split>> = dao.observeSplits()

    /** Stores a parsed payment. Returns the new row, or null when it was merged into an existing one. */
    suspend fun ingest(parsed: ParsedTxn): Transaction? = ingestLock.withLock {
        val candidates = dao.candidates(
            from = parsed.timestamp - Deduper.WINDOW_MS,
            to = parsed.timestamp + Deduper.WINDOW_MS,
            upiRef = parsed.upiRef,
        )
        val dup = Deduper.findDuplicate(parsed, candidates)
        val inserted = if (dup != null) {
            val merged = Deduper.merge(dup, parsed)
            if (merged != dup) dao.update(merged)
            null
        } else {
            insertNew(parsed)
        }
        linkSelfTransfer(parsed.upiRef)
        if (inserted?.type == TxnType.CREDIT) settleFromCredit(inserted)
        inserted
    }

    /** A friend paying back by UPI settles their outstanding share(s) automatically. */
    private suspend fun settleFromCredit(credit: Transaction) {
        SplitMatcher.settledBy(credit, dao.outstandingSplits())
            .forEach { dao.updateSplit(it.copy(settledAt = credit.timestamp)) }
    }

    suspend fun saveNote(txn: Transaction, note: String?, tags: String?) =
        dao.updateNote(txn.id, note?.trim()?.ifEmpty { null }, Tags.join(Tags.parse(tags)))

    /** Splits [txn] equally between the user and [people]; any rounding remainder stays with the user. */
    suspend fun split(txn: Transaction, people: List<String>) {
        val names = people.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        dao.deleteSplitsFor(txn.id)
        if (names.isEmpty()) return
        val share = txn.amountPaise / (names.size + 1)
        dao.insertSplits(names.map { Split(txnId = txn.id, person = it, sharePaise = share) })
    }

    suspend fun removeSplits(txn: Transaction) = dao.deleteSplitsFor(txn.id)

    suspend fun setSettled(split: Split, settled: Boolean) =
        dao.updateSplit(split.copy(settledAt = if (settled) System.currentTimeMillis() else null))

    /** Debit in one of the user's accounts + credit in another with the same UPI ref → both are self transfers. */
    private suspend fun linkSelfTransfer(upiRef: String?) {
        if (upiRef == null) return
        Deduper.selfTransferPair(dao.byUpiRef(upiRef))
            .filter { it.category != Categorizer.SELF }
            .forEach { dao.update(it.copy(category = Categorizer.SELF)) }
    }

    private suspend fun insertNew(parsed: ParsedTxn): Transaction {
        val remembered = Categorizer.merchantKey(parsed.merchant)?.let { dao.merchantCategory(it) }
        val row =
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
            )
        return row.copy(id = dao.insert(row))
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

    suspend fun delete(txn: Transaction) {
        dao.deleteSplitsFor(txn.id)
        dao.delete(txn)
    }

    /** Money spent since [from], not counting transfers between the user's own accounts. */
    suspend fun spentSince(from: Long): Long = dao.spentSince(from, Categorizer.SELF)

    /**
     * Drops SMS-derived rows inside the re-import window so an improved parser can re-read them.
     * Older rows (e.g. restored from a backup) are kept, since the inbox scan can't bring them back.
     */
    suspend fun clearSmsImports(since: Long) = ingestLock.withLock { dao.deleteSmsImportsSince(since) }

    suspend fun allOnce(): List<Transaction> = dao.all()

    suspend fun spentInCategorySince(category: String, from: Long): Long = dao.spentInCategorySince(category, from)

    /**
     * Restores rows from a backup, skipping ones already present (same time, amount and direction).
     * Categories the user picked are remembered for their merchants. Returns how many were added.
     */
    suspend fun importBackup(rows: List<Transaction>): Int = ingestLock.withLock {
        var added = 0
        for (row in rows) {
            if (dao.countSame(row.timestamp, row.amountPaise, row.type.name) > 0) continue
            dao.insert(row.copy(id = 0))
            added++
            val key = Categorizer.merchantKey(row.merchant) ?: continue
            if (row.category != Categorizer.categorize(row.type, row.merchant, row.rawText)) {
                dao.saveMerchantCategory(MerchantCategory(key, row.category))
            }
        }
        added
    }
}
