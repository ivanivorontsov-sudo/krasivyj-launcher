package org.ivanivorontsov.launcher.stop

import org.ivanivorontsov.launcher.core.Folder
import org.ivanivorontsov.launcher.core.ScreenLayout
import org.ivanivorontsov.launcher.core.unveiled
import org.ivanivorontsov.launcher.core.veiled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VeilTest {
    private val l = ScreenLayout(home = listOf("a", "X", "b", "folder:1", "c"), dock = listOf("d", "Y"),
        folders = mapOf("folder:1" to Folder("folder:1", apps = listOf("e", "Z"))))
    private val hidden = setOf("X", "Y", "Z")

    @Test fun veiledHasNoGaps() {
        val v = l.veiled(hidden)
        assertEquals(listOf("a", "b", "folder:1", "c"), v.home)
        assertEquals(listOf("d"), v.dock)
        assertEquals(listOf("e"), v.folders["folder:1"]!!.apps)
    }

    @Test fun unchangedEditRestoresExactly() {
        assertEquals(l, l.veiled(hidden).unveiled(l, hidden))
    }

    @Test fun moveKeepsHiddenAppsPlaced() {
        val v = l.veiled(hidden)
        val moved = v.copy(home = listOf("c", "a", "b", "folder:1"))
        val r = moved.unveiled(l, hidden)
        assertTrue(r.placedApps().containsAll(hidden))
        assertEquals("X", r.home[1])
    }

    @Test fun ungroupedFolderDoesNotLoseHiddenApp() {
        val v = l.veiled(hidden)
        val ungrouped = v.copy(home = listOf("a", "b", "e", "c"), folders = emptyMap())
        val r = ungrouped.unveiled(l, hidden)
        assertTrue("Z" in r.placedApps())
    }
}
