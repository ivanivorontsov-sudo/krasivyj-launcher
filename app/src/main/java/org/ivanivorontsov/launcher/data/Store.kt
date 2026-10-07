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

    init {
        scope.launch { _config.drop(1).debounce(350).collect { prefs.edit().putString(K_CONFIG, ConfigCodec.encode(it)).apply() } }
        scope.launch { _theme.drop(1).debounce(200).collect { prefs.edit().putString(K_THEME, ThemeCodec.encode(it)).apply() } }
        scope.launch { _custom.drop(1).debounce(200).collect { c -> prefs.edit().putString(K_CUSTOM, c?.let { ThemeCodec.encode(it) }).apply() } }
    }

    fun setTheme(t: ThemeSpec) { _theme.value = t.sanitized() }
    fun setCustom(t: ThemeSpec) { val s = t.sanitized().copy(id = "custom"); _custom.value = s; _theme.value = s }
    fun update(f: (LauncherConfig) -> LauncherConfig) = _config.update(f)

    companion object {
        private const val K_THEME = "theme"; private const val K_CUSTOM = "custom"; private const val K_CONFIG = "config"
    }
}
