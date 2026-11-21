package org.ivanivorontsov.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAnchorsTest {

    @Test fun everyIndexEntryHasResolvableAnchorOnItsScreen() {
        val all = SettingsIndex.all()
        assertTrue(all.isNotEmpty())
        val missing = mutableListOf<String>()
        for (e in all) {
            val t = SettingsAnchors.resolve(e)
            if (t == null) {
                missing += "${e.id} on ${e.screen}"
                continue
            }
            assertEquals("screen mismatch for ${e.id}", e.screen, t.screen)
            assertEquals(e.id, t.id)
            val order = SettingsAnchors.lazyOrder()[e.screen]
            assertNotNull("no lazy item order for screen ${e.screen} (${e.id})", order)
            assertTrue(
                "${e.id}: lazyKey '${t.lazyKey}' is not a resolvable item on ${e.screen}: $order",
                t.lazyKey in order!!,
            )
            t.group?.let { g ->
                assertTrue(
                    "${e.id}: group '$g' is not a known expandable group on ${e.screen}",
                    g in SettingsAnchors.groupsOf(e.screen),
                )
            }
        }
        assertTrue("entries without a resolvable anchor: $missing", missing.isEmpty())
    }

    @Test fun nestedRowsExpandTheirGroupFirst() {
        fun target(id: String) = SettingsAnchors.resolve(id)!!
        val dock = target("dock-style")
        assertEquals(SettingsIndex.Screens.EDITOR, dock.screen)
        assertEquals("grid", dock.group)
        assertEquals("grid", dock.lazyKey)
        assertTrue(dock.needsExpand())

        val clock = target("clock-style")
        assertEquals("grid", clock.group)

        val dyn = target("dynamic-color")
        assertEquals("colors", dyn.group)
        assertEquals("colors", target("dark-theme").group)
        assertEquals("wallpaper", target("blur").group)
        assertEquals("icon-shape", target("labels").group)

        for (id in listOf("ai-arrange", "ai-undo", "ai-repair", "sort-folders", "all-apps-on-home")) {
            val t = target(id)
            assertEquals("home-apps", t.group)
            assertTrue(t.needsExpand())
            assertEquals("home-apps", t.lazyKey)
        }
    }

    @Test fun playFlavorDropsRestrictedAnchorsButKeepsTheRest() {
        val play = SettingsIndex.all(includePlayRestricted = false)
        for (e in play) {
            assertNotNull(e.id, SettingsAnchors.resolve(e.id, includePlayRestricted = false))
        }
        val order = SettingsAnchors.lazyOrder(includePlayRestricted = false)
        assertTrue("lock-double-tap" !in (order[SettingsIndex.Screens.GESTURES] ?: emptyList()))
        assertTrue(SettingsIndex.Screens.STOP !in order)
        assertTrue(SettingsIndex.Screens.DIGITS !in order)
        assertTrue("ai-arrange" in (order[SettingsIndex.Screens.HOME] ?: emptyList()) ||
            SettingsAnchors.resolve("ai-arrange", includePlayRestricted = false) != null)
    }
}
