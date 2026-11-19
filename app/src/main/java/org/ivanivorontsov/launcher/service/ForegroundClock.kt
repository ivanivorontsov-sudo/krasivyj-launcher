package org.ivanivorontsov.launcher.service

import android.os.SystemClock

/**
 * How long the current foreground app has been in front. Updated from the accessibility
 * service when a window-state event names a new package. Reading it is cheap.
 */
object ForegroundClock {
    @Volatile var pkg: String? = null
        private set
    @Volatile private var started = 0L

    fun note(packageName: String) {
        if (packageName == pkg) return
        pkg = packageName
        started = SystemClock.elapsedRealtime()
    }

    fun sessionMs(): Long {
        val start = started
        if (pkg == null || start == 0L) return 0L
        return (SystemClock.elapsedRealtime() - start).coerceAtLeast(0L)
    }
}
