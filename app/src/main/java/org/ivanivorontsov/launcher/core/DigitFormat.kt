package org.ivanivorontsov.launcher.core

/** mm:ss, or h:mm:ss once an hour has passed. Pure so the overlay and tests share it. */
object DigitFormat {
    fun duration(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
