package com.shubham.spendtracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Light = lightColorScheme(
    primary = Color(0xFF1F6F5C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBE1),
    onPrimaryContainer = Color(0xFF00201A),
    secondaryContainer = Color(0xFFE6EEEA),
    // A touch deeper than the white cards so card edges read clearly in light mode.
    background = Color(0xFFEEF1EE),
    surface = Color(0xFFEEF1EE),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F4F2),
    surfaceContainerHigh = Color(0xFFECEFEC),
    surfaceContainerHighest = Color(0xFFE4E8E5),
    surfaceVariant = Color(0xFFDDE5E0),
    outlineVariant = Color(0xFFD5DDD8),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8DD5BF),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF0F5144),
    onPrimaryContainer = Color(0xFFCDEBE1),
    secondaryContainer = Color(0xFF26302D),
    background = Color(0xFF111413),
    surface = Color(0xFF111413),
    surfaceContainer = Color(0xFF1B1F1D),
    surfaceContainerLow = Color(0xFF161A18),
    surfaceContainerHigh = Color(0xFF232825),
    surfaceContainerHighest = Color(0xFF2B312E),
    surfaceVariant = Color(0xFF2B312E),
    outlineVariant = Color(0xFF353C38),
)

@Composable
fun SpendTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = TabularTypography,
        content = content,
    )
}

// Fixed-width digits everywhere: amounts line up in lists and counting animations don't jitter.
private val TabularTypography = Typography().let { t ->
    fun TextStyle.tnum() = copy(fontFeatureSettings = "tnum")
    t.copy(
        displayLarge = t.displayLarge.tnum(), displayMedium = t.displayMedium.tnum(), displaySmall = t.displaySmall.tnum(),
        headlineLarge = t.headlineLarge.tnum(), headlineMedium = t.headlineMedium.tnum(), headlineSmall = t.headlineSmall.tnum(),
        titleLarge = t.titleLarge.tnum(), titleMedium = t.titleMedium.tnum(), titleSmall = t.titleSmall.tnum(),
        bodyLarge = t.bodyLarge.tnum(), bodyMedium = t.bodyMedium.tnum(), bodySmall = t.bodySmall.tnum(),
        labelLarge = t.labelLarge.tnum(), labelMedium = t.labelMedium.tnum(), labelSmall = t.labelSmall.tnum(),
    )
}

/** Brand gradient shared by the hero card, launcher icon, splash and widget. */
val BrandGradientStart = Color(0xFF2A8A72)
val BrandGradientEnd = Color(0xFF0E4A3C)
val BrandMint = Color(0xFF9BF2CF)
val BrandCoral = Color(0xFFFF8A80)
val BrandAmber = Color(0xFFFFC86B)

// Text colours that must stay readable on both themes (WCAG-ish 4.5:1 on cards):
// a deeper shade on light backgrounds, a brighter one on dark.
private val isLightTheme: Boolean
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background.luminance() > 0.5f

val CreditGreen: Color
    @Composable @ReadOnlyComposable get() = if (isLightTheme) Color(0xFF1E7A46) else Color(0xFF5BC88E)

/** Coral for "spent" bars: the pastel reads on dark cards, a deeper shade keeps 3:1+ on white. */
val SpendCoral: Color
    @Composable @ReadOnlyComposable get() = if (isLightTheme) Color(0xFFD35A3E) else BrandCoral

val WarnAmber: Color
    @Composable @ReadOnlyComposable get() = if (isLightTheme) Color(0xFF9A5B00) else Color(0xFFE09A1A)

