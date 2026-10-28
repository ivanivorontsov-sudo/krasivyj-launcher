package org.ivanivorontsov.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSearchTest {
    private data class W(val label: String, val app: String) { val key = WidgetSearch.key(label, app) }
    private val items = listOf(
        W("Часы", "Часы"), W("Погода", "Яндекс Погода"), W("Заметка", "Google Keep"),
        W("Календарь на месяц", "Календарь"), W("Музыка", "Spotify"),
    )
    private fun f(q: String) = WidgetSearch.filter(items, q) { it.key }.map { it.label }

    @Test fun emptyQueryKeepsAll() = assertEquals(items.size, f("  ").size)
    @Test fun byWidgetLabelCaseInsensitive() = assertEquals(listOf("Погода"), f("ПОГ"))
    @Test fun byAppName() { assertEquals(listOf("Заметка"), f("keep")); assertEquals(listOf("Погода"), f("яндекс")) }
    @Test fun middleOfWord() = assertTrue("Календарь на месяц" in f("месяц"))
    @Test fun yoInsensitive() = assertEquals(listOf("Заметка"), f("заМётка"))
    @Test fun wrongLayout() = assertTrue("Часы" in f("xfcs"))
    @Test fun noMatch() = assertTrue(f("qqqzzz").isEmpty())
}
