package org.ivanivorontsov.launcher.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import org.ivanivorontsov.launcher.core.AnimGuard
import org.ivanivorontsov.launcher.core.PageEffect
import org.ivanivorontsov.launcher.core.PageFxMath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Page-turn effects (1.2). Everything is computed in graphics layers from the pager's scroll position, so a
 * swipe only re-draws layers (no recomposition, no relayout) and stays smooth at 144 Hz.
 *
 * Page-level effects (cube, flip, rotate, zoom, stack) transform the whole page. The cylinder and the wave are
 * per item: every icon column is mapped onto a vertical cylinder whose radius grows to infinity at rest, so the
 * page is perfectly flat when it stops and curves away at the edges while it is moving — the neighbouring
 * pages wrap around the back of the cylinder (mirrored and smaller), like realme/OPPO «Цилиндр» or Nova «Carousel».
 *
 * 1.4.9: STACK/DEPTH z-order is directional so a backward swipe no longer freezes the incoming page;
 * every layer resets its transforms at rest; cylinder columns get a 2% seam overlap and are depth-sorted.
 * 1.4.12: off-screen pages (offset ±1 and leftovers) are fully hidden (alpha 0, translationX off-screen,
 * z-index below); settled pages always get an identity layer so a cancelled or repeated swipe cannot leave two pages on screen.
 */
class PageFx(val effect: PageEffect, val pager: PagerState, val page: Int)

/** The current theme's page effect (provided by LauncherRoot). */
val LocalPageEffect = compositionLocalOf { PageEffect.NONE }

val LocalPageFx = compositionLocalOf<PageFx?> { null }

/** Landscape home pages swipe vertically (tablet-style). */
val LocalVerticalPages = compositionLocalOf { false }

internal fun PagerState.position() = currentPage + currentPageOffsetFraction

/** Whole-page transform. Apply to the root of a pager page. */
fun Modifier.pageEffect(effect: PageEffect, pager: PagerState, page: Int, itemsDoIt: Boolean = false): Modifier = composed {
    val fx = AnimGuard.safeEffect(effect)
    if (fx == PageEffect.NONE) this
    else {
        val vertical = LocalVerticalPages.current
        // In landscape, cylinder/wave fall back to a page-level vertical slide (no per-icon bend).
        when {
            fx.perItem() && itemsDoIt && !vertical -> this.zIndexFor(pager, page, fx)
            fx == PageEffect.WAVE && !vertical -> this
            else -> this.zIndexFor(pager, page, fx).graphicsLayer {
                applyTransform(PageFxMath.pageTransform(fx, page - pager.position(), size.width, size.height, vertical))
            }
        }
    }
}

/**
 * 1.3.2: z-order from the *continuous* scroll position, read only in the placement phase. Before, it used
 * pager.currentPage, which is an integer that flips at the half-way point: when swiping back the incoming
 * (lower-index) page sat under the current one and then popped on top mid-swipe — the "поддёргивание".
 * Reading currentPage also recomposed the page on every flip.
 *
 * 1.4.9: STACK and DEPTH use signed z-order (−o), so the card that follows the finger stays on top
 * when swiping backwards as well as forwards.
 */
private fun Modifier.zIndexFor(pager: PagerState, page: Int, effect: PageEffect): Modifier =
    this.then(Modifier.layout { m, c ->
        val p = m.measure(c)
        layout(p.width, p.height) { p.place(0, 0, zIndex = PageFxMath.zIndex(effect, page - pager.position())) }
    })

/** Identity + a far camera, so a settled page never keeps a leftover rotation or scale. */
private fun GraphicsLayerScope.resetLayer() {
    translationX = 0f
    translationY = 0f
    scaleX = 1f
    scaleY = 1f
    rotationX = 0f
    rotationY = 0f
    rotationZ = 0f
    alpha = 1f
    shadowElevation = 0f
    transformOrigin = TransformOrigin.Center
    cameraDistance = 24f * density
    compositingStrategy = CompositingStrategy.Auto
}

private fun GraphicsLayerScope.applyTransform(t: PageFxMath.PageTransform) {
    translationX = t.translationX
    translationY = t.translationY
    scaleX = t.scaleX
    scaleY = t.scaleY
    rotationX = t.rotationX
    rotationY = t.rotationY
    rotationZ = t.rotationZ
    alpha = t.alpha
    shadowElevation = t.shadowElevation
    transformOrigin = TransformOrigin(t.originX, t.originY)
    cameraDistance = t.cameraDistanceFactor * density
    compositingStrategy = if (t.modulateAlpha) CompositingStrategy.ModulateAlpha else CompositingStrategy.Auto
}

internal fun GraphicsLayerScope.pageLayer(effect: PageEffect, o: Float) {
    applyTransform(PageFxMath.pageTransform(effect, o, size.width))
}

/**
 * Per-item part of the cylinder / wave. [cx] is the item's centre x relative to the page centre, [w] the
 * page width (px). Called from the page's placement layer block, so it only reads the scroll state at draw.
 */
fun GraphicsLayerScope.itemPageLayer(fx: PageFx, cx: Float, cy: Float, w: Float, h: Float) {
    resetLayer()
    val pos = fx.pager.position()
    val o = fx.page - pos
    if (PageFxMath.isOffscreen(o)) { applyTransform(PageFxMath.hidden(o, w)); return }
    if (PageFxMath.isSettled(o)) return
    when (fx.effect) {
        PageEffect.CYLINDER -> {
            val e = PageFxMath.bend(pos)
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
                val s = PageFxMath.seamScale(p)
                scaleX = s; scaleY = p
                rotationY = deg
            } else {
                // 1.3.4: back faces are no longer drawn — they landed behind the current page's icons and
                // showed up as doubled, ghosted labels.
                alpha = 0f
            }
        }
        PageEffect.WAVE -> {
            val e = PageFxMath.bend(pos)
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
