package org.ivanivorontsov.launcher.ui

import android.app.ActivityOptions
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.ivanivorontsov.launcher.JankMonitor
import org.ivanivorontsov.launcher.LauncherApp
import org.ivanivorontsov.launcher.core.GestureAction
import org.ivanivorontsov.launcher.core.HomeLayout
import org.ivanivorontsov.launcher.core.Motion
import org.ivanivorontsov.launcher.core.TileItem
import org.ivanivorontsov.launcher.core.TileKind
import org.ivanivorontsov.launcher.core.TileSize
import org.ivanivorontsov.launcher.core.WidgetItem
import org.ivanivorontsov.launcher.data.AppInfo
import org.ivanivorontsov.launcher.data.Device
import org.ivanivorontsov.launcher.service.LockService

class MainActivity : ComponentActivity(), LauncherActions, WidgetBinder {
    private val app get() = LauncherApp.instance
    private lateinit var ui: UiState
    private var wasResumed = false
    private var pendingWidgetId = -1
    private var pendingWidgetInfo: AppWidgetProviderInfo? = null

    private val photoPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { ui.permissionsVersion++ }
    private val calendarPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ui.permissionsVersion++ }
    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) configureOrAdd() else dropPendingWidget()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.auto(AColor.TRANSPARENT, AColor.TRANSPARENT), SystemBarStyle.auto(AColor.TRANSPARENT, AColor.TRANSPARENT))
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        // Animations need a MonotonicFrameClock: AndroidUiDispatcher.Main carries the Choreographer-backed one.
        ui = UiState(kotlinx.coroutines.CoroutineScope(lifecycleScope.coroutineContext + androidx.compose.ui.platform.AndroidUiDispatcher.Main))
        ui.actions = this
        setContent {
            CompositionLocalProvider(LocalActions provides this) { LauncherRoot(ui) }
        }
        lifecycleScope.launch {
            app.store.theme.collect { t ->
                val c = WindowCompat.getInsetsController(window, window.decorView)
                val lightBars = !t.dark
                c.isAppearanceLightStatusBars = lightBars; c.isAppearanceLightNavigationBars = lightBars
                // Hide the system wallpaper window when the theme paints its own (saves composition work).
                if (t.wallpaper == org.ivanivorontsov.launcher.core.WallpaperStyle.SYSTEM && t.layout != HomeLayout.TILES) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            }
        }
        lifecycleScope.launch { app.store.config.collect { app.icons.iconPack = it.iconPack; Device.applyRefreshRate(window, it.highRefreshRate) } }
        // Cross-window blur behind the drawer when the phone wallpaper is visible (Android 12+, if the device supports it).
        if (Build.VERSION.SDK_INT >= 31) lifecycleScope.launch {
            combine(snapshotFlow { ui.drawer.progress > 0.5f }, app.store.theme) { open, t -> open && t.blur && t.wallpaper == org.ivanivorontsov.launcher.core.WallpaperStyle.SYSTEM }
                .distinctUntilChanged().collect { on -> setBlurBehind(on) }
        }
        JankMonitor.attach(this)
    }

    private fun setBlurBehind(on: Boolean) {
        if (Build.VERSION.SDK_INT < 31) return
        try {
            if (!windowManager.isCrossWindowBlurEnabled) return
            val lp = window.attributes
            if (on) { lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND; lp.blurBehindRadius = 48 }
            else { lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv(); lp.blurBehindRadius = 0 }
            window.attributes = lp
        } catch (_: Exception) {}
    }

    override fun onStart() { super.onStart(); try { WidgetHostHolder.host(this).startListening() } catch (_: Exception) {} }
    override fun onStop() { super.onStop(); try { WidgetHostHolder.host(this).stopListening() } catch (_: Exception) {} }

    override fun onResume() {
        super.onResume()
        wasResumed = true
        ui.resumed = true
        app.live.refreshNow()
        Device.applyRefreshRate(window, app.store.config.value.highRefreshRate)
        val anim = Device.animationsEnabled()
        if (ui.launchedApp) {
            ui.launchedApp = false
            ui.scope.launch {
                if (ui.turnstile.value > 0f) ui.turnstile.animateTo(0f, if (anim) tween(420, easing = MotionSpecs.turnstileEase) else tween(0))
                else if (anim) { ui.enter.snapTo(0f); ui.enter.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 260f)) }
            }
        } else ui.scope.launch { ui.turnstile.snapTo(0f); ui.enter.snapTo(1f) }
    }

    override fun onPause() { super.onPause(); wasResumed = false; ui.resumed = false; ui.menu = null }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (wasResumed) ui.homePresses++ else { ui.drawer.close(animated = false); ui.menu = null }
        }
    }

    // ---------------- LauncherActions ----------------
    private fun opts(bounds: Rect?): ActivityOptions? {
        val b = bounds ?: return null
        val v: View = window.decorView
        val spec = app.store.theme.value
        return if (spec.motion == Motion.TURNSTILE || spec.motion == Motion.PIXEL_STEP) ActivityOptions.makeClipRevealAnimation(v, b.left.toInt(), b.top.toInt(), b.width.toInt(), b.height.toInt())
        else ActivityOptions.makeScaleUpAnimation(v, b.left.toInt(), b.top.toInt(), b.width.toInt(), b.height.toInt())
    }

    override fun launch(app: AppInfo, bounds: Rect?) {
        val spec = this.app.store.theme.value
        val doLaunch = {
            try {
                this.app.apps.launcher.startMainActivity(app.component, app.user, bounds?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }, opts(bounds)?.toBundle())
                ui.launchedApp = true
                this.app.store.update { it.bumpUsage(app.key) }
            } catch (e: Exception) { Toast.makeText(this, "Не удалось открыть «${app.label}»", Toast.LENGTH_SHORT).show() }
        }
        if (spec.layout == HomeLayout.TILES && Device.animationsEnabled() && ui.drawer.progress == 0f) {
            ui.scope.launch { ui.turnstile.animateTo(1f, tween(260, easing = MotionSpecs.turnstileEase)); doLaunch() }
        } else doLaunch()
    }

    override fun launchShortcut(app: AppInfo, info: ShortcutInfo, bounds: Rect?) {
        try {
            this.app.apps.launcher.startShortcut(info, null, opts(bounds)?.toBundle()); ui.launchedApp = true
        } catch (e: Exception) { Toast.makeText(this, "Ярлык недоступен", Toast.LENGTH_SHORT).show() }
    }

    override fun appInfo(app: AppInfo, bounds: Rect?) {
        try { this.app.apps.launcher.startAppDetailsActivity(app.component, app.user, null, opts(bounds)?.toBundle()) } catch (_: Exception) {}
    }

    override fun uninstall(app: AppInfo) = Device.uninstall(this, app.pkg)

    override fun openSettings(section: String?) {
        startActivity(Intent(this, SettingsActivity::class.java).apply { section?.let { putExtra("section", it) } })
    }

    override fun addWidget() { ui.widgetPicker = true }

    override fun requestPhotos() { photoPerms.launch(app.live.photoPermissions()) }
    override fun requestCalendar() { calendarPerm.launch(android.Manifest.permission.READ_CALENDAR) }

    override fun gesture(action: GestureAction) {
        when (action) {
            GestureAction.NONE -> {}
            GestureAction.DRAWER -> ui.drawer.open()
            GestureAction.NOTIFICATIONS -> if (!Device.expandNotifications(this)) Toast.makeText(this, "Шторку открыть не удалось", Toast.LENGTH_SHORT).show()
            GestureAction.SEARCH -> ui.drawer.open()
            GestureAction.LOCK -> if (!LockService.lock()) {
                Toast.makeText(this, "Включите «Уют: блокировка экрана» в Спец. возможностях", Toast.LENGTH_LONG).show()
                Device.openAccessibilitySettings(this)
            }
            GestureAction.SETTINGS -> openSettings()
            GestureAction.WIDGETS -> {}
        }
    }

    override fun showMenu(app: AppInfo?, tile: TileItem?, anchor: Rect) {
        ui.menu = buildAppMenu(ui, app, tile, this.app.store.theme.value, this.app.store.config.value, this, anchor)
    }

    override fun homeMenu(anchor: Rect) {
        val spec = app.store.theme.value
        val e = ArrayList<MenuEntry>()
        e += MenuEntry("Темы и стиль") { openSettings("themes") }
        if (spec.layout == HomeLayout.TILES) {
            e += MenuEntry("Плитка: часы") { pinSpecialTile(TileKind.CLOCK) }
            e += MenuEntry("Плитка: фото") { pinSpecialTile(TileKind.PHOTOS) }
            e += MenuEntry("Плитка: календарь") { pinSpecialTile(TileKind.CALENDAR) }
        } else e += MenuEntry("Добавить виджет") { addWidget() }
        e += MenuEntry("Обои телефона") { Device.openWallpaperPicker(this) }
        e += MenuEntry("Настройки лаунчера") { openSettings() }
        ui.menu = MenuState(anchor, title = "Уют", entries = e)
    }

    override fun pinSpecialTile(kind: TileKind) {
        val size = if (kind == TileKind.CLOCK) TileSize.WIDE else TileSize.MEDIUM
        app.store.update { it.pinTile(TileItem(kind.name.lowercase(), kind, size = size)) }
        // Permission is requested only when the live tile that needs it is pinned.
        if (kind == TileKind.PHOTOS && !app.live.hasPhotoAccess()) requestPhotos()
        if (kind == TileKind.CALENDAR && !app.live.hasCalendarAccess()) requestCalendar()
    }

    // ---------------- widgets ----------------
    override fun bindWidget(info: AppWidgetProviderInfo) {
        val host = WidgetHostHolder.host(this)
        val id = host.allocateAppWidgetId()
        pendingWidgetId = id; pendingWidgetInfo = info
        val awm = AppWidgetManager.getInstance(this)
        val ok = try { awm.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) } catch (_: Exception) { false }
        if (ok) configureOrAdd()
        else bindWidget.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
        })
    }

    private fun configureOrAdd() {
        val info = pendingWidgetInfo ?: return
        if (info.configure != null) {
            try {
                // The host API handles cross-profile and non-exported configure activities (incl. Android 14+).
                WidgetHostHolder.host(this).startAppWidgetConfigureActivityForResult(this, pendingWidgetId, 0, 77, null); return
            } catch (_: Exception) { addPendingWidget(); return }
        }
        addPendingWidget()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 77) { if (resultCode == RESULT_OK) addPendingWidget() else dropPendingWidget() }
    }

    private fun addPendingWidget() {
        val info = pendingWidgetInfo ?: return
        val hDp = (info.minHeight / resources.displayMetrics.density).toInt().coerceIn(90, 420)
        val id = pendingWidgetId
        app.store.update { it.copy(widgets = it.widgets + WidgetItem(id, hDp)) }
        pendingWidgetId = -1; pendingWidgetInfo = null
    }

    private fun dropPendingWidget() {
        if (pendingWidgetId != -1) try { WidgetHostHolder.host(this).deleteAppWidgetId(pendingWidgetId) } catch (_: Exception) {}
        pendingWidgetId = -1; pendingWidgetInfo = null
    }
}
