package org.ivanivorontsov.launcher.ai

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ivanivorontsov.launcher.core.ScreenLayout

/** One-slot undo of the home arrangement after an AI (or local) apply. */
class LayoutUndoStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ai_layout_undo", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun save(themeId: String, layout: ScreenLayout) {
        prefs.edit()
            .putString("theme", themeId)
            .putString("layout", json.encodeToString(layout))
            .apply()
    }

    fun peek(themeId: String): ScreenLayout? {
        if (prefs.getString("theme", null) != themeId) return null
        val raw = prefs.getString("layout", null) ?: return null
        return try { json.decodeFromString<ScreenLayout>(raw) } catch (_: Exception) { null }
    }

    fun consume(themeId: String): ScreenLayout? {
        val layout = peek(themeId) ?: return null
        prefs.edit().remove("theme").remove("layout").apply()
        return layout
    }

    fun has(themeId: String): Boolean = peek(themeId) != null
}
