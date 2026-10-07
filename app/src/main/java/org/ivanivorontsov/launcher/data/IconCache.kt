package org.ivanivorontsov.launcher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ivanivorontsov.launcher.core.IconShape
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Renders icons once, with the theme shape baked in, and caches them (LRU by bytes).
 * Drawing a pre-shaped bitmap is far cheaper than clipping every frame — the main cause of
 * app-drawer jank reported by other launchers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IconCache(private val context: Context) {
    private val maxBytes = (Runtime.getRuntime().maxMemory() / 7).toInt().coerceAtMost(96 * 1024 * 1024)
    private val cache = object : LruCache<String, ImageBitmap>(maxBytes) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val colors = ConcurrentHashMap<String, Int>()
    val dispatcher = Dispatchers.IO.limitedParallelism(4)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    val packs = IconPacks(context)
    @Volatile var iconPack: String? = null
        set(v) { if (field != v) { field = v; cache.evictAll() } }

    data class Req(val app: AppInfo, val shape: IconShape, val px: Int, val mono: Boolean = false)

    fun keyOf(r: Req) = "${r.app.key}|${r.shape}|${r.px}|${r.mono}|$iconPack"
    fun peek(r: Req): ImageBitmap? = cache.get(keyOf(r))
    fun color(appKey: String): Int? = colors[appKey]
    /** Called only under real memory pressure: icons are cheap to bring back from the disk cache. */
    fun trim() = cache.trimToSize(maxBytes / 2)

    // ---- disk layer: shaped icons survive process death, so a cold home screen is instant ----
    private val dir = File(context.cacheDir, "icons").apply { mkdirs() }
    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
    private fun diskPrefix(r: Req) = safe(r.app.pkg) + "__" + Integer.toHexString(keyOf(r).hashCode()) + "__"
    private fun diskFile(r: Req) = File(dir, diskPrefix(r) + java.lang.Long.toHexString(r.app.stamp) + ".png")

    private fun fromDisk(r: Req): ImageBitmap? = try {
        val f = diskFile(r)
        if (!f.exists()) null else android.graphics.BitmapFactory.decodeFile(f.path)?.let { b ->
            if (!colors.containsKey(r.app.key) && !r.mono) colors.putIfAbsent(r.app.key, dominant(b))
            b.asImageBitmap()
        }
    } catch (_: Throwable) { null }

    private fun toDisk(r: Req, img: ImageBitmap) {
        try {
            val prefix = diskPrefix(r)
            dir.listFiles { _, n -> n.startsWith(prefix) }?.forEach { it.delete() }
            val f = diskFile(r); val tmp = File(f.path + ".tmp")
            tmp.outputStream().use { img.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            tmp.renameTo(f)
        } catch (_: Throwable) {}
    }

    /** Removes cached files of an uninstalled package. */
    fun forgetPackage(pkg: String) {
        scope.launch { val p = safe(pkg) + "__"; dir.listFiles { _, n -> n.startsWith(p) }?.forEach { it.delete() } }
    }

    private fun obtain(r: Req): ImageBitmap? {
        val k = keyOf(r)
        cache.get(k)?.let { return it }
        fromDisk(r)?.let { cache.put(k, it); return it }
        return render(r)?.also { cache.put(k, it); toDisk(r, it) }
    }

    suspend fun load(r: Req): ImageBitmap? {
        cache.get(keyOf(r))?.let { return it }
        return withContext(dispatcher) { obtain(r) }
    }

    /** Warm the cache in the background so the drawer never waits for icons (placed apps first). */
    fun prefetch(apps: List<AppInfo>, shape: IconShape, px: Int) {
        scope.launch { for (a in apps) obtain(Req(a, shape, px)) }
    }

    private fun density(): Int = context.resources.displayMetrics.densityDpi.coerceAtLeast(320).let { if (it < 480) 480 else it }

    private fun sourceDrawable(app: AppInfo): Drawable? {
        iconPack?.let { pack -> packs.drawable(pack, app.pkg, app.cls, density())?.let { return it } }
        return try {
            val acts = launcherApps.getActivityList(app.pkg, app.user)
            val a = acts.firstOrNull { it.componentName.className == app.cls } ?: acts.firstOrNull()
            a?.getIcon(density())
        } catch (_: Exception) { null }
    }

    private fun render(r: Req): ImageBitmap? {
        val d = sourceDrawable(r.app) ?: context.packageManager.defaultActivityIcon
        return try {
            if (r.mono) renderMono(d, r.px)?.asImageBitmap() ?: renderShaped(r.app, d, IconShape.ORIGINAL, r.px).asImageBitmap()
            else renderShaped(r.app, d, r.shape, r.px).asImageBitmap()
        } catch (_: Throwable) { null }
    }

    private fun renderShaped(app: AppInfo, d: Drawable, shape: IconShape, px: Int): Bitmap {
        val content = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val cc = Canvas(content)
        val path = ShapePath.of(shape, px.toFloat())
        if (d is AdaptiveIconDrawable) {
            // Layers are 108dp with a 72dp safe zone: draw them 1.5× and centered so the mask shows the safe zone.
            val inset = px / 4
            d.background?.let { it.setBounds(-inset, -inset, px + inset, px + inset); it.draw(cc) }
            d.foreground?.let { it.setBounds(-inset, -inset, px + inset, px + inset); it.draw(cc) }
            if (path == null) {
                // “Original” shape: let the system mask decide.
                cc.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                d.setBounds(0, 0, px, px); d.draw(cc)
            }
        } else {
            if (path != null) {
                cc.drawColor(Color.WHITE)
                val pad = (px * 0.17f).toInt(); d.setBounds(pad, pad, px - pad, px - pad)
            } else d.setBounds(0, 0, px, px)
            d.draw(cc)
        }
        colors.putIfAbsent(app.key, dominant(content))
        var out = content
        if (path != null) {
            out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            Canvas(out).drawPath(path, paint)
            content.recycle()
        }
        if (app.isWork) {
            val badged = context.packageManager.getUserBadgedIcon(BitmapDrawable(context.resources, out), app.user)
            val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            badged.setBounds(0, 0, px, px); badged.draw(Canvas(b)); out = b
        }
        return out
    }

    /** White glyph from the adaptive-icon monochrome layer (Android 13+), used by flat tiles. */
    private fun renderMono(d: Drawable, px: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < 33 || d !is AdaptiveIconDrawable) return null
        val m = d.monochrome ?: return null
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val inset = px / 4
        m.mutate().setTint(Color.WHITE)
        m.setBounds(-inset, -inset, px + inset, px + inset); m.draw(Canvas(b))
        return b
    }

    private fun dominant(b: Bitmap): Int {
        var r = 0L; var g = 0L; var bl = 0L; var n = 0L
        val step = (b.width / 12).coerceAtLeast(1)
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) {
                val c = b.getPixel(x, y)
                val a = Color.alpha(c)
                if (a > 200) {
                    val cr = Color.red(c); val cg = Color.green(c); val cb = Color.blue(c)
                    val sat = maxOf(cr, cg, cb) - minOf(cr, cg, cb)
                    val w = if (sat > 40) 3 else 1
                    r += cr * w; g += cg * w; bl += cb * w; n += w
                }
                x += step
            }
            y += step
        }
        if (n == 0L) return Color.DKGRAY
        val hsv = FloatArray(3)
        Color.colorToHSV(Color.rgb((r / n).toInt(), (g / n).toInt(), (bl / n).toInt()), hsv)
        hsv[1] = (hsv[1] * 1.15f).coerceAtMost(1f); hsv[2] = hsv[2].coerceIn(0.35f, 0.72f)
        return Color.HSVToColor(hsv)
    }

    fun shortcutIcon(info: ShortcutInfo, px: Int): ImageBitmap? = try {
        val d = launcherApps.getShortcutIconDrawable(info, density()) ?: null
        d?.let {
            val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            it.setBounds(0, 0, px, px); it.draw(Canvas(b)); b.asImageBitmap()
        }
    } catch (_: Exception) { null }

    fun clear() = cache.evictAll()
}
