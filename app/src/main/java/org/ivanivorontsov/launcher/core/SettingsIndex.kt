package org.ivanivorontsov.launcher.core

/**
 * Searchable catalog of every real settings screen and row in SuperSmart.
 * Pure Kotlin so unit tests can guard that settings search never silently disappears.
 */
object SettingsIndex {

    /** One searchable row or screen. [screen] matches the settings navigator id. */
    data class Entry(
        val id: String,
        val title: String,
        val screen: String,
        val subtitle: String = "",
        val keywords: String = "",
        /** Hidden in the Play flavor (стоп-экран, цифры поверх, блокировка). */
        val playRestricted: Boolean = false,
    )

    /** Navigator ids used by SettingsActivity. Kept as strings so this stays in :core. */
    object Screens {
        const val MAIN = "MAIN"
        const val HOME = "HOME"
        const val THEMES = "THEMES"
        const val EDITOR = "EDITOR"
        const val FONTS = "FONTS"
        const val GESTURES = "GESTURES"
        const val HIDDEN = "HIDDEN"
        const val ICONS = "ICONS"
        const val LIVE = "LIVE"
        const val DIGITS = "DIGITS"
        const val SYSTEM = "SYSTEM"
        const val SMOOTH = "SMOOTH"
        const val ABOUT = "ABOUT"
        const val LICENSES = "LICENSES"
        const val STOP = "STOP"
    }

    fun all(includePlayRestricted: Boolean = true): List<Entry> =
        if (includePlayRestricted) catalog else catalog.filterNot { it.playRestricted }

    /**
     * Ranked hits for a Russian (or transliterated / wrong-layout) query.
     * Empty query returns an empty list — the settings root itself is shown instead.
     */
    fun search(query: String, includePlayRestricted: Boolean = true): List<Entry> {
        if (query.isBlank()) return emptyList()
        val items = all(includePlayRestricted)
        fun keyOf(e: Entry) = SearchKey(e.title, extra = listOf(e.subtitle, e.keywords, e.screen).filter { it.isNotBlank() }.joinToString(" "))
        val ranked = SearchEngine.search(items, query, ::keyOf)
        val words = normalize(query).split(' ').filter { it.length >= 2 }
        if (words.size < 2) return ranked
        // "вернуть раскладку" should still hit «Вернуть предыдущую раскладку».
        val seen = ranked.map { it.id }.toHashSet()
        val extra = items.filter { e ->
            e.id !in seen && words.all { w -> SearchEngine.score(keyOf(e), w) > 0 }
        }
        return ranked + extra
    }

    fun screensCovered(includePlayRestricted: Boolean = true): Set<String> =
        all(includePlayRestricted).map { it.screen }.toSet()

    private fun e(
        id: String,
        title: String,
        screen: String,
        subtitle: String = "",
        keywords: String = "",
        playRestricted: Boolean = false,
    ) = Entry(id, title, screen, subtitle, keywords, playRestricted)