private val categoryColors = mapOf(
    "Food" to Color(0xFFE76F51),
    "Groceries" to Color(0xFF7CB342),
    "Shopping" to Color(0xFF8E6CD9),
    "Travel" to Color(0xFF2A9D8F),
    "Fuel" to Color(0xFFB5838D),
    "Bills & Recharge" to Color(0xFFE9A23B),
    "Entertainment" to Color(0xFFD9488F),
    "Health" to Color(0xFF3B82C4),
    "Education" to Color(0xFF5C6BC0),
    "Investments" to Color(0xFF00897B),
    "Rent" to Color(0xFF8D6E63),
    "Cash" to Color(0xFF6D8B74),
    "Transfers" to Color(0xFF78909C),
    "Income" to Color(0xFF2E8B57),
)

fun categoryColor(category: String): Color = categoryColors[category] ?: Color(0xFF9E9E9E)

private val inr = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).apply { maximumFractionDigits = 2 }

/**
 * Privacy mode. Backed by Compose state, so flipping it redraws every amount on screen.
 * Notifications pass `maskable = false`: they're the user's own alerts on their own lock screen.
 */
object AmountMask {
    var hidden by mutableStateOf(false)
    const val MASKED = "₹••••"
}

fun formatRupees(paise: Long, maskable: Boolean = true): String {
    if (maskable && AmountMask.hidden) return AmountMask.MASKED
    val s = inr.format(paise / 100.0)
    return if (paise % 100 == 0L) s.removeSuffix(".00") else s
}

/** Whole rupees (₹2,758) for totals, charts and alerts; exact amounts stay on individual payments. */
fun formatRupeesShort(paise: Long, maskable: Boolean = true): String =
    formatRupees(Math.round(paise / 100.0) * 100, maskable)

private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM")
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

fun formatDay(ts: Long): String = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(dayFmt)

private val fullFmt = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy · h:mm a")
fun formatDateTime(ts: Long): String = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(fullFmt)

/** "Today", "Yesterday", or "Tue, 20 Oct". */
fun relativeDay(ts: Long): String {
    val d = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = java.time.LocalDate.now()
    return when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        // Older years show the year, so "20 Oct" this year and last year never look the same.
        else -> if (d.year == today.year) d.format(dayFmt) else d.format(dayYearFmt)
    }
}

private val dayYearFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

private val categoryEmojis = mapOf(
    "Food" to "🍔", "Groceries" to "🛒", "Shopping" to "🛍️", "Travel" to "🚕", "Fuel" to "⛽",
    "Bills & Recharge" to "💡", "Entertainment" to "🎬", "Health" to "💊", "Education" to "🎓",
    "Investments" to "📈", "Rent" to "🏠", "Cash" to "💵", "Transfers" to "👤", "Self transfer" to "🔁", "Income" to "💰",
)

fun categoryEmoji(category: String): String = categoryEmojis[category] ?: "💳"

private val bankCodes = listOf(
    "HDFC" to "HDFC Bank", "SBI" to "SBI", "ICICI" to "ICICI Bank", "AXIS" to "Axis Bank", "KOTAK" to "Kotak",
    "BOB" to "Bank of Baroda", "BARB" to "Bank of Baroda", "JIOPBS" to "Jio Payments Bank", "JUSPAY" to "Amazon Pay",
    "AIRTEL" to "Airtel Payments Bank", "PNB" to "PNB", "CANBNK" to "Canara Bank", "UNION" to "Union Bank",
    "IDFC" to "IDFC First", "INDUS" to "IndusInd", "YESB" to "Yes Bank", "PAYTM" to "Paytm", "LNTFIN" to "L&T Finance",
)
private val dltSender = Regex("""^[A-Z]{2}-([A-Z0-9]+)(?:-[A-Z])?$""")

/** Turns "AD-HDFCBK-S + Google Pay" into "HDFC Bank · Google Pay". */
fun sourceLabel(sourceApp: String): String =
    sourceApp.split(" + ").map { part ->
        val code = dltSender.find(part.trim())?.groupValues?.get(1) ?: return@map part.trim()
        bankCodes.firstOrNull { code.contains(it.first) }?.second ?: code
    }.distinct().joinToString(" · ")
fun formatTime(ts: Long): String = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(timeFmt)
