package org.ivanivorontsov.launcher.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable enum class HomeLayout(val title: String) { GRID("Сетка иконок"), MINIMAL("Минимал: список"), TILES("Плитки") }
@Serializable enum class DrawerStyle(val title: String) { GRID("Сетка"), LIST("Список с алфавитом"), LIBRARY("Библиотека по категориям"), TILES_LIST("Список с буквами-плитками") }
@Serializable enum class IconShape(val title: String) { SQUIRCLE("Сквиркл"), CIRCLE("Круг"), ROUNDED("Скруглённый квадрат"), SQUARE("Квадрат"), TEARDROP("Капля"), COOKIE("Печенька"), PEBBLE("Галька"), HEXAGON("Соты"), ORIGINAL("Как у приложения") }
@Serializable enum class WallpaperStyle(val title: String) {
    SYSTEM("Обои телефона"), AURORA("Сияние"), SOLID("Сплошной цвет"), MESH("Пастельный градиент"), GLASS("Призма"),
    NOTEBOOK("Тетрадь в линейку"), PARCHMENT("Пергамент"), CRT("Фосфорный экран"), SYNTHWAVE("Неоновый город"), PIXEL("8-бит пейзаж"),
    AQUARIUM("Аквариум"), SPACE("Космос"), WATERCOLOR("Акварель")
}
@Serializable enum class DockStyle(val title: String) { GLASS("Матовое стекло"), SOLID("Плашка"), FLAT("Без фона"), NONE("Без дока") }
@Serializable enum class ClockStyle(val title: String) { BIG("Крупные часы"), CARD("Виджет-карточка"), STACKED("Часы столбиком"), TERMINAL("Терминал"), SCRIPT("Рукописные"), NONE("Без часов") }
@Serializable enum class Motion(val title: String) { SPRING("Пружина (плавно)"), SMOOTH("Мягкое затухание"), TURNSTILE("Турникет и наклон"), SNAPPY("Быстро"), PIXEL_STEP("Пиксельные шаги") }
@Serializable enum class Overlay(val title: String) { NONE("Нет"), SCANLINES("Строки ЭЛТ"), GRAIN("Зерно бумаги"), VIGNETTE("Виньетка") }
@Serializable enum class TextCase(val title: String) { AS_IS("Как есть"), LOWER("строчные"), UPPER("ПРОПИСНЫЕ") }
@Serializable enum class PageIndicator(val title: String) { DOTS("Точки"), LINE("Линия"), NONE("Нет") }
@Serializable enum class TileFill(val title: String) { ACCENT("Цвет акцента"), ICON_COLOR("Цвет иконки"), TRANSPARENT("Прозрачные (обои внутри плиток)") }

/** Complete, serializable description of a theme. Colors are ARGB longs so this stays pure Kotlin. */
@Serializable
data class ThemeSpec(
    val id: String,
    val name: String,
    val description: String = "",
    val layout: HomeLayout = HomeLayout.GRID,
    val drawer: DrawerStyle = DrawerStyle.GRID,
    val headingFont: String = "inter",
    val bodyFont: String = "inter",
    val clockFont: String = "inter",
    val clockWeight: Int = 300,
    val dynamicColor: Boolean = false,
    val dark: Boolean = true,
    val accent: Long = 0xFF7C9CFF,
    val background: Long = 0xFF101114,
    val surface: Long = 0xFF1B1D22,
    val onSurface: Long = 0xFFF2F3F5,
    val onWallpaper: Long = 0xFFFFFFFF,
    val surfaceAlpha: Float = 0.92f,
    val wallpaper: WallpaperStyle = WallpaperStyle.SYSTEM,
    val wallpaperColors: List<Long> = emptyList(),
    val wallpaperDim: Float = 0f,
    val iconShape: IconShape = IconShape.SQUIRCLE,
    val iconSizeDp: Int = 56,
    val cornerDp: Int = 24,
    val columns: Int = 4,
    val rows: Int = 6,
    val showLabels: Boolean = true,
    val labelSizeSp: Float = 12f,
    val textCase: TextCase = TextCase.AS_IS,
    val labelPrefix: String = "",
    val dockStyle: DockStyle = DockStyle.GLASS,
    val pageIndicator: PageIndicator = PageIndicator.DOTS,
    val clockStyle: ClockStyle = ClockStyle.BIG,
    val motion: Motion = Motion.SPRING,
    val blur: Boolean = true,
    val overlay: Overlay = Overlay.NONE,
    val glow: Boolean = false,
    val iconTilt: Boolean = false,
    val tileFill: TileFill = TileFill.ACCENT,
    val tileColumns: Int = 4,
    val tileGapDp: Float = 6f,
    val letterSpacing: Float = 0f,
    val version: Int = 1,
) {
    fun withCase(s: String): String = when (textCase) {
        TextCase.AS_IS -> s
        TextCase.LOWER -> s.lowercase()
        TextCase.UPPER -> s.uppercase()
    }

    /** Clamps user-editable values so a broken/old JSON can never crash layout. */
    fun sanitized(): ThemeSpec = copy(
        iconSizeDp = iconSizeDp.coerceIn(36, 80),
        cornerDp = cornerDp.coerceIn(0, 40),
        columns = columns.coerceIn(3, 6),
        rows = rows.coerceIn(3, 8),
        labelSizeSp = labelSizeSp.coerceIn(9f, 22f),
        surfaceAlpha = surfaceAlpha.coerceIn(0.3f, 1f),
        wallpaperDim = wallpaperDim.coerceIn(0f, 0.8f),
        tileColumns = if (tileColumns == 6) 6 else 4,
        tileGapDp = tileGapDp.coerceIn(0f, 16f),
        headingFont = headingFont.takeIf { it in FontIds.all } ?: "inter",
        bodyFont = bodyFont.takeIf { it in FontIds.all } ?: "inter",
        clockFont = clockFont.takeIf { it in FontIds.all } ?: "inter",
        labelPrefix = labelPrefix.take(4),
    )
}

object ThemeCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; coerceInputValues = true }
    fun encode(t: ThemeSpec): String = json.encodeToString(t)
    fun decode(s: String?): ThemeSpec? = try { s?.let { json.decodeFromString<ThemeSpec>(it).sanitized() } } catch (_: Exception) { null }
}

/** Classic flat accent palette (colors are not proprietary; names are ours). */
object AccentPalette {
    val tiles: List<Pair<String, Long>> = listOf(
        "лайм" to 0xFFA4C400, "зелёный" to 0xFF60A917, "изумруд" to 0xFF008A00, "бирюза" to 0xFF00ABA9,
        "голубой" to 0xFF1BA1E2, "кобальт" to 0xFF0050EF, "индиго" to 0xFF6A00FF, "фиолет" to 0xFFAA00FF,
        "розовый" to 0xFFF472D0, "маджента" to 0xFFD80073, "малина" to 0xFFA20025, "красный" to 0xFFE51400,
        "оранжевый" to 0xFFFA6800, "янтарь" to 0xFFF0A30A, "жёлтый" to 0xFFE3C800, "коричневый" to 0xFF825A2C,
        "олива" to 0xFF6D8764, "сталь" to 0xFF647687, "мальва" to 0xFF76608A, "серо-бурый" to 0xFF87794E,
    )
    val soft: List<Long> = listOf(0xFF7C9CFF, 0xFFFF8FA3, 0xFFFFC46B, 0xFF6EE7B7, 0xFFB794F6, 0xFF5EEAD4, 0xFFF0ABFC, 0xFF93C5FD)
}

