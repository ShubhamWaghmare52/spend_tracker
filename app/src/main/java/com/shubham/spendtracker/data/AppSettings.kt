package com.shubham.spendtracker.data

import android.content.Context
import com.shubham.spendtracker.ui.AmountMask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** User preferences that aren't tied to one screen. */
object AppSettings {
    data class Values(
        /** Ask for fingerprint / face / screen lock when opening the app. */
        val appLock: Boolean = false,
        /** Privacy mode: show amounts as ₹•••• in the app and widget. */
        val hideAmounts: Boolean = false,
        /** Notify about payments that look out of character. */
        val unusualAlerts: Boolean = true,
    )

    private const val PREFS = "settings"
    private val _values = MutableStateFlow<Values?>(null)

    fun values(context: Context): StateFlow<Values?> {
        if (_values.value == null) publish(load(context))
        return _values
    }

    fun current(context: Context): Values = values(context).value ?: Values()

    fun update(context: Context, change: (Values) -> Values) {
        val next = change(current(context))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("app_lock", next.appLock)
            .putBoolean("hide_amounts", next.hideAmounts)
            .putBoolean("unusual_alerts", next.unusualAlerts)
            .apply()
        publish(next)
    }

    private fun publish(v: Values) {
        _values.value = v
        AmountMask.hidden = v.hideAmounts
    }

    private fun load(context: Context): Values {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Values(
            appLock = p.getBoolean("app_lock", false),
            hideAmounts = p.getBoolean("hide_amounts", false),
            unusualAlerts = p.getBoolean("unusual_alerts", true),
        )
    }
}
