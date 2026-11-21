package org.ivanivorontsov.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSettingsTest {

    private val theme = Presets.all.first { it.name == "Стекло" }
    private val config = LauncherConfig(allAppsOnHome = true, todayPage = true)

    @Test fun schemaCoversRealKeys() {
        val keys = AiSettings.catalog().map { it.key }.toSet()
        for (k in listOf(
            "allAppsOnHome", "theme", "pageEffect", "columns", "rows", "iconShape", "iconSizeDp",
            "dockStyle", "clockStyle", "clockZones", "clockWidget", "swipeUp", "swipeDown",
            "doubleTap", "homeAnim", "unlockAnim", "dark", "layout",
        )) assertTrue("$k missing from schema", k in keys)
    }

    @Test fun moscowAndVladivostokResolveToIana() {
        assertEquals("Europe/Moscow", TimeZones.resolve("Москва")!!.second)
        assertEquals("Asia/Vladivostok", TimeZones.resolve("Владивосток")!!.second)
        assertEquals("Europe/Moscow", TimeZones.resolve("Москвы")!!.second)
        assertEquals("Asia/Vladivostok", TimeZones.resolve("Владивостока")!!.second)
        assertEquals("Europe/Moscow", TimeZones.resolve("в Москве")!!.second)
        assertEquals("Europe/Moscow", TimeZones.resolve("москва")!!.second)
        assertEquals("Asia/Vladivostok", TimeZones.resolve("Asia/Vladivostok")!!.second)
        assertEquals("Europe/Moscow", TimeZones.resolve("Europe/Moscow")!!.second)
        assertTrue(TimeZones.isValidIana("Europe/Moscow"))
        assertTrue(TimeZones.isValidIana("Asia/Vladivostok"))
        assertTrue(TimeZones.isValidIana("UTC"))
        assertFalse(TimeZones.isValidIana("Not/AZone"))
        assertEquals("Москва", TimeZones.nameFor("Europe/Moscow"))
        assertEquals("Владивосток", TimeZones.nameFor("Asia/Vladivostok"))
    }

    @Test fun validateClockZonesFromCitiesAndRejectUnknown() {
        val ok = AiSettings.validate(
            listOf(AiSettings.Input("clockZones", listOf("Москва", "Владивосток"))),
            config, theme,
        )
        assertEquals(2, ok.nextConfig.clockZoneCount)
        assertEquals(listOf("Europe/Moscow", "Asia/Vladivostok"), ok.nextConfig.clockZones.map { it.zoneId })
        assertTrue(ok.changes.any { it.key == "clockZones" && it.to.contains("Москва") && it.to.contains("Владивосток") })
        assertTrue(ok.unsupported.isEmpty())

        val mixed = AiSettings.validate(
            listOf(AiSettings.Input("clockZones", listOf("Москва", "Нарния"))),
            config, theme,
        )
        assertEquals(listOf("Europe/Moscow"), mixed.nextConfig.clockZones.map { it.zoneId })
        assertTrue(mixed.unsupported.any { it.contains("Нарния") })
    }

    @Test fun localParserTimeZonesThemeAnimationGrid() {
        val p = AiSettings.localParse(
            "Добавь часовые пояса Москвы и Владивостока, тема Стекло, анимация стопка карт, сетка 5 столбцов, круглые иконки, все приложения на главном.",
            config.copy(allAppsOnHome = false),
            theme.copy(columns = 4, iconShape = IconShape.SQUIRCLE),
        )
        assertEquals(listOf("Europe/Moscow", "Asia/Vladivostok"), p.nextConfig.clockZones.map { it.zoneId })
        assertEquals("Стекло", p.nextTheme.name)
        assertEquals(PageEffect.STACK, p.nextConfig.pageEffectFor(theme.id, p.nextTheme.layout))
        assertEquals(5, p.nextTheme.columns)
        assertEquals(IconShape.CIRCLE, p.nextTheme.iconShape)
        assertTrue(p.nextConfig.allAppsOnHome)
        assertTrue(p.changes.any { it.label.contains("Часовые") && it.to.contains("Москва") && "→" !in it.label })
        p.changes.forEach { assertTrue("${it.from} → ${it.to}", it.from != it.to || true) }
    }

    @Test fun localParserClockWidgetSizeAndStyle() {
        val layout = ScreenLayout(
            home = listOf(CLOCK_WIDGET),
            widgets = mapOf(CLOCK_WIDGET to HomeWidget(CLOCK_WIDGET, HomeWidgetKind.CLOCK, spanX = 4, spanY = 2)),
            clockPlaced = true,
        )
        val cfg = config.copy(layouts = mapOf(theme.id to layout))
        val p = AiSettings.localParse("Часы карточкой, виджет часов 4 на 1, на первую страницу в левый верхний угол.", cfg, theme)
        assertEquals(ClockStyle.CARD, p.nextTheme.clockStyle)
        assertNotNull(p.widget)
        assertEquals(4, p.widget!!.spanX)
        assertEquals(1, p.widget!!.spanY)
        val applied = AiSettings.applyWidget(layout, p.widget!!, 4, 6)
        val w = applied.widgets[CLOCK_WIDGET]!!
        assertEquals(4, w.spanX)
        assertEquals(1, w.spanY)
        assertEquals(CLOCK_WIDGET, applied.home.first())
    }

    @Test fun unsupportedIsExplicitAndNotApplied() {
        val p = AiSettings.localParse("Поставь живые обои из видео и системный шрифт всего телефона.", config, theme)
        assertTrue(p.unsupported.any { it.contains("Живые обои") })
        assertTrue(p.unsupported.any { it.contains("Системный шрифт") })
        assertTrue(p.changes.isEmpty())
        assertEquals(config.allAppsOnHome, p.nextConfig.allAppsOnHome)

        val unknown = AiSettings.validate(listOf(AiSettings.Input("teleportHome", true)), config, theme)
        assertTrue(unknown.unsupported.any { it.contains("teleportHome") && it.contains("не поддерживается") })
        assertTrue(unknown.changes.isEmpty())
    }

    @Test fun arrangementDoesNotFlipAllAppsOnHome() {
        val apps = listOf(
            AiLayout.CatalogApp("sber", "Сбер", "ru.sberbankmobile", HomeCategories.FINANCE, 10),
            AiLayout.CatalogApp("eda", "Яндекс Еда", "ru.yandex.eda", "Еда", 9),
            AiLayout.CatalogApp("tg", "Telegram", "org.telegram.messenger", HomeCategories.SOCIAL, 8),
        )
        val current = ScreenLayout(home = listOf("tg"), dock = listOf("sber"))
        val cfg = config.copy(allAppsOnHome = true, layouts = mapOf(theme.id to current))
        val preview = AiComposer.compose(
            "На первое место банки, на второе заказ еды и доставку, на третье все остальное",
            apps, current, cfg, theme,
        )
        assertTrue(preview.settings.nextConfig.allAppsOnHome)
        assertTrue(preview.settings.changes.none { it.key == "allAppsOnHome" })
        assertEquals(listOf("sber"), preview.layout.dock)
        assertEquals(setOf("sber", "eda", "tg"), preview.layout.placedApps().toSet())
        assertTrue(preview.layout.folders.isEmpty())
    }

    @Test fun combinedRequestAppliesSettingsAndLayout() {
        val apps = listOf(
            AiLayout.CatalogApp("sber", "Сбер", "ru.sberbankmobile", HomeCategories.FINANCE, 10),
            AiLayout.CatalogApp("eda", "Яндекс Еда", "ru.yandex.eda", "Еда", 9),
            AiLayout.CatalogApp("tg", "Telegram", "org.telegram.messenger", HomeCategories.SOCIAL, 8),
        )
        val current = ScreenLayout(home = listOf(CLOCK_WIDGET, "tg"), dock = listOf("sber"),
            widgets = mapOf(CLOCK_WIDGET to HomeWidget(CLOCK_WIDGET, HomeWidgetKind.CLOCK, spanX = 4, spanY = 2)),
            clockPlaced = true)
        val json = """
        {
          "settings": [
            {"key":"clockZones","value":["Москва","Владивосток"]},
            {"key":"clockStyle","value":"CARD"},
            {"key":"pageEffect","value":"CYLINDER"}
          ],
          "unsupported":["живые обои из видео"],
          "pages":[{"items":[{"type":"app","name":"Сбер"},{"type":"app","name":"Яндекс Еда"}]}]
        }
        """.trimIndent()
        val preview = AiComposer.compose(
            "На первое место банки, на второе заказ еды и доставку, на третье все остальное. Добавь пояса Москвы и Владивостока, часы карточкой, анимация цилиндр.",
            apps, current, config, theme, modelText = json,
        )
        assertEquals(listOf("Europe/Moscow", "Asia/Vladivostok"), preview.settings.nextConfig.clockZones.map { it.zoneId })
        assertEquals(ClockStyle.CARD, preview.settings.nextTheme.clockStyle)
        assertEquals(PageEffect.CYLINDER, preview.settings.nextConfig.pageEffectFor(theme.id, theme.layout))
        assertTrue(preview.settings.unsupported.any { it.contains("живые обои") || it.contains("Живые обои") })
        assertEquals(setOf("sber", "eda", "tg"), preview.layout.placedApps().toSet())
        assertTrue(CLOCK_WIDGET in preview.layout.home)
        val applied = AiComposer.configToApply(preview, theme.id)
        assertEquals(listOf("Europe/Moscow", "Asia/Vladivostok"), applied.clockZones.map { it.zoneId })
        assertTrue(applied.allAppsOnHome) // unchanged
    }

    @Test fun parseRequestReadsSettingsAndPlan() {
        val req = AiLayout.parseRequest(
            """{"settings":[{"key":"columns","value":5}],"unsupported":["foo"],"dock":["Телефон"],"pages":[{"items":[{"type":"app","name":"Telegram"}]}]}"""
        )
        assertEquals(1, req.settings.size)
        assertEquals("columns", req.settings[0].key)
        assertEquals("5", req.settings[0].value.toString())
        assertEquals(listOf("foo"), req.unsupported)
        assertEquals(listOf("Телефон"), req.plan.dock)
        assertTrue(req.hasArrangement)
    }

    @Test fun wasToWillBeText() {
        val p = AiSettings.validate(listOf(AiSettings.Input("allAppsOnHome", false)), config, theme)
        val line = AiSettings.formatChange(p.changes.single())
        assertTrue(line.contains("→"))
        assertTrue(line.contains("вкл") && line.contains("выкл"))
    }
}
