package org.ivanivorontsov.launcher.core

/**
 * Scroll/highlight bindings for every [SettingsIndex] row.
 * Pure Kotlin so unit tests can prove each catalog entry has a resolvable
 * anchor on its screen (including rows inside a group that must be expanded first).
 */
object SettingsAnchors {

    const val EXTRA_FOCUS = "settings_focus_anchor"
    const val HIGHLIGHT_MS = 1500L

    data class Target(
        val id: String,
        val screen: String,
        /** Expand this group before scrolling, if the row lives inside one. */
        val group: String? = null,
        /** LazyColumn item key that must be composed so the row can be brought into view. */
        val lazyKey: String,
    ) {
        fun needsExpand(): Boolean = group != null
    }

    fun resolve(id: String, includePlayRestricted: Boolean = true): Target? =
        SettingsIndex.all(includePlayRestricted).firstOrNull { it.id == id }?.let { resolve(it) }

    fun resolve(entry: SettingsIndex.Entry): Target? {
        val b = bindings[entry.id] ?: return null
        if (b.screen != entry.screen) return null
        val order = lazyOrder()[b.screen] ?: return null
        if (b.lazyKey !in order) return null
        if (b.group != null && b.group !in groupsOf(b.screen)) return null
        return b
    }

    fun lazyOrder(includePlayRestricted: Boolean = true): Map<String, List<String>> {
        val raw = order
        if (includePlayRestricted) return raw
        val banned = playOnlyLazyKeys()
        return raw
            .filterKeys { it != SettingsIndex.Screens.STOP && it != SettingsIndex.Screens.DIGITS }
            .mapValues { (_, keys) -> keys.filter { it !in banned } }
    }

    fun groupsOf(screen: String): Set<String> = groups[screen].orEmpty()

    /** Highlight ids wired on a given lazy item or inner row. */
    fun highlightIds(anchorId: String): Set<String> {
        val hit = mutableSetOf(anchorId)
        bindings.values.forEach { b ->
            if (b.id == anchorId) hit += b.id
            // Screen-level entries share the first section's lazy key but are highlighted on that section.
            if (b.lazyKey == anchorId && b.group == anchorId) hit += b.id
        }
        return hit
    }

    private fun bind(
        id: String,
        screen: String,
        group: String? = null,
        lazyKey: String? = null,
    ) = Target(id, screen, group, lazyKey ?: group ?: id)

    private val S = SettingsIndex.Screens

    private val bindings: Map<String, Target> = listOf(
        // Screen-level entries (search opens the screen and scrolls to its first real row)
        bind("themes", S.THEMES),
        bind("home", S.HOME, group = "home-apps", lazyKey = "home-apps"),
        bind("stop", S.STOP),
        bind("editor", S.EDITOR, lazyKey = "editor-preview"),
        bind("fonts", S.FONTS),
        bind("system", S.SYSTEM, group = "system-font", lazyKey = "system-font"),
        bind("gestures", S.GESTURES, group = "swipe-up", lazyKey = "swipe-up"),
        bind("live", S.LIVE, group = "badges", lazyKey = "badges"),
        bind("digits", S.DIGITS, group = "digit-overlay", lazyKey = "digit-overlay"),
        bind("hidden", S.HIDDEN),
        bind("icons", S.ICONS),
        bind("smooth", S.SMOOTH, group = "refresh-rate", lazyKey = "refresh-rate"),
        bind("about", S.ABOUT, lazyKey = "about-version"),
        bind("licenses", S.LICENSES),

        // Home — first section is an expandable group with several rows
        bind("all-apps-on-home", S.HOME, group = "home-apps"),
        bind("ai-arrange", S.HOME, group = "home-apps"),
        bind("ai-undo", S.HOME, group = "home-apps"),
        bind("ai-repair", S.HOME, group = "home-apps"),
        bind("sort-folders", S.HOME, group = "home-apps"),
        bind("today-page", S.HOME, group = "today-page"),
        bind("auto-add", S.HOME, group = "auto-add"),
        bind("page-effects", S.HOME, group = "page-effects"),
        bind("multi-finger", S.HOME, group = "multi-finger"),
        bind("home-anim", S.HOME, group = "home-anim"),
        bind("clock-zones", S.HOME, group = "clock-zones"),
        bind("frame-meter", S.HOME, group = "frame-meter"),
        bind("unlock-anim", S.HOME, group = "unlock-anim"),
        bind("auto-rotate", S.HOME, group = "auto-rotate"),
        bind("return-icon", S.HOME, group = "return-icon"),

        // Editor — some rows live inside a parent section that may be collapsed
        bind("layout-kind", S.EDITOR, group = "layout-kind"),
        bind("editor-fonts", S.EDITOR, group = "editor-fonts"),
        bind("colors", S.EDITOR, group = "colors"),
        bind("wallpaper", S.EDITOR, group = "wallpaper"),
        bind("icon-shape", S.EDITOR, group = "icon-shape"),
        bind("grid", S.EDITOR, group = "grid"),
        bind("tiles", S.EDITOR, group = "tiles"),
        bind("motion", S.EDITOR, group = "motion"),
        bind("dock-style", S.EDITOR, group = "grid"),
        bind("clock-style", S.EDITOR, group = "grid"),
        bind("dynamic-color", S.EDITOR, group = "colors"),
        bind("dark-theme", S.EDITOR, group = "colors"),
        bind("blur", S.EDITOR, group = "wallpaper"),
        bind("labels", S.EDITOR, group = "icon-shape"),

        // Gestures
        bind("swipe-up", S.GESTURES, group = "swipe-up"),
        bind("swipe-down", S.GESTURES, group = "swipe-down"),
        bind("double-tap", S.GESTURES, group = "double-tap"),
        bind("lock-double-tap", S.GESTURES, group = "lock-double-tap"),
        bind("auto-keyboard", S.GESTURES, group = "auto-keyboard"),

        // Live / digits / system / smooth / about
        bind("badges", S.LIVE, group = "badges"),
        bind("photo-tile", S.LIVE, group = "photo-tile"),
        bind("calendar-tile", S.LIVE, group = "calendar-tile"),
        bind("digit-overlay", S.DIGITS, group = "digit-overlay"),
        bind("digit-mode", S.DIGITS, group = "digit-mode"),
        bind("system-font", S.SYSTEM, group = "system-font"),
        bind("system-wallpaper", S.SYSTEM, group = "system-wallpaper"),
        bind("refresh-rate", S.SMOOTH, group = "refresh-rate"),
        bind("background", S.SMOOTH, group = "background"),
        bind("about-version", S.ABOUT, lazyKey = "about-version"),
    ).associateBy { it.id }

