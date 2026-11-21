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


    private val userPrompt = "На первое место банки, на второе заказ еды и доставку, на третье все остальное"

    private val manyApps: List<AiLayout.CatalogApp> = listOf(
        app("sber", "Сбер", "ru.sberbankmobile", HomeCategories.FINANCE, 99),
        app("tbank", "Т-Банк", "com.idamob.tinkoff.android", HomeCategories.FINANCE, 90),
        app("alfa", "Альфа-Банк", "ru.alfabank.mobile.android", HomeCategories.FINANCE, 80),
        app("vtb", "ВТБ", "ru.vtb24.mobilebanking.android", HomeCategories.FINANCE, 70),
        app("ozonbank", "Ozon Банк", "ru.ozon.fintech.bank", HomeCategories.FINANCE, 60),
        app("eda", "Яндекс Еда", "ru.yandex.eda", "Еда", 85),
        app("dc", "Delivery Club", "com.deliveryclub.android", "Еда", 50),
        app("samokat", "Самокат", "ru.samokat.client", "Еда", 55),
        app("kuper", "Купер", "ru.kuper.app", "Еда", 40),
        app("lavka", "Лавка", "ru.yandex.lavka", "Еда", 45),
        app("dodo", "Додо Пицца", "ru.dodopizza.android", "Еда", 30),
        app("kfc", "KFC", "com.kfc.mobile", "Еда", 20),
        app("bk", "Burger King", "ru.burgerking", "Еда", 18),
        app("vit", "Вкусно и точка", "ru.vkusnoitochka.app", "Еда", 22),
        app("tg", "Telegram", "org.telegram.messenger", HomeCategories.SOCIAL, 88),
        app("wa", "WhatsApp", "com.whatsapp", HomeCategories.SOCIAL, 70),
        app("chrome", "Chrome", "com.android.chrome", HomeCategories.OTHER, 66),
        app("yt", "YouTube", "com.google.android.youtube", HomeCategories.VIDEO, 64),
        app("maps", "Карты", "ru.yandex.yandexmaps", HomeCategories.MAPS, 40),
        app("cam", "Камера", "com.android.camera", HomeCategories.IMAGE, 20),
        app("gal", "Галерея", "com.android.gallery3d", HomeCategories.IMAGE, 15),
        app("phone", "Телефон", "com.android.dialer", HomeCategories.OTHER, 50),
        app("sms", "Сообщения", "com.android.mms", HomeCategories.SOCIAL, 48),
        app("settings", "Настройки", "com.android.settings", HomeCategories.OTHER, 10),
        app("clock", "Часы", "com.android.deskclock", HomeCategories.OTHER, 8),
        app("cal", "Календарь", "com.android.calendar", HomeCategories.OTHER, 7),
        app("ozon", "Ozon", "ru.ozon.app.android", HomeCategories.OTHER, 75),
        app("wb", "Wildberries", "com.wildberries.ru", HomeCategories.OTHER, 65),
        app("vk", "VK", "com.vkontakte.android", HomeCategories.SOCIAL, 44),
        app("ig", "Instagram", "com.instagram.android", HomeCategories.SOCIAL, 33),
        app("spot", "Spotify", "com.spotify.music", HomeCategories.AUDIO, 12),
        app("game", "Башенка", "com.example.towergame", HomeCategories.GAMES, 5),
        app("google", "Google", "com.google.android.googlequicksearchbox", HomeCategories.OTHER, 25),
        app("ostrich", "Страус", "org.example.ostrich", HomeCategories.OTHER, 3),
    )

    private fun keysOf(kind: AiLayout.AppKind) = manyApps.filter { AiLayout.classify(it) == kind }.map { it.key }

    @Test fun classifyBanksAndFoodNotMarketplaces() {
        assertEquals(AiLayout.AppKind.BANK, AiLayout.classify(manyApps.first { it.key == "sber" }))
        assertEquals(AiLayout.AppKind.BANK, AiLayout.classify(manyApps.first { it.key == "tbank" }))
        assertEquals(AiLayout.AppKind.BANK, AiLayout.classify(manyApps.first { it.key == "ozonbank" }))
        assertEquals(AiLayout.AppKind.FOOD_DELIVERY, AiLayout.classify(manyApps.first { it.key == "eda" }))
        assertEquals(AiLayout.AppKind.FOOD_DELIVERY, AiLayout.classify(manyApps.first { it.key == "samokat" }))
        assertEquals(AiLayout.AppKind.FOOD_DELIVERY, AiLayout.classify(manyApps.first { it.key == "vit" }))
        assertEquals(AiLayout.AppKind.OTHER, AiLayout.classify(manyApps.first { it.key == "ozon" }))
        assertEquals(AiLayout.AppKind.OTHER, AiLayout.classify(manyApps.first { it.key == "wb" }))
        assertEquals(AiLayout.AppKind.OTHER, AiLayout.classify(manyApps.first { it.key == "tg" }))
        assertTrue(keysOf(AiLayout.AppKind.BANK).containsAll(listOf("sber", "tbank", "alfa", "vtb", "ozonbank")))
        assertTrue(keysOf(AiLayout.AppKind.FOOD_DELIVERY).containsAll(listOf("eda", "dc", "samokat", "kuper", "lavka", "dodo", "kfc", "bk", "vit")))
        assertEquals(34, manyApps.size)
    }

    @Test fun orderPromptDoesNotAskForFolders() {
        assertFalse(AiLayout.wantsFolders(userPrompt))
        assertFalse(AiLayout.wantsDockChange(userPrompt))
        assertTrue(AiLayout.wantsArrangement(userPrompt))
        val groups = AiLayout.parseOrderGroups(userPrompt)!!
        assertEquals(3, groups.size)
        assertEquals(AiLayout.AppKind.BANK, groups[0].kind)
        assertEquals(AiLayout.AppKind.FOOD_DELIVERY, groups[1].kind)
        assertEquals(AiLayout.AppKind.OTHER, groups[2].kind)
        assertFalse(AiLayout.wantsFolders("сначала банки, потом еду, остальное"))
        assertTrue(AiLayout.wantsFolders("разложи по папкам"))
        assertTrue(AiLayout.wantsFolders("Папка Общение: Telegram, WhatsApp"))
    }

    @Test fun exactUserPromptArrangesFlatGroupsAndKeepsDock() {
        val widget = HomeWidget(CLOCK_WIDGET, HomeWidgetKind.CLOCK, spanX = 4, spanY = 2)
        val current = ScreenLayout(
            home = listOf(CLOCK_WIDGET, "tg", "ozon", "sber"),
            dock = listOf("sber", "ostrich", "google"),
            widgets = mapOf(CLOCK_WIDGET to widget),
            clockPlaced = true,
        )
        val result = AiLayout.localFallback(manyApps, current, maxDock = 5, columns = 4, rows = 6, prompt = userPrompt)
        assertTrue(result.layout.folders.isEmpty())
        assertEquals(listOf("sber", "ostrich", "google"), result.layout.dock)
        assertTrue(CLOCK_WIDGET in result.layout.home)
        assertEquals(widget, result.layout.widgets[CLOCK_WIDGET])

        val icons = result.layout.home.filter { !isWidgetId(it) && !isFolderId(it) }
        val banks = keysOf(AiLayout.AppKind.BANK).filter { it !in result.layout.dock }
        val food = keysOf(AiLayout.AppKind.FOOD_DELIVERY).filter { it !in result.layout.dock }
        val rest = manyApps.map { it.key }.filter { it !in result.layout.dock && it !in banks && it !in food }
        val bankEnd = banks.size
        val foodEnd = bankEnd + food.size
        assertEquals(banks.toSet(), icons.take(bankEnd).toSet())
        assertEquals(food.toSet(), icons.drop(bankEnd).take(food.size).toSet())
        assertEquals(rest.toSet(), icons.drop(foodEnd).toSet())

        val placed = result.layout.placedApps().toSet()
        assertEquals(manyApps.map { it.key }.toSet(), placed)
        assertEquals(manyApps.size, placed.size)
        assertEquals(current, result.previous)
    }

    @Test fun modelFolderPlanIsFlattenedAndCompletedForOrderPrompt() {
        val current = ScreenLayout(home = listOf("tg"), dock = listOf("sber", "ostrich", "google"))
        val json = """
        {"dock":["Сбер","Страус","Google"],
         "folders":[{"name":"Банки","apps":["Сбер","Т-Банк","Альфа-Банк","ВТБ"]}],
         "pages":[{"items":[{"type":"folder","folder":"Банки","apps":["Сбер","Т-Банк","Альфа-Банк","ВТБ"]}]}]}
        """.trimIndent()
        val result = AiLayout.applyPlan(current, AiLayout.parse(json), manyApps, 5, 4, 6, userPrompt)
        assertTrue(result.layout.folders.isEmpty())
        assertEquals(listOf("sber", "ostrich", "google"), result.layout.dock)
        val placed = result.layout.placedApps().toSet()
        assertEquals(manyApps.map { it.key }.toSet(), placed)
        val icons = result.layout.home.filter { !isWidgetId(it) }
        val firstNonDockBank = icons.first { AiLayout.classify(manyApps.first { a -> a.key == it }) == AiLayout.AppKind.BANK }
        val lastBankIdx = icons.indexOfLast { AiLayout.classify(manyApps.first { a -> a.key == it }) == AiLayout.AppKind.BANK }
        val firstFoodIdx = icons.indexOfFirst { AiLayout.classify(manyApps.first { a -> a.key == it }) == AiLayout.AppKind.FOOD_DELIVERY }
        assertTrue(firstFoodIdx > lastBankIdx)
        assertTrue(icons.contains("eda") && icons.contains("tg") && icons.contains("ozon"))
        assertFalse("sber" in icons) // stays in the preserved dock
    }

    @Test fun applyPlanWithoutPromptStillAllowsFoldersFromJson() {
        val json = """{"folders":[{"name":"Общение","apps":["Telegram","WhatsApp","VK"]}],
            "pages":[{"items":[{"type":"folder","folder":"Общение","apps":["Telegram","WhatsApp","VK"]}]}]}"""
        val result = AiLayout.applyPlan(current, AiLayout.parse(json), apps, 5, 4, 6)
        assertTrue(result.layout.folders.isNotEmpty())
        assertEquals("Общение", result.layout.folders.values.single().name)
    }

    @Test fun readdMissingAppendsAbsentApps() {
        val layout = ScreenLayout(home = listOf("sber"), dock = listOf("google"))
        val fixed = AiLayout.readdMissing(layout, manyApps)
        val placed = fixed.placedApps().toSet()
        assertEquals(manyApps.map { it.key }.toSet(), placed)
        assertEquals(listOf("google"), fixed.dock)
    }

    @Test fun enableAllAppsOnHomeSetsFlagAndPlacesEverything() {
        val cfg = LauncherConfig(allAppsOnHome = false, layouts = mapOf("cupertino" to ScreenLayout(home = listOf("tg"))))
        val next = AiLayout.enableAllAppsOnHome(cfg, "cupertino", manyApps.map { it.key })
        assertTrue(next.allAppsOnHome)
        assertEquals(manyApps.map { it.key }.toSet(), next.layoutFor("cupertino").placedApps().toSet())
    }

    @Test fun buildPromptForbidsFoldersUnlessAsked() {
        val no = AiLayout.buildPrompt(apps, userPrompt, 4, 6, 5)
        assertTrue(no.contains("Папки НЕ создавать") || no.contains("НЕ создавать"))
        assertTrue(no.contains("unsupported"))
        assertTrue(no.contains("clockZones"))
        val yes = AiLayout.buildPrompt(apps, "разложи по папкам", 4, 6, 5)
        assertTrue(yes.contains("можно создавать folders") || yes.contains("Папки"))
    }
}
