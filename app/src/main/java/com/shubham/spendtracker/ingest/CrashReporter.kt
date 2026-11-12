package com.shubham.spendtracker.ingest

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash and problem reports sent by email. The app has no internet permission, so nothing leaves the phone
 * unless the user sends the prepared email from their own mail app.
 */
object CrashReporter {
    /** Where reports go. Cloudflare Email Routing forwards this address to the developer's inbox. */
    const val REPORT_EMAIL = "spendtracker@shubhamwaghmare.dev"

    private const val CRASH_FILE = "last_crash.txt"
    private const val LOG_LINES = 100

    /** Saves any uncaught crash so the next launch can offer to report it. Call first in Application.onCreate. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                File(app.filesDir, CRASH_FILE).writeText("Crashed at $time on thread ${thread.name}\n${e.stackTraceToString()}")
                IngestLog.log(app, "CRASH ${e::class.java.simpleName}: ${e.message}")
            }
            previous?.uncaughtException(thread, e)
        }
    }

    /** The crash saved from a previous run, if it hasn't been reported or dismissed yet. */
    fun pendingCrash(context: Context): String? =
        runCatching { File(context.filesDir, CRASH_FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clearCrash(context: Context) {
        runCatching { File(context.filesDir, CRASH_FILE).delete() }
    }

    /** Opens the user's email app with a prepared report. They review and send it themselves. */
    fun send(context: Context, crash: String?) {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        val subject = if (crash != null) "Spend Tracker $version crash report" else "Spend Tracker $version problem report"
        val body = buildReport(context, version, crash)
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(REPORT_EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No email app found. Write to $REPORT_EMAIL", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildReport(context: Context, version: String?, crash: String?): String = buildString {
        appendLine(if (crash != null) "Spend Tracker closed unexpectedly." else "Describe the problem here:")
        appendLine()
        appendLine()
        appendLine("---- Details (no message text; merchant names and refs hidden) ----")
        appendLine("App: $version")
        appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("SMS permission: ${granted(context, Manifest.permission.READ_SMS)} read, ${granted(context, Manifest.permission.RECEIVE_SMS)} receive")
        appendLine("Notification access: ${PaymentNotificationListener.isEnabled(context)}")
        appendLine("App alerts allowed: ${NotificationManagerCompat.from(context).areNotificationsEnabled()}")
        if (crash != null) {
            appendLine()
            appendLine("---- Crash ----")
            appendLine(crash.lineSequence().take(60).joinToString("\n"))
        }
        appendLine()
        appendLine("---- Recent activity ----")
        appendLine(IngestLog.recent(context, LOG_LINES).joinToString("\n") { redact(it) }.ifEmpty { "(empty)" })
    }

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private val merchant = Regex("""merchant=.*?(?= ref=|$)""")
    private val ref = Regex("""ref=\S+""")

    private fun redact(line: String) = line.replace(merchant, "merchant=•••").replace(ref, "ref=•••")
}
