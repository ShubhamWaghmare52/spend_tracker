package com.shubham.spendtracker.data

import android.content.Context
import com.shubham.spendtracker.insights.AccountBalance
import com.shubham.spendtracker.insights.Balances
import com.shubham.spendtracker.spendApp
import com.shubham.spendtracker.ui.formatRupees
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Warns when a bank account's balance drops below the user's threshold. */
object LowBalance {
    private const val PREFS = "spend"
    private const val KEY_THRESHOLD = "low_balance_paise"
    private const val KEY_ALERTED = "low_balance_alerted"

    /** ₹1,000 until the user picks another amount; 0 turns the alert off. */
    const val DEFAULT_PAISE = 100_000L

    // Only balances this fresh can alert, so an old figure isn't announced as news.
    private const val FRESH_MS = 2 * 24 * 60 * 60 * 1000L

    private val _threshold = MutableStateFlow(-1L)

    fun threshold(context: Context): StateFlow<Long> {
        if (_threshold.value < 0) _threshold.value = prefs(context).getLong(KEY_THRESHOLD, DEFAULT_PAISE)
        return _threshold
    }

    fun setThreshold(context: Context, paise: Long) {
        prefs(context).edit().putLong(KEY_THRESHOLD, paise).remove(KEY_ALERTED).apply()
        _threshold.value = paise
    }

    /**
     * Accounts that should alert now: below [threshold], fresh, and not already announced. An account
     * that recovers above the threshold is dropped from [alerted], so the next dip alerts again.
     * Returns the accounts to announce and the new alerted set.
     */
    fun due(balances: List<AccountBalance>, threshold: Long, alerted: Set<String>, now: Long): Pair<List<AccountBalance>, Set<String>> {
        if (threshold <= 0) return emptyList<AccountBalance>() to emptySet()
        val low = balances.filter { it.balancePaise < threshold }
        val announce = low.filter { it.key !in alerted && now - it.asOf <= FRESH_MS }
        val stillLow = low.map { it.key }.toSet()
        return announce to (alerted.intersect(stillLow) + announce.map { it.key })
    }

    /** Called after a new payment is recorded. */
    suspend fun checkAndNotify(context: Context) {
        val threshold = threshold(context).value
        if (threshold <= 0) return
        val p = prefs(context)
        val balances = Balances.latest(context.spendApp.repository.allOnce())
        val (announce, alerted) = due(balances, threshold, p.getStringSet(KEY_ALERTED, emptySet()).orEmpty(), System.currentTimeMillis())
        p.edit().putStringSet(KEY_ALERTED, alerted).apply()
        announce.forEach { b ->
            val amount = formatRupees(b.balancePaise, maskable = false)
            val account = b.bank + (b.last4?.let { " ••$it" } ?: "")
            Notifier.notify(
                context, Notifier.Channel.LOW_BALANCE, 5000 + (b.key.hashCode() and 0xfff),
                "Low balance: $account",
                "${if (b.exact) "" else "About "}$amount left, below your ${formatRupees(threshold, maskable = false)} alert.",
            )
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
