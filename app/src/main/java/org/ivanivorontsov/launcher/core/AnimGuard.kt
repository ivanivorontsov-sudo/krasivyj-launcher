package org.ivanivorontsov.launcher.core

/**
 * Guards page-turn effects so a compositor-heavy animation can never brick launcher start.
 * Pure Kotlin: unit-tested without Android.
 *
 * «Затухание» (FADE) used to stack two full-screen hardware layers at the same pixels
 * (pager translation cancelled + alpha cross-fade). On some GPUs (notably Mali in MediaTek
 * phones) that hangs HWUI, the HOME activity never becomes ready, and the system cannot
 * set the app as the default launcher. Rotation (SPIN) does not stack pages that way.
 */
object AnimGuard {
    const val STALL_MS = 1_200L
    const val SETTLE_MS = 4_000L
    const val PREFS = "uyut_anim_guard"
    const val KEY_PENDING = "pending_page_effect"

    @Volatile var effectsEnabled: Boolean = true
        private set

    fun needsStartGuard(effect: PageEffect): Boolean = when (effect) {
        PageEffect.FADE, PageEffect.ZOOM_IN, PageEffect.DEPTH,
        PageEffect.CUBE, PageEffect.FLIP, PageEffect.ACCORDION -> true
        else -> false
    }

    fun shouldFallback(frameGapMs: Long): Boolean = frameGapMs >= STALL_MS

    fun safeEffect(requested: PageEffect): PageEffect =
        if (effectsEnabled) requested else PageEffect.NONE

    fun disable() { effectsEnabled = false }

    fun enable() { effectsEnabled = true }

    /**
     * If the previous start left [pending] set, that effect did not finish drawing —
     * treat it as unsafe (process was killed or ANR'd while it was active).
     */
    fun consumeFailedStart(pending: String?): PageEffect? {
        if (pending.isNullOrBlank()) return null
        return try { PageEffect.valueOf(pending) } catch (_: Exception) { null }
    }
}

/** App-drawer helpers: the alphabet scroller must not eat the opening swipe. */
object DrawerOpen {
    fun scrollerEnabled(progress: Float): Boolean = progress >= 0.999f
    const val INITIAL_INDEX = 0
}
