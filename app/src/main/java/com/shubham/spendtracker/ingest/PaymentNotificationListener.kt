package com.shubham.spendtracker.ingest

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.shubham.spendtracker.data.Notifier
import com.shubham.spendtracker.parser.NotificationParser
import com.shubham.spendtracker.spendApp
import kotlinx.coroutines.launch

/** Reads "Paid ₹X to Y" notifications from UPI apps (needs Notification access). */
class PaymentNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in NotificationParser.supportedApps) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()

        val parsed = NotificationParser.parse(sbn.packageName, title, text, sbn.postTime) ?: return
        val app = spendApp
        app.appScope.launch {
            val added = listOfNotNull(app.repository.ingest(parsed))
            Notifier.afterNewPayment(app, added)
        }
    }

    companion object {
        fun isEnabled(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
    }
}
