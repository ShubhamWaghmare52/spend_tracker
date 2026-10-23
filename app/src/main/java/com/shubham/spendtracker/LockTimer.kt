package com.shubham.spendtracker

/**
 * Decides when app lock should ask again. State lives for the whole process (not one screen),
 * so pressing Back and reopening within [relockAfterMs] behaves like switching apps: no new unlock.
 * A new process (phone restart, app killed) always starts locked.
 */
class LockTimer(private val relockAfterMs: Long) {
    private var unlocked = false
    private var leftAt = 0L

    fun needsUnlock(now: Long): Boolean = !unlocked || (leftAt > 0 && now - leftAt > relockAfterMs)

    fun onUnlocked() {
        unlocked = true
        leftAt = 0
    }

    /** The app went to the background (or its screen closed) at [now]. */
    fun onLeft(now: Long) {
        if (unlocked) leftAt = now
    }

    /** Back in the foreground at [now]: forget the unlock if it has expired. */
    fun onReturned(now: Long) {
        if (needsUnlock(now)) unlocked = false
    }

    companion object {
        /** Shared by every MainActivity instance in this process. */
        val shared = LockTimer(AppLock.RELOCK_AFTER_MS)
    }
}