    private val catalog: List<Entry> = listOf(
        // Root
        e("themes", "Все темы", Screens.THEMES, "Готовые темы с живым предпросмотром", "стекло плитки тишина материал призма пастель тетрадь письмо акварель терминал неон космос аквариум свет"),
        e("home", "Главный экран", Screens.HOME, "Перетаскивание, папки, эффекты перелистывания, анимация возврата, ИИ"),
        e("stop", "Стоп-экран", Screens.STOP, "Лимит экранного времени, расписание, запрещённые приложения, пароль", "родительский контроль", playRestricted = true),
        e("editor", "Свой стиль", Screens.EDITOR, "Шрифты, цвета, форма иконок, сетка, анимации, обои", "редактор темы"),
        e("fonts", "Шрифты", Screens.FONTS, "Шрифты с кириллицей, в том числе рукописные"),
        e("system", "Системный шрифт и обои", Screens.SYSTEM, "Что можно поменять во всём телефоне"),
        e("gestures", "Жесты", Screens.GESTURES, "Свайпы, двойное касание, блокировка, клавиатура"),
        e("live", "Живые плитки и разрешения", Screens.LIVE, "Фото, календарь, счётчики уведомлений"),
        e("digits", "Цифры поверх приложений", Screens.DIGITS, "Крупный счётчик времени поверх других приложений", playRestricted = true),
        e("hidden", "Скрытые приложения", Screens.HIDDEN, "Приложения, которые не видны в меню, но находятся поиском"),
        e("icons", "Набор иконок", Screens.ICONS, "Пакет иконок"),
        e("smooth", "Плавность и батарея", Screens.SMOOTH, "Частота экрана, фоновая работа, анимации"),
        e("about", "О приложении", Screens.ABOUT, "Версия, лицензии"),
        e("licenses", "Лицензии", Screens.LICENSES, "Шрифты и библиотеки"),

        // Home + AI
        e("all-apps-on-home", "Все приложения на главном экране", Screens.HOME, "Убрать отдельное меню приложений", "без меню все приложения на главном"),
        e("ai-arrange", "Расставить с ИИ", Screens.HOME, "Описать словами раскладку и настройки", "нейросеть искусственный интеллект поллинатионс pollinations чат расстановка папки пояса"),
        e("ai-undo", "Вернуть предыдущую раскладку", Screens.HOME, "Откатить раскладку и настройки после ИИ", "отмена undo бэкап"),
        e("ai-repair", "Показать все приложения на главном", Screens.HOME, "Дописать пропавшие приложения и включить режим всех на главном", "ремонт восстановить пропали"),
        e("sort-folders", "Разложить по папкам", Screens.HOME, "Собрать приложения в папки по категориям"),
        e("today-page", "Экран «Сегодня» слева от главного", Screens.HOME, "Виджеты на отдельной странице", "виджеты сегодня"),
        e("auto-add", "Добавлять новые приложения на главный экран", Screens.HOME),
        e("page-effects", "Перелистывание страниц", Screens.HOME, "Цилиндр, стопка карт и другие эффекты", "цилиндр стопка карт колода куб карусель аккордеон волна глубина анимация страниц"),
        e("multi-finger", "Перетаскивание двумя или тремя пальцами", Screens.HOME, "Поднять иконку без долгого нажатия", "перетащить папки"),
        e("home-anim", "Анимации «Домой» и «Назад»", Screens.HOME, "Сворачивание в иконку, эффект на значке, скорость жеста", "возврат пружина длительность"),
        e("clock-zones", "Часы: часовые пояса", Screens.HOME, "Сколько времён показывать в виджете часов", "москва владивосток пояс виджет часов стиль часов"),
        e("frame-meter", "Диагностика фризов", Screens.HOME, "Счётчик кадров и упрощённый дом"),
        e("unlock-anim", "Анимация при разблокировке", Screens.HOME, "Как появляется домашний экран после снятия блокировки"),
        e("auto-rotate", "Автоповорот лаунчера", Screens.HOME, "Поворот экрана", "ландшафт альбом планшет вертикально листать"),
        e("return-icon", "Анимация «приложение сворачивается в иконку»", Screens.HOME, "Возврат на главный экран"),

        // Editor
        e("layout-kind", "Макет", Screens.EDITOR, "Сетка иконок, минимал, плитки"),
        e("editor-fonts", "Шрифты темы", Screens.EDITOR, "Заголовки, текст и подписи, часы"),
        e("colors", "Цвета", Screens.EDITOR, "Тёмная тема, акцент, Material You", "динамический цвет"),
        e("wallpaper", "Обои", Screens.EDITOR, "Обои темы и затемнение"),
        e("icon-shape", "Иконки", Screens.EDITOR, "Форма, размер, скругление", "круглые квадратные сквиркл капля соты"),
        e("grid", "Сетка", Screens.EDITOR, "Столбцы и ряды, подписи", "колонки ряды"),
        e("tiles", "Плитки", Screens.EDITOR, "Заливка и число столбцов плиток"),
        e("motion", "Движение и эффекты", Screens.EDITOR, "Характер движения, размытие, свечение, наклон"),
        e("dock-style", "Стиль дока", Screens.EDITOR, "Матовое стекло, плашка, без дока", "док"),
        e("clock-style", "Стиль часов", Screens.EDITOR, "Крупные часы, карточка, столбиком", "виджет часов"),
        e("dynamic-color", "Цвета из обоев (Material You)", Screens.EDITOR),
        e("dark-theme", "Тёмная тема", Screens.EDITOR),
        e("blur", "Матовое размытие", Screens.EDITOR),
        e("labels", "Подписи", Screens.EDITOR),

        // Gestures
        e("swipe-up", "Свайп вверх", Screens.GESTURES, "Меню приложений, шторка, настройки"),
        e("swipe-down", "Свайп вниз", Screens.GESTURES),
        e("double-tap", "Двойное касание", Screens.GESTURES, "Блокировка, шторка, настройки"),
        e("lock-double-tap", "Блокировка двойным касанием", Screens.GESTURES, "Служба спец. возможностей", playRestricted = true),
        e("auto-keyboard", "Открывать клавиатуру в меню приложений", Screens.GESTURES),

        // Live / digits / system / smooth / about
        e("badges", "Счётчики уведомлений на плитках", Screens.LIVE),
        e("photo-tile", "Плитка «Фото»", Screens.LIVE),
        e("calendar-tile", "Плитка «Календарь»", Screens.LIVE),
        e("digit-overlay", "Показывать цифры", Screens.DIGITS, playRestricted = true),
        e("digit-mode", "Что показывать на цифрах", Screens.DIGITS, "Время в приложении, часы, экран сегодня", playRestricted = true),
        e("system-font", "Честно о системном шрифте", Screens.SYSTEM, "Системный шрифт всего телефона"),
        e("system-wallpaper", "Обои темы — во всём телефоне", Screens.SYSTEM),
        e("refresh-rate", "Максимальная частота экрана", Screens.SMOOTH, "144 Гц высокая частота"),
        e("background", "Фоновая работа", Screens.SMOOTH, "Производитель батарея оптимизация"),
        e("about-version", "Версия СуперСмарт", Screens.ABOUT),
    )
}
