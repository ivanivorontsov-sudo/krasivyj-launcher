package org.ivanivorontsov.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DigitFormatTest {
    @Test fun padsMinutesAndSeconds() {
        assertEquals("00:00", DigitFormat.duration(0))
        assertEquals("00:05", DigitFormat.duration(5_400))
        assertEquals("01:30", DigitFormat.duration(90_000))
    }

    @Test fun switchesToHours() {
        assertEquals("1:00:00", DigitFormat.duration(3_600_000))
        assertEquals("1:02:03", DigitFormat.duration(3_723_000))
    }

    @Test fun overlayStaysOffUntilEnabled() {
        assertFalse(LauncherConfig().digitOverlay)
        assertEquals(DigitMode.APP, LauncherConfig().digitMode)
    }
}
