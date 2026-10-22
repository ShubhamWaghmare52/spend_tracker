package com.shubham.spendtracker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import com.shubham.spendtracker.data.Budgets
import com.shubham.spendtracker.data.CsvBackup
import com.shubham.spendtracker.ui.BudgetsScreen
import com.shubham.spendtracker.ui.SortScreen
import java.time.LocalDate
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
                fun askNotificationPermission() {
                    // Limit, budget and summary alerts are notifications, which need permission on Android 13+.
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                val budgets by Budgets.budgets(context).collectAsStateWithLifecycle()
                var page by rememberSaveable { mutableStateOf(Page.MAIN) }
                var menuOpen by remember { mutableStateOf(false) }
                BackHandler(enabled = page != Page.MAIN) { page = Page.MAIN }

                val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
                    if (uri != null) app.appScope.launch { exportBackup(uri) }
                }
                val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) app.appScope.launch { importBackup(uri) }
                }
                val setCategory: (Transaction, String) -> Unit = { t, c -> app.appScope.launch { app.repository.setCategory(t, c) } }
                val delete: (Transaction) -> Unit = { t -> app.appScope.launch { app.repository.delete(t) } }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    when (page) {
                                        Page.SORT -> "Sort payments"
                                        Page.BUDGETS -> "Budgets"
                                        Page.MAIN -> if (tab == 0) "Spend Tracker" else "Payments"
                                    },
                                )
                            },
                            navigationIcon = {
                                if (page != Page.MAIN) {
                                    IconButton(onClick = { page = Page.MAIN }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                }
                            },
                            actions = {
                                if (page == Page.MAIN) {
                                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        DropdownMenuItem(text = { Text("Sort payments") }, onClick = { menuOpen = false; page = Page.SORT })
                                        DropdownMenuItem(text = { Text("Budgets") }, onClick = { menuOpen = false; page = Page.BUDGETS })
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("Export backup (CSV)") },
                                            onClick = { menuOpen = false; exportLauncher.launch("spend-tracker-${LocalDate.now()}.csv") },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Import backup") },
                                            onClick = { menuOpen = false; importLauncher.launch(arrayOf("text/*", "application/octet-stream")) },
                                        )
                                    }
                                }
                            },
                        )
                    },
                    bottomBar = {
                        if (page == Page.MAIN) {
                            NavigationBar {
                                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Home, null) }, label = { Text("Overview") })
                                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text("Payments") })
                            }
                        }
                    },
                ) { padding ->
                    // Pages slide in from the right over the tabs; tabs slide in the direction you move.
                    AnimatedContent(
                        targetState = page to tab,
                        transitionSpec = {
                            val dir = when {
                                targetState.first != initialState.first -> if (targetState.first != Page.MAIN) 1 else -1
                                else -> if (targetState.second > initialState.second) 1 else -1
                            }
                            (fadeIn(tween(220, delayMillis = 60)) + slideInHorizontally(tween(280)) { dir * it / 10 }) togetherWith
                                fadeOut(tween(120))
                        },
                        label = "page",
                    ) { (shownPage, shownTab) ->
                        val m = Modifier.padding(padding)
                        when {
                            shownPage == Page.SORT -> SortScreen(txns, setCategory, m)
                            shownPage == Page.BUDGETS -> BudgetsScreen(
                                txns, budgets.orEmpty(),
                                onSetBudget = { cat, paise ->
                                    Budgets.set(context, cat, paise)
                                    if (paise > 0) askNotificationPermission()
                                },
                                modifier = m,
                            )
                            shownTab == 0 -> HomeScreen(
                                txns = txns,
                                playIntro = !introPlayed,
                                onIntroStarted = { introPlayed = true },
                                budgets = budgets.orEmpty(),
                                onOpenSort = { page = Page.SORT },
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
                                    if (paise > 0) askNotificationPermission()
                                },
                                onSetCategory = setCategory,
                                onDelete = delete,
                                modifier = m,
                            )
                            else -> TransactionsScreen(txns, setCategory, delete, m)
                        }
                    }
                }
            }
        }
    }

    private enum class Page { MAIN, SORT, BUDGETS }

    private suspend fun exportBackup(uri: Uri) {
        val rows = spendApp.repository.allOnce()
        val ok = runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(CsvBackup.write(rows).toByteArray()) } ?: error("no stream")
        }.isSuccess
        toast(if (ok) "Exported ${rows.size} payments" else "Couldn't save the backup")
    }

    private suspend fun importBackup(uri: Uri) {
        val message = try {
            val text = contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("no stream")
            val rows = CsvBackup.read(text)
            val added = spendApp.repository.importBackup(rows)
            "Restored $added payments" + if (rows.size > added) " (${rows.size - added} were already here)" else ""
        } catch (e: IllegalArgumentException) {
            "That file isn't a Spend Tracker backup"
        } catch (e: Exception) {
            "Couldn't read the backup"
        }
        toast(message)
    }

    private suspend fun toast(text: String) = withContext(Dispatchers.Main) {
        Toast.makeText(this@MainActivity, text, Toast.LENGTH_LONG).show()
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
                if (done > 0) app.repository.clearSmsImports(InboxBackfill.windowStart())
                val found = InboxBackfill.run(context)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Imported $found payments from SMS", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