object Presets {
    val cupertino = ThemeSpec(
        id = "cupertino", name = "Купертино",
        description = "Сетка 4×6, сквирклы, матовый док, точки страниц, библиотека приложений, пружинная анимация",
        layout = HomeLayout.GRID, drawer = DrawerStyle.LIBRARY,
        headingFont = "inter", bodyFont = "inter", clockFont = "inter", clockWeight = 300,
        dark = true, accent = 0xFF0A84FF, background = 0xFF000000, surface = 0xFF1C1C1E, onSurface = 0xFFFFFFFF,
        surfaceAlpha = 0.72f, wallpaper = WallpaperStyle.AURORA,
        wallpaperColors = listOf(0xFF1B1464, 0xFF6A2C91, 0xFFE0457B, 0xFFFF9F43, 0xFF0F3057),
        iconShape = IconShape.SQUIRCLE, iconSizeDp = 60, cornerDp = 32, columns = 4, rows = 6,
        labelSizeSp = 12f, dockStyle = DockStyle.GLASS, pageIndicator = PageIndicator.DOTS, clockStyle = ClockStyle.CARD,
        motion = Motion.SPRING, blur = true,
    )
    val tiles = ThemeSpec(
        id = "tiles", name = "Плитки",
        description = "Живые плитки 3 размеров, акцентный цвет, турникет и наклон, список с буквами-плитками",
        layout = HomeLayout.TILES, drawer = DrawerStyle.TILES_LIST,
        headingFont = "open_sans", bodyFont = "open_sans", clockFont = "open_sans", clockWeight = 300,
        dark = true, accent = 0xFF1BA1E2, background = 0xFF000000, surface = 0xFF000000, onSurface = 0xFFFFFFFF,
        surfaceAlpha = 1f, wallpaper = WallpaperStyle.SOLID, wallpaperColors = listOf(0xFF000000),
        iconShape = IconShape.SQUARE, iconSizeDp = 48, cornerDp = 0, textCase = TextCase.LOWER,
        dockStyle = DockStyle.NONE, pageIndicator = PageIndicator.NONE, clockStyle = ClockStyle.NONE,
        motion = Motion.TURNSTILE, blur = false, iconTilt = true, tileFill = TileFill.ACCENT, tileColumns = 4, tileGapDp = 5f,
    )
    val tilesGlass = tiles.copy(
        id = "tiles_glass", name = "Плитки: окна",
        description = "Обои видны только внутри плиток и плывут с параллаксом, промежутки тёмные",
        tileFill = TileFill.TRANSPARENT, wallpaper = WallpaperStyle.AURORA,
        wallpaperColors = listOf(0xFF0B3D91, 0xFF1BA1E2, 0xFFAA00FF, 0xFFF472D0, 0xFF00ABA9), accent = 0xFF1BA1E2,
    )
    val minimal = ThemeSpec(
        id = "minimal", name = "Тишина",
        description = "Чёрный AMOLED, только текст, крупные часы, список с быстрым алфавитом",
        layout = HomeLayout.MINIMAL, drawer = DrawerStyle.LIST,
        headingFont = "manrope", bodyFont = "manrope", clockFont = "manrope", clockWeight = 200,
        dark = true, accent = 0xFFFFFFFF, background = 0xFF000000, surface = 0xFF000000, onSurface = 0xFFEDEDED,
        surfaceAlpha = 0.96f, wallpaper = WallpaperStyle.SOLID, wallpaperColors = listOf(0xFF000000),
        iconShape = IconShape.CIRCLE, labelSizeSp = 26f, dockStyle = DockStyle.NONE, pageIndicator = PageIndicator.NONE,
        clockStyle = ClockStyle.BIG, motion = Motion.SMOOTH, blur = false,
    )
    val material = ThemeSpec(
        id = "material", name = "Материал",
        description = "Цвета из обоев телефона (Android 12+), круглые иконки, тональный док",
        dynamicColor = true, headingFont = "golos", bodyFont = "golos", clockFont = "golos", clockWeight = 400,
        iconShape = IconShape.CIRCLE, iconSizeDp = 54, dockStyle = DockStyle.SOLID, wallpaper = WallpaperStyle.SYSTEM,
        clockStyle = ClockStyle.STACKED, motion = Motion.SPRING, drawer = DrawerStyle.GRID, columns = 5, rows = 6,
    )
    val glass = ThemeSpec(
        id = "glass", name = "Стекло",
        description = "Призматический градиент, размытие, тонкий шрифт",
        headingFont = "inter", bodyFont = "inter", clockFont = "poiret_one", clockWeight = 400,
        accent = 0xFF9BE7FF, background = 0xFF0B1020, surface = 0xFF1A2236, onSurface = 0xFFFFFFFF, surfaceAlpha = 0.55f,
        wallpaper = WallpaperStyle.GLASS, wallpaperColors = listOf(0xFF0B1020, 0xFF3A7BD5, 0xFF00D2FF, 0xFF9D50BB, 0xFFF8CDDA),
        iconShape = IconShape.SQUIRCLE, dockStyle = DockStyle.GLASS, clockStyle = ClockStyle.BIG, motion = Motion.SPRING,
    )
    val pastel = ThemeSpec(
        id = "pastel", name = "Пастель",
        description = "Мягкие цвета, круглый шрифт, иконки-галька",
        dark = false, headingFont = "comfortaa", bodyFont = "nunito", clockFont = "comfortaa", clockWeight = 400,
        accent = 0xFFFF8FA3, background = 0xFFFFF5F7, surface = 0xFFFFFFFF, onSurface = 0xFF3D3550, onWallpaper = 0xFF3D3550,
        surfaceAlpha = 0.88f, wallpaper = WallpaperStyle.MESH,
        wallpaperColors = listOf(0xFFFFE3EC, 0xFFE0F2FF, 0xFFFFF4D6, 0xFFE9E3FF),
        iconShape = IconShape.PEBBLE, cornerDp = 28, dockStyle = DockStyle.SOLID, clockStyle = ClockStyle.STACKED, motion = Motion.SPRING,
    )
    val notebook = ThemeSpec(
        id = "notebook", name = "Тетрадь",
        description = "Бумага в линейку, рукописный шрифт Caveat, чернила",
        dark = false, headingFont = "caveat", bodyFont = "caveat", clockFont = "marck_script", clockWeight = 400,
        accent = 0xFF1E40AF, background = 0xFFFBF8EF, surface = 0xFFFFFDF6, onSurface = 0xFF1E3A8A, onWallpaper = 0xFF1E3A8A,
        surfaceAlpha = 0.97f, wallpaper = WallpaperStyle.NOTEBOOK, overlay = Overlay.GRAIN,
        iconShape = IconShape.ROUNDED, cornerDp = 10, labelSizeSp = 16f, dockStyle = DockStyle.FLAT,
        drawer = DrawerStyle.LIST, clockStyle = ClockStyle.SCRIPT, motion = Motion.SMOOTH, blur = false, iconTilt = true,
    )
    val letter = ThemeSpec(
        id = "letter", name = "Письмо",
        description = "Каллиграфия Great Vibes и Cormorant на пергаменте",
        dark = false, headingFont = "cormorant", bodyFont = "cormorant", clockFont = "great_vibes", clockWeight = 400,
        accent = 0xFF8B5E34, background = 0xFFF4ECD8, surface = 0xFFFBF5E6, onSurface = 0xFF3B2A1A, onWallpaper = 0xFF3B2A1A,
        surfaceAlpha = 0.95f, wallpaper = WallpaperStyle.PARCHMENT, overlay = Overlay.VIGNETTE,
        iconShape = IconShape.CIRCLE, labelSizeSp = 14f, dockStyle = DockStyle.FLAT, clockStyle = ClockStyle.SCRIPT,
        motion = Motion.SMOOTH, blur = false,
    )
    val handwriting = ThemeSpec(
        id = "handwriting", name = "Акварель",
        description = "Акварельные пятна, рукописный Marck Script",
        dark = false, headingFont = "marck_script", bodyFont = "neucha", clockFont = "marck_script", clockWeight = 400,
        accent = 0xFFDB2777, background = 0xFFFFFBF5, surface = 0xFFFFFFFF, onSurface = 0xFF3F3A52, onWallpaper = 0xFF3F3A52,
        surfaceAlpha = 0.9f, wallpaper = WallpaperStyle.WATERCOLOR,
        wallpaperColors = listOf(0xFFFFB4C6, 0xFFA5D8FF, 0xFFFFE08A, 0xFFB9F6CA, 0xFFD0BCFF),
        iconShape = IconShape.TEARDROP, labelSizeSp = 15f, dockStyle = DockStyle.FLAT, clockStyle = ClockStyle.SCRIPT,
        motion = Motion.SPRING,
    )
    val terminal = ThemeSpec(
        id = "terminal", name = "Терминал",
        description = "Зелёный фосфор, моноширинный шрифт, строки ЭЛТ, мигающий курсор",
        layout = HomeLayout.MINIMAL, drawer = DrawerStyle.LIST,
        headingFont = "plex_mono", bodyFont = "plex_mono", clockFont = "jetbrains_mono", clockWeight = 400,
        accent = 0xFF33FF66, background = 0xFF020A04, surface = 0xFF041208, onSurface = 0xFF33FF66, onWallpaper = 0xFF33FF66,
        surfaceAlpha = 0.97f, wallpaper = WallpaperStyle.CRT, overlay = Overlay.SCANLINES, glow = true,
        iconShape = IconShape.SQUARE, labelSizeSp = 18f, textCase = TextCase.LOWER, labelPrefix = "> ",
        dockStyle = DockStyle.NONE, pageIndicator = PageIndicator.NONE, clockStyle = ClockStyle.TERMINAL,
        motion = Motion.SNAPPY, blur = false,
    )
    val neon = ThemeSpec(
        id = "neon", name = "Неон",
        description = "Киберпанк: неоновая сетка, свечение, Unbounded и Russo One",
        headingFont = "russo_one", bodyFont = "exo2", clockFont = "unbounded", clockWeight = 400,
        accent = 0xFFFF2BD6, background = 0xFF0A0014, surface = 0xFF14002A, onSurface = 0xFFF5E9FF, onWallpaper = 0xFF7DF9FF,
        surfaceAlpha = 0.82f, wallpaper = WallpaperStyle.SYNTHWAVE,
        wallpaperColors = listOf(0xFF0A0014, 0xFF2B0057, 0xFFFF2BD6, 0xFF00F0FF, 0xFFFFB800),
        glow = true, iconShape = IconShape.HEXAGON, cornerDp = 6, dockStyle = DockStyle.GLASS, pageIndicator = PageIndicator.LINE,
        textCase = TextCase.UPPER, labelSizeSp = 10f, letterSpacing = 0.08f, clockStyle = ClockStyle.BIG, motion = Motion.SNAPPY,
    )
    val pixel = ThemeSpec(
        id = "pixel", name = "8 бит",
        description = "Пиксельный пейзаж, шрифты Press Start 2P и Tiny5, ступенчатая анимация",
        headingFont = "press_start", bodyFont = "tiny5", clockFont = "press_start", clockWeight = 400,
        accent = 0xFFFFD23F, background = 0xFF1D2B53, surface = 0xFF29366F, onSurface = 0xFFFFF1E8, onWallpaper = 0xFFFFF1E8,
        surfaceAlpha = 0.95f, wallpaper = WallpaperStyle.PIXEL, iconShape = IconShape.SQUARE, cornerDp = 0,
        labelSizeSp = 13f, dockStyle = DockStyle.SOLID, pageIndicator = PageIndicator.DOTS, clockStyle = ClockStyle.BIG,
        motion = Motion.PIXEL_STEP, blur = false,
    )
    val aquarium = ThemeSpec(
        id = "aquarium", name = "Аквариум",
        description = "Глубина, пузырьки, водоросли, мягкий Comfortaa",
        headingFont = "comfortaa", bodyFont = "comfortaa", clockFont = "comfortaa", clockWeight = 300,
        accent = 0xFF4FD1C5, background = 0xFF03283D, surface = 0xFF06415F, onSurface = 0xFFE6FFFB, onWallpaper = 0xFFE6FFFB,
        surfaceAlpha = 0.75f, wallpaper = WallpaperStyle.AQUARIUM,
        wallpaperColors = listOf(0xFF011627, 0xFF03506F, 0xFF0A8F8F, 0xFF4FD1C5),
        iconShape = IconShape.CIRCLE, dockStyle = DockStyle.GLASS, clockStyle = ClockStyle.STACKED, motion = Motion.SPRING,
    )
    val space = ThemeSpec(
        id = "space", name = "Космос",
        description = "Звёзды и туманность, шрифт Jura, иконки-капли",
        headingFont = "jura", bodyFont = "jura", clockFont = "tektur", clockWeight = 400,
        accent = 0xFFB794F6, background = 0xFF05030F, surface = 0xFF120C2A, onSurface = 0xFFEDE9FE, onWallpaper = 0xFFEDE9FE,
        surfaceAlpha = 0.78f, wallpaper = WallpaperStyle.SPACE,
        wallpaperColors = listOf(0xFF05030F, 0xFF2D1B69, 0xFFB794F6, 0xFFF472B6, 0xFF38BDF8),
        iconShape = IconShape.TEARDROP, dockStyle = DockStyle.GLASS, clockStyle = ClockStyle.BIG, motion = Motion.SPRING,
        letterSpacing = 0.04f,
    )
    val light = ThemeSpec(
        id = "light", name = "Свет",
        description = "Светлый минимализм: белый фон, тонкий Manrope",
        dark = false, headingFont = "manrope", bodyFont = "manrope", clockFont = "manrope", clockWeight = 200,
        accent = 0xFF2563EB, background = 0xFFF7F7F8, surface = 0xFFFFFFFF, onSurface = 0xFF111827, onWallpaper = 0xFF111827,
        surfaceAlpha = 0.94f, wallpaper = WallpaperStyle.SOLID, wallpaperColors = listOf(0xFFF3F4F6),
        iconShape = IconShape.ROUNDED, dockStyle = DockStyle.SOLID, clockStyle = ClockStyle.BIG, motion = Motion.SMOOTH, blur = false,
    )

    val all: List<ThemeSpec> = listOf(cupertino, tiles, tilesGlass, material, minimal, light, glass, pastel, notebook,
        handwriting, letter, terminal, neon, pixel, aquarium, space)

    fun byId(id: String?): ThemeSpec? = all.firstOrNull { it.id == id }
}
