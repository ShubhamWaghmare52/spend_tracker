package com.shubham.spendtracker.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shubham.spendtracker.MainActivity
import com.shubham.spendtracker.R
import com.shubham.spendtracker.spendApp
import com.shubham.spendtracker.ui.formatRupees
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
    private const val CHANNEL = "daily_limit"

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
            "Daily limit crossed" to "You've spent ${formatRupees(spent)} today, ${formatRupees(spent - limit)} over your ${formatRupees(limit)} limit."
        } else {
            "Close to your daily limit" to "${formatRupees(spent)} of ${formatRupees(limit)} spent today. ${formatRupees(limit - spent)} left."
        }
        notify(context, title, text)
    }

    private fun notify(context: Context, title: String, text: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Daily limit alerts", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_limit)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(1001, n)
        } catch (_: SecurityException) {
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
