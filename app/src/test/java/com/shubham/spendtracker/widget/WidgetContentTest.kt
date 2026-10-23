package com.shubham.spendtracker.widget

import com.shubham.spendtracker.data.DailyLimit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetContentTest {
    @Test fun normalShowsAmountsAndProgress() {
        val w = WidgetContent.of(spentPaise = 42_000, limitPaise = 50_000, appLock = false, hideAmounts = false)
        assertEquals("₹420", w.amount)
        assertEquals("₹80 left of ₹500", w.detail)
        assertEquals(0.84f, w.fraction, 0.001f)
        assertEquals(DailyLimit.Level.NEAR, w.level)
    }

    @Test fun appLockHidesEverythingIncludingTheRing() {
        val w = WidgetContent.of(spentPaise = 90_000, limitPaise = 50_000, appLock = true, hideAmounts = false)
        assertEquals("₹••••", w.amount)
        assertFalse(w.detail.contains("₹"))
        assertEquals(0f, w.fraction)
        assertEquals(DailyLimit.Level.OK, w.level) // no coral "over" colour giving it away
    }

    @Test fun privacyModeHidesToo() {
        val w = WidgetContent.of(spentPaise = 90_000, limitPaise = 50_000, appLock = false, hideAmounts = true)
        assertEquals("₹••••", w.amount)
        assertEquals(0f, w.fraction)
    }
}
