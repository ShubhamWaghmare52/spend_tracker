package com.shubham.spendtracker.data

import android.content.Context
import com.shubham.spendtracker.spendApp
import com.shubham.spendtracker.ui.formatRupeesShort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.ZoneId

/** The user's daily spending limit, and the 80% / 100% warnings. */
object DailyLimit {
    private const val PREFS = "spend"
    private const val KEY_LIMIT = "daily_limit_paise"
    private const val KEY_ALERT_DAY = "limit_alert_day"
    private const val KEY_ALERT_LEVEL = "limit_alert_level"

    enum class Level { OK, NEAR, OVER }

    fun level(spentPaise: Long, limitPaise: Long): Level = when {
        limitPaise <= 0 -> Level.OK
        spentPaise >= limitPaise -> Level.OVER
        spentPaise * 100 >= limitPaise * 80 -> Level.NEAR
        else -> Level.OK
    }

    private val _limit = MutableStateFlow(-1L)

    /** Limit in paise; 0 means no limit set. */
    fun limit(context: Context): StateFlow<Long> {
        if (_limit.value < 0) _limit.value = prefs(context).getLong(KEY_LIMIT, 0L)
        return _limit
    }

    fun setLimit(context: Context, paise: Long) {
        prefs(context).edit().putLong(KEY_LIMIT, paise).remove(KEY_ALERT_LEVEL).apply()
        _limit.value = paise
    }

    fun startOfToday(): Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Called after a new payment is recorded; notifies once per level per day. */
    suspend fun checkAndNotify(context: Context) {
        val limit = prefs(context).getLong(KEY_LIMIT, 0L)
        if (limit <= 0) return
        val spent = context.spendApp.repository.spentSince(startOfToday())
        val level = level(spent, limit)
        if (level == Level.OK) return

        val p = prefs(context)
        val today = LocalDate.now().toString()
        val alreadySent = if (p.getString(KEY_ALERT_DAY, null) == today) p.getInt(KEY_ALERT_LEVEL, 0) else 0
        if (level.ordinal <= alreadySent) return
        p.edit().putString(KEY_ALERT_DAY, today).putInt(KEY_ALERT_LEVEL, level.ordinal).apply()

        val (title, text) = if (level == Level.OVER) {
            "Daily limit crossed" to "You've spent ${formatRupeesShort(spent, maskable = false)} today, ${formatRupeesShort(spent - limit, maskable = false)} over your ${formatRupeesShort(limit, maskable = false)} limit."
        } else {
            "Close to your daily limit" to "${formatRupeesShort(spent, maskable = false)} of ${formatRupeesShort(limit, maskable = false)} spent today. ${formatRupeesShort(limit - spent, maskable = false)} left."
        }
        Notifier.notify(context, Notifier.Channel.LIMIT, 1001, title, text)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
