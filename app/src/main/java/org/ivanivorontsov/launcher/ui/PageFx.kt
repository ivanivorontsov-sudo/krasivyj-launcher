package org.ivanivorontsov.launcher.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
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
        val raw = page - pager.position()
        // 1.3.4: only the current page and the incoming neighbour may draw. Pages kept composed by the
        // pager (or a far page such as the first one) are fully hidden, so nothing ghosts on top.
        if (abs(raw) >= 1f) { alpha = 0f; return@graphicsLayer }
        pageLayer(effect, raw)
    }
}

/**
 * 1.3.2: z-order from the *continuous* scroll position, read only in the placement phase. Before, it used
 * pager.currentPage, which is an integer that flips at the half-way point: when swiping back the incoming
 * (lower-index) page sat under the current one and then popped on top mid-swipe — the "поддёргивание".
 * Reading currentPage also recomposed the page on every flip.
 */
private fun Modifier.zIndexFor(pager: PagerState, page: Int): Modifier =
    this.then(Modifier.layout { m, c ->
        val p = m.measure(c)
        layout(p.width, p.height) { p.place(0, 0, zIndex = -abs(page - pager.position())) }
    })

internal fun GraphicsLayerScope.pageLayer(effect: PageEffect, o: Float) {
    val a = abs(o)
    // Camera far enough that a 90-180° turn never clips the near edge on a tall 144 Hz panel.
    cameraDistance = 24f * density
    when (effect) {
        PageEffect.CUBE -> {
            transformOrigin = TransformOrigin(if (o < 0f) 1f else 0f, 0.5f)
            rotationY = 90f * o.coerceIn(-1f, 1f)
            alpha = if (a >= 1f) 0f else 1f
        }
        PageEffect.FLIP -> {
            translationX = -o * size.width
            rotationY = 180f * o.coerceIn(-1f, 1f)
            // Edge-on at |o| = 0.5; hide only the back face (no hard pop before that).
            alpha = if (a >= 0.5f) 0f else 1f
            shadowElevation = 0f
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
        // 1.3.9 — new effects. All are pure layer transforms of the page (no recomposition while swiping).
        PageEffect.CUBE_IN -> {
            // Inside of the cube: the shared edge stays put, the faces turn towards the viewer.
            transformOrigin = TransformOrigin(if (o < 0f) 1f else 0f, 0.5f)
            rotationY = -90f * o.coerceIn(-1f, 1f)
            alpha = 1f - 0.25f * a.coerceAtMost(1f)
        }
        PageEffect.CAROUSEL -> {
            // Pages ride on a round table: they turn around their own centre and step back.
            val c = a.coerceAtMost(1f)
            rotationY = 55f * o.coerceIn(-1f, 1f)
            translationX = -o * size.width * 0.28f
            val s = 1f - 0.24f * c
            scaleX = s; scaleY = s
            alpha = 1f - 0.4f * c
        }
        PageEffect.TILT -> {
            transformOrigin = TransformOrigin(0.5f, 0.5f)
            rotationY = -28f * o.coerceIn(-1f, 1f)
            rotationX = 6f * a.coerceAtMost(1f)
            val s = 1f - 0.08f * a.coerceAtMost(1f)
            scaleX = s; scaleY = s
        }
        PageEffect.ZOOM_IN -> {
            // The leaving page swells towards the viewer and dissolves; the next one grows into place.
            val c = a.coerceAtMost(1f)
            val s = if (o < 0f) 1f + 0.35f * c else 1f - 0.25f * c
            scaleX = s; scaleY = s
            alpha = 1f - c
        }
        PageEffect.DEPTH -> if (o > 0f) {
            // The page on the right waits underneath; the current one slides over it.
            val c = o.coerceAtMost(1f)
            translationX = -o * size.width
            val s = 0.75f + 0.25f * (1f - c)
            scaleX = s; scaleY = s
            alpha = 1f - c
        }
        PageEffect.ACCORDION -> {
            transformOrigin = TransformOrigin(if (o < 0f) 1f else 0f, 0.5f)
            scaleX = (1f - a).coerceIn(0f, 1f)
            alpha = 1f - 0.3f * a.coerceAtMost(1f)
        }
        PageEffect.SPIN -> {
            val c = a.coerceAtMost(1f)
            rotationZ = 90f * o.coerceIn(-1f, 1f)
            val s = 1f - 0.45f * c
            scaleX = s; scaleY = s
            alpha = 1f - c
        }
        PageEffect.FADE -> {
            // Pages do not move at all: one dissolves into the other.
            translationX = -o * size.width
            alpha = 1f - a.coerceAtMost(1f)
        }
        PageEffect.WHEEL -> {
            // Ferris wheel: pages swing around a hub high above the screen.
            transformOrigin = TransformOrigin(0.5f, -1.1f)
            rotationZ = -24f * o.coerceIn(-1f, 1f)
            alpha = 1f - 0.3f * a.coerceAtMost(1f)
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
    // 1.3.4: a page that is not the current one or its direct neighbour never draws.
    if (abs(o) >= 1f) { alpha = 0f; return }
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
            cameraDistance = 24f * density
            if (abs(theta) <= PI.toFloat() / 2f) {
                scaleX = p; scaleY = p
                rotationY = deg
            } else {
                // 1.3.4: back faces are no longer drawn — they landed behind the current page's icons and
                // showed up as doubled, ghosted labels.
                alpha = 0f
            }
        }
        PageEffect.WAVE -> {
            val e = bend(pos)
            if (e <= 0.001f) return
            val x = cx + o * w
            val amp = 26f * density
            // Phase from the signed swipe progress (continuous, symmetric for forward/back) — the old
            // absolute "PI * pos" term made the wave jump between page pairs.
            val frac = pos - kotlin.math.floor(pos)
            translationY = amp * e * sin(2f * PI.toFloat() * (x / w) - 2f * PI.toFloat() * frac)
            val s = 1f - 0.08f * e * (0.5f + 0.5f * cos(2f * PI.toFloat() * x / w))
            scaleX = s; scaleY = s
        }
        else -> {}
    }
}

fun PageEffect.perItem() = this == PageEffect.CYLINDER || this == PageEffect.WAVE
