package com.shubham.spendtracker.widget

import com.shubham.spendtracker.data.DailyLimit
import com.shubham.spendtracker.ui.AmountMask
import com.shubham.spendtracker.ui.formatRupeesShort

/** What the home screen widget shows. Kept free of Android types so it can be unit-tested. */
data class WidgetContent(
    val amount: String,
    val detail: String,
    /** How much of the ring to fill, 0..1. */
    val fraction: Float,
    val level: DailyLimit.Level,
) {
    companion object {
        /**
         * The home screen is visible to anyone holding the phone, so with app lock on (or privacy
         * mode) the widget shows no amounts and no ring progress, which would give the total away.
         */
        fun of(spentPaise: Long, limitPaise: Long, appLock: Boolean, hideAmounts: Boolean): WidgetContent {
            if (appLock) return WidgetContent(AmountMask.MASKED, "Unlock the app to see today", 0f, DailyLimit.Level.OK)
            if (hideAmounts) return WidgetContent(AmountMask.MASKED, "Amounts hidden", 0f, DailyLimit.Level.OK)

            fun rupees(p: Long) = formatRupeesShort(p, maskable = false)
            val level = DailyLimit.level(spentPaise, limitPaise)
            val detail = when {
                limitPaise <= 0 -> "Set a daily limit in the app"
                level == DailyLimit.Level.OVER -> "${rupees(spentPaise - limitPaise)} over your ${rupees(limitPaise)} limit"
                else -> "${rupees(limitPaise - spentPaise)} left of ${rupees(limitPaise)}"
            }
            val fraction = if (limitPaise > 0) (spentPaise.toFloat() / limitPaise).coerceIn(0f, 1f) else 0f
            return WidgetContent(rupees(spentPaise), detail, fraction, level)
        }
    }
}
