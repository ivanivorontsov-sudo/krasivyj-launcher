package org.ivanivorontsov.launcher.ui

import android.os.Build
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.ivanivorontsov.launcher.core.IconShape
import org.ivanivorontsov.launcher.core.Motion
import org.ivanivorontsov.launcher.core.ThemeSpec
import org.ivanivorontsov.launcher.data.ShapePath
import java.util.concurrent.ConcurrentHashMap

object FontRegistry {
    private val cache = ConcurrentHashMap<String, FontFamily>()
    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    fun family(id: String): FontFamily = cache.getOrPut(id) {
        val info = FontCatalog[id] ?: return@getOrPut FontFamily.Default
        FontFamily(info.files.map { f ->
            if (f.variable) Font(f.res, FontWeight(f.weight), variationSettings = FontVariation.Settings(FontVariation.weight(f.weight)))
            else Font(f.res, FontWeight(f.weight))
        })
    }
}

@Immutable
data class LauncherColors(
    val accent: Color, val background: Color, val surface: Color, val onSurface: Color, val onWallpaper: Color,
    val dim: Color, val dark: Boolean,
)

@Immutable
data class LauncherFonts(val heading: FontFamily, val body: FontFamily, val clock: FontFamily)

val LocalSpec = compositionLocalOf { org.ivanivorontsov.launcher.core.Presets.cupertino }
val LocalColors = compositionLocalOf { LauncherColors(Color.Blue, Color.Black, Color.DarkGray, Color.White, Color.White, Color.Gray, true) }
val LocalFonts = compositionLocalOf { LauncherFonts(FontFamily.Default, FontFamily.Default, FontFamily.Default) }
val LocalAnimations = compositionLocalOf { true }

/** Motion language per theme. All specs are time/physics based, so they look identical at 60…144 Hz. */
object MotionSpecs {
    val iosEase: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    val turnstileEase: Easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)   // fast-out exponential feel
    val stepEase = Easing { f -> (kotlin.math.floor(f * 6f) / 6f).coerceAtMost(1f) }

    fun <T> enter(m: Motion, animations: Boolean): AnimationSpec<T> = if (!animations) snap() else when (m) {
        Motion.SPRING -> spring(dampingRatio = 0.86f, stiffness = 300f)   // ≈ 0.45 s critically-damped-ish spring
        Motion.SMOOTH -> tween(420, easing = FastOutSlowInEasing)
        Motion.TURNSTILE -> tween(320, easing = turnstileEase)
        Motion.SNAPPY -> tween(170, easing = LinearOutSlowInEasing)
        Motion.PIXEL_STEP -> tween(300, easing = stepEase)
    }

    fun <T> press(m: Motion, animations: Boolean): AnimationSpec<T> = if (!animations) snap() else when (m) {
        Motion.PIXEL_STEP -> tween(90, easing = stepEase)
        Motion.SNAPPY -> tween(80)
        else -> spring(dampingRatio = 0.7f, stiffness = 900f)
    }
}

class PathShape(private val shape: IconShape) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = ShapePath.of(shape, size.width, size.height) ?: return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
        return Outline.Generic(p.asComposePath())
    }
}

fun Long.c() = Color(this.toInt())

@Composable
fun LauncherTheme(spec: ThemeSpec, animations: Boolean = true, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val colors = remember(spec) {
        var accent = spec.accent.c(); var surface = spec.surface.c(); var onSurface = spec.onSurface.c(); var bg = spec.background.c()
        if (spec.dynamicColor && Build.VERSION.SDK_INT >= 31) {
            val d = if (spec.dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            accent = d.primary; surface = d.surfaceContainer; onSurface = d.onSurface; bg = d.surface
        }
        LauncherColors(accent, bg, surface, onSurface, if (spec.dynamicColor) Color.White else spec.onWallpaper.c(),
            if (spec.dark) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.35f), spec.dark)
    }
    val fonts = remember(spec.headingFont, spec.bodyFont, spec.clockFont) {
        LauncherFonts(FontRegistry.family(spec.headingFont), FontRegistry.family(spec.bodyFont), FontRegistry.family(spec.clockFont))
    }
    val scheme: ColorScheme = remember(colors) {
        val base = if (colors.dark) darkColorScheme() else lightColorScheme()
        base.copy(
            primary = colors.accent, onPrimary = if (colors.accent.luminance() > 0.5f) Color.Black else Color.White,
            secondary = colors.accent, tertiary = colors.accent,
            background = colors.background, surface = colors.surface, onSurface = colors.onSurface, onBackground = colors.onSurface,
            surfaceVariant = colors.surface, onSurfaceVariant = colors.onSurface.copy(alpha = 0.72f),
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.surface, surfaceContainerHighest = colors.surface,
            surfaceContainerLow = colors.surface, outline = colors.onSurface.copy(alpha = 0.3f),
            primaryContainer = colors.accent.copy(alpha = 0.25f), onPrimaryContainer = colors.onSurface,
            secondaryContainer = colors.accent.copy(alpha = 0.22f), onSecondaryContainer = colors.onSurface,
        )
    }
    val typography = remember(fonts, spec.letterSpacing) {
        val t = Typography()
        val ls = spec.letterSpacing.em
        fun TextStyle.b() = copy(fontFamily = fonts.body, letterSpacing = if (spec.letterSpacing != 0f) ls else letterSpacing)
        fun TextStyle.h() = copy(fontFamily = fonts.heading, letterSpacing = if (spec.letterSpacing != 0f) ls else letterSpacing)
        t.copy(
            displayLarge = t.displayLarge.h(), displayMedium = t.displayMedium.h(), displaySmall = t.displaySmall.h(),
            headlineLarge = t.headlineLarge.h(), headlineMedium = t.headlineMedium.h(), headlineSmall = t.headlineSmall.h(),
            titleLarge = t.titleLarge.h(), titleMedium = t.titleMedium.h(), titleSmall = t.titleSmall.h(),
            bodyLarge = t.bodyLarge.b(), bodyMedium = t.bodyMedium.b(), bodySmall = t.bodySmall.b(),
            labelLarge = t.labelLarge.b(), labelMedium = t.labelMedium.b(), labelSmall = t.labelSmall.b().copy(fontSize = 11.sp),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(LocalSpec provides spec, LocalColors provides colors, LocalFonts provides fonts, LocalAnimations provides animations) {
            content()
        }
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
