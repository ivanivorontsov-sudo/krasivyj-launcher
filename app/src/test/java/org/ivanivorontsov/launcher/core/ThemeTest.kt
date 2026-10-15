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
        Presets.all.forEach { p -> BrandWords.find(p.name)?.let { b -> throw AssertionError("${p.name} contains $b") } }
        Presets.all.forEach { p -> BrandWords.find(p.description)?.let { b -> throw AssertionError("${p.id} description contains $b") } }
    }

    /** 1.3.9: every user-visible enum title (animations, styles, effects) is brand-free too. */
    @Test fun noBrandNamesInOptionTitles() {
        val titles = HomeAnim.entries.map { it.title } + BackAnim.entries.map { it.title } + UnlockAnim.entries.map { it.title } +
            IconFx.entries.map { it.title } + Motion.entries.map { it.title } + PageEffect.entries.map { it.title } +
            HomeLayout.entries.map { it.title } + DrawerStyle.entries.map { it.title } + IconShape.entries.map { it.title } +
            WallpaperStyle.entries.map { it.title } + DockStyle.entries.map { it.title } + ClockStyle.entries.map { it.title }
        titles.forEach { t -> BrandWords.find(t)?.let { b -> throw AssertionError("«$t» contains $b") } }
        assertEquals("Пружина", HomeAnim.SPRING.title); assertEquals("Пружина", BackAnim.SPRING.title)
        assertEquals("Стекло", Presets.cupertino.name)
    }

    /** 1.3.9: all Russian string literals in the app sources, strings.xml and the Play listing files. */
    @Test fun noBrandNamesInUiStringsAndListing() {
        val root = java.io.File(System.getProperty("user.dir")!!).let { if (java.io.File(it, "src/main").exists()) it else java.io.File(it, "app") }
        val bad = mutableListOf<String>()
        val literal = Regex("\"((?:\\\\.|[^\"\\\\])*)\"")
        root.resolve("src").walkTopDown().filter { it.isFile && it.extension == "kt" && "/test/" !in it.path }.forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                if ("legacy-name" in line) return@forEachIndexed   // LegacyNames: old saved names, read-only migration
                if (line.trimStart().startsWith("//") || line.trimStart().startsWith("*") || line.trimStart().startsWith("/*")) return@forEachIndexed
                literal.findAll(line).map { it.groupValues[1] }
                    .filter { s -> s.any { it in 'А'..'я' || it == 'ё' } && s != Legal.DISCLAIMER }
                    .forEach { s -> BrandWords.find(s)?.let { b -> bad += "${f.name}:${i + 1} «$s» ($b)" } }
            }
        }
        root.resolve("src/main/res").walkTopDown().filter { it.isFile && it.name == "strings.xml" }.forEach { f ->
            BrandWords.find(f.readText())?.let { b -> bad += "${f.name} ($b)" }
        }
        val play = root.resolve("../play")
        assertTrue("play/ listing folder missing", play.isDirectory)
        play.listFiles()!!.filter { it.name.startsWith("listing") && it.extension == "md" }.also { assertTrue(it.isNotEmpty()) }.forEach { f ->
            val text = f.readText()
            assertTrue("${f.name}: disclaimer missing at the end", text.trimEnd().endsWith(Legal.DISCLAIMER))
            BrandWords.find(text.replace(Legal.DISCLAIMER, ""))?.let { b -> bad += "${f.name} ($b)" }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun brandWordMatcher() {
        listOf("Пружина (как iOS)", "Купертино", "Cupertino", "Lumia", "Windows Phone", "Metro", "iPhone", "Apple", "Microsoft").forEach { assertNotNull(it, BrandWords.find(it)) }
        listOf("Пружина", "Стекло", "Плитки", "Призма", "Studios", "audios").forEach { assertNull(it, BrandWords.find(it)) }
    }

    @Test fun legacyThemeNamesMigrate() {
        val old = ThemeCodec.encode(Presets.cupertino.copy(name = "Купертино"))
        assertEquals("Стекло", ThemeCodec.decode(old)!!.name)
        assertEquals("cupertino", ThemeCodec.decode(old)!!.id)
        val oldGlass = ThemeCodec.encode(Presets.glass.copy(name = "Стекло"))
        assertEquals("Призма", ThemeCodec.decode(oldGlass)!!.name)
        val custom = ThemeCodec.encode(Presets.cupertino.copy(id = "custom", name = "Купертино"))
        assertEquals("Стекло", ThemeCodec.decode(custom)!!.name)
        val renamed = ThemeCodec.encode(Presets.cupertino.copy(id = "custom", name = "Мой стиль"))
        assertEquals("Мой стиль", ThemeCodec.decode(renamed)!!.name)
        assertEquals(1, Presets.all.count { it.name == "Стекло" })
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

/** Brand / trademark words that must not appear in anything the user sees (case-insensitive, whole words for latin). */
object BrandWords {
    private val latin = listOf("ios", "iphone", "ipad", "apple", "windows", "windows phone", "microsoft", "metro", "lumia", "segoe", "sf pro", "cupertino")
    private val cyr = listOf("купертино", "как ios", "айфон", "эппл", "виндовс", "люмия", "метро-стиль")
    private val latinRe = latin.map { it to Regex("(?<![a-z])" + Regex.escape(it) + "(?![a-z])", RegexOption.IGNORE_CASE) }
    fun find(text: String): String? {
        val low = text.lowercase()
        cyr.firstOrNull { it in low }?.let { return it }
        return latinRe.firstOrNull { it.second.containsMatchIn(text) }?.first
    }
}
