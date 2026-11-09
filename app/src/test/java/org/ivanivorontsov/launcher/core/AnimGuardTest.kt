package org.ivanivorontsov.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimGuardTest {
    @Test fun fadeNeedsStartGuardAndSpinDoesNot() {
        assertTrue(AnimGuard.needsStartGuard(PageEffect.FADE))
        assertFalse(AnimGuard.needsStartGuard(PageEffect.SPIN))
        assertFalse(AnimGuard.needsStartGuard(PageEffect.NONE))
        assertFalse(AnimGuard.needsStartGuard(PageEffect.CYLINDER))
    }

    @Test fun stallThreshold() {
        assertFalse(AnimGuard.shouldFallback(1_199L))
        assertTrue(AnimGuard.shouldFallback(1_200L))
        assertTrue(AnimGuard.shouldFallback(5_000L))
    }

    @Test fun consumeFailedStart() {
        assertEquals(PageEffect.FADE, AnimGuard.consumeFailedStart("FADE"))
        assertNull(AnimGuard.consumeFailedStart(null))
        assertNull(AnimGuard.consumeFailedStart(""))
        assertNull(AnimGuard.consumeFailedStart("NOT_AN_EFFECT"))
    }

    @Test fun disableBlocksEffects() {
        AnimGuard.enable()
        assertEquals(PageEffect.FADE, AnimGuard.safeEffect(PageEffect.FADE))
        AnimGuard.disable()
        assertEquals(PageEffect.NONE, AnimGuard.safeEffect(PageEffect.FADE))
        AnimGuard.enable()
        assertEquals(PageEffect.SPIN, AnimGuard.safeEffect(PageEffect.SPIN))
    }

    @Test fun drawerScrollerWaitsUntilOpen() {
        assertFalse(DrawerOpen.scrollerEnabled(0f))
        assertFalse(DrawerOpen.scrollerEnabled(0.5f))
        assertFalse(DrawerOpen.scrollerEnabled(0.998f))
        assertTrue(DrawerOpen.scrollerEnabled(0.999f))
        assertTrue(DrawerOpen.scrollerEnabled(1f))
        assertEquals(0, DrawerOpen.INITIAL_INDEX)
    }
}
