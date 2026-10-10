package org.ivanivorontsov.launcher.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable enum class TileSize(val w: Int, val h: Int, val title: String) {
    SMALL(1, 1, "маленькая"), MEDIUM(2, 2, "средняя"), WIDE(4, 2, "широкая");
    fun next(): TileSize = entries[(ordinal + 1) % entries.size]
}

@Serializable enum class TileKind { APP, CLOCK, PHOTOS, CALENDAR }

@Serializable
data class TileItem(val id: String, val kind: TileKind = TileKind.APP, val appKey: String? = null, val size: TileSize = TileSize.MEDIUM)

@Serializable
data class WidgetItem(val widgetId: Int, val heightDp: Int = 180)

@Serializable enum class GestureAction(val title: String) {
    NONE("Ничего"), DRAWER("Меню приложений"), NOTIFICATIONS("Шторка уведомлений"), SEARCH("Поиск"),
    LOCK("Заблокировать экран"), SETTINGS("Настройки лаунчера"), WIDGETS("Виджеты")
}

/** Everything the user arranged, persisted as JSON. Pure model so it is unit-testable. */
@Serializable
data class LauncherConfig(
    // Legacy 1.0 fields: only read once to build [base], then left empty.
    val home: List<String> = emptyList(),
    val dock: List<String> = emptyList(),
    val minimalFavorites: List<String> = emptyList(),
    val tiles: List<TileItem> = emptyList(),
    val widgets: List<WidgetItem> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val renamed: Map<String, String> = emptyMap(),
    val usage: Map<String, Int> = emptyMap(),
    val swipeUp: GestureAction = GestureAction.DRAWER,
    val swipeDown: GestureAction = GestureAction.NOTIFICATIONS,
    val doubleTap: GestureAction = GestureAction.LOCK,
    val iconPack: String? = null,
    val tileBackgroundUri: String? = null,
    val notificationBadges: Boolean = false,
    val autoKeyboard: Boolean = false,
    val highRefreshRate: Boolean = true,
    val seeded: Boolean = false,
    /** Shared arrangement used by every theme that has not been rearranged yet. */
    val base: ScreenLayout? = null,
    /** Per-theme arrangements, created the first time the user rearranges something in that theme. */
    val layouts: Map<String, ScreenLayout> = emptyMap(),
    val lastEdited: String? = null,
    /** Put newly installed apps on the home screen. */
    val autoAddNew: Boolean = true,
    /** "pkg#userSerial" of every launchable app seen so far; used to detect fresh installs. */
    val knownApps: Set<String> = emptySet(),
    /** Answer the system's "gesture navigation contract" so swipe-home flies into our icon. */
    val gestureContract: Boolean = true,
    /** Play the in-launcher "return into icon" animation when coming back home. */
    val returnAnimation: Boolean = true,
    /** 1.2: page-turn effect per theme id (absent = the theme's default, see [pageEffectFor]). */
    val pageEffects: Map<String, PageEffect> = emptyMap(),
) {
    fun baseLayout(): ScreenLayout = base ?: ScreenLayout(home = home, dock = dock, tiles = tiles, favorites = minimalFavorites)

    /** Moves legacy 1.0 fields into [base]. */
    fun migrated(): LauncherConfig =
        if (base != null) this
        else copy(base = baseLayout().normalized(), home = emptyList(), dock = emptyList(), tiles = emptyList(), minimalFavorites = emptyList())

    /** The arrangement shown for [themeId]: its own, else the most recently edited one, else the shared base. */
    fun layoutFor(themeId: String): ScreenLayout = layouts[themeId] ?: lastEdited?.let { layouts[it] } ?: baseLayout()

    /** What the user sees for [themeId]: [layoutFor] minus the apps veiled by «Стоп-экран». */
    fun shownLayout(themeId: String): ScreenLayout = layoutFor(themeId).veiled(LayoutVeil.keys)

    /** Edits (and forks) the arrangement of [themeId]. A null result from [f] means "not allowed" and keeps everything. */
    fun editLayout(themeId: String, f: (ScreenLayout) -> ScreenLayout?): LauncherConfig {
        val cur = layoutFor(themeId)
        val veil = LayoutVeil.keys
        val next = (if (veil.isEmpty()) f(cur) else f(cur.veiled(veil))?.unveiled(cur, veil))?.normalized() ?: return this
        if (next == cur && themeId in layouts) return this
        return copy(layouts = layouts + (themeId to next), lastEdited = themeId)
    }

    fun pageEffectFor(themeId: String, layout: HomeLayout): PageEffect =
        pageEffects[themeId] ?: if (layout == HomeLayout.GRID) PageEffect.CYLINDER else PageEffect.NONE

    fun hasOwnLayout(themeId: String): Boolean = themeId in layouts

    fun resetLayout(themeId: String): LauncherConfig =
        copy(layouts = layouts - themeId, lastEdited = if (lastEdited == themeId) null else lastEdited)

    /** Applies [f] to the shared base and every per-theme arrangement. */
    fun editAllLayouts(f: (ScreenLayout) -> ScreenLayout): LauncherConfig =
        copy(base = f(baseLayout()).normalized(), layouts = layouts.mapValues { f(it.value).normalized() })

    fun bumpUsage(key: String): LauncherConfig = copy(usage = usage + (key to ((usage[key] ?: 0) + 1).coerceAtMost(9999)))

    /** Drops keys of apps that no longer exist (package removed). */
    fun prune(existing: Set<String>): LauncherConfig = editAllLayouts { it.normalized(existing) }

    /**
     * Records the currently installed apps ([installed] = "pkg#serial" ids → app key) and, when enabled,
     * places the ones that were not known before. The first call only remembers (no flood on upgrade).
     */
    fun registerInstalled(installed: Map<String, String>, eligible: (String) -> Boolean): Pair<LauncherConfig, List<String>> {
        val ids = installed.keys
        if (knownApps.isEmpty()) return copy(knownApps = ids) to emptyList()
        val fresh = installed.filterKeys { it !in knownApps }.values.filter(eligible).filter { it !in hidden }
        var c = copy(knownApps = ids)
        if (autoAddNew && fresh.isNotEmpty()) c = c.editAllLayouts { l -> fresh.fold(l) { acc, k -> acc.withNewApp(k) } }
        return c to (if (autoAddNew) fresh else emptyList())
    }
}

object ConfigCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; coerceInputValues = true }
    fun encode(c: LauncherConfig): String = json.encodeToString(c)
    fun decode(s: String?): LauncherConfig = try { (s?.let { json.decodeFromString<LauncherConfig>(it) } ?: LauncherConfig()).migrated() } catch (_: Exception) { LauncherConfig().migrated() }
}

/** Places tiles in a grid of [columns] units, first-fit, top-to-bottom like the classic tile start screen. */
object TilePacker {
    data class Placed(val index: Int, val col: Int, val row: Int, val w: Int, val h: Int)

    fun pack(sizes: List<TileSize>, columns: Int): List<Placed> {
        val occ = ArrayList<BooleanArray>()
        fun free(c: Int, r: Int, w: Int, h: Int): Boolean {
            if (c + w > columns) return false
            for (rr in r until r + h) {
                while (occ.size <= rr) occ += BooleanArray(columns)
                for (cc in c until c + w) if (occ[rr][cc]) return false
            }
            return true
        }
        val out = ArrayList<Placed>(sizes.size)
        sizes.forEachIndexed { i, s ->
            val w = s.w.coerceAtMost(columns); val h = s.h
            var r = 0
            var placed = false
            while (!placed) {
                for (c in 0..columns - w) {
                    if (free(c, r, w, h)) {
                        for (rr in r until r + h) for (cc in c until c + w) occ[rr][cc] = true
                        out += Placed(i, c, r, w, h); placed = true; break
                    }
                }
                if (!placed) r++
            }
        }
        return out
    }

