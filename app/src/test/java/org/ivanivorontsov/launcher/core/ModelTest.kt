package org.ivanivorontsov.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    @Test fun configRoundTrip() {
        val c = LauncherConfig(base = ScreenLayout(home = listOf("a", "b"), dock = listOf("c"), tiles = listOf(TileItem("t1", TileKind.CLOCK, size = TileSize.WIDE))),
            layouts = mapOf("letter" to ScreenLayout(home = listOf("folder:1"), folders = mapOf("folder:1" to Folder("folder:1", "Игры", listOf("a", "b"))))),
            hidden = setOf("x"), renamed = mapOf("a" to "Альфа"), usage = mapOf("a" to 3), doubleTap = GestureAction.NONE, widgets = listOf(WidgetItem(7, 200)),
            knownApps = setOf("p#0"), autoAddNew = false)
        assertEquals(c, ConfigCodec.decode(ConfigCodec.encode(c)))
        assertEquals(LauncherConfig().migrated(), ConfigCodec.decode("garbage"))
    }

    @Test fun legacyConfigMigrates() {
        val old = """{"home":["a","b"],"dock":["c"],"minimalFavorites":["b"],"tiles":[{"id":"clock","kind":"CLOCK"}],"seeded":true}"""
        val c = ConfigCodec.decode(old)
        assertEquals(ScreenLayout(home = listOf("a", "b"), dock = listOf("c"), tiles = listOf(TileItem("clock", TileKind.CLOCK)), favorites = listOf("b")), c.base)
        assertTrue(c.home.isEmpty()); assertTrue(c.seeded)
        assertEquals(c.base, c.layoutFor("letter"))
    }

    @Test fun pruneRemovesUninstalledEverywhere() {
        val c = LauncherConfig(base = ScreenLayout(home = listOf("a", "gone"), dock = listOf("gone"), tiles = listOf(TileItem("t", TileKind.APP, "gone"), TileItem("clock", TileKind.CLOCK))),
            layouts = mapOf("x" to ScreenLayout(home = listOf("folder:1"), folders = mapOf("folder:1" to Folder("folder:1", "F", listOf("a", "gone"))))), hidden = setOf("gone"))
            .prune(setOf("a"))
        assertEquals(listOf("a"), c.baseLayout().home); assertTrue(c.baseLayout().dock.isEmpty())
        assertEquals(listOf("clock"), c.baseLayout().tiles.map { it.id })
        // The folder lost one of its two apps and dissolved into the remaining one.
        assertEquals(listOf("a"), c.layouts.getValue("x").home); assertTrue(c.layouts.getValue("x").folders.isEmpty())
    }

    @Test fun pagesSplit() {
        val l = ScreenLayout(home = (1..30).map { "k$it" })
        val p = l.pages(16, 24)
        assertEquals(16, p[0].size); assertEquals(14, p[1].size)
        assertEquals(16, l.pageStart(1, 16, 24)); assertEquals(40, l.pageStart(2, 16, 24))
    }

    @Test fun tilePackerNoOverlap() {
        val sizes = listOf(TileSize.WIDE, TileSize.MEDIUM, TileSize.SMALL, TileSize.SMALL, TileSize.SMALL, TileSize.SMALL, TileSize.MEDIUM, TileSize.WIDE, TileSize.SMALL)
        val placed = TilePacker.pack(sizes, 4)
        assertEquals(sizes.size, placed.size)
        val cells = HashSet<Pair<Int, Int>>()
        for (p in placed) {
            assertTrue(p.col + p.w <= 4)
            for (r in p.row until p.row + p.h) for (col in p.col until p.col + p.w) assertTrue("overlap at $r,$col", cells.add(r to col))
        }
        // Four small tiles fill the 2×2 hole next to the first medium tile.
        assertEquals(2, placed[1].row); assertEquals(0, placed[1].col)
        assertEquals(2, placed[2].row); assertEquals(2, placed[2].col)
        assertEquals(3, placed[5].row); assertEquals(3, placed[5].col)
        assertEquals(6, TilePacker.rows(placed.take(7)))
    }

    @Test fun categorizer() {
        assertEquals(AppCategory.SOCIAL, Categorizer.categorize("org.telegram.messenger", "Telegram", -1))
        assertEquals(AppCategory.GAMES, Categorizer.categorize("com.foo", "Foo", 0))
        assertEquals(AppCategory.FINANCE, Categorizer.categorize("ru.sberbankmobile", "СберБанк", -1))
        assertEquals(AppCategory.OTHER, Categorizer.categorize("com.qwe.rty", "Qwerty", -1))
    }
}
