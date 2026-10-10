package org.ivanivorontsov.launcher.stop.core

/**
 * Small deterministic reducer for accessibility usage events. Kept pure so edge cases
 * (including an app already in the foreground at the beginning of the interval) can be tested.
 */
object AppUsageCalc {
    const val RESUMED = 1
    const val PAUSED = 2
    const val SCREEN_OFF = 3

    data class Ev(val at: Long, val pkg: String, val type: Int)

    fun compute(events: List<Ev>, start: Long, end: Long): Map<String, Long> {
        if (end <= start) return emptyMap()
        val totals = linkedMapOf<String, Long>()
        var activePackage: String? = null
        var activeSince = start

        fun close(at: Long) {
            val pkg = activePackage ?: return
            val duration = (at.coerceAtMost(end) - activeSince.coerceAtLeast(start)).coerceAtLeast(0L)
            if (duration > 0) totals[pkg] = (totals[pkg] ?: 0L) + duration
            activePackage = null
        }

        for (event in events.sortedBy { it.at }) {
            if (event.at < start) {
                if (event.type == RESUMED) {
                    activePackage = event.pkg
                    activeSince = start
                } else if (event.type == PAUSED && activePackage == event.pkg) {
                    activePackage = null
                } else if (event.type == SCREEN_OFF) {
                    activePackage = null
                }
                continue
            }
            if (event.at >= end) break
            when (event.type) {
                RESUMED -> {
                    close(event.at)
                    activePackage = event.pkg
                    activeSince = event.at
                }
                PAUSED -> if (activePackage == event.pkg) close(event.at)
                SCREEN_OFF -> close(event.at)
            }
        }
        close(end)
        return totals.filterValues { it > 0L }
    }
}
