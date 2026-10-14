package org.ivanivorontsov.launcher.core

/** Resolved style of one return to home (Home or Back), see [resolveReturn]. */
enum class ReturnStyle(val card: Boolean) { NONE(false), ICON(true), SPRING(true), ZOOM(false), SLIDE_UP(false), SLIDE_SIDE(false), FADE(false),
    /** The system flew the window into our icon (GestureNavContract): no card, no zoom, icon fades in afterwards. */
    CONTRACT(false),
    /** 1.3.6: card variants that land with a spin, with ripples, as a drop, as a comet. */
    SPIN(true), RINGS(true), DROP(true), COMET(true) }

fun resolveReturn(home: HomeAnim, back: BackAnim, fromBack: Boolean): ReturnStyle {
    fun h(a: HomeAnim) = when (a) {
        HomeAnim.SYSTEM -> ReturnStyle.NONE; HomeAnim.ICON -> ReturnStyle.ICON; HomeAnim.SPRING -> ReturnStyle.SPRING
        HomeAnim.ZOOM -> ReturnStyle.ZOOM; HomeAnim.SLIDE -> ReturnStyle.SLIDE_UP; HomeAnim.FADE -> ReturnStyle.FADE
        HomeAnim.SPIN -> ReturnStyle.SPIN; HomeAnim.RINGS -> ReturnStyle.RINGS; HomeAnim.DROP -> ReturnStyle.DROP; HomeAnim.COMET -> ReturnStyle.COMET
    }
    if (!fromBack) return h(home)
    return when (back) {
        BackAnim.SAME -> h(home); BackAnim.SYSTEM -> ReturnStyle.NONE; BackAnim.ZOOM -> ReturnStyle.ZOOM
        BackAnim.SLIDE -> ReturnStyle.SLIDE_SIDE; BackAnim.FADE -> ReturnStyle.FADE; BackAnim.SPRING -> ReturnStyle.SPRING
    }
}

/** Spring stiffness scaled so that duration ~ 1/speed (duration of a spring ~ 1/sqrt(stiffness)). */
fun speedStiffness(base: Float, speed: Float): Float = base * speed.coerceIn(0.4f, 2f).let { it * it }

/** 1.3.6: spring stiffness whose visible settle takes about [ms] (ω ≈ 4.6 / (ζ·T) → 1 % envelope at T). */
fun stiffnessFor(ms: Int, damping: Float): Float {
    val t = ms.coerceIn(120, 1200) / 1000f
    val w = 4.6f / (damping.coerceIn(0.3f, 1f) * t)
    return w * w
}
