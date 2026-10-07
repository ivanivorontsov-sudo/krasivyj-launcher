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
) {
    fun isPlaced(key: String): Boolean = key in home || key in dock

    fun addToHome(key: String): LauncherConfig = if (isPlaced(key)) this else copy(home = home + key)

    fun addToDock(key: String, max: Int): LauncherConfig =
        if (key in dock || dock.size >= max) this else copy(dock = dock + key, home = home - key)

    fun pinTile(item: TileItem): LauncherConfig =
        if (item.kind == TileKind.APP && tiles.any { it.appKey == item.appKey }) this
        else if (item.kind != TileKind.APP && tiles.any { it.kind == item.kind }) this
        else copy(tiles = tiles + item)

    fun resizeTile(id: String): LauncherConfig = copy(tiles = tiles.map { if (it.id == id) it.copy(size = it.size.next()) else it })

    fun unpinTile(id: String): LauncherConfig = copy(tiles = tiles.filter { it.id != id })

    /** Splits the flat home list into pages; the first page may hold fewer items (clock/widget area). */
    fun pagesFor(firstPageCapacity: Int, pageCapacity: Int): List<List<String>> {
        val out = ArrayList<List<String>>()
        val first = home.take(firstPageCapacity.coerceAtLeast(0))
        out += first
        var rest = home.drop(first.size)
        val cap = pageCapacity.coerceAtLeast(1)
        while (rest.isNotEmpty()) { out += rest.take(cap); rest = rest.drop(cap) }
        return out
    }

    fun removeEverywhere(key: String): LauncherConfig = copy(
        home = home - key,
        dock = dock - key,
        minimalFavorites = minimalFavorites - key,
        tiles = tiles.filter { it.appKey != key },
    )

    fun move(key: String, delta: Int): LauncherConfig {
        val i = home.indexOf(key)
        if (i < 0) {
            val d = dock.indexOf(key); if (d < 0) return this
            val j = (d + delta).coerceIn(0, dock.size - 1); if (j == d) return this
            val l = dock.toMutableList(); l.removeAt(d); l.add(j, key); return copy(dock = l)
        }
        val j = (i + delta).coerceIn(0, home.size - 1)
        if (i == j) return this
        val l = home.toMutableList(); l.removeAt(i); l.add(j, key)
        return copy(home = l)
    }

    fun moveTile(from: Int, to: Int): LauncherConfig {
        if (from !in tiles.indices || to !in tiles.indices || from == to) return this
        val l = tiles.toMutableList(); val t = l.removeAt(from); l.add(to, t)
        return copy(tiles = l)
    }

    fun bumpUsage(key: String): LauncherConfig = copy(usage = usage + (key to ((usage[key] ?: 0) + 1).coerceAtMost(9999)))

    /** Drops keys of apps that no longer exist (package removed). */
    fun prune(existing: Set<String>): LauncherConfig = copy(
        home = home.filter { it in existing },
        dock = dock.filter { it in existing },
        minimalFavorites = minimalFavorites.filter { it in existing },
        tiles = tiles.filter { it.kind != TileKind.APP || it.appKey in existing },
    )
}

object ConfigCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; coerceInputValues = true }
    fun encode(c: LauncherConfig): String = json.encodeToString(c)
    fun decode(s: String?): LauncherConfig = try { s?.let { json.decodeFromString<LauncherConfig>(it) } ?: LauncherConfig() } catch (_: Exception) { LauncherConfig() }
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
