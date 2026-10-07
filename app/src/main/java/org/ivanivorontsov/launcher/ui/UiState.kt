package org.ivanivorontsov.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.ivanivorontsov.launcher.data.AppInfo
import org.ivanivorontsov.launcher.core.TileItem

/** Drawer position 0..1 driven directly by the finger, settled with physics (time-based → same at any Hz). */
@Stable
class DrawerController(private val scope: CoroutineScope) {
    var progress by mutableFloatStateOf(0f); private set
    var animationsEnabled = true
    private var job: Job? = null
    val isOpen get() = progress > 0.5f

    fun dragBy(delta: Float) { job?.cancel(); progress = (progress + delta).coerceIn(0f, 1f) }

    /** velocity in progress-units per second, positive = opening. */
    fun settle(velocity: Float) {
        val target = when { velocity > 1.2f -> 1f; velocity < -1.2f -> 0f; progress > 0.45f -> 1f; else -> 0f }
        animateTo(target, velocity)
    }

    fun open() = animateTo(1f, 0f)
    fun close(animated: Boolean = true) { if (!animated || !animationsEnabled) { job?.cancel(); progress = 0f } else animateTo(0f, 0f) }

    private fun animateTo(target: Float, velocity: Float) {
        job?.cancel()
        if (!animationsEnabled) { progress = target; return }
        job = scope.launch {
            animate(progress, target, velocity, spring(dampingRatio = 0.92f, stiffness = 420f)) { v, _ -> progress = v.coerceIn(0f, 1f) }
        }
    }
}

data class MenuEntry(val label: String, val danger: Boolean = false, val onClick: () -> Unit)

data class MenuState(val anchor: Rect, val app: AppInfo? = null, val tile: TileItem? = null, val title: String? = null, val entries: List<MenuEntry> = emptyList())

@Stable
class UiState(val scope: CoroutineScope) {
    val drawer = DrawerController(scope)
    var menu by mutableStateOf<MenuState?>(null)
    var renameTarget by mutableStateOf<AppInfo?>(null)
    var widgetPicker by mutableStateOf(false)
    var homePresses by mutableIntStateOf(0)
    var permissionsVersion by mutableIntStateOf(0)
    var resumed by mutableStateOf(true)
    var widgetEdit by mutableStateOf(false)
    var tileEdit by mutableStateOf<String?>(null)
    /** 0 = tiles in place, 1 = swung away (turnstile). */
    val turnstile = Animatable(0f)
    /** 1 = home at rest, <1 = zoomed out (return-from-app animation). */
    val enter = Animatable(1f)
    var launchedApp = false
    var actions: LauncherActions? = null
}
