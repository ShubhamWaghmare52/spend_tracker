package com.shubham.spendtracker.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shubham.spendtracker.MainActivity
import com.shubham.spendtracker.R
import com.shubham.spendtracker.insights.Unusual
import com.shubham.spendtracker.insights.UnusualPayments
import com.shubham.spendtracker.spendApp
import com.shubham.spendtracker.ui.formatRupeesShort
import com.shubham.spendtracker.ui.formatTime

/** All app notifications. Each kind has its own channel so it can be muted separately. */
object Notifier {
    enum class Channel(val id: String, val title: String, val importance: Int) {
        LIMIT("daily_limit", "Daily limit alerts", NotificationManager.IMPORTANCE_HIGH),
        BUDGET("budgets", "Budget alerts", NotificationManager.IMPORTANCE_HIGH),
        SUMMARY("monthly_summary", "Monthly summary", NotificationManager.IMPORTANCE_DEFAULT),
        UNUSUAL("unusual_payments", "Unusual payments", NotificationManager.IMPORTANCE_HIGH),
    }

    fun notify(context: Context, channel: Channel, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(channel.id, channel.title, channel.importance))
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_stat_limit)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            // With app lock on, keep spending details off the lock screen entirely: the alert still
            // sounds, and shows in the shade once the phone is unlocked. ("Private" isn't enough:
            // phones set to show sensitive content would still display it.)
            .setVisibility(
                if (AppSettings.current(context).appLock) NotificationCompat.VISIBILITY_SECRET
                else NotificationCompat.VISIBILITY_PRIVATE,
            )
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    /** Run after live payments are recorded (not after the inbox backfill). */
    suspend fun afterNewPayment(context: Context, added: List<Transaction>) {
        DailyLimit.checkAndNotify(context)
        Budgets.checkAndNotify(context)
        if (added.isNotEmpty() && AppSettings.current(context).unusualAlerts) {
            val history = context.spendApp.repository.allOnce()
            added.forEach { t -> UnusualPayments.check(t, history)?.let { notifyUnusual(context, t, it) } }
        }
    }

    private fun notifyUnusual(context: Context, t: Transaction, why: Unusual) {
        val amount = formatRupeesShort(t.amountPaise, maskable = false)
        val who = t.merchant ?: "an unknown payee"
        val reason = when (why) {
            is Unusual.BigForMerchant ->
                "That's about ${why.times}× your usual ${formatRupeesShort(why.typicalPaise, maskable = false)} there."
            Unusual.LargeToNewPayee -> "It's your first payment to them."
            is Unusual.LateNight -> "It was made at ${formatTime(t.timestamp)}."
        }
        notify(
            context, Channel.UNUSUAL, 4000 + (t.id % 1000).toInt(),
            "Unusual payment: $amount to $who",
            "$reason If this wasn't you, call your bank right away.",
        )
    }
}
