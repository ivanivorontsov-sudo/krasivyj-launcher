package org.ivanivorontsov.launcher.ui

import android.app.WallpaperManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.ivanivorontsov.launcher.core.ThemeSpec
import kotlin.math.max

/**
 * When the theme's drawn wallpaper changes, drop the in-memory bitmap and set the system
 * wallpaper off the main thread. The first theme seen after process start is left alone:
 * opening the launcher must not rewrite a wallpaper the user already has.
 */
object WallpaperApplier {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    @Volatile private var seen: String? = null

    fun onTheme(context: Context, spec: ThemeSpec) {
        val key = "${spec.id}|${spec.wallpaper}|${spec.wallpaperColors}|${spec.wallpaperDim}|${spec.background}|${spec.accent}"
        if (key == seen) return
        val first = seen == null
        seen = key
        if (first) return
        bumpWallpaperEpoch()
        job?.cancel()
        val app = context.applicationContext
        job = scope.launch {
            runCatching {
                app.cacheDir.listFiles { _, name -> name.startsWith("wall_") }?.forEach { it.delete() }
            }
            if (!Wallpapers.hasGenerated(spec)) return@launch
            delay(400)
            val dm = app.resources.displayMetrics
            var w = dm.widthPixels.coerceAtLeast(1)
            var h = dm.heightPixels.coerceAtLeast(1)
            val longSide = max(w, h)
            if (longSide > 2400) {
                val s = 2400f / longSide
                w = (w * s).toInt().coerceAtLeast(1)
                h = (h * s).toInt().coerceAtLeast(1)
            }
            val bmp = Wallpapers.render(spec, w, h)
            try {
                WallpaperManager.getInstance(app).setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM)
            } catch (_: Exception) {}
        }
    }
}
