package org.ivanivorontsov.mudrost.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF3B4F94),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF14204D),
    secondary = Color(0xFF5B5D72),
    secondaryContainer = Color(0xFFE0E0F9),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFF8A5A00),
    tertiaryContainer = Color(0xFFFFE8C2),
    onTertiaryContainer = Color(0xFF2C1A00),
    background = Color(0xFFF7F5F0),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFF7F5F0),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0EEE9),
    surfaceContainerHigh = Color(0xFFEAE8E3),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFD6D3DC),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB8C4FF),
    onPrimary = Color(0xFF1E2C63),
    primaryContainer = Color(0xFF34457F),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFC4C5DD),
    secondaryContainer = Color(0xFF3F4257),
    onSecondaryContainer = Color(0xFFE0E0F9),
    tertiary = Color(0xFFF4C361),
    tertiaryContainer = Color(0xFF4A3510),
    onTertiaryContainer = Color(0xFFFFE8C2),
    background = Color(0xFF15171C),
    onBackground = Color(0xFFE4E2E6),
    surface = Color(0xFF15171C),
    onSurface = Color(0xFFE4E2E6),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceContainerLowest = Color(0xFF101216),
    surfaceContainerLow = Color(0xFF1D2026),
    surfaceContainer = Color(0xFF21242B),
    surfaceContainerHigh = Color(0xFF2B2E36),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF46464F),
)

private val base = Typography()
private val Type = base.copy(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp),
)

val QuoteStyle = TextStyle(fontFamily = FontFamily.Serif, fontSize = 19.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal)

@Composable
fun MudrostTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, content = content)
}
