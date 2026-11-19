package org.ivanivorontsov.launcher.core

/**
 * Russian folder names for «Разложить по папкам».
 *
 * [ApplicationInfo.category](https://developer.android.com/reference/android/content/pm/ApplicationInfo#category)
 * wins when the platform knows the app (GAME, AUDIO, VIDEO, IMAGE, SOCIAL, NEWS, MAPS,
 * PRODUCTIVITY, ACCESSIBILITY). There is no public `CATEGORY_FINANCE` through API 35; if a
 * build exposes that field, pass its int as [financeCategory]. Everything else is guessed
 * from the package name and label, and what is still unknown goes to [OTHER] («Прочее»).
 */
object HomeCategories {
    const val GAMES = "Игры"
    const val AUDIO = "Музыка"
    const val VIDEO = "Видео"
    const val IMAGE = "Фото"
    const val SOCIAL = "Общение"
    const val NEWS = "Новости"
    const val MAPS = "Карты"
    const val PRODUCTIVITY = "Продуктивность"
    const val ACCESSIBILITY = "Специальные возможности"
    const val FINANCE = "Финансы"
    const val OTHER = "Прочее"

    // android.content.pm.ApplicationInfo.CATEGORY_* — stable public ints.
    private const val GAME = 0
    private const val CAT_AUDIO = 1
    private const val CAT_VIDEO = 2
    private const val CAT_IMAGE = 3
    private const val CAT_SOCIAL = 4
    private const val CAT_NEWS = 5
    private const val CAT_MAPS = 6
    private const val CAT_PRODUCTIVITY = 7
    private const val CAT_ACCESSIBILITY = 8

    private val keywords: List<Pair<String, List<String>>> = listOf(
        GAMES to listOf("game", "games", "игр"),
        SOCIAL to listOf(
            "telegram", "whatsapp", "viber", "vkontakte", "com.vk", ".vk.", "messenger", "discord",
            "instagram", "facebook", "twitter", "odnoklassniki", "skype", "signal", "snapchat",
            "tiktok", "contacts", "dialer", "mms", "messaging", "zoom", "teams", "mail",
        ),
        IMAGE to listOf("camera", "photo", "gallery", "album", "snapseed", "lightroom", "picsart", "vsco"),
        AUDIO to listOf("music", "spotify", "podcast", "radio", "yandex.music", "zvuk", "deezer", "soundcloud"),
        VIDEO to listOf("youtube", "video", "kinopoisk", "ru.ivi", "okko", "rutube", "netflix", "twitch"),
        FINANCE to listOf(
            "bank", "sber", "tinkoff", "tbank", "vtb", "alfa", "gazprombank", "wallet", "paypal",
            "ozon", "wildberries", "avito", "aliexpress", "sbp", "crypto",
        ),
        MAPS to listOf("maps", "navi", "taxi", "uber", "metro", "transport", "2gis", "dgis", "gps", "train", "rzd"),
        PRODUCTIVITY to listOf(
            "office", "docs", "sheets", "notes", "calendar", "drive", "disk", "keep", "todo",
            "word", "excel", "pdf", "translate", "classroom", "notion", "evernote", "scanner",
        ),
        NEWS to listOf("news", "reader", "book", "litres", "dzen", "zen", "rss", "habr"),
        ACCESSIBILITY to listOf("accessibility", "talkback", "voiceaccess"),
    )

    fun folderName(packageName: String, label: String, androidCategory: Int, financeCategory: Int? = null): String {
        when (androidCategory) {
            GAME -> return GAMES
            CAT_AUDIO -> return AUDIO
            CAT_VIDEO -> return VIDEO
            CAT_IMAGE -> return IMAGE
            CAT_SOCIAL -> return SOCIAL
            CAT_NEWS -> return NEWS
            CAT_MAPS -> return MAPS
            CAT_PRODUCTIVITY -> return PRODUCTIVITY
            CAT_ACCESSIBILITY -> return ACCESSIBILITY
        }
        if (financeCategory != null && androidCategory == financeCategory) return FINANCE
        val hay = packageName.lowercase() + " " + label.lowercase()
        for ((name, words) in keywords) if (words.any { hay.contains(it) }) return name
        return OTHER
    }

    /** `ApplicationInfo.CATEGORY_FINANCE` when the device/SDK actually has the field; otherwise null. */
    fun platformFinanceCategory(): Int? = try {
        Class.forName("android.content.pm.ApplicationInfo").getField("CATEGORY_FINANCE").getInt(null)
    } catch (_: Throwable) {
        null
    }
}
