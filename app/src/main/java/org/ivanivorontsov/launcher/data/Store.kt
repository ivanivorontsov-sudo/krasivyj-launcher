package org.ivanivorontsov.launcher.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ivanivorontsov.launcher.core.ConfigCodec
import org.ivanivorontsov.launcher.core.LauncherConfig
import org.ivanivorontsov.launcher.core.Presets
import org.ivanivorontsov.launcher.core.ThemeCodec
import org.ivanivorontsov.launcher.core.ThemeSpec

/** Small JSON-in-SharedPreferences store. Writes are debounced and done off the main thread. */
@OptIn(FlowPreview::class)
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("uyut", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _theme = MutableStateFlow(ThemeCodec.decode(prefs.getString(K_THEME, null)) ?: Presets.cupertino)
    val theme: StateFlow<ThemeSpec> = _theme
    private val _custom = MutableStateFlow(ThemeCodec.decode(prefs.getString(K_CUSTOM, null)))
    val custom: StateFlow<ThemeSpec?> = _custom
    private val _config = MutableStateFlow(ConfigCodec.decode(prefs.getString(K_CONFIG, null)))
    val config: StateFlow<LauncherConfig> = _config
    /** Raw pref text last applied to memory, so resume can skip a JSON decode of an unchanged theme. */
    private var seenTheme: String? = prefs.getString(K_THEME, null)
    private var seenCustom: String? = prefs.getString(K_CUSTOM, null)

    init {
        scope.launch { _config.drop(1).debounce(350).collect { prefs.edit().putString(K_CONFIG, ConfigCodec.encode(it)).apply() } }
    }

    // Persist theme edits immediately: the launcher activity may resume before a debounced
    // write runs, and must never reload an older theme after SettingsActivity closes.
    fun setTheme(t: ThemeSpec) {
        val s = t.sanitized()
        val encoded = ThemeCodec.encode(s)
        seenTheme = encoded
        prefs.edit().putString(K_THEME, encoded).apply()
        _theme.value = s
    }

    fun setCustom(t: ThemeSpec) {
        val s = t.sanitized().copy(id = "custom")
        val encoded = ThemeCodec.encode(s)
        seenTheme = encoded
        seenCustom = encoded
        prefs.edit()
            .putString(K_CUSTOM, encoded)
            .putString(K_THEME, encoded)
            .apply()
        _custom.value = s
        _theme.value = s
    }

    /**
     * Re-read theme preferences when the home activity becomes visible again.
     * Returns true when the active theme actually changed.
     *
     * The common path (coming back from an app) compares the raw pref strings and does
     * no JSON work. A pref write that this process already applied is also ignored if
     * `apply()` has not flushed yet, so resume cannot reload a stale theme.
     */
    fun refreshThemeFromPreferences(): Boolean {
        val raw = prefs.getString(K_THEME, null)
        val customRaw = prefs.getString(K_CUSTOM, null)
        if (raw == seenTheme && customRaw == seenCustom) return false
        if (seenTheme != null && ThemeCodec.encode(_theme.value) == seenTheme) return false
        val saved = ThemeCodec.decode(raw) ?: Presets.cupertino
        val custom = ThemeCodec.decode(customRaw)
        seenTheme = raw
        seenCustom = customRaw
        val changed = _theme.value != saved
        _custom.value = custom
        if (changed) _theme.value = saved
        return changed
    }

    fun update(f: (LauncherConfig) -> LauncherConfig) = _config.update(f)

    companion object {
        private const val K_THEME = "theme"; private const val K_CUSTOM = "custom"; private const val K_CONFIG = "config"
    }
}
