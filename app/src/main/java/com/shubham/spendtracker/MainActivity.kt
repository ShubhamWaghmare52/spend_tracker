package com.shubham.spendtracker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.animation.PathInterpolator
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.SideEffect
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shubham.spendtracker.data.DailyLimit
import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.ingest.InboxBackfill
import com.shubham.spendtracker.ingest.PaymentNotificationListener
import com.shubham.spendtracker.ui.HomeScreen
import com.shubham.spendtracker.ui.SpendTheme
import com.shubham.spendtracker.ui.TransactionsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    @Volatile private var dataReady = false

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = spendApp

        // Hold the splash until data is loaded (so the Overview animates in with real numbers)
        // and the ring has finished drawing.
        val shownAt = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition { !dataReady || SystemClock.uptimeMillis() - shownAt < SPLASH_MIN_MS }
        splash.setOnExitAnimationListener { provider ->
            val ease = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            // Warm/hot launches show an icon-less splash and iconView throws; just fade then.
            runCatching { provider.iconView }.getOrNull()
                ?.animate()?.scaleX(1.35f)?.scaleY(1.35f)?.setDuration(SPLASH_EXIT_MS)?.setInterpolator(ease)?.start()
            provider.view.animate().alpha(0f).setDuration(SPLASH_EXIT_MS).setInterpolator(ease)
                .withEndAction { provider.remove() }.start()
        }
        if (hasSms(this)) backfillOnce(this)

        setContent {
            SpendTheme {
                val context = LocalContext.current
                val loaded by app.repository.transactions.collectAsStateWithLifecycle<List<Transaction>?>(null)
                val txns = loaded ?: emptyList()
                if (loaded != null) SideEffect { dataReady = true }
                var tab by rememberSaveable { mutableIntStateOf(0) }
                // The Overview's build-in animation plays once per launch, not on every tab switch.
                var introPlayed by rememberSaveable { mutableStateOf(false) }
                var smsGranted by remember { mutableStateOf(hasSms(context)) }
                var notifEnabled by remember { mutableStateOf(PaymentNotificationListener.isEnabled(context)) }

                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    smsGranted = hasSms(context)
                    notifEnabled = PaymentNotificationListener.isEnabled(context)
                }

                val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    smsGranted = hasSms(context)
                    if (smsGranted) backfillOnce(context)
                }

                val dailyLimit by DailyLimit.limit(context).collectAsStateWithLifecycle()
                val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

                Scaffold(
                    topBar = { TopAppBar(title = { Text(if (tab == 0) "Spend Tracker" else "Payments") }) },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Home, null) }, label = { Text("Overview") })
                            NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text("Payments") })
                        }
                    },
                ) { padding ->
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (fadeIn(tween(220, delayMillis = 60)) + slideInHorizontally(tween(280)) { dir * it / 10 }) togetherWith
                                fadeOut(tween(120))
                        },
                        label = "tab",
                    ) { shown ->
                    if (shown == 0) {
                        HomeScreen(
                            txns = txns,
                            playIntro = !introPlayed,
                            onIntroStarted = { introPlayed = true },
                            smsGranted = smsGranted,
                            notifEnabled = notifEnabled,
                            onGrantSms = {
                                smsLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                            },
                            onEnableNotif = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                            dailyLimit = dailyLimit,
                            onSetLimit = { paise ->
                                DailyLimit.setLimit(context, paise)
                                // Limit alerts are notifications, which need permission on Android 13+.
                                if (paise > 0 && Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onSetCategory = { t, c -> app.appScope.launch { app.repository.setCategory(t, c) } },
                            onDelete = { t -> app.appScope.launch { app.repository.delete(t) } },
                            modifier = Modifier.padding(padding),
                        )
                    } else {
                        TransactionsScreen(
                            txns = txns,
                            onSetCategory = { t, c -> app.appScope.launch { app.repository.setCategory(t, c) } },
                            onDelete = { t -> app.appScope.launch { app.repository.delete(t) } },
                            modifier = Modifier.padding(padding),
                        )
                    }
                    }
                }
            }
        }
    }

    companion object {
        private const val PREFS = "spend"
        private const val KEY_PARSER_VERSION = "parser_version"
        private const val SPLASH_MIN_MS = 750L
        private const val SPLASH_EXIT_MS = 320L

        // Bump when the SMS parser improves so existing imports are re-parsed once.
        private const val PARSER_VERSION = 6

        fun hasSms(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

        /** Imports the last 90 days of bank SMS on first grant, and again after a parser upgrade. */
        fun backfillOnce(context: Context) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val done = prefs.getInt(KEY_PARSER_VERSION, if (prefs.getBoolean("backfilled", false)) 1 else 0)
            if (done >= PARSER_VERSION) return
            prefs.edit().putInt(KEY_PARSER_VERSION, PARSER_VERSION).apply()
            val app = context.spendApp
            app.appScope.launch {
                if (done > 0) app.repository.clearSmsImports()
                val found = InboxBackfill.run(context)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Imported $found payments from SMS", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
