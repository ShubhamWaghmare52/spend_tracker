package com.shubham.spendtracker.insights

import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.parser.Categorizer
import com.shubham.spendtracker.parser.TxnType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A payment that repeats roughly every month: EMIs, recharges, subscriptions, rent. */
data class RecurringPayment(
    val merchant: String,
    val category: String,
    val typicalPaise: Long,
    val intervalDays: Int,
    val lastPaid: LocalDate,
    val nextDue: LocalDate,
    val count: Int,
) {
    fun daysUntilDue(today: LocalDate) = ChronoUnit.DAYS.between(today, nextDue).toInt()
}

object RecurringDetector {
    private val MONTHLY = 25..35

    // Amounts may vary a little (e.g. a bill), but not wildly.
    private const val MAX_SPREAD = 1.25

    // Past this many days overdue we assume it stopped (cancelled subscription, paid-off loan).
    private const val STALE_AFTER_DAYS = 10

    fun detect(txns: List<Transaction>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<RecurringPayment> {
        val debits = txns.filter { it.type == TxnType.DEBIT && it.category != Categorizer.SELF && it.merchant != null }
        val found = debits
            .groupBy { Categorizer.merchantKey(it.merchant) ?: it.merchant!! }
            .values
            .mapNotNull { group -> analyse(group, zone) }
            .map { catchUp(it, debits, zone) }
            .filter { ChronoUnit.DAYS.between(it.nextDue, today) <= STALE_AFTER_DAYS }
            .sortedByDescending { it.lastPaid }
        // The same bill can appear under two series; keep the most recent one.
        val kept = mutableListOf<RecurringPayment>()
        for (r in found) if (kept.none { sameBill(it, r) }) kept += r
        return kept.sortedBy { it.nextDue }
    }

    // Banks name the same payee differently over time ("AIRTEL PAYMENTS BANK LIMI", then "Airtel").
    private fun alias(merchant: String) = merchant.lowercase().split(Regex("[^a-z]+")).firstOrNull { it.length >= 3 }

    private fun similar(a: Long, b: Long) = maxOf(a, b) <= minOf(a, b) * MAX_SPREAD

    private fun sameBill(a: RecurringPayment, b: RecurringPayment) =
        alias(a.merchant) != null && alias(a.merchant) == alias(b.merchant) && similar(a.typicalPaise, b.typicalPaise)

    /** Moves the series forward if a later payment of a similar amount went out under another name. */
    private fun catchUp(r: RecurringPayment, debits: List<Transaction>, zone: ZoneId): RecurringPayment {
        val a = alias(r.merchant) ?: return r
        val later = debits
            .filter { alias(it.merchant!!) == a && similar(it.amountPaise, r.typicalPaise) }
            .map { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .filter { it.isAfter(r.lastPaid.plusDays(MONTHLY.first.toLong() - 5)) }
            .maxOrNull() ?: return r
        return r.copy(lastPaid = later, nextDue = later.plusMonths(1), count = r.count + 1)
    }

    private fun analyse(group: List<Transaction>, zone: ZoneId): RecurringPayment? {
        // One payment per day: a split or retried payment shouldn't look like a pattern.
        val byDay = group.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .mapValues { (_, v) -> v.sumOf { it.amountPaise } }
            .toSortedMap()
        if (byDay.size < 2) return null

        val days = byDay.keys.toList()
        val intervals = days.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }
        if (intervals.any { it !in MONTHLY }) return null

        val amounts = byDay.values.sorted()
        if (amounts.last() > amounts.first() * MAX_SPREAD) return null

        val interval = intervals.sorted()[intervals.size / 2]
        // Monthly bills land on the same date each month, whatever the month's length.
        val next = days.last().plusMonths(1)

        val latest = group.maxBy { it.timestamp }
        return RecurringPayment(
            merchant = latest.merchant!!,
            category = latest.category,
            typicalPaise = amounts[amounts.size / 2],
            intervalDays = interval,
            lastPaid = days.last(),
            nextDue = next,
            count = byDay.size,
        )
    }
}
