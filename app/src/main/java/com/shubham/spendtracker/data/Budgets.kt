package com.shubham.spendtracker.data

import android.content.Context
import com.shubham.spendtracker.spendApp
import com.shubham.spendtracker.ui.formatRupeesShort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Monthly spending budgets per category, with 80% / over-budget alerts. */
object Budgets {
    private const val PREFS = "budgets"
    private const val ALERT_PREFIX = "alert:"

    private val _budgets = MutableStateFlow<Map<String, Long>?>(null)

    /** Category → monthly budget in paise. Categories without a budget are absent. */
    fun budgets(context: Context): StateFlow<Map<String, Long>?> {
        if (_budgets.value == null) _budgets.value = load(context)
        return _budgets
    }

    fun current(context: Context): Map<String, Long> = budgets(context).value.orEmpty()

    fun set(context: Context, category: String, paise: Long) {
        prefs(context).edit().apply {
            if (paise > 0) putLong(category, paise) else remove(category)
            remove(ALERT_PREFIX + category)
        }.apply()
        _budgets.value = load(context)
    }

    fun startOfMonth(): Long = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Notifies once per category per level per month. */
    suspend fun checkAndNotify(context: Context) {
        val month = YearMonth.now().toString()
        val p = prefs(context)
        for ((category, budget) in current(context)) {
            val spent = context.spendApp.repository.spentInCategorySince(category, startOfMonth())
            val level = DailyLimit.level(spent, budget)
            if (level == DailyLimit.Level.OK) continue
            val key = ALERT_PREFIX + category
            val sent = p.getString(key, null)?.takeIf { it.startsWith("$month:") }?.substringAfter(':')?.toIntOrNull() ?: 0
            if (level.ordinal <= sent) continue
            p.edit().putString(key, "$month:${level.ordinal}").apply()

            val (title, text) = if (level == DailyLimit.Level.OVER) {
                "$category budget crossed" to
                    "${formatRupeesShort(spent)} spent this month, ${formatRupeesShort(spent - budget)} over your ${formatRupeesShort(budget)} budget."
            } else {
                "$category budget at ${spent * 100 / budget}%" to
                    "${formatRupeesShort(spent)} of ${formatRupeesShort(budget)} spent this month. ${formatRupeesShort(budget - spent)} left."
            }
            Notifier.notify(context, Notifier.Channel.BUDGET, 2000 + (category.hashCode() and 0xFFF), title, text)
        }
    }

    private fun load(context: Context): Map<String, Long> =
        prefs(context).all.filterKeys { !it.startsWith(ALERT_PREFIX) }
            .mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
