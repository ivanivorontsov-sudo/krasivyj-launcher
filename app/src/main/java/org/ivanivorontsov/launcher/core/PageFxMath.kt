package org.ivanivorontsov.launcher.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure math for page-turn effects, so transforms and z-order can be unit-tested
 * without Compose. Keep this allocation-free on the hot path: [pageTransform]
 * returns a data class (one object per frame per page), not collections.
 */
object PageFxMath {
    /**
     * Offsets this close to an integer page are treated as settled / fully off-screen.
     * Catches spring leftovers, cancelled flings and float error so a neighbour is never
     * left with a mid-swipe translation after the gesture ends.
     */
    const val SETTLE_EPS = 1e-3f

    /** How "bent" the cylinder is: 0 at rest, 1 half-way through a swipe. */
    fun bend(pos: Float): Float {
        val f = abs(pos - pos.roundToInt())
        val t = (f * 4f).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** True when this page must not be drawn (neighbour or further, including tiny overshoot). */
    fun isOffscreen(o: Float): Boolean = abs(o) >= 1f - SETTLE_EPS

    /** True when the page is the settled current page (offset 0, or a leftover fraction). */
    fun isSettled(o: Float): Boolean = abs(o) <= SETTLE_EPS

    /**
     * Sibling draw order for a page at signed offset [o] = page - pagerPosition.
     *
     * [PageEffect.STACK] and [PageEffect.DEPTH] keep the card that follows the finger
     * (o ≤ 0) on top. Using −|o| made both pages equal at mid-swipe, so when going
     * backwards the incoming page sat behind the leaving one and appeared to freeze.
     * Fully off-screen pages are pushed far below so a stale layer cannot overdraw.
     */
    fun zIndex(effect: PageEffect, o: Float): Float {
        if (isOffscreen(o)) return -100f - abs(o)
        return when (effect) {
            PageEffect.STACK, PageEffect.DEPTH -> -o
            else -> -abs(o)
        }
    }

    /**
     * Camera-facing depth of a cylinder column (higher = closer = drawn on top).
     * [cx] is the column centre relative to the page centre, [pageOffset] is page − position.
     */
    fun cylinderDepth(cx: Float, pageOffset: Float, pageW: Float, bend: Float): Float {
        if (bend <= 0.001f || pageW <= 0f) return 0f
        val x = cx + pageOffset * pageW
        val theta = x * (2f * bend / pageW)
        if (abs(theta) > PI.toFloat()) return -1f
        return cos(theta)
    }

    /** Slight horizontal scale so neighbouring columns overlap by ~2% and hide 1 px seams. */
    fun seamScale(perspective: Float): Float = perspective * 1.02f

    /**
     * Immutable graphics-layer values for a page at signed offset [o] = page − position.
     * At settled offsets 0, ±1 (and within [SETTLE_EPS]) exactly one page is visible and
     * it has the identity transform; neighbours are hidden (alpha 0, translation off-screen).
     * The same rule applies after a cancelled swipe, a fling back, a fast repeated swipe
     * or a page-count change — the function only depends on [o] and [pageWidth].
     */
    data class PageTransform(
        val alpha: Float = 1f,
        val translationX: Float = 0f,
        val translationY: Float = 0f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val rotationX: Float = 0f,
        val rotationY: Float = 0f,
        val rotationZ: Float = 0f,
        val originX: Float = 0.5f,
        val originY: Float = 0.5f,
        val shadowElevation: Float = 0f,
        val modulateAlpha: Boolean = false,
        val cameraDistanceFactor: Float = 24f,
    ) {
        val visible: Boolean get() = alpha > 0f
        val identity: Boolean get() =
            alpha == 1f &&
                translationX == 0f && translationY == 0f &&
                scaleX == 1f && scaleY == 1f &&
                rotationX == 0f && rotationY == 0f && rotationZ == 0f
    }

    val Identity = PageTransform()

    fun hidden(o: Float, pageWidth: Float, vertical: Boolean = false): PageTransform {
        val e = max(pageWidth, 1f)
        val t = when {
            o > 0f -> e
            o < 0f -> -e
            else -> 0f
        }
        return if (vertical) PageTransform(alpha = 0f, translationY = t)
        else PageTransform(alpha = 0f, translationX = t)
    }

    fun pageTransform(effect: PageEffect, o: Float, pageWidth: Float, pageHeight: Float = pageWidth, vertical: Boolean = false): PageTransform {
        val extent = if (vertical) pageHeight else pageWidth
        if (effect == PageEffect.NONE) return if (isOffscreen(o)) hidden(o, extent, vertical) else Identity
        if (isOffscreen(o)) return hidden(o, extent, vertical)
        if (isSettled(o)) return Identity
        if (vertical) return verticalTransform(effect, o, pageHeight)
        val a = abs(o)
        val w = pageWidth
        return when (effect) {
            PageEffect.CUBE -> PageTransform(
                originX = if (o < 0f) 1f else 0f,
                originY = 0.5f,
                rotationY = 90f * o.coerceIn(-1f, 1f),
            )
            PageEffect.FLIP -> PageTransform(
                translationX = -o * w,
                rotationY = 180f * o.coerceIn(-1f, 1f),
                alpha = if (a >= 0.5f) 0f else 1f,
            )
            PageEffect.ROTATE -> PageTransform(
                originX = 0.5f,
                originY = 1.6f,
                rotationZ = 22f * o,
            )
            PageEffect.ZOOM -> {
                val s = 1f - 0.22f * a.coerceAtMost(1f)
                PageTransform(scaleX = s, scaleY = s, alpha = 1f - 0.6f * a.coerceAtMost(1f))
            }
            PageEffect.STACK -> if (o > 0f) {
                val c = o.coerceAtMost(1f)
                val s = 1f - 0.14f * c
                PageTransform(
                    translationX = -o * w,
                    scaleX = s,
                    scaleY = s,
                    alpha = 1f - 0.35f * c,
                )
            } else Identity
            PageEffect.CUBE_IN -> PageTransform(
                originX = if (o < 0f) 1f else 0f,
                originY = 0.5f,
                rotationY = -90f * o.coerceIn(-1f, 1f),
                alpha = 1f - 0.25f * a.coerceAtMost(1f),
            )
            PageEffect.CAROUSEL -> {
                val c = a.coerceAtMost(1f)
                val s = 1f - 0.24f * c
                PageTransform(
                    rotationY = 55f * o.coerceIn(-1f, 1f),
                    translationX = -o * w * 0.28f,
                    scaleX = s,
                    scaleY = s,
                    alpha = 1f - 0.4f * c,
                )
            }
            PageEffect.TILT -> {
                val s = 1f - 0.08f * a.coerceAtMost(1f)
                PageTransform(
                    rotationY = -28f * o.coerceIn(-1f, 1f),
                    rotationX = 6f * a.coerceAtMost(1f),
                    scaleX = s,
                    scaleY = s,
                )
            }
            PageEffect.ZOOM_IN -> {
                val c = a.coerceAtMost(1f)
                val s = if (o < 0f) 1f + 0.35f * c else 1f - 0.25f * c
                PageTransform(scaleX = s, scaleY = s, alpha = 1f - c)
            }
            PageEffect.DEPTH -> if (o > 0f) {
                val c = o.coerceAtMost(1f)
                val s = 0.75f + 0.25f * (1f - c)
                PageTransform(
                    translationX = -o * w,
                    scaleX = s,
                    scaleY = s,
                    alpha = 1f - 0.5f * c,
                )
            } else Identity
            PageEffect.ACCORDION -> PageTransform(
                originX = if (o < 0f) 1f else 0f,
                originY = 0.5f,
                scaleX = (1f - a).coerceIn(0f, 1f),
                alpha = 1f - 0.3f * a.coerceAtMost(1f),
            )
            PageEffect.SPIN -> {
                val c = a.coerceAtMost(1f)
                val s = 1f - 0.45f * c
                PageTransform(
                    rotationZ = 90f * o.coerceIn(-1f, 1f),
                    scaleX = s,
                    scaleY = s,
                    alpha = 1f - c,
                )
            }
            PageEffect.FADE -> PageTransform(
                modulateAlpha = true,
                translationX = -o * w,
                alpha = (1f - a.coerceAtMost(1f)).coerceIn(0f, 1f),
            )
            PageEffect.WHEEL -> PageTransform(
                originX = 0.5f,
                originY = -1.1f,
                rotationZ = -24f * o.coerceIn(-1f, 1f),
                alpha = 1f - 0.3f * a.coerceAtMost(1f),
            )
            PageEffect.CYLINDER -> {
                val b = bend(o)
                val s = 1f - 0.12f * a.coerceAtMost(1f)
                PageTransform(
                    originX = if (o < 0f) 1f else 0f,
                    originY = 0.5f,
                    rotationY = 70f * o.coerceIn(-1f, 1f) * (0.4f + 0.6f * b),
                    scaleX = s,
                    scaleY = s,
                )
            }
            else -> Identity
        }
    }

    /**
     * Landscape pages swipe vertically. Stack / fade / depth / zoom keep their character
     * with X mapped to Y; cube-like effects use rotationX; everything else is a clean slide.
     */
    private fun verticalTransform(effect: PageEffect, o: Float, pageHeight: Float): PageTransform {
        val a = abs(o)
        val h = pageHeight
        return when (effect) {
            PageEffect.STACK -> if (o > 0f) {
                val c = o.coerceAtMost(1f)
                val s = 1f - 0.14f * c
                PageTransform(translationY = -o * h, scaleX = s, scaleY = s, alpha = 1f - 0.35f * c)
            } else Identity
            PageEffect.DEPTH -> if (o > 0f) {
                val c = o.coerceAtMost(1f)
                val s = 0.75f + 0.25f * (1f - c)
                PageTransform(translationY = -o * h, scaleX = s, scaleY = s, alpha = 1f - 0.5f * c)
            } else Identity
            PageEffect.FADE -> PageTransform(
                modulateAlpha = true,
                translationY = -o * h,
                alpha = (1f - a.coerceAtMost(1f)).coerceIn(0f, 1f),
            )
            PageEffect.ZOOM -> {
                val s = 1f - 0.22f * a.coerceAtMost(1f)
                PageTransform(scaleX = s, scaleY = s, alpha = 1f - 0.6f * a.coerceAtMost(1f))
            }
            PageEffect.ZOOM_IN -> {
                val c = a.coerceAtMost(1f)
                val s = if (o < 0f) 1f + 0.35f * c else 1f - 0.25f * c
                PageTransform(scaleX = s, scaleY = s, alpha = 1f - c)
            }
            PageEffect.SPIN -> {
                val c = a.coerceAtMost(1f)
                val s = 1f - 0.45f * c
                PageTransform(rotationZ = 90f * o.coerceIn(-1f, 1f), scaleX = s, scaleY = s, alpha = 1f - c)
            }
            PageEffect.CUBE -> PageTransform(
                originX = 0.5f,
                originY = if (o < 0f) 1f else 0f,
                rotationX = -90f * o.coerceIn(-1f, 1f),
            )
            PageEffect.CUBE_IN -> PageTransform(
                originX = 0.5f,
                originY = if (o < 0f) 1f else 0f,
                rotationX = 90f * o.coerceIn(-1f, 1f),
                alpha = 1f - 0.25f * a.coerceAtMost(1f),
            )
            PageEffect.FLIP -> PageTransform(
                translationY = -o * h,
                rotationX = 180f * o.coerceIn(-1f, 1f),
                alpha = if (a >= 0.5f) 0f else 1f,
            )
            PageEffect.ACCORDION -> PageTransform(
                originX = 0.5f,
                originY = if (o < 0f) 1f else 0f,
                scaleY = (1f - a).coerceIn(0f, 1f),
                alpha = 1f - 0.3f * a.coerceAtMost(1f),
            )
            else -> PageTransform(translationY = -o * h)
        }
    }
}