    /**
     * LazyColumn item keys in visual order. Must stay in sync with SettingsActivity
     * (and the stop-screen header). Extra keys are structural items with no catalog row.
     */
    private val order: Map<String, List<String>> = mapOf(
        S.THEMES to listOf("themes"),
        S.HOME to listOf(
            "home-apps", "today-page", "auto-add", "page-effects", "multi-finger",
            "home-anim", "clock-zones", "frame-meter", "unlock-anim", "auto-rotate", "return-icon",
        ),
        S.EDITOR to listOf(
            "editor-preview", "layout-kind", "editor-fonts", "colors", "wallpaper",
            "icon-shape", "grid", "tiles", "motion",
        ),
        S.FONTS to listOf("fonts"),
        S.GESTURES to listOf("swipe-up", "swipe-down", "double-tap", "lock-double-tap", "auto-keyboard"),
        S.HIDDEN to listOf("hidden"),
        S.ICONS to listOf("icons"),
        S.LIVE to listOf("badges", "photo-tile", "calendar-tile", "live-tile-bg"),
        S.DIGITS to listOf("digit-overlay", "digit-mode", "digit-look"),
        S.SYSTEM to listOf("system-font", "system-wallpaper"),
        S.SMOOTH to listOf("refresh-rate", "smooth-anim", "background"),
        S.ABOUT to listOf("about-version", "about-ofl"),
        S.LICENSES to listOf("licenses"),
        S.STOP to listOf("stop"),
    )

    private val groups: Map<String, Set<String>> = mapOf(
        S.HOME to setOf(
            "home-apps", "today-page", "auto-add", "page-effects", "multi-finger",
            "home-anim", "clock-zones", "frame-meter", "unlock-anim", "auto-rotate", "return-icon",
        ),
        S.EDITOR to setOf(
            "layout-kind", "editor-fonts", "colors", "wallpaper", "icon-shape", "grid", "tiles", "motion",
        ),
        S.GESTURES to setOf("swipe-up", "swipe-down", "double-tap", "lock-double-tap", "auto-keyboard"),
        S.LIVE to setOf("badges", "photo-tile", "calendar-tile"),
        S.DIGITS to setOf("digit-overlay", "digit-mode"),
        S.SYSTEM to setOf("system-font", "system-wallpaper"),
        S.SMOOTH to setOf("refresh-rate", "background"),
    )

    private fun playOnlyLazyKeys(): Set<String> {
        val restricted = SettingsIndex.all(true).filter { it.playRestricted }
        return restricted.map { bindings[it.id]?.lazyKey ?: it.id }.toSet().filter { key ->
            SettingsIndex.all(true).none { !it.playRestricted && (bindings[it.id]?.lazyKey ?: it.id) == key }
        }.toSet()
    }
}
