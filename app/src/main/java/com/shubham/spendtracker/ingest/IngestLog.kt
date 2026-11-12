package com.shubham.spendtracker.ingest

import android.content.Context
import android.util.Log
import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.parser.ParsedTxn
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small on-device record of what the SMS / notification pipeline did, so it can be checked days later
 * (logcat only survives until the next reboot). Never stores message bodies, only sender, amount and outcome.
 *
 * Read it from a computer with:
 *   adb shell dumpsys activity service com.shubham.spendtracker/.ingest.PaymentNotificationListener
 */
object IngestLog {
    private const val TAG = "SpendIngest"
    private const val FILE = "ingest.log"
    private const val KEEP_LINES = 500

    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun log(context: Context, message: String) {
        Log.i(TAG, message)
        synchronized(this) {
            runCatching {
                val file = file(context)
                file.appendText("${stamp.format(Date())}  $message\n")
                // Trim occasionally rather than on every write.
                if (file.length() > KEEP_LINES * 200L) {
                    val lines = file.readLines()
                    if (lines.size > KEEP_LINES) file.writeText(lines.takeLast(KEEP_LINES).joinToString("\n", postfix = "\n"))
                }
            }
        }
    }

    fun error(context: Context, where: String, e: Throwable) {
        Log.e(TAG, where, e)
        log(context, "ERROR $where: ${e::class.java.simpleName}: ${e.message}")
    }

    /** One line describing what happened to a parsed payment. */
    fun describe(parsed: ParsedTxn, saved: Transaction?): String {
        val rupees = "₹%d.%02d".format(parsed.amountPaise / 100, parsed.amountPaise % 100)
        val outcome = if (saved != null) "saved #${saved.id}" else "duplicate (merged)"
        return "$outcome $rupees ${parsed.type} merchant=${parsed.merchant ?: "?"} ref=${parsed.upiRef ?: "-"}"
    }

    fun recent(context: Context, lines: Int): List<String> =
        synchronized(this) { runCatching { file(context).readLines().takeLast(lines) }.getOrDefault(emptyList()) }

    fun dump(context: Context, out: PrintWriter) {
        out.println("SpendTracker ingest log (newest last)")
        out.println("listener enabled: ${PaymentNotificationListener.isEnabled(context)}")
        val text = synchronized(this) { runCatching { file(context).readText() }.getOrNull() }
        out.print(if (text.isNullOrEmpty()) "(empty)\n" else text)
    }

    private fun file(context: Context) = File(context.filesDir, FILE)
}
