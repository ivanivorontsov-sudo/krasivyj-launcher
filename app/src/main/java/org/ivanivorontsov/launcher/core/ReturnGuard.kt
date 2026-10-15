package org.ivanivorontsov.launcher.core

/**
 * 1.3.9: safety net for the custom "back to home" overlay (card flying into the icon).
 * Whatever happens (OEM never sends the enter/focus callbacks, the flight coroutine is cancelled,
 * the user starts another app mid-flight), the overlay is removed and the grid is shown at full size
 * no later than [deadlineMs] after the return began. Pure function so it is unit-tested.
 */
object ReturnGuard {
    /** Extra time on top of the planned animation before the overlay is force-removed. */
    const val HARD_MARGIN_MS = 400L
    /** Start fallbacks in MainActivity (50 / 180 ms) — the flight may begin this late. */
    const val START_SLACK_MS = 180L

    /** Planned length of the flight for this style (springs settle ~1.4× slower, ripples/drop have a tail). */
    fun plannedMs(durationMs: Int, style: ReturnStyle): Long {
        val ms = durationMs.coerceIn(150, 1000)
        val main = if (style == ReturnStyle.SPRING) (ms * 1.4f).toLong().coerceAtLeast(260L) else ms.toLong()
        val tail = if (style == ReturnStyle.RINGS || style == ReturnStyle.DROP) (ms * 1.2f).toLong().coerceIn(380L, 700L) else 0L
        return main + tail
    }

    /** Timeout for the flight coroutine itself. */
    fun flightTimeoutMs(durationMs: Int, style: ReturnStyle): Long = plannedMs(durationMs, style) + HARD_MARGIN_MS

    /** Watchdog posted on resume: covers a flight that never started. */
    fun deadlineMs(durationMs: Int, style: ReturnStyle): Long = START_SLACK_MS + flightTimeoutMs(durationMs, style)

    /** True when an overlay that began at [startedAt] must be force-removed at [now]. */
    fun expired(startedAt: Long, now: Long, durationMs: Int, style: ReturnStyle): Boolean =
        startedAt > 0L && now - startedAt >= deadlineMs(durationMs, style)
}
