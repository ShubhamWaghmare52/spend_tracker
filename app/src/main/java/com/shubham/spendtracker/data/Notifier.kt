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

/** All app notifications. Each kind has its own channel so it can be muted separately. */
object Notifier {
    enum class Channel(val id: String, val title: String, val importance: Int) {
        LIMIT("daily_limit", "Daily limit alerts", NotificationManager.IMPORTANCE_HIGH),
        BUDGET("budgets", "Budget alerts", NotificationManager.IMPORTANCE_HIGH),
        SUMMARY("monthly_summary", "Monthly summary", NotificationManager.IMPORTANCE_DEFAULT),
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
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    /** Run after any new payment is recorded. */
    suspend fun afterNewPayment(context: Context) {
        DailyLimit.checkAndNotify(context)
        Budgets.checkAndNotify(context)
    }
}
