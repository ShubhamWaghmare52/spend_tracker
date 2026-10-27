package com.shubham.spendtracker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Missing-payment suggestions the user waved away, so they don't come back. */
object DismissedGaps {
    private const val PREFS = "spend"
    private const val KEY = "dismissed_gaps"

    private val _keys = MutableStateFlow<Set<String>?>(null)

    fun keys(context: Context): StateFlow<Set<String>?> {
        if (_keys.value == null) _keys.value = prefs(context).getStringSet(KEY, emptySet()).orEmpty().toSet()
        return _keys
    }

    fun dismiss(context: Context, key: String) {
        val next = keys(context).value.orEmpty() + key
        prefs(context).edit().putStringSet(KEY, next).apply()
        _keys.value = next
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
