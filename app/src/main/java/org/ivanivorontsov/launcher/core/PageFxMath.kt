package org.ivanivorontsov.launcher.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Pure math for page-turn effects, so transforms and z-order can be unit-tested
 * without Compose. Keep this allocation-free: it runs every animation frame.
 */
object PageFxMath {
    /** How "bent" the cylinder is: 0 at rest, 1 half-way through a swipe. */
    fun bend(pos: Float): Float {
        val f = abs(pos - pos.roundToInt())
        val t = (f * 4f).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * Sibling draw order for a page at signed offset [o] = page - pagerPosition.
     *
     * [PageEffect.STACK] and [PageEffect.DEPTH] keep the card that follows the finger
     * (o ≤ 0) on top. Using −|o| made both pages equal at mid-swipe, so when going
     * backwards the incoming page sat behind the leaving one and appeared to freeze.
     */
    fun zIndex(effect: PageEffect, o: Float): Float = when (effect) {
        PageEffect.STACK, PageEffect.DEPTH -> -o
        else -> -abs(o)
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
}
