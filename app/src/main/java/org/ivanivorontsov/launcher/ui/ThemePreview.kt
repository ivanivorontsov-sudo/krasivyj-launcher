package org.ivanivorontsov.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ivanivorontsov.launcher.core.HomeLayout
import org.ivanivorontsov.launcher.core.IconShape
import org.ivanivorontsov.launcher.core.ThemeSpec
import org.ivanivorontsov.launcher.core.WallpaperStyle
import java.util.concurrent.ConcurrentHashMap

private val thumbCache = ConcurrentHashMap<String, ImageBitmap>()

/** Live miniature of a theme: its own wallpaper, fonts, icon shape, layout and colours. */
@Composable
fun ThemePreview(spec: ThemeSpec, selected: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val key = spec.wallpaper.name + spec.wallpaperColors.hashCode() + spec.background + spec.id + spec.wallpaperDim
    val thumb by produceState(thumbCache[key], key) {
        if (value == null && spec.wallpaper != WallpaperStyle.SYSTEM) value = withContext(Dispatchers.Default) { Wallpapers.render(spec, 270, 540).asImageBitmap().also { thumbCache[key] = it } }
    }
    val heading = FontRegistry.family(spec.headingFont); val clock = FontRegistry.family(spec.clockFont); val body = FontRegistry.family(spec.bodyFont)
    val accent = spec.accent.c(); val onWall = spec.onWallpaper.c()
    Column(modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(0.56f).clip(RoundedCornerShape(18.dp))
            .border(if (selected) 3.dp else 1.dp, if (selected) accent else Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(18.dp))) {
            val t = thumb
            if (t != null) Image(t, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accent.copy(alpha = 0.7f), spec.background.c()))))
            val w = maxWidth
            when (spec.layout) {
                HomeLayout.GRID -> Column(Modifier.fillMaxSize().padding(10.dp)) {
                    Text(timeText(System.currentTimeMillis()), color = onWall, fontFamily = clock, fontWeight = FontWeight(spec.clockWeight), fontSize = 24.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
                    Spacer(Modifier.height(10.dp))
                    repeat(3) { r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            repeat(4) { c ->
                                val col = lerp(accent, Color(0xFFFFFFFF.toInt()), ((r * 4 + c) % 5) * 0.15f)
                                Box(Modifier.size(w / 7).clip(PathShape(if (spec.iconShape == IconShape.ORIGINAL) IconShape.SQUIRCLE else spec.iconShape)).background(col))
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.fillMaxWidth().height(w / 5).clip(RoundedCornerShape(spec.cornerDp.coerceAtMost(20).dp / 2)).background(onWall.copy(alpha = 0.18f)))
                }
                HomeLayout.MINIMAL -> Column(Modifier.fillMaxSize().padding(12.dp)) {
                    Text(timeText(System.currentTimeMillis()), color = onWall, fontFamily = clock, fontWeight = FontWeight(spec.clockWeight), fontSize = 30.sp)
                    Spacer(Modifier.weight(1f))
                    listOf("телефон", "сообщения", "камера", "карты").forEach {
                        Text(spec.labelPrefix + spec.withCase(it), color = onWall, fontFamily = heading, fontSize = 14.sp, maxLines = 1)
                    }
                    Spacer(Modifier.weight(0.4f))
                }
                HomeLayout.TILES -> Column(Modifier.fillMaxSize().padding(8.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val u = (w - 16.dp - 12.dp) / 4
                    @Composable fun tile(width: androidx.compose.ui.unit.Dp, label: String) = Box(Modifier.size(width, u * 2 + 4.dp).background(if (spec.tileFill == org.ivanivorontsov.launcher.core.TileFill.TRANSPARENT) Color.White.copy(alpha = 0.25f) else accent).padding(4.dp), contentAlignment = Alignment.BottomStart) {
                        Text(label, color = Color.White, fontSize = 7.sp, fontFamily = body)
                    }
                    tile(u * 4 + 12.dp, timeText(System.currentTimeMillis()))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { tile(u * 2 + 4.dp, "телефон"); tile(u * 2 + 4.dp, "фото") }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { tile(u * 2 + 4.dp, "почта"); tile(u * 2 + 4.dp, "музыка") }
                }
            }
        }
        Text(spec.name, fontFamily = heading, fontSize = 17.sp, maxLines = 1, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
        if (spec.description.isNotEmpty()) Text(spec.description, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), modifier = Modifier.padding(start = 2.dp))
    }
}
