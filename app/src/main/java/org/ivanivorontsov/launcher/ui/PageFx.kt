package org.ivanivorontsov.launcher.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import org.ivanivorontsov.launcher.core.PageEffect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Page-turn effects (1.2). Everything is computed in graphics layers from the pager's scroll position, so a
 * swipe only re-draws layers (no recomposition, no relayout) and stays smooth at 144 Hz.
 *
 * Page-level effects (cube, flip, rotate, zoom, stack) transform the whole page. The cylinder and the wave are
 * per item: every icon column is mapped onto a vertical cylinder whose radius grows to infinity at rest, so the
 * page is perfectly flat when it stops and curves away at the edges while it is moving — the neighbouring
 * pages wrap around the back of the cylinder (mirrored and smaller), like realme/OPPO «Цилиндр» or Nova «Carousel».
 */
class PageFx(val effect: PageEffect, val pager: PagerState, val page: Int)

/** The current theme's page effect (provided by LauncherRoot). */
val LocalPageEffect = compositionLocalOf { PageEffect.NONE }

val LocalPageFx = compositionLocalOf<PageFx?> { null }

private fun PagerState.position() = currentPage + currentPageOffsetFraction

/** How "bent" the cylinder is: 0 at rest, 1 half-way through a swipe. */
private fun bend(pos: Float): Float {
    val f = abs(pos - pos.roundToInt())          // 0..0.5
    val t = (f * 4f).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)                 // smoothstep
}

/** Whole-page transform. Apply to the root of a pager page. */
fun Modifier.pageEffect(effect: PageEffect, pager: PagerState, page: Int, itemsDoIt: Boolean = false): Modifier {
    if (effect == PageEffect.NONE) return this
    if (effect.perItem() && itemsDoIt) return this.zIndexFor(pager, page)
    if (effect == PageEffect.WAVE) return this
    // Pages nearer to the centre draw on top (cube/stack/cylinder back faces).
    return this.zIndexFor(pager, page).graphicsLayer {
        val o = (page - pager.position()).coerceIn(-1.5f, 1.5f)
        pageLayer(effect, o)
    }
}

private fun Modifier.zIndexFor(pager: PagerState, page: Int): Modifier =
    this.then(Modifier.zIndex(-abs(page - pager.currentPage).toFloat()))

private fun GraphicsLayerScope.pageLayer(effect: PageEffect, o: Float) {
    val a = abs(o)
    cameraDistance = 14f * density
    when (effect) {
        PageEffect.CUBE -> {
            transformOrigin = TransformOrigin(if (o < 0f) 1f else 0f, 0.5f)
            rotationY = 90f * o.coerceIn(-1f, 1f)
            alpha = if (a >= 1f) 0f else 1f
        }
        PageEffect.FLIP -> {
            translationX = -o * size.width
            rotationY = 180f * o.coerceIn(-1f, 1f)
            alpha = if (a > 0.5f) 0f else 1f
        }
        PageEffect.ROTATE -> {
            transformOrigin = TransformOrigin(0.5f, 1.6f)
            rotationZ = 22f * o
        }
        PageEffect.ZOOM -> {
            val s = 1f - 0.22f * a.coerceAtMost(1f)
            scaleX = s; scaleY = s
            alpha = 1f - 0.6f * a.coerceAtMost(1f)
        }
        PageEffect.STACK -> if (o > 0f) {
            translationX = -o * size.width
            val s = 1f - 0.14f * o.coerceAtMost(1f)
            scaleX = s; scaleY = s
            alpha = 1f - 0.5f * o.coerceAtMost(1f)
        }
        // Pages without per-item content (widgets page, tiles, text lists) get a page-level cylinder.
        PageEffect.CYLINDER -> {
            val b = bend(o)
            transformOrigin = TransformOrigin(if (o < 0f) 1f else 0f, 0.5f)
            rotationY = 70f * o.coerceIn(-1f, 1f) * (0.4f + 0.6f * b)
            val s = 1f - 0.12f * a.coerceAtMost(1f)
            scaleX = s; scaleY = s
        }
        else -> {}
    }
}

/**
 * Per-item part of the cylinder / wave. [cx] is the item's centre x relative to the page centre, [w] the
 * page width (px). Called from the page's placement layer block, so it only reads the scroll state at draw.
 */
fun GraphicsLayerScope.itemPageLayer(fx: PageFx, cx: Float, cy: Float, w: Float, h: Float) {
    val pos = fx.pager.position()
    val o = fx.page - pos
    when (fx.effect) {
        PageEffect.CYLINDER -> {
            val e = bend(pos)
            if (e <= 0.001f) return
            val x = cx + o * w                           // screen x relative to the viewport centre
            // Fully bent: the front half of the drum is one screen wide (radius = half the width), so a page
            // covers ~115° — its columns curve away at the edges and the next page shows on the back, mirrored.
            val r = (w / 2f) / e
            val theta = x / r
            if (abs(theta) > PI.toFloat()) { alpha = 0f; return }
            val sx = r * sin(theta)
            val z = r * (cos(theta) - 1f)                // ≤ 0: away from the viewer
            val d = 2.4f * w
            val p = d / (d - z)                          // perspective
            translationX = sx * p - x
            val deg = theta * 180f / PI.toFloat()
            cameraDistance = 12f * density
            if (abs(theta) <= PI.toFloat() / 2f) {
                scaleX = p; scaleY = p
                rotationY = deg
            } else {
                // Back of the cylinder: seen from behind → mirrored (negative scale, not a >90° rotation, which
                // the renderer may cull), turned by the remaining angle, smaller and translucent like in the photo.
                scaleX = -p; scaleY = p
                rotationY = deg - 180f * kotlin.math.sign(deg)
                alpha = 0.6f
            }
        }
        PageEffect.WAVE -> {
            val e = bend(pos)
            if (e <= 0.001f) return
            val x = cx + o * w
            val amp = 26f * density
            translationY = amp * e * sin(2f * PI.toFloat() * (x / w) + PI.toFloat() * pos)
            val s = 1f - 0.08f * e * (0.5f + 0.5f * cos(2f * PI.toFloat() * x / w))
            scaleX = s; scaleY = s
        }
        else -> {}
    }
}

fun PageEffect.perItem() = this == PageEffect.CYLINDER || this == PageEffect.WAVE
