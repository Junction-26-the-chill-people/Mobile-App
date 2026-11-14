package com.junction.catnudge

/** No polling or wake locks: callers update from screen events and schedule one remaining delay. */
internal class ScreenReadiness {
    var sinceElapsed: Long? = null
        private set
    var sinceWallTime: Long = 0L
        private set

    fun update(interactive: Boolean, unlocked: Boolean, nowElapsed: Long, nowWallTime: Long) {
        if (!interactive || !unlocked) {
            reset()
        } else if (sinceElapsed == null) {
            sinceElapsed = nowElapsed
            sinceWallTime = nowWallTime
        }
    }

    fun remainingMillis(nowElapsed: Long): Long = sinceElapsed?.let {
        (READY_DELAY_MS - (nowElapsed - it)).coerceIn(0L, READY_DELAY_MS)
    } ?: READY_DELAY_MS

    fun reset() { sinceElapsed = null; sinceWallTime = 0L }

    companion object { const val READY_DELAY_MS = 15_000L }
}
