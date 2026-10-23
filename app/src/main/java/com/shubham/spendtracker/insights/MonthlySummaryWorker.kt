package com.shubham.spendtracker.insights

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shubham.spendtracker.data.Budgets
import com.shubham.spendtracker.data.Notifier
import com.shubham.spendtracker.spendApp
import com.shubham.spendtracker.ui.formatRupeesShort
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Checks twice a day whether a month has ended, and sends that month's summary once. */
class MonthlySummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("spend", Context.MODE_PRIVATE)
        val lastMonth = YearMonth.now().minusMonths(1)
        val sentFor = prefs.getString(KEY_SENT_FOR, null)
        if (sentFor == null) {
            // First run after install: start with the current month, don't summarise history.
            prefs.edit().putString(KEY_SENT_FOR, lastMonth.toString()).apply()
            return Result.success()
        }
        if (sentFor >= lastMonth.toString()) return Result.success()

        val s = MonthlySummary.summarize(
            applicationContext.spendApp.repository.allOnce(), lastMonth, Budgets.current(applicationContext),
        )
        prefs.edit().putString(KEY_SENT_FOR, lastMonth.toString()).apply()
        if (s.payments == 0) return Result.success()

        val name = lastMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val prevName = lastMonth.minusMonths(1).month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val lines = buildList {
            if (s.previousPaise > 0) {
                val diff = s.spentPaise - s.previousPaise
                add("${formatRupeesShort(abs(diff), maskable = false)} ${if (diff > 0) "more" else "less"} than $prevName")
            }
            s.topCategory?.let { add("Most on $it (${formatRupeesShort(s.topCategoryPaise, maskable = false)})") }
            if (s.overBudget.isNotEmpty()) {
                add("Over budget: " + s.overBudget.joinToString { (cat, over) -> "$cat by ${formatRupeesShort(over, maskable = false)}" })
            }
        }
        Notifier.notify(
            applicationContext, Notifier.Channel.SUMMARY, 3000,
            "$name: ${formatRupeesShort(s.spentPaise, maskable = false)} spent in ${s.payments} payments",
            lines.joinToString(" · "),
        )
        return Result.success()
    }

    companion object {
        private const val KEY_SENT_FOR = "summary_sent_for"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "monthly-summary",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MonthlySummaryWorker>(12, TimeUnit.HOURS).build(),
            )
        }
    }
}
