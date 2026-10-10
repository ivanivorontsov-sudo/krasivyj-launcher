package org.ivanivorontsov.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.ivanivorontsov.launcher.stop.StopGuard
import org.ivanivorontsov.launcher.stop.StopView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The only «Стоп-экран» UI on the home screen: a small pill in the theme's own colours and fonts.
 * No pop-ups, no separate lock screen — the launcher stays exactly as themed.
 */
@Composable
fun StopBanner(ui: UiState, stop: StopView) {
    val ctx = LocalContext.current
    val colors = LocalColors.current; val fonts = LocalFonts.current; val spec = LocalSpec.current
    var blockedLabel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { StopGuard.intercepted.collect { blockedLabel = it; delay(2600); blockedLabel = null } }
    val text = blockedLabel?.let { "«$it» недоступно · Стоп-экран" }
        ?: if (stop.paused) "⏸ Пауза" + (if (stop.pauseUntil > 0) " до " + SimpleDateFormat("HH:mm", Locale("ru")).format(Date(stop.pauseUntil)) else "") else null
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 6.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(text != null && ui.drag.item == null, enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut() + slideOutVertically { -it / 2 }) {
            Column(Modifier.clip(RoundedCornerShape(50)).background(colors.surface.copy(alpha = 0.86f))
                .clickable { ctx.startActivity(android.content.Intent(ctx, org.ivanivorontsov.launcher.stop.StopActivity::class.java)) }
                .padding(horizontal = 16.dp, vertical = 7.dp)) {
                Text(spec.withCase(text ?: ""), style = labelStyle(colors.onSurface, 13.sp).copy(fontFamily = fonts.heading))
            }
        }
    }
}
