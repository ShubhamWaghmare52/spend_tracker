package com.shubham.spendtracker.insights

import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.parser.Categorizer
import com.shubham.spendtracker.parser.TxnType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** The facts behind a month's "Wrapped" story. */
data class WrapStats(
    val month: YearMonth,
    val spentPaise: Long,
    val payments: Int,
    val topCategory: String,
    val topCategoryPaise: Long,
    val topMerchant: String?,
    val topMerchantPaise: Long,
    val mostVisited: String?,
    val visits: Int,
    val biggest: Transaction,
    val priciestWeekday: DayOfWeek,
    val priciestWeekdayPaise: Long,
    val savingsRate: Int?,
)

object Wrapped {
    /** Which month to recap: last month during the first week, otherwise this month so far. */
    fun monthFor(today: LocalDate): YearMonth =
        if (today.dayOfMonth <= 7) YearMonth.from(today).minusMonths(1) else YearMonth.from(today)

    fun compute(txns: List<Transaction>, month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): WrapStats? {
        fun dateOf(t: Transaction) = Instant.ofEpochMilli(t.timestamp).atZone(zone).toLocalDate()
        val inMonth = txns.filter { it.category != Categorizer.SELF && YearMonth.from(dateOf(it)) == month }
        val spend = inMonth.filter { it.type == TxnType.DEBIT }
        if (spend.isEmpty()) return null

        val total = spend.sumOf { it.amountPaise }
        val topCat = spend.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amountPaise } }.maxBy { it.value }
        val named = spend.filter { it.merchant != null }
        val topMerchant = named.groupBy { it.merchant!! }.mapValues { (_, v) -> v.sumOf { it.amountPaise } }.maxByOrNull { it.value }
        val visited = named.groupBy { it.merchant!! }.maxByOrNull { it.value.size }
        val weekday = spend.groupBy { dateOf(it).dayOfWeek }.mapValues { (_, v) -> v.sumOf { it.amountPaise } }.maxBy { it.value }
        val income = inMonth.filter { it.type == TxnType.CREDIT }.sumOf { it.amountPaise }

        return WrapStats(
            month = month,
            spentPaise = total,
            payments = spend.size,
            topCategory = topCat.key,
            topCategoryPaise = topCat.value,
            topMerchant = topMerchant?.key,
            topMerchantPaise = topMerchant?.value ?: 0,
            mostVisited = visited?.takeIf { it.value.size >= 2 }?.key,
            visits = visited?.value?.size ?: 0,
            biggest = spend.maxBy { it.amountPaise },
            priciestWeekday = weekday.key,
            priciestWeekdayPaise = weekday.value,
            savingsRate = if (income > 0) ((income - total) * 100 / income).toInt() else null,
        )
    }
}
