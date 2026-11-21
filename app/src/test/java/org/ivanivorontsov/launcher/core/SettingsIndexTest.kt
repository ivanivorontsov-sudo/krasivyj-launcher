package org.ivanivorontsov.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsIndexTest {

    private val requiredScreens = setOf(
        SettingsIndex.Screens.THEMES, SettingsIndex.Screens.HOME, SettingsIndex.Screens.EDITOR,
        SettingsIndex.Screens.FONTS, SettingsIndex.Screens.GESTURES, SettingsIndex.Screens.HIDDEN,
        SettingsIndex.Screens.ICONS, SettingsIndex.Screens.LIVE, SettingsIndex.Screens.DIGITS,
        SettingsIndex.Screens.SYSTEM, SettingsIndex.Screens.SMOOTH, SettingsIndex.Screens.ABOUT,
        SettingsIndex.Screens.LICENSES, SettingsIndex.Screens.STOP,
    )

    @Test fun catalogIsPresentAndCoversEveryScreen() {
        val all = SettingsIndex.all()
        assertTrue("Settings search catalog must not be empty", all.size >= 40)
        assertEquals("duplicate ids", all.size, all.map { it.id }.toSet().size)
        val covered = SettingsIndex.screensCovered()
        val missing = requiredScreens - covered
        assertTrue("catalog missing screens: $missing", missing.isEmpty())
    }

    @Test fun playFlavorHidesRestrictedRowsButKeepsSearch() {
        val play = SettingsIndex.all(includePlayRestricted = false)
        assertTrue(play.isNotEmpty())
        assertFalse(play.any { it.playRestricted })
        assertFalse(play.any { it.screen == SettingsIndex.Screens.STOP || it.screen == SettingsIndex.Screens.DIGITS })
        assertTrue(SettingsIndex.screensCovered(includePlayRestricted = false).contains(SettingsIndex.Screens.HOME))
    }

    @Test fun searchFindsRowsAcrossScreensIncludingAi() {
        fun titles(q: String) = SettingsIndex.search(q).map { it.title }
        assertTrue("цилиндр", titles("цилиндр").any { it.contains("Перелистывание") })
        assertTrue("стопка карт", titles("стопка карт").any { it.contains("Перелистывание") })
        assertTrue("пояса", titles("пояса").any { it.contains("пояса", ignoreCase = true) || it.contains("Часы") })
        assertTrue("владивосток", titles("владивосток").any { it.contains("Часы") || it.contains("пояса") })
        assertTrue("жесты", titles("жесты").any { it.contains("Жесты") })
        assertTrue("свайп вверх", titles("свайп вверх").isNotEmpty())
        assertTrue("сетка", titles("сетка").any { it.contains("Сетка") || it.contains("стиль") })
        val ai = titles("расставить с ИИ")
        assertTrue("ИИ: $ai", ai.any { it.contains("ИИ") })
        assertTrue("нейросеть", titles("нейросеть").any { it.contains("ИИ") })
        assertTrue("вернуть раскладку", titles("вернуть раскладку").any { it.contains("Вернуть") })
        assertTrue("все приложения на главном", titles("все приложения на главном").isNotEmpty())
        assertTrue("тёмная тема", titles("тёмная тема").isNotEmpty())
        assertTrue("empty query lists nothing (root is shown instead)", SettingsIndex.search("").isEmpty())
        assertTrue("unknown stays empty", SettingsIndex.search("телепортация домой xyzzy").isEmpty())
    }

    /** Guards the feature itself: if the catalog or matcher is removed, this test fails. */
    @Test fun settingsSearchMustRemainAvailable() {
        assertTrue(SettingsIndex.all().isNotEmpty())
        assertTrue(SettingsIndex.search("тема").isNotEmpty())
        assertTrue(SettingsIndex.search("ии").isNotEmpty() || SettingsIndex.search("ИИ").isNotEmpty())
    }

    @Test fun indexContainsAllAnimationOptionLabels() {
        val labels = SettingsIndex.animationOptionLabels()
        assertTrue("animation enums must contribute labels", labels.size >= 20)
        assertTrue("Круги на воде" in labels)
        for (label in labels) {
            val hits = SettingsIndex.search(label)
            val hit = hits.firstOrNull { it.title == label }
                ?: hits.firstOrNull { it.title.contains(label, ignoreCase = true) || it.keywords.contains(label, ignoreCase = true) || it.subtitle.contains(label, ignoreCase = true) }
            assertTrue("animation option not indexed: '$label' hits=${hits.map { it.title }}", hit != null)
            val anchor = SettingsAnchors.resolve(hit!!)
            assertTrue("animation option '$label' (${hit.id}) has no scroll anchor", anchor != null)
            assertTrue("anchor screen for '$label'", anchor!!.screen == hit.screen)
        }
    }

    @Test fun searchFindsCirclesOnWaterCollapseAndAnimation() {
        fun titles(q: String) = SettingsIndex.search(q).map { it.title }
        val circles = titles("круги")
        assertTrue("круги: $circles", circles.any { it.contains("Круги на воде") })
        val collapse = titles("сворачивание")
        assertTrue("сворачивание: $collapse", collapse.isNotEmpty())
        val collapseGen = titles("сворачивания")
        assertTrue("сворачивания: $collapseGen", collapseGen.isNotEmpty())
        val anim = titles("анимация")
        assertTrue("анимация: $anim", anim.isNotEmpty())
        val rings = SettingsIndex.search("Круги на воде")
        assertTrue(rings.any { it.title == "Круги на воде" })
        val t = SettingsAnchors.resolve(rings.first { it.title == "Круги на воде" })
        assertTrue("circles must scroll to the home-anim section", t != null && t.lazyKey == "home-anim")
    }
}
