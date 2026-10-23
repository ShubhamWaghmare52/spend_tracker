package com.shubham.spendtracker.insights

import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.parser.Categorizer
import com.shubham.spendtracker.parser.TxnType
import java.time.YearMonth
import java.time.ZoneId

data class MonthSummary(
    val month: YearMonth,
    val spentPaise: Long,
    val previousPaise: Long,
    val payments: Int,
    val topCategory: String?,
    val topCategoryPaise: Long,
    /** Category → amount over budget, biggest first. */
    val overBudget: List<Pair<String, Long>>,
)

object MonthlySummary {
    fun summarize(
        txns: List<Transaction>,
        month: YearMonth,
        budgets: Map<String, Long>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): MonthSummary {
        fun spendIn(m: YearMonth) = txns.filter {
            it.type == TxnType.DEBIT && it.category != Categorizer.SELF &&
                YearMonth.from(java.time.Instant.ofEpochMilli(it.timestamp).atZone(zone)) == m
        }
        val spent = spendIn(month)
        val byCategory = spent.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amountPaise } }
        // "Other" just means uncategorised, so it only headlines when nothing else was spent on.
        val top = byCategory.filterKeys { it != Categorizer.OTHER }.maxByOrNull { it.value } ?: byCategory.maxByOrNull { it.value }
        return MonthSummary(
            month = month,
            spentPaise = spent.sumOf { it.amountPaise },
            previousPaise = spendIn(month.minusMonths(1)).sumOf { it.amountPaise },
            payments = spent.size,
            topCategory = top?.key,
            topCategoryPaise = top?.value ?: 0,
            overBudget = budgets.mapNotNull { (cat, budget) ->
                val over = (byCategory[cat] ?: 0L) - budget
                if (over > 0) cat to over else null
            }.sortedByDescending { it.second },
        )
    }
}
