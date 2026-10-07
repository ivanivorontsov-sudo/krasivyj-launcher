package org.ivanivorontsov.launcher.data

import android.graphics.Path
import android.graphics.RectF
import org.ivanivorontsov.launcher.core.IconShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Icon mask paths, generated once per size (all shapes are original math, no copied assets). */
object ShapePath {
    fun of(shape: IconShape, w: Float, h: Float = w): Path? = when (shape) {
        IconShape.ORIGINAL -> null
        IconShape.CIRCLE -> Path().apply { addOval(RectF(0f, 0f, w, h), Path.Direction.CW) }
        IconShape.SQUARE -> Path().apply { addRect(0f, 0f, w, h, Path.Direction.CW) }
        IconShape.ROUNDED -> Path().apply { addRoundRect(RectF(0f, 0f, w, h), w * 0.24f, h * 0.24f, Path.Direction.CW) }
        IconShape.TEARDROP -> Path().apply {
            val r = w / 2; val s = w * 0.14f
            addRoundRect(RectF(0f, 0f, w, h), floatArrayOf(r, r, r, r, s, s, r, r), Path.Direction.CW)
        }
        IconShape.SQUIRCLE -> superellipse(w, h, 5.0)
        IconShape.COOKIE -> polar(w, h) { t -> 0.93 + 0.07 * cos(9 * t) }
        IconShape.PEBBLE -> polar(w, h) { t -> 0.94 + 0.045 * sin(2 * t + 0.7) + 0.03 * cos(3 * t + 0.2) }
        IconShape.HEXAGON -> roundedPolygon(w, h, 6, 0.16f)
    }

    /** |x|^n + |y|^n = 1 — continuous-curvature corners (the smooth “squircle”). */
    fun superellipse(w: Float, h: Float, n: Double): Path {
        val p = Path(); val steps = 120
        for (i in 0..steps) {
            val t = 2 * PI * i / steps
            val c = cos(t); val s = sin(t)
            val x = (w / 2) * (1 + sign(c) * abs(c).pow(2 / n)).toFloat()
            val y = (h / 2) * (1 + sign(s) * abs(s).pow(2 / n)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close(); return p
    }

    private inline fun polar(w: Float, h: Float, r: (Double) -> Double): Path {
        val p = Path(); val steps = 180
        for (i in 0..steps) {
            val t = 2 * PI * i / steps
            val k = r(t)
            val x = (w / 2 + (w / 2) * k * cos(t)).toFloat(); val y = (h / 2 + (h / 2) * k * sin(t)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close(); return p
    }

    private fun roundedPolygon(w: Float, h: Float, n: Int, rounding: Float): Path {
        val pts = (0 until n).map { i ->
            val t = 2 * PI * i / n - PI / 2
            floatArrayOf((w / 2 + w / 2 * cos(t)).toFloat(), (h / 2 + h / 2 * sin(t)).toFloat())
        }
        val p = Path()
        for (i in 0 until n) {
            val prev = pts[(i - 1 + n) % n]; val cur = pts[i]; val next = pts[(i + 1) % n]
            val ax = cur[0] + (prev[0] - cur[0]) * rounding; val ay = cur[1] + (prev[1] - cur[1]) * rounding
            val bx = cur[0] + (next[0] - cur[0]) * rounding; val by = cur[1] + (next[1] - cur[1]) * rounding
            if (i == 0) p.moveTo(ax, ay) else p.lineTo(ax, ay)
            p.quadTo(cur[0], cur[1], bx, by)
        }
        p.close(); return p
    }
}
