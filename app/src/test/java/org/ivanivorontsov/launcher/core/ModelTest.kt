package org.ivanivorontsov.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    @Test fun configRoundTrip() {
        val c = LauncherConfig(home = listOf("a", "b"), dock = listOf("c"), tiles = listOf(TileItem("t1", TileKind.CLOCK, size = TileSize.WIDE)),
            hidden = setOf("x"), renamed = mapOf("a" to "Альфа"), usage = mapOf("a" to 3), doubleTap = GestureAction.NONE, widgets = listOf(WidgetItem(7, 200)))
        assertEquals(c, ConfigCodec.decode(ConfigCodec.encode(c)))
        assertEquals(LauncherConfig(), ConfigCodec.decode("garbage"))
    }

    @Test fun placementOps() {
        var c = LauncherConfig().addToHome("a").addToHome("b").addToHome("a")
        assertEquals(listOf("a", "b"), c.home)
        c = c.addToDock("c", 5)
        assertTrue(c.isPlaced("c"))
        c = c.move("b", -1)
        assertEquals(listOf("b", "a"), c.home)
        c = c.removeEverywhere("a")
        assertFalse(c.isPlaced("a"))
    }

    @Test fun tilesOps() {
        var c = LauncherConfig().pinTile(TileItem("t", TileKind.APP, "a", TileSize.SMALL))
        c = c.resizeTile("t"); assertEquals(TileSize.MEDIUM, c.tiles[0].size)
        c = c.resizeTile("t"); assertEquals(TileSize.WIDE, c.tiles[0].size)
        c = c.resizeTile("t"); assertEquals(TileSize.SMALL, c.tiles[0].size)
        c = c.unpinTile("t"); assertTrue(c.tiles.isEmpty())
    }

    @Test fun pruneRemovesUninstalled() {
        val c = LauncherConfig(home = listOf("a", "gone"), dock = listOf("gone"), hidden = setOf("gone"), tiles = listOf(TileItem("t", TileKind.APP, "gone"), TileItem("clock", TileKind.CLOCK)))
            .prune(setOf("a"))
        assertEquals(listOf("a"), c.home); assertTrue(c.dock.isEmpty())
        assertEquals(listOf("clock"), c.tiles.map { it.id })
    }

    @Test fun pagesSplit() {
        val c = LauncherConfig(home = (1..30).map { "k$it" })
        val p = c.pagesFor(16, 24)
        assertEquals(16, p[0].size); assertEquals(14, p[1].size)
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
