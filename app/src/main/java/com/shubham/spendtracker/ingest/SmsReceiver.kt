package com.shubham.spendtracker.ingest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.shubham.spendtracker.data.Notifier
import com.shubham.spendtracker.parser.SmsParser
import com.shubham.spendtracker.spendApp
import kotlinx.coroutines.launch

/** Picks up bank alerts the moment they arrive. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val app = context.spendApp
        val pending = goAsync()

        // Long alerts arrive split into several parts from the same sender.
        val messages = parts.groupBy { it.originatingAddress.orEmpty() }
        app.appScope.launch {
            try {
                for ((sender, pieces) in messages) {
                    val body = pieces.joinToString("") { it.messageBody.orEmpty() }
                    val ts = pieces.first().timestampMillis
                    SmsParser.parse(sender, body, ts)?.let { app.repository.ingest(it) }
                }
                Notifier.afterNewPayment(context)
            } finally {
                pending.finish()
            }
        }
    }
}
