package org.ivanivorontsov.launcher.ai

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ivanivorontsov.launcher.core.ConfigCodec
import org.ivanivorontsov.launcher.core.LauncherConfig
import org.ivanivorontsov.launcher.core.ScreenLayout
import org.ivanivorontsov.launcher.core.ThemeCodec
import org.ivanivorontsov.launcher.core.ThemeSpec
import org.ivanivorontsov.launcher.data.Store

/**
 * One-slot undo of an AI apply: layout, settings and theme.
 * Still reads the 1.4.9 format (theme id + layout only) so a backup taken before this
 * release can restore the previous arrangement.
 */
class LayoutUndoStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ai_layout_undo", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    data class Snapshot(
        val themeId: String,
        val layout: ScreenLayout,
        val allAppsOnHome: Boolean? = null,
        val configJson: String? = null,
        val themeJson: String? = null,
    ) {
        val isLegacy: Boolean get() = configJson == null
    }

    fun save(themeId: String, layout: ScreenLayout, config: LauncherConfig? = null, theme: ThemeSpec? = null) {
        val ed = prefs.edit()
            .putString("theme", themeId)
            .putString("layout", json.encodeToString(layout))
        if (config != null) {
            ed.putBoolean("allAppsOnHome", config.allAppsOnHome)
            ed.putString("config", ConfigCodec.encode(config))
        } else {
            ed.remove("allAppsOnHome").remove("config")
        }
        if (theme != null) ed.putString("themeSpec", ThemeCodec.encode(theme))
        else ed.remove("themeSpec")
        ed.apply()
    }

    fun peekAny(): Snapshot? {
        val themeId = prefs.getString("theme", null) ?: return null
        val raw = prefs.getString("layout", null) ?: return null
        val layout = try { json.decodeFromString<ScreenLayout>(raw) } catch (_: Exception) { return null }
        val allApps = if (prefs.contains("allAppsOnHome")) prefs.getBoolean("allAppsOnHome", false) else null
        return Snapshot(
            themeId = themeId,
            layout = layout,
            allAppsOnHome = allApps,
            configJson = prefs.getString("config", null),
            themeJson = prefs.getString("themeSpec", null),
        )
    }

    fun peek(themeId: String): ScreenLayout? = peekAny()?.takeIf { it.themeId == themeId }?.layout

    fun peekSnapshot(themeId: String): Snapshot? = peekAny()?.takeIf { it.themeId == themeId } ?: peekAny()

    fun consume(themeId: String): ScreenLayout? = consumeSnapshot(themeId)?.layout

    fun consumeSnapshot(themeId: String? = null): Snapshot? {
        val snap = if (themeId != null) peekSnapshot(themeId) else peekAny()
        if (snap != null) clear()
        return snap
    }

    fun has(themeId: String): Boolean = peekAny() != null

    fun hasAny(): Boolean = peekAny() != null

    fun clear() {
        prefs.edit()
            .remove("theme").remove("layout")
            .remove("allAppsOnHome").remove("config").remove("themeSpec")
            .apply()
    }

    /** Restores layout + settings + theme. A 1.4.9 backup restores only the layout. */
    fun restore(store: Store, themeId: String): Boolean {
        val snap = consumeSnapshot(themeId) ?: return false
        if (snap.configJson != null) {
            val cfg = ConfigCodec.decode(snap.configJson)
            store.update { cfg }
        } else {
            store.update { cur ->
                var next = cur.copy(layouts = cur.layouts + (snap.themeId to snap.layout), lastEdited = snap.themeId)
                if (snap.allAppsOnHome != null) next = next.copy(allAppsOnHome = snap.allAppsOnHome)
                next
            }
        }
        snap.themeJson?.let { ThemeCodec.decode(it)?.let { t -> store.setTheme(t) } }
        return true
    }
}
