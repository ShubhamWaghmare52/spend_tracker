package com.shubham.spendtracker.insights

import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.parser.Categorizer
import com.shubham.spendtracker.parser.TxnType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Money in and out for one calendar month, ignoring transfers between the user's own accounts. */
data class MonthFlow(val month: YearMonth, val incomePaise: Long, val spentPaise: Long) {
    val savedPaise get() = incomePaise - spentPaise

    /** Share of income kept, in percent; null when there was no income. */
    val savingsRate: Int? get() = if (incomePaise > 0) (savedPaise * 100 / incomePaise).toInt() else null
}

object Savings {
    /** The last [count] months, oldest first, ending with the month containing [today]. */
    fun lastMonths(txns: List<Transaction>, today: YearMonth, count: Int = 6, zone: ZoneId = ZoneId.systemDefault()): List<MonthFlow> {
        val months = (count - 1 downTo 0).map { today.minusMonths(it.toLong()) }
        val byMonth = txns.filter { it.category != Categorizer.SELF }
            .groupBy { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)) }
        return months.map { m ->
            val list = byMonth[m].orEmpty()
            MonthFlow(
                month = m,
                incomePaise = list.filter { it.type == TxnType.CREDIT }.sumOf { it.amountPaise },
                spentPaise = list.filter { it.type == TxnType.DEBIT }.sumOf { it.amountPaise },
            )
        }
    }
}
