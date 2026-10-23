package com.shubham.spendtracker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockTimerTest {
    @Test fun freshStartIsLocked() {
        assertTrue(LockTimer(60_000).needsUnlock(now = 1_000))
    }

    @Test fun backAndReopenWithinAMinuteStaysUnlocked() {
        val t = LockTimer(60_000)
        t.onUnlocked()
        t.onLeft(now = 10_000)
        t.onReturned(now = 40_000)
        assertFalse(t.needsUnlock(now = 40_000))
    }

    @Test fun awayLongerThanAMinuteLocksAgainAndStaysLocked() {
        val t = LockTimer(60_000)
        t.onUnlocked()
        t.onLeft(now = 10_000)
        t.onReturned(now = 80_000)
        assertTrue(t.needsUnlock(now = 80_000))
        t.onLeft(now = 81_000) // leaving the lock screen doesn't count as an unlocked visit
        assertTrue(t.needsUnlock(now = 82_000))
    }

    @Test fun unlockingResetsTheClock() {
        val t = LockTimer(60_000)
        t.onUnlocked(); t.onLeft(10_000); t.onReturned(80_000)
        t.onUnlocked()
        assertFalse(t.needsUnlock(now = 200_000)) // still in the app
    }
}
