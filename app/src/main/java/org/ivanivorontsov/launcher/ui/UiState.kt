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
    var launchedApp = false
    var actions: LauncherActions? = null
    /** System "animator duration scale" > 0; refreshed on resume, read by the whole tree. */
    var animations by mutableStateOf(true)
    /** Id of the theme whose arrangement is on screen (layouts are stored per theme). */
    var themeId: String = ""

    // ---- drag and drop ----
    val drag = DragState()
    /** Icon (not cell) bounds of everything on the home screen, by app key / folder id / tile id. */
    val iconBounds = HashMap<String, BoundsRef>()
    var openFolder by mutableStateOf<String?>(null)
    var renameFolder by mutableStateOf<String?>(null)
    /** Set by the tiles home: bring the start screen forward when a drag starts in the app list. */
    var showStartPage: (() -> Unit)? = null
    /** Pager page to reveal before answering the home-gesture contract (0 = widgets). */
    var revealPage by mutableStateOf<Int?>(null)
    var revealFolder by mutableStateOf<String?>(null)

    // ---- return-into-icon animation ----
    var lastLaunch: LaunchRecord? = null
    var returning by mutableStateOf<ReturnAnim?>(null)
    val returnProgress = Animatable(1f)
    /** Icon that gets a small "landing" pop (after the system flew the window into it). */
    var popKey by mutableStateOf<String?>(null)
    val pop = Animatable(0f)
    var hint by mutableStateOf<String?>(null)
    /** Haptic tick when a folder target lights up (set by the root). */
    var onFolderHover: (() -> Unit)? = null
}

/** What was launched and from where, for the return animation and the gesture-nav contract. */
class LaunchRecord(val key: String, val bounds: Rect?, val time: Long)

class ReturnAnim(val key: String, val color: Int?, val fallback: Rect?)
