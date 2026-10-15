package org.ivanivorontsov.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnGuardTest {
    @Test fun overlayNeverOutlivesAnimationByMoreThanMargin() {
        for (ms in listOf(100, 150, 300, 600, 1000, 5000)) for (st in ReturnStyle.entries) {
            val planned = ReturnGuard.plannedMs(ms, st)
            assertEquals(planned + 400L, ReturnGuard.flightTimeoutMs(ms, st))
            assertTrue("$ms $st", ReturnGuard.deadlineMs(ms, st) <= planned + 400L + 180L)
        }
    }

    @Test fun defaultReturnIsRemovedWithinAboutOneSecond() {
        // Default: «Классическая (в иконку)», 0.3 s → hard stop at 300 + 400 (+180 start slack).
        assertEquals(700L, ReturnGuard.flightTimeoutMs(300, ReturnStyle.ICON))
        assertEquals(880L, ReturnGuard.deadlineMs(300, ReturnStyle.ICON))
        // Worst case (1 s, ripples tail): still bounded.
        assertTrue(ReturnGuard.deadlineMs(1000, ReturnStyle.RINGS) <= 1000 + 700 + 400 + 180)
    }

    @Test fun expiry() {
        assertFalse(ReturnGuard.expired(0L, 10_000L, 300, ReturnStyle.ICON))       // nothing running
        assertFalse(ReturnGuard.expired(1_000L, 1_500L, 300, ReturnStyle.ICON))
        assertTrue(ReturnGuard.expired(1_000L, 1_880L, 300, ReturnStyle.ICON))
    }
}
