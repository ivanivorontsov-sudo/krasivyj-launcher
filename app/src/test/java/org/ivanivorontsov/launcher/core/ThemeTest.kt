package org.ivanivorontsov.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test fun allPresetsRoundTrip() {
        for (p in Presets.all) {
            val back = ThemeCodec.decode(ThemeCodec.encode(p))
            assertEquals(p.id, p.sanitized(), back)
        }
    }

    @Test fun presetIdsUniqueAndNamesRussian() {
        assertEquals(Presets.all.size, Presets.all.map { it.id }.toSet().size)
        Presets.all.forEach { assertTrue(it.name, it.name.any { c -> c in 'А'..'я' || c.isDigit() }) }
        assertTrue(Presets.all.size >= 14)
    }

    @Test fun presetFontsExistInBundle() {
        for (p in Presets.all) for (f in listOf(p.headingFont, p.bodyFont, p.clockFont)) assertTrue("${p.id}: $f", f in FontIds.all)
    }

    @Test fun noBrandNamesInThemeTitles() {
        val banned = listOf("ios", "apple", "iphone", "windows", "microsoft", "metro", "segoe", "sf pro")
        Presets.all.forEach { p -> banned.forEach { b -> assertTrue("${p.name} contains $b", !p.name.lowercase().contains(b)) } }
    }

    @Test fun sanitizeClampsAndFallsBack() {
        val bad = Presets.cupertino.copy(columns = 99, rows = -3, iconSizeDp = 500, headingFont = "comic-sans-ms", wallpaperDim = 5f)
        val s = bad.sanitized()
        assertTrue(s.columns in 3..6); assertTrue(s.rows in 3..8); assertTrue(s.iconSizeDp <= 80)
        assertEquals("inter", s.headingFont); assertTrue(s.wallpaperDim <= 1f)
    }

    @Test fun decodeToleratesUnknownAndGarbage() {
        assertNull(ThemeCodec.decode("{not json"))
        assertNull(ThemeCodec.decode(null))
        val json = ThemeCodec.encode(Presets.tiles).dropLast(1) + ",\"futureField\":42,\"motion\":\"WARP\"}"
        val t = ThemeCodec.decode(json)
        assertNotNull(t); assertEquals("tiles", t!!.id)
    }

    @Test fun textCase() {
        assertEquals("телефон", Presets.tiles.copy(textCase = TextCase.LOWER).withCase("Телефон"))
        assertEquals("ТЕЛЕФОН", Presets.cupertino.copy(textCase = TextCase.UPPER).withCase("Телефон"))
    }

    @Test fun tilePaletteHas20Colors() {
        assertEquals(20, AccentPalette.tiles.size)
        assertEquals(20, AccentPalette.tiles.map { it.second }.toSet().size)
    }
}