    fun rows(placed: List<Placed>): Int = placed.maxOfOrNull { it.row + it.h } ?: 0
}

/** Category buckets for the “library” drawer. */
enum class AppCategory(val title: String) {
    SUGGESTED("Часто используемые"), SOCIAL("Общение"), MEDIA("Музыка и видео"), PHOTO("Фото и камера"),
    GAMES("Игры"), FINANCE("Финансы и покупки"), MAPS("Карты и транспорт"), PRODUCTIVITY("Работа и учёба"),
    NEWS("Чтение и новости"), TOOLS("Утилиты"), SYSTEM("Система"), OTHER("Другое")
}

object Categorizer {
    // Values of android.content.pm.ApplicationInfo.CATEGORY_* (stable public constants).
    private const val GAME = 0; private const val AUDIO = 1; private const val VIDEO = 2; private const val IMAGE = 3
    private const val SOCIAL = 4; private const val NEWS = 5; private const val MAPS = 6; private const val PRODUCTIVITY = 7
    private const val ACCESSIBILITY = 8

    private val keywords: List<Pair<AppCategory, List<String>>> = listOf(
        AppCategory.SOCIAL to listOf("telegram", "whatsapp", "viber", "vk", "vkontakte", "messenger", "discord", "instagram", "facebook", "twitter", "max.", "ok.android", "odnoklassniki", "skype", "signal", "snapchat", "tiktok", "contacts", "dialer", "mms", "messaging", "zoom", "teams", "mail"),
        AppCategory.MEDIA to listOf("music", "youtube", "spotify", "player", "video", "kinopoisk", "ivi", "okko", "rutube", "podcast", "radio", "yandex.music", "zvuk", "netflix", "twitch"),
        AppCategory.PHOTO to listOf("camera", "photo", "gallery", "album", "snapseed", "lightroom", "picsart", "vsco"),
        AppCategory.FINANCE to listOf("bank", "sber", "tinkoff", "tbank", "vtb", "alfa", "gazprombank", "pay", "wallet", "ozon", "wildberries", "market", "avito", "aliexpress", "shop", "store.ru", "sbp", "crypto"),
        AppCategory.MAPS to listOf("maps", "navi", "taxi", "uber", "metro", "transport", "2gis", "dgis", "gps", "train", "rzd", "aero"),
        AppCategory.PRODUCTIVITY to listOf("office", "docs", "sheets", "notes", "calendar", "drive", "disk", "keep", "todo", "word", "excel", "pdf", "translate", "classroom", "notion", "evernote", "scanner"),
        AppCategory.NEWS to listOf("news", "reader", "book", "litres", "dzen", "zen", "rss", "habr"),
        AppCategory.TOOLS to listOf("calculator", "clock", "files", "filemanager", "weather", "compass", "flashlight", "recorder", "vpn", "browser", "chrome", "firefox", "opera", "yandex.browser", "tiho", "launcher", "keyboard"),
        AppCategory.SYSTEM to listOf("settings", "android.vending", "security", "phonemanager", "safecenter", "coloros", "realme", "oplus", "heytap", "miui", "samsung", "huawei", "google.android.gms", "themestore", "store"),
    )

    fun categorize(packageName: String, label: String, androidCategory: Int): AppCategory {
        when (androidCategory) {
            GAME -> return AppCategory.GAMES
            AUDIO, VIDEO -> return AppCategory.MEDIA
            IMAGE -> return AppCategory.PHOTO
            SOCIAL -> return AppCategory.SOCIAL
            NEWS -> return AppCategory.NEWS
            MAPS -> return AppCategory.MAPS
            PRODUCTIVITY -> return AppCategory.PRODUCTIVITY
            ACCESSIBILITY -> return AppCategory.TOOLS
        }
        val hay = packageName.lowercase() + " " + label.lowercase()
        for ((cat, words) in keywords) if (words.any { hay.contains(it) }) return cat
        if (hay.contains("game") || hay.contains("игр")) return AppCategory.GAMES
        return AppCategory.OTHER
    }
}
