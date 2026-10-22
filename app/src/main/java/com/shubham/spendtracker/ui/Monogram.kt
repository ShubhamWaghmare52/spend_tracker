package com.shubham.spendtracker.ui

import kotlin.math.abs

/**
 * Two-letter initials for a payee: "SWIGGY" → "SW", "RAHUL K SHARMA" → "RK",
 * "rahul.k@okaxis" → "RK". The UPI handle after '@' and digits are ignored.
 */
fun monogram(merchant: String): String {
    val words = merchant.substringBefore('@')
        .replace(Regex("[^A-Za-z]+"), " ")
        .trim()
        .split(' ')
        .filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> merchant.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> "${words[0][0]}${words[1][0]}".uppercase()
    }
}

/** Stable palette slot for a payee, so each merchant keeps its colour everywhere. */
fun monogramSlot(merchant: String, slots: Int): Int =
    abs(merchant.lowercase().filter { it.isLetterOrDigit() }.hashCode() % slots)
