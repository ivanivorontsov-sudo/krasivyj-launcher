package org.ivanivorontsov.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridMigrationTest {
    private val folder = Folder("folder:1", "Игры", listOf("g1", "g2"))
    private val user = ScreenLayout(
        home = listOf(CLOCK_WIDGET, "a", "folder:1", "widget:7", "b", "c"),
        dock = listOf("d"),
        folders = mapOf("folder:1" to folder),
        widgets = mapOf(
            CLOCK_WIDGET to HomeWidget(CLOCK_WIDGET, HomeWidgetKind.CLOCK, spanX = 4, spanY = 2),
            "widget:7" to HomeWidget("widget:7", HomeWidgetKind.SYSTEM, 7, spanX = 2, spanY = 2),
        ),
        clockPlaced = true,
    )
    private val seededBase = ScreenLayout(home = listOf("a", "b", "c", "g1", "g2"), dock = listOf("d"))

    @Test fun editingPresetKeepsArrangement() {
        val before = LauncherConfig(base = seededBase, layouts = mapOf("cupertino" to user), lastEdited = "cupertino",
            pageEffects = mapOf("cupertino" to PageEffect.entries.last()))
        val after = ConfigCodec.decode(ConfigCodec.encode(before.forkForEdit("cupertino", "custom", 4, 6, 6, 8)))
        val l = after.layoutFor("custom")
        assertEquals(user.home, l.home)            // order, folder and widgets kept
        assertEquals(user.dock, l.dock)
        assertEquals(user.folders, l.folders)      // folders not rebuilt
        assertTrue(l.clockPlaced)
        assertEquals(6, l.widgets.getValue(CLOCK_WIDGET).spanX)   // full-width clock stays full width
        assertEquals(3, l.widgets.getValue(CLOCK_WIDGET).spanY)
        assertEquals(3, l.widgets.getValue("widget:7").spanX)
        assertEquals(user, after.layoutFor("cupertino"))           // original theme untouched
        assertEquals(PageEffect.entries.last(), after.pageEffectFor("custom", HomeLayout.GRID))
    }

    @Test fun shrinkAndGrowBackKeepsFullWidthClock() {
        val small = user.remapGrid(4, 6, 3, 4)
        assertEquals(3, small.widgets.getValue(CLOCK_WIDGET).spanX)
        val big = small.remapGrid(3, 4, 6, 8)
        assertEquals(6, big.widgets.getValue(CLOCK_WIDGET).spanX)
        assertEquals(user.home, big.home)
        val packed = big.packPages(6, 8)
        assertEquals(user.home.size, packed.pages.sumOf { it.size })
    }

    @Test fun iconSizeChangeOnlyIsNoOpForLayout() {
        val c = LauncherConfig(base = seededBase, layouts = mapOf("custom" to user), lastEdited = "custom")
        assertEquals(c, c.forkForEdit("custom", "custom", 4, 6, 4, 6))
    }
}
