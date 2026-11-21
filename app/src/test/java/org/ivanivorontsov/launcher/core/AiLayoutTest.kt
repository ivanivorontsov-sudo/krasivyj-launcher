package org.ivanivorontsov.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiLayoutTest {

    private val apps = listOf(
        app("phone", "Телефон", "com.android.dialer", HomeCategories.OTHER, 90),
        app("sms", "Сообщения", "com.android.mms", HomeCategories.SOCIAL, 80),
        app("chrome", "Chrome", "com.android.chrome", HomeCategories.OTHER, 70),
        app("tg", "Telegram", "org.telegram.messenger", HomeCategories.SOCIAL, 60),
        app("wa", "WhatsApp", "com.whatsapp", HomeCategories.SOCIAL, 50),
        app("vk", "VK", "com.vkontakte.android", HomeCategories.SOCIAL, 40),
        app("yt", "YouTube", "com.google.android.youtube", HomeCategories.VIDEO, 30),
        app("cam", "Камера", "com.android.camera", HomeCategories.IMAGE, 20),
        app("gal", "Галерея", "com.android.gallery3d", HomeCategories.IMAGE, 15),
        app("game1", "Башенка", "com.example.towergame", HomeCategories.GAMES, 5),
        app("game2", "Шахматы", "com.example.chess", HomeCategories.GAMES, 4),
        app("maps", "Карты", "com.google.android.apps.maps", HomeCategories.MAPS, 25),
    )
    private val current = ScreenLayout(home = listOf("tg", "yt", "maps"), dock = listOf("phone", "chrome"))

    private fun app(key: String, label: String, pkg: String, category: String, usage: Int) =
        AiLayout.CatalogApp(key, label, pkg, category, usage)

    @Test fun extractJsonFromFenceAndProse() {
        val raw = """Конечно, вот план:
```json
{"dock":["Телефон"],"home":["Telegram"]}
```
удачи!"""
        assertEquals("""{"dock":["Телефон"],"home":["Telegram"]}""", AiLayout.extractJson(raw))
        assertEquals("""{"a":1}""", AiLayout.extractJson("prefix {\"a\":1} suffix"))
        assertNull(AiLayout.extractJson("нет объекта"))
    }

    @Test fun parseDockFoldersAndPages() {
        val json = """
        {
          "dock": ["Телефон", "com.android.chrome"],
          "folders": [{"name": "Общение", "apps": ["Telegram", "WhatsApp", "VK"]}],
          "pages": [{
            "items": [
              {"type":"folder","folder":"Общение","apps":["Telegram","WhatsApp","VK"],"col":0,"row":0},
              {"type":"app","name":"YouTube","col":1,"row":0},
              {"type":"app","pkg":"com.android.camera"}
            ]
          }]
        }
        """.trimIndent()
        val plan = AiLayout.parse(json)
        assertEquals(listOf("Телефон", "com.android.chrome"), plan.dock)
        assertEquals("Общение", plan.folders.single().name)
        assertEquals(3, plan.pages.single().items.size)
    }

    @Test fun fuzzyMatchLabelPackageAndAlias() {
        assertEquals("phone", AiLayout.match("Телефон", apps)!!.app.key)
        assertEquals("tg", AiLayout.match("телеграм", apps)!!.app.key)
        assertEquals("chrome", AiLayout.match("com.android.chrome", apps)!!.app.key)
        assertEquals("sms", AiLayout.match("сообщения", apps)!!.app.key)
        assertEquals("cam", AiLayout.match("камера", apps)!!.app.key)
        assertEquals("yt", AiLayout.match("ютуб", apps)!!.app.key)
        assertNull(AiLayout.match("несуществующееприложение", apps))
    }

    @Test fun applyAndUndoRestorePreviousLayout() {
        val json = """
        {"dock":["Телефон","Сообщения","Chrome"],
         "folders":[{"name":"Общение","apps":["Telegram","WhatsApp","VK"]}],
         "pages":[{"items":[
            {"type":"folder","folder":"Общение","apps":["Telegram","WhatsApp","VK"]},
            {"type":"app","name":"YouTube"},
            {"type":"app","name":"Камера"},
            {"type":"app","name":"Галерея"}
         ]}]}
        """.trimIndent()
        val app = AiLayout.applyPlan(current, AiLayout.parse(json), apps, maxDock = 5, columns = 4, rows = 6)
        assertEquals(listOf("phone", "sms", "chrome"), app.layout.dock)
        assertTrue(app.layout.folders.values.any { it.name == "Общение" && it.apps.containsAll(listOf("tg", "wa", "vk")) })
        assertTrue(app.layout.home.any { isFolderId(it) })
        assertTrue("yt" in app.layout.home || app.layout.folderOf("yt") != null)
        assertEquals(current, app.previous)
        assertEquals(current, AiLayout.undo(app.layout, app.previous))
    }

    @Test fun widgetsAreKeptOnApply() {
        val withWidget = current.copy(
            home = listOf("widget:1", "tg"),
            widgets = mapOf("widget:1" to HomeWidget("widget:1", HomeWidgetKind.CLOCK, 0, 4, 2)),
        )
        val plan = AiLayout.parse("""{"dock":["Телефон"],"home":["Telegram","YouTube"]}""")
        val app = AiLayout.applyPlan(withWidget, plan, apps)
        assertEquals("widget:1", app.layout.home.first())
        assertTrue("tg" in app.layout.home)
        assertTrue("yt" in app.layout.home)
        assertTrue("widget:1" in app.layout.widgets)
    }

    @Test fun unmatchedNamesAreReportedAndSkipped() {
        val plan = AiLayout.parse("""{"dock":["Телефон","Неизвестное"],"home":["Telegram","Фантазия"]}""")
        val resolved = AiLayout.resolve(plan, apps)
        assertEquals(listOf("phone"), resolved.dock)
        assertTrue("tg" in resolved.home)
        assertTrue("Неизвестное" in resolved.unmatched)
        assertTrue("Фантазия" in resolved.unmatched)
    }

    @Test fun localFallbackGroupsByCategory() {
        val app = AiLayout.localFallback(apps, current, maxDock = 4, columns = 4, rows = 6, prompt = "разложи по папкам")
        assertTrue(app.layout.dock.isNotEmpty())
        assertTrue(app.layout.folders.isNotEmpty())
        val social = app.layout.folders.values.firstOrNull { it.name == HomeCategories.SOCIAL }
        assertNotNull(social)
        assertTrue(social!!.apps.size >= 2)
        assertEquals(current, app.previous)
    }

    @Test fun localFallbackReadsDockAndFolderHints() {
        val app = AiLayout.localFallback(
            apps, current, maxDock = 5, columns = 4, rows = 6,
            prompt = "На док: телефон, сообщения, Chrome. Папка Общение: Telegram, WhatsApp, VK.",
        )
        assertEquals(listOf("phone", "sms", "chrome"), app.layout.dock)
        val f = app.layout.folders.values.single { it.name == "Общение" }
        assertEquals(setOf("tg", "wa", "vk"), f.apps.toSet())
    }

    @Test fun buildPromptListsInstalledApps() {
        val p = AiLayout.buildPrompt(apps, "соцсети в папку", 4, 6, 5)
        assertTrue(p.contains("Telegram"))
        assertTrue(p.contains("org.telegram.messenger"))
        assertTrue(p.contains("соцсети в папку"))
        assertTrue(p.contains("ТОЛЬКО одним JSON"))
    }
}
