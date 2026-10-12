package org.ivanivorontsov.kodekszhizni.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF1B5E4A)
private val GreenLight = Color(0xFF4C8C74)
private val Cream = Color(0xFFF7F4EF)
private val Ink = Color(0xFF1A1C1B)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    secondary = GreenLight,
    background = Cream,
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink,
    primaryContainer = Color(0xFFD8EDE4),
    onPrimaryContainer = Green
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD0B5),
    onPrimary = Color(0xFF003828),
    secondary = Color(0xFFA8D5C2),
    background = Color(0xFF121412),
    surface = Color(0xFF1C1F1D),
    onBackground = Color(0xFFE4E7E4),
    onSurface = Color(0xFFE4E7E4)
)

@Composable
fun KodeksTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
