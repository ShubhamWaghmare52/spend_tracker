package com.shubham.spendtracker.ingest

import android.content.Context
import android.provider.Telephony
import com.shubham.spendtracker.parser.SmsParser
import com.shubham.spendtracker.spendApp

/** Imports past bank alerts already sitting in the SMS inbox. Safe to re-run: duplicates are merged. */
object InboxBackfill {
    private const val DAYS = 90L

    /** Start of the window the inbox scan covers. */
    fun windowStart(): Long = System.currentTimeMillis() - DAYS * 24 * 60 * 60 * 1000

    suspend fun run(context: Context): Int {
        val since = windowStart()
        val repo = context.spendApp.repository
        var imported = 0
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val parsed = SmsParser.parse(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getLong(2))
                if (parsed != null) {
                    repo.ingest(parsed)
                    imported++
                }
            }
        }
        return imported
    }
}
