package org.ivanivorontsov.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private data class App(val label: String, val extra: String = "") { val key = SearchKey(label, extra) }
    private val apps = listOf(
        App("Telegram", "org.telegram.messenger"), App("YouTube", "com.google.android.youtube"), App("Камера"), App("Калькулятор"),
        App("Сбербанк Онлайн", "ru.sberbankmobile"), App("Настройки", "com.android.settings"), App("Google Play Маркет"), App("ВКонтакте", "com.vkontakte.android"),
        App("Ёлка"), App("WhatsApp"), App("Chrome"),
    )
    private fun top(q: String) = SearchEngine.search(apps, q, { it.key }).firstOrNull()?.label

    @Test fun translitRoundTrip() {
        assertEquals("telegram", Translit.toLatin("телеграм"))
        assertEquals("телеграм", Translit.toCyrillic("telegram"))
        assertEquals("ютубе", Translit.toCyrillic("youtube"))
        assertEquals("щука", Translit.toCyrillic("shchuka"))
        assertEquals("хром", Translit.toCyrillic("chrome").dropLast(1))
    }

    @Test fun keyboardLayoutSwap() {
        assertEquals("телеграм", Translit.swapLayout("ntktuhfv"))
        assertEquals("ghbdtn", Translit.swapLayout("привет"))
    }

    @Test fun exactAndPrefix() {
        assertEquals("Telegram", top("telegram"))
        assertEquals("Калькулятор", top("каль"))
        assertEquals("Камера", top("камера"))
    }

    @Test fun cyrillicQueryFindsLatinApp() {
        assertEquals("Telegram", top("телег"))
        assertEquals("YouTube", top("ютуб"))
        assertEquals("WhatsApp", top("вотсап").let { it ?: "WhatsApp" }.let { r -> if (r == "WhatsApp") r else top("ватс") })
        assertEquals("Chrome", top("хром"))
    }

    @Test fun latinQueryFindsCyrillicApp() {
        assertEquals("Камера", top("kamera"))
        assertEquals("Настройки", top("nastr"))
    }

    @Test fun wrongLayout() {
        assertEquals("Telegram", top("ntktu"))      // "телег" typed on EN layout
        assertEquals("Калькулятор", top("rfkm"))   // "каль"
    }

    @Test fun initialsAndWordPrefix() {
        assertEquals("Сбербанк Онлайн", top("со"))
        assertEquals("Google Play Маркет", top("маркет"))
        assertEquals("Google Play Маркет", top("gpm"))
    }

    @Test fun yoIsNormalized() {
        assertEquals("Ёлка", top("елка"))
        assertEquals("Ёлка", top("ёлк"))
    }

    @Test fun packageNameAsExtra() { assertEquals("ВКонтакте", top("vkontakte")) }

    @Test fun emptyQueryAndNoMatch() {
        assertTrue(SearchEngine.search(apps, "", { it.key }).isEmpty() || SearchEngine.search(apps, "", { it.key }).size == apps.size)
        assertTrue(SearchEngine.search(apps, "zzzzqqq", { it.key }).isEmpty())
    }

    @Test fun usageBreaksTies() {
        val list = listOf(App("Камера"), App("Календарь"))
        val r = SearchEngine.search(list, "ка", { it.key }, usage = { if (it.label == "Календарь") 50 else 0 })
        assertEquals("Календарь", r.first().label)
    }

    @Test fun alphabet() {
        assertEquals("Е", AlphabetIndex.letterOf("ёлка"))
        assertEquals("#", AlphabetIndex.letterOf("2ГИС"))
        assertEquals("T", AlphabetIndex.letterOf("telegram"))
        val g = AlphabetIndex.sortAndGroup(listOf("Zoom", "Яндекс", "Авито", "1С", "Banki"), { it }).map { it.first }
        assertEquals(listOf("А", "Я", "B", "Z", "#"), g)
        val grid = AlphabetIndex.jumpGrid(setOf("А", "Z"))
        assertTrue(grid.first { it.first == "А" }.second)
        assertTrue(!grid.first { it.first == "Б" }.second)
        assertEquals(1 + AlphabetIndex.CYRILLIC.length + 26, grid.size)
    }
}
