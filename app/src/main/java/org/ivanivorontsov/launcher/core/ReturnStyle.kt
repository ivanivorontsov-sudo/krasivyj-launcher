package org.ivanivorontsov.launcher.core

/** Resolved style of one return to home (Home or Back), see [resolveReturn]. */
enum class ReturnStyle(val card: Boolean) { NONE(false), ICON(true), SPRING(true), ZOOM(false), SLIDE_UP(false), SLIDE_SIDE(false), FADE(false),
    /** The system flew the window into our icon (GestureNavContract): no card, no zoom, icon fades in afterwards. */
    CONTRACT(false) }

fun resolveReturn(home: HomeAnim, back: BackAnim, fromBack: Boolean): ReturnStyle {
    fun h(a: HomeAnim) = when (a) {
        HomeAnim.SYSTEM -> ReturnStyle.NONE; HomeAnim.ICON -> ReturnStyle.ICON; HomeAnim.SPRING -> ReturnStyle.SPRING
        HomeAnim.ZOOM -> ReturnStyle.ZOOM; HomeAnim.SLIDE -> ReturnStyle.SLIDE_UP; HomeAnim.FADE -> ReturnStyle.FADE
    }
    if (!fromBack) return h(home)
    return when (back) {
        BackAnim.SAME -> h(home); BackAnim.SYSTEM -> ReturnStyle.NONE; BackAnim.ZOOM -> ReturnStyle.ZOOM
        BackAnim.SLIDE -> ReturnStyle.SLIDE_SIDE; BackAnim.FADE -> ReturnStyle.FADE; BackAnim.SPRING -> ReturnStyle.SPRING
    }
}

/** Spring stiffness scaled so that duration ~ 1/speed (duration of a spring ~ 1/sqrt(stiffness)). */
fun speedStiffness(base: Float, speed: Float): Float = base * speed.coerceIn(0.4f, 2f).let { it * it }
