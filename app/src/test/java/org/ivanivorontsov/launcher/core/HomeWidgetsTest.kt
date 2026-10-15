package org.ivanivorontsov.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWidgetsTest {
    private val apps = (1..30).map { "app$it/a" }

    @Test fun iconsOnlyPackLikeBefore() {
        val p = ScreenLayout(home = apps).packPages(4, 6)
        assertEquals(2, p.size); assertEquals(24, p.page(0).size); assertEquals(6, p.page(1).size)
        assertEquals(Placed("app5/a", 0, 1, 1, 1, 4), p.page(0)[4])
    }

    @Test fun widgetOccupiesCellsAndIconsFlowAround() {
        val l = ScreenLayout(home = listOf("a/a", "b/b")).addWidget(HomeWidget("widget:7", appWidgetId = 7, spanX = 2, spanY = 2), 1)
        val p = l.packPages(4, 6)
        val w = p.page(0).first { it.id == "widget:7" }
        assertEquals(Placed("widget:7", 1, 0, 2, 2, 1), w)
        // b goes to the first free cell after the widget's top-left cells: (3,0).
        assertEquals(3, p.page(0).first { it.id == "b/b" }.col)
        assertEquals("widget:7", p.at(0, 2, 1)!!.id)
        assertNull(p.at(0, 0, 1))
    }

    @Test fun clockMigrationTakesTopTwoRowsOnce() {
        val l = ScreenLayout(home = apps.take(5)).withClockWidget(4)
        assertEquals(CLOCK_WIDGET, l.home[0]); assertTrue(l.clockPlaced)
        val p = l.packPages(4, 6)
        assertEquals(Placed(CLOCK_WIDGET, 0, 0, 4, 2, 0), p.page(0)[0])
        assertEquals(2, p.page(0)[1].row)
        // Removed by the user → not re-added.
        val removed = l.remove(CLOCK_WIDGET)
        assertFalse(CLOCK_WIDGET in removed.home)
        assertEquals(removed, removed.withClockWidget(4))
    }

    @Test fun widgetTooBigStartsNewPage() {
        val l = ScreenLayout(home = apps.take(23)).addWidget(HomeWidget("widget:1", appWidgetId = 1, spanX = 2, spanY = 2))
        val p = l.packPages(4, 6)
        assertEquals(2, p.size); assertEquals("widget:1", p.page(1)[0].id)
    }

    @Test fun resizeClampsAndPersistsThroughNormalize() {
        var l = ScreenLayout(home = listOf("a/a")).addWidget(HomeWidget("widget:3", appWidgetId = 3, spanX = 2, spanY = 1))
        l = l.resizeWidget("widget:3", 9, 3, 4, 6)
        assertEquals(4, l.widgets["widget:3"]!!.spanX); assertEquals(3, l.widgets["widget:3"]!!.spanY)
        val n = l.normalized(existing = setOf("a/a"))
        assertEquals(listOf("a/a", "widget:3"), n.home); assertEquals(l.widgets, n.widgets)
        assertEquals(setOf(3), n.appWidgetIds())
    }

    @Test fun widgetsNeverGoToDockOrFolders() {
        val l = ScreenLayout(home = listOf("a/a", "b/b")).addWidget(HomeWidget("widget:3", appWidgetId = 3))
        assertNull(l.place("widget:3", DropSpot.Dock(0)))
        assertNull(l.place("widget:3", DropSpot.Into("a/a")))
        assertNull(l.place("a/a", DropSpot.Into("widget:3")))
        val moved = l.place("widget:3", DropSpot.Home(0))!!
        assertEquals("widget:3", moved.home[0])
        val g = l.groupHomeIntoFolders { "Все" }
        assertTrue("widget:3" in g.home)
    }

    @Test fun removeDropsWidgetEntry() {
        val l = ScreenLayout(home = listOf("a/a")).addWidget(HomeWidget("widget:3", appWidgetId = 3)).remove("widget:3")
        assertEquals(listOf("a/a"), l.home); assertTrue(l.widgets.isEmpty())
    }

    @Test fun indexAtCells() {
        val l = ScreenLayout(home = listOf("a/a", "b/b", "c/c")).addWidget(HomeWidget("widget:1", appWidgetId = 1, spanX = 2, spanY = 2), 1)
        val p = l.packPages(4, 6)
        assertEquals(1, p.indexAt(0, 2, 1))   // on the widget
        assertEquals(4, p.indexAt(0, 3, 5))   // empty cell after everything → end
        assertEquals(4, p.indexAt(1, 0, 0))   // extra empty page → end
    }

    @Test fun disableTodayMovesWidgetsHome() {
        val c = LauncherConfig(base = ScreenLayout(home = listOf("a/a")), widgets = listOf(WidgetItem(11, 180), WidgetItem(12, 90)))
        val d = c.disableTodayPage("cupertino", 4, 6, moveToHome = true)
        assertFalse(d.todayPage); assertTrue(d.widgets.isEmpty())
        val l = d.layoutFor("cupertino")
        assertEquals(listOf("a/a", "widget:11", "widget:12"), l.home)
        assertEquals(3, l.widgets["widget:11"]!!.spanY); assertEquals(4, l.widgets["widget:11"]!!.spanX)
        assertEquals(setOf(11, 12), d.allAppWidgetIds())
        val dropped = c.disableTodayPage("cupertino", 4, 6, moveToHome = false)
        assertTrue(dropped.allAppWidgetIds().isEmpty())
    }

    @Test fun persistsThroughJson() {
        val c = LauncherConfig(base = ScreenLayout(home = listOf("a/a"))).editLayout("cupertino") { it.withClockWidget(4).addWidget(HomeWidget("widget:5", appWidgetId = 5, spanX = 3, spanY = 2)) }
        val back = ConfigCodec.decode(ConfigCodec.encode(c))
        assertEquals(c.layoutFor("cupertino"), back.layoutFor("cupertino"))
    }
}
