package org.ivanivorontsov.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTest {
    private val l0 = ScreenLayout(home = listOf("a", "b", "c", "d"), dock = listOf("x", "y"))

    @Test fun reorderOnHome() {
        assertEquals(listOf("b", "c", "a", "d"), l0.place("a", DropSpot.Home(2))!!.home)
        assertEquals(listOf("d", "a", "b", "c"), l0.place("d", DropSpot.Home(0))!!.home)
        // Out-of-range indices clamp to the end instead of crashing.
        assertEquals(listOf("b", "c", "d", "a"), l0.place("a", DropSpot.Home(99))!!.home)
        // Dropping where it already is changes nothing.
        assertEquals(l0, l0.place("b", DropSpot.Home(1)))
    }

    @Test fun homeToDockAndBack() {
        val l = l0.place("b", DropSpot.Dock(1), maxDock = 4)!!
        assertEquals(listOf("x", "b", "y"), l.dock); assertEquals(listOf("a", "c", "d"), l.home)
        val back = l.place("x", DropSpot.Home(0))!!
        assertEquals(listOf("x", "a", "c", "d"), back.home); assertEquals(listOf("b", "y"), back.dock)
    }

    @Test fun fullDockRejectsButReorderInsideDockWorks() {
        assertNull(l0.place("a", DropSpot.Dock(0), maxDock = 2))
        assertEquals(listOf("y", "x"), l0.place("x", DropSpot.Dock(1), maxDock = 2)!!.dock)
    }

    @Test fun dragFromDrawerAddsNewIcon() {
        val l = l0.place("new", DropSpot.Home(1))!!
        assertEquals(listOf("a", "new", "b", "c", "d"), l.home)
    }

    @Test fun dropOnIconCreatesFolderInPlace() {
        val l = l0.place("d", DropSpot.Into("b"), folderName = "Игры")!!
        assertEquals(listOf("a", "folder:1", "c"), l.home)
        val f = l.folders.getValue("folder:1")
        assertEquals("Игры", f.name); assertEquals(listOf("b", "d"), f.apps)
        assertTrue(l.isPlaced("d")); assertEquals("folder:1", l.folderOf("b"))
        // A dock icon can become a folder too.
        val dockFolder = l0.place("a", DropSpot.Into("y"))!!
        assertEquals(listOf("x", "folder:1"), dockFolder.dock)
    }

    @Test fun addToExistingFolderAndNoNesting() {
        var l = l0.place("d", DropSpot.Into("b"))!!
        l = l.place("a", DropSpot.Into("folder:1"))!!
        assertEquals(listOf("b", "d", "a"), l.folders.getValue("folder:1").apps)
        assertEquals(listOf("folder:1", "c"), l.home)
        val l2 = l.place("x", DropSpot.Into("c"))!!
        assertNull(l2.place("folder:1", DropSpot.Into("folder:2")))
        assertNull(l.place("c", DropSpot.Into("c")))
        assertNull(l.place("c", DropSpot.Into("nope")))
        // Folders themselves can be moved around.
        assertEquals(listOf("c", "folder:1"), l.place("folder:1", DropSpot.Home(1))!!.home)
    }

    @Test fun draggingOutOfFolderDissolvesSingleAppFolder() {
        var l = l0.place("d", DropSpot.Into("b"))!!          // home: a, folder:1(b,d), c
        l = l.place("d", DropSpot.Home(0))!!                  // folder had 2 → now 1 → dissolves to "b" in place
        assertEquals(listOf("d", "a", "b", "c"), l.home)
        assertTrue(l.folders.isEmpty())
        var three = l0.place("d", DropSpot.Into("b"))!!.place("c", DropSpot.Into("folder:1"))!!
        three = three.place("b", DropSpot.Dock(0), maxDock = 5)!!
        assertEquals(listOf("d", "c"), three.folders.getValue("folder:1").apps)
        assertEquals(listOf("b", "x", "y"), three.dock)
    }

    @Test fun removeAndRename() {
        var l = l0.place("d", DropSpot.Into("b"))!!.copy(favorites = listOf("a"), tiles = listOf(TileItem("t_a", TileKind.APP, "a")))
        l = l.remove("a")
        assertFalse(l.isPlaced("a")); assertTrue(l.favorites.isEmpty()); assertTrue(l.tiles.isEmpty())
        l = l.renameFolder("folder:1", "  Работа ")
        assertEquals("Работа", l.folders.getValue("folder:1").name)
        assertEquals("Работа", l.renameFolder("folder:1", "  ").folders.getValue("folder:1").name)
        l = l.remove("folder:1")
        assertEquals(listOf("c"), l.home); assertTrue(l.folders.isEmpty())
    }

    @Test fun ungroupPutsAppsBackInPlace() {
        val l = l0.place("d", DropSpot.Into("b"))!!.ungroup("folder:1")
        assertEquals(listOf("a", "b", "d", "c"), l.home); assertTrue(l.folders.isEmpty())
    }

    @Test fun favouritesReorder() {
        val l = ScreenLayout(favorites = listOf("a", "b", "c"))
        assertEquals(listOf("b", "c", "a"), l.place("a", DropSpot.Fav(2))!!.favorites)
        assertEquals(listOf("n", "a", "b", "c"), l.place("n", DropSpot.Fav(0))!!.favorites)
    }

    @Test fun tilesMovePinResize() {
        var l = ScreenLayout(tiles = listOf(TileItem("clock", TileKind.CLOCK, size = TileSize.WIDE), TileItem("t_a", TileKind.APP, "a", TileSize.SMALL), TileItem("t_b", TileKind.APP, "b")))
        l = l.moveTile("t_b", 0)
        assertEquals(listOf("t_b", "clock", "t_a"), l.tiles.map { it.id })
        l = l.moveTile("t_b", 99)
        assertEquals(listOf("clock", "t_a", "t_b"), l.tiles.map { it.id })
        l = l.pinTile(TileItem("t_c", TileKind.APP, "c", TileSize.SMALL), at = 1)
        assertEquals(listOf("clock", "t_c", "t_a", "t_b"), l.tiles.map { it.id })
        assertEquals(l, l.pinTile(TileItem("t_c2", TileKind.APP, "c")))
        assertEquals(l, l.pinTile(TileItem("clock2", TileKind.CLOCK)))
        assertEquals(TileSize.MEDIUM, l.resizeTile("t_a").tiles[2].size)
        assertEquals(TileSize.WIDE, l.resizeTile("t_a", TileSize.WIDE).tiles[2].size)
        assertEquals(TileSize.SMALL, l.resizeTile("t_a").resizeTile("t_a").resizeTile("t_a").tiles[2].size)
        assertEquals(3, l.unpinTile("clock").tiles.size)
    }

    @Test fun normalizeFixesBrokenData() {
        val broken = ScreenLayout(home = listOf("a", "a", "folder:9", "folder:1", "b"), dock = listOf("b"),
            folders = mapOf("folder:1" to Folder("folder:1", "F", listOf("c")), "folder:2" to Folder("folder:2", "orphan", listOf("d", "e"))))
        val n = broken.normalized()
        assertEquals(listOf("b"), n.dock)
        assertEquals(listOf("a", "c"), n.home)
        assertTrue(n.folders.isEmpty())
    }

    @Test fun perThemeLayoutsForkAndFallBack() {
        var c = LauncherConfig(base = l0)
        assertEquals(l0, c.layoutFor("letter")); assertEquals(l0, c.layoutFor("tiles"))
        c = c.editLayout("letter") { it.place("a", DropSpot.Home(3)) }
        assertEquals(listOf("b", "c", "d", "a"), c.layoutFor("letter").home)
        assertTrue(c.hasOwnLayout("letter")); assertFalse(c.hasOwnLayout("cupertino"))
        // Themes never rearranged follow the latest edited one…
        assertEquals(listOf("b", "c", "d", "a"), c.layoutFor("cupertino").home)
        c = c.editLayout("cupertino") { it.place("d", DropSpot.Home(0)) }
        // …while an edited theme keeps its own arrangement.
        assertEquals(listOf("b", "c", "d", "a"), c.layoutFor("letter").home)
        assertEquals(listOf("d", "b", "c", "a"), c.layoutFor("cupertino").home)
        // Rejected drops do not fork or change anything.
        assertEquals(c, c.editLayout("minimal") { it.place("a", DropSpot.Dock(0), maxDock = 2) })
        c = c.resetLayout("cupertino")
        // A reset theme goes back to the shared arrangement; other themes keep theirs.
        assertEquals(l0, c.layoutFor("cupertino"))
        assertEquals(listOf("b", "c", "d", "a"), c.layoutFor("letter").home)
        assertEquals(l0, c.baseLayout())
    }

    @Test fun autoAddNewApps() {
        val base = ScreenLayout(home = listOf("a"), favorites = listOf("a"), tiles = listOf(TileItem("clock", TileKind.CLOCK)))
        var c = LauncherConfig(base = base, layouts = mapOf("letter" to ScreenLayout(home = listOf("b"))))
        // First run only remembers what is installed.
        val (first, added0) = c.registerInstalled(mapOf("pa#0" to "A", "pb#0" to "B"), { true })
        assertTrue(added0.isEmpty()); assertEquals(c.layouts, first.layouts)
        c = first
        val (second, added) = c.registerInstalled(mapOf("pa#0" to "A", "pb#0" to "B", "pn#0" to "N", "sys#0" to "S"), { it != "S" })
        assertEquals(listOf("N"), added)
        assertEquals(listOf("a", "N"), second.baseLayout().home)
        assertEquals(listOf("a", "N"), second.baseLayout().favorites)
        assertEquals(listOf("clock", "t_N"), second.baseLayout().tiles.map { it.id })
        assertEquals(listOf("b", "N"), second.layoutFor("letter").home)
        assertTrue(second.layoutFor("letter").favorites.isEmpty())
        // Updates (already known) are not re-added; switching the setting off stops placement.
        assertTrue(second.registerInstalled(mapOf("pa#0" to "A", "pn#0" to "N"), { true }).second.isEmpty())
        val off = second.copy(autoAddNew = false).registerInstalled(mapOf("pa#0" to "A", "pz#0" to "Z"), { true })
        assertTrue(off.second.isEmpty()); assertFalse(off.first.baseLayout().isPlaced("Z"))
        assertTrue("pz#0" in off.first.knownApps)
    }

    @Test fun autoAddDoesNotDuplicatePlacedApp() {
        val c = LauncherConfig(base = ScreenLayout(home = listOf("folder:1"), folders = mapOf("folder:1" to Folder("folder:1", "F", listOf("a", "n")))), knownApps = setOf("pa#0"))
        val (next, _) = c.registerInstalled(mapOf("pa#0" to "a", "pn#0" to "n"), { true })
        assertEquals(listOf("folder:1"), next.baseLayout().home)
    }
}
