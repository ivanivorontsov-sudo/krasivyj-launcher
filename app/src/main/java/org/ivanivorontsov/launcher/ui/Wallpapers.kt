package org.ivanivorontsov.launcher.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import org.ivanivorontsov.launcher.core.ThemeSpec
import org.ivanivorontsov.launcher.core.WallpaperStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Original wallpapers drawn in code (no image assets → tiny APK). Rendered once per theme/size
 * on a background thread; a heavily downscaled copy doubles as the “frosted glass” source.
 */
object Wallpapers {
    fun hasGenerated(spec: ThemeSpec) = spec.wallpaper != WallpaperStyle.SYSTEM

    fun render(spec: ThemeSpec, w: Int, h: Int, seed: Int = spec.id.hashCode()): Bitmap {
        val bmp = Bitmap.createBitmap(max(w, 1), max(h, 1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cols = spec.wallpaperColors.map { it.toInt() }.ifEmpty { listOf(spec.background.toInt(), spec.accent.toInt()) }
        val rnd = Random(seed)
        val W = w.toFloat(); val H = h.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (spec.wallpaper) {
            WallpaperStyle.SYSTEM, WallpaperStyle.SOLID -> c.drawColor(cols[0])
            WallpaperStyle.AURORA -> {
                c.drawColor(darken(cols[0], 0.55f))
                blobs(c, W, H, cols.drop(1).ifEmpty { cols }, rnd, 5, 0.75f, 0.85f)
                p.shader = LinearGradient(0f, 0f, 0f, H, intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(90, 0, 0, 0)), null, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, W, H, p)
            }
            WallpaperStyle.MESH -> { c.drawColor(cols[0]); blobs(c, W, H, cols, rnd, 6, 0.8f, 0.9f) }
            WallpaperStyle.GLASS -> {
                p.shader = LinearGradient(0f, 0f, W, H, cols.toIntArray(), null, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, W, H, p)
                p.shader = null
                repeat(7) {
                    val path = Path(); val cx = rnd.nextFloat() * W; val cy = rnd.nextFloat() * H; val r = W * (0.3f + rnd.nextFloat() * 0.5f)
                    val a0 = rnd.nextFloat() * 6.28f
                    for (k in 0 until 3) { val a = a0 + k * 2.094f; val x = cx + r * cos(a); val y = cy + r * sin(a); if (k == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                    path.close(); p.color = Color.argb(28 + rnd.nextInt(30), 255, 255, 255); c.drawPath(path, p)
                }
            }
            WallpaperStyle.NOTEBOOK -> {
                c.drawColor(spec.background.toInt())
                val step = W / 13f
                p.color = Color.argb(70, 70, 120, 200); p.strokeWidth = max(1.5f, W / 600f)
                var y = step * 3
                while (y < H) { c.drawLine(0f, y, W, y, p); y += step }
                p.color = Color.argb(110, 220, 60, 60); c.drawLine(step * 1.6f, 0f, step * 1.6f, H, p)
                grain(c, W, H, rnd, 18)
            }
            WallpaperStyle.PARCHMENT -> {
                p.shader = RadialGradient(W / 2, H * 0.45f, max(W, H) * 0.8f, intArrayOf(0xFFF8F0DC.toInt(), 0xFFE9D8B4.toInt(), 0xFFC9A974.toInt()), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, W, H, p); p.shader = null
                repeat(40) { p.color = Color.argb(10 + rnd.nextInt(14), 120, 80, 30); c.drawCircle(rnd.nextFloat() * W, rnd.nextFloat() * H, W * (0.02f + rnd.nextFloat() * 0.12f), p) }
                grain(c, W, H, rnd, 22)
            }
            WallpaperStyle.CRT -> {
                p.shader = RadialGradient(W / 2, H * 0.4f, max(W, H) * 0.75f, intArrayOf(0xFF06240E.toInt(), 0xFF021006.toInt(), 0xFF000300.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, W, H, p)
            }
            WallpaperStyle.SYNTHWAVE -> synthwave(c, W, H, cols, p)
            WallpaperStyle.PIXEL -> pixel(c, w, h, rnd)
            WallpaperStyle.AQUARIUM -> aquarium(c, W, H, cols, rnd, p)
            WallpaperStyle.SPACE -> space(c, W, H, cols, rnd, p)
            WallpaperStyle.WATERCOLOR -> {
                c.drawColor(spec.background.toInt())
                repeat(9) {
                    val col = cols[rnd.nextInt(cols.size)]; val cx = rnd.nextFloat() * W; val cy = rnd.nextFloat() * H; val r = W * (0.18f + rnd.nextFloat() * 0.3f)
                    repeat(14) { k ->
                        p.color = Color.argb(14, Color.red(col), Color.green(col), Color.blue(col))
                        c.drawCircle(cx + (rnd.nextFloat() - 0.5f) * r * 0.5f, cy + (rnd.nextFloat() - 0.5f) * r * 0.5f, r * (0.55f + 0.04f * k), p)
                    }
                }
                grain(c, W, H, rnd, 16)
            }
        }
        if (spec.wallpaperDim > 0f) c.drawColor(Color.argb((spec.wallpaperDim * 255).toInt(), 0, 0, 0))
        return bmp
    }

    /** Cheap, good-looking blur: downscale in two steps, bilinear upscale happens when drawn. */
    fun frosted(src: Bitmap): Bitmap {
        val a = Bitmap.createScaledBitmap(src, max(1, src.width / 4), max(1, src.height / 4), true)
        val b = Bitmap.createScaledBitmap(a, max(1, src.width / 16), max(1, src.height / 16), true)
        val c = Bitmap.createScaledBitmap(b, max(1, src.width / 8), max(1, src.height / 8), true)
        a.recycle(); b.recycle(); return c
    }

    private fun darken(c: Int, f: Float) = Color.rgb((Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())

    private fun blobs(c: Canvas, W: Float, H: Float, cols: List<Int>, rnd: Random, n: Int, rMin: Float, alpha: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(n) { i ->
            val col = cols[i % cols.size]
            val cx = W * (0.1f + rnd.nextFloat() * 0.8f); val cy = H * (0.08f + rnd.nextFloat() * 0.84f)
            val r = W * (rMin + rnd.nextFloat() * 0.6f)
            val a = (alpha * 255).toInt()
            p.shader = RadialGradient(cx, cy, r, intArrayOf(Color.argb(a, Color.red(col), Color.green(col), Color.blue(col)), Color.argb(0, Color.red(col), Color.green(col), Color.blue(col))), null, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, p)
        }
    }

    private fun grain(c: Canvas, W: Float, H: Float, rnd: Random, alpha: Int) {
        val p = Paint()
        val n = (W * H / 900f).toInt().coerceAtMost(6000)
        repeat(n) { p.color = Color.argb(rnd.nextInt(alpha), 60, 40, 20); val x = rnd.nextFloat() * W; val y = rnd.nextFloat() * H; c.drawRect(x, y, x + 2f, y + 2f, p) }
    }

    private fun synthwave(c: Canvas, W: Float, H: Float, cols: List<Int>, p: Paint) {
        val horizon = H * 0.58f
        p.shader = LinearGradient(0f, 0f, 0f, horizon, intArrayOf(cols[0], cols.getOrElse(1) { cols[0] }, cols.getOrElse(2) { cols[0] }), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, horizon, p)
        // Sun with horizontal cut stripes.
        val r = W * 0.3f; val cx = W / 2; val cy = horizon - r * 0.35f
        p.shader = LinearGradient(0f, cy - r, 0f, cy + r, intArrayOf(cols.getOrElse(4) { Color.YELLOW }, cols.getOrElse(2) { Color.MAGENTA }), null, Shader.TileMode.CLAMP)
        c.save(); c.clipRect(0f, 0f, W, horizon); c.drawCircle(cx, cy, r, p); p.shader = null
        p.color = cols.getOrElse(1) { Color.BLACK }
        var y = cy + r * 0.05f; var gap = r * 0.04f
        while (y < cy + r) { c.drawRect(cx - r, y, cx + r, y + gap, p); y += gap * 3.2f; gap *= 1.25f }
        c.restore()
        // Mountains.
        val m = Path(); m.moveTo(0f, horizon)
        val peaks = floatArrayOf(0.12f, 0.22f, 0.3f, 0.18f, 0.08f)
        for (i in peaks.indices) m.lineTo(W * (i + 0.5f) / peaks.size, horizon - H * peaks[i] * 0.45f).also { m.lineTo(W * (i + 1f) / peaks.size, horizon) }
        m.close(); p.color = Color.argb(255, 20, 0, 40); c.drawPath(m, p)
        // Floor grid in perspective.
        p.color = Color.rgb(10, 0, 20); c.drawRect(0f, horizon, W, H, p)
        p.color = cols.getOrElse(3) { Color.CYAN }; p.strokeWidth = max(2f, W / 400f)
        p.maskFilter = BlurMaskFilter(W / 300f, BlurMaskFilter.Blur.SOLID)
        for (i in -12..12) c.drawLine(W / 2 + i * W * 0.02f, horizon, W / 2 + i * W * 0.32f, H, p)
        var t = 0.02f
        while (t < 1f) { val yy = horizon + (H - horizon) * t * t; c.drawLine(0f, yy, W, yy, p); t += 0.07f }
        p.maskFilter = null
        p.color = cols.getOrElse(2) { Color.MAGENTA }; p.strokeWidth = max(3f, W / 300f); c.drawLine(0f, horizon, W, horizon, p)
    }

    private fun pixel(c: Canvas, w: Int, h: Int, rnd: Random) {
        val s = max(6, w / 90)
        val lw = w / s + 1; val lh = h / s + 1
        val small = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888)
        val sky = intArrayOf(0xFF1D2B53.toInt(), 0xFF29366F.toInt(), 0xFF3B5DC9.toInt(), 0xFF41A6F6.toInt(), 0xFF73EFF7.toInt())
        for (y in 0 until lh) {
            val band = (y * sky.size * 0.62f / lh).toInt().coerceIn(0, sky.size - 1)
            for (x in 0 until lw) small.setPixel(x, y, sky[band])
        }
        repeat(lw * lh / 70) { small.setPixel(rnd.nextInt(lw), rnd.nextInt(lh / 3), Color.WHITE) }
        val sunX = lw * 3 / 4; val sunY = lh / 5; val sr = lw / 9
        for (y in -sr..sr) for (x in -sr..sr) if (x * x + y * y <= sr * sr) small.setPixel((sunX + x).coerceIn(0, lw - 1), (sunY + y).coerceIn(0, lh - 1), 0xFFFFCD75.toInt())
        fun ridge(base: Float, amp: Float, color: Int, freq: Float, ph: Float) {
            for (x in 0 until lw) {
                val top = (lh * base - amp * lh * (0.5f + 0.5f * sin(x * freq + ph)) - (if ((x / 3) % 2 == 0) 1 else 0)).toInt().coerceIn(0, lh - 1)
                for (y in top until lh) small.setPixel(x, y, color)
            }
        }
        ridge(0.62f, 0.12f, 0xFF5D275D.toInt(), 0.11f, 1f)
        ridge(0.72f, 0.08f, 0xFF333C57.toInt(), 0.17f, 2f)
        ridge(0.82f, 0.05f, 0xFF257179.toInt(), 0.23f, 0.3f)
        ridge(0.9f, 0.02f, 0xFF38B764.toInt(), 0.5f, 0f)
        repeat(3) { k ->
            val cx = rnd.nextInt(lw); val cy = lh / 8 + rnd.nextInt(lh / 5)
            for (x in -6..6) for (y in -2..1) if (kotlin.math.abs(x) + kotlin.math.abs(y) * 3 < 8) small.setPixel((cx + x + k).coerceIn(0, lw - 1), (cy + y).coerceIn(0, lh - 1), 0xFFF4F4F4.toInt())
        }
        val p = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
        c.drawBitmap(small, null, android.graphics.Rect(0, 0, lw * s, lh * s), p)
        small.recycle()
    }

    private fun aquarium(c: Canvas, W: Float, H: Float, cols: List<Int>, rnd: Random, p: Paint) {
        p.shader = LinearGradient(0f, 0f, 0f, H, intArrayOf(cols.getOrElse(3) { Color.CYAN }, cols.getOrElse(2) { Color.BLUE }, cols.getOrElse(1) { Color.BLUE }, cols[0]), floatArrayOf(0f, 0.25f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, H, p); p.shader = null
        repeat(6) {
            val x = rnd.nextFloat() * W; val path = Path()
            path.moveTo(x - W * 0.04f, 0f); path.lineTo(x + W * 0.04f, 0f); path.lineTo(x + W * 0.25f, H * 0.75f); path.lineTo(x - W * 0.05f, H * 0.75f); path.close()
            p.shader = LinearGradient(0f, 0f, 0f, H * 0.75f, Color.argb(45, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
            c.drawPath(path, p)
        }
        p.shader = null
        p.color = Color.rgb(194, 178, 128); val sand = Path(); sand.moveTo(0f, H)
        var x = 0f; while (x <= W) { sand.lineTo(x, H * 0.93f + sin(x / W * 9f) * H * 0.012f); x += W / 40 }
        sand.lineTo(W, H); sand.close(); c.drawPath(sand, p)
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        repeat(9) {
            val bx = rnd.nextFloat() * W; val hh = H * (0.15f + rnd.nextFloat() * 0.25f)
            p.strokeWidth = W * (0.012f + rnd.nextFloat() * 0.01f); p.color = Color.argb(220, 20, 120 + rnd.nextInt(80), 70)
            val sw = Path(); sw.moveTo(bx, H * 0.95f)
            sw.cubicTo(bx - W * 0.05f, H * 0.95f - hh * 0.33f, bx + W * 0.05f, H * 0.95f - hh * 0.66f, bx + (rnd.nextFloat() - 0.5f) * W * 0.06f, H * 0.95f - hh)
            c.drawPath(sw, p)
        }
        p.strokeWidth = max(1.5f, W / 500f)
        repeat(45) {
            val r = W * (0.006f + rnd.nextFloat() * 0.02f)
            p.color = Color.argb(120, 255, 255, 255); c.drawCircle(rnd.nextFloat() * W, rnd.nextFloat() * H * 0.9f, r, p)
        }
        p.style = Paint.Style.FILL
    }

    private fun space(c: Canvas, W: Float, H: Float, cols: List<Int>, rnd: Random, p: Paint) {
        c.drawColor(cols[0])
        blobs(c, W, H, cols.drop(1), rnd, 5, 0.35f, 0.45f)
        repeat(((W * H) / 2500f).toInt().coerceAtMost(1200)) {
            val r = if (rnd.nextFloat() < 0.04f) W / 260f else W / 900f
            p.color = Color.argb(80 + rnd.nextInt(175), 255, 255, 255); c.drawCircle(rnd.nextFloat() * W, rnd.nextFloat() * H, r, p)
        }
        repeat(6) {
            val x = rnd.nextFloat() * W; val y = rnd.nextFloat() * H
            p.shader = RadialGradient(x, y, W / 40f, Color.argb(200, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, W / 40f, p); p.shader = null
            p.color = Color.argb(160, 255, 255, 255); p.strokeWidth = 1.5f
            c.drawLine(x - W / 30f, y, x + W / 30f, y, p); c.drawLine(x, y - W / 30f, x, y + W / 30f, p)
        }
        // Small ringed planet.
        val px = W * 0.78f; val py = H * 0.2f; val pr = W * 0.07f
        p.shader = RadialGradient(px - pr / 3, py - pr / 3, pr * 1.4f, cols.getOrElse(4) { Color.CYAN }, cols.getOrElse(1) { Color.BLUE }, Shader.TileMode.CLAMP)
        c.drawCircle(px, py, pr, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = W / 220f; p.color = Color.argb(170, 255, 230, 200)
        c.save(); c.rotate(-18f, px, py); c.drawOval(px - pr * 1.9f, py - pr * 0.45f, px + pr * 1.9f, py + pr * 0.45f, p); c.restore()
        p.style = Paint.Style.FILL
    }

    @Suppress("unused") private fun ang(i: Int, n: Int) = 2 * PI * i / n
    @Suppress("unused") private fun clamp(v: Float) = min(1f, max(0f, v))
}
