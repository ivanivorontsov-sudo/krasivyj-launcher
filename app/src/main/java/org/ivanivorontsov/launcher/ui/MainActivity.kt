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
        ui.animations = Device.animationsEnabled()
        setContent {
            CompositionLocalProvider(LocalActions provides this) { LauncherRoot(ui) }
        }
        handleGestureContract(intent)
        lifecycleScope.launch {
            app.apps.autoAdded.collect { keys ->
                if (keys.isEmpty()) return@collect
                val names = keys.mapNotNull { k -> app.apps.find(k)?.label }
                if (names.isNotEmpty()) Toast.makeText(this@MainActivity, (if (names.size == 1) "«${names[0]}» добавлено" else "Добавлено: " + names.joinToString(", ") { "«$it»" }) + " на главный экран", Toast.LENGTH_SHORT).show()
                app.apps.autoAddedShown()
            }
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
        lifecycleScope.launch { app.store.config.collect { app.icons.iconPack = it.iconPack; applyRefreshRate(it.highRefreshRate) } }
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

    private var refreshApplied: Boolean? = null
    private fun applyRefreshRate(high: Boolean) {
        if (refreshApplied == high) return
        refreshApplied = high
        Device.applyRefreshRate(window, high)
    }

    /** Key of the app whose window the system is flying into our icon (gesture-nav contract answered). */
    private var contractKey: String? = null
    private var enterDone: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override fun onResume() {
        super.onResume()
        wasResumed = true
        ui.resumed = true
        val anim = Device.animationsEnabled()
        if (ui.animations != anim) ui.animations = anim
        // Nothing heavy here: home must be on screen in the very next frame.
        val rec = ui.lastLaunch
        val returning = ui.launchedApp
        ui.launchedApp = false
        ui.scope.launch { if (ui.turnstile.value != 0f) ui.turnstile.snapTo(0f) }
        val cfg = app.store.config.value
        if (returning && rec != null && anim && cfg.returnAnimation) {
            if (contractKey == rec.key) playPop(rec.key) else playReturn(rec)
        }
        contractKey = null
        window.decorView.post { app.live.refreshNow() }
    }

    private fun playReturn(rec: LaunchRecord) {
        val done = kotlinx.coroutines.CompletableDeferred<Unit>().also { enterDone = it }
        ui.returning = ReturnAnim(rec.key, app.icons.color(rec.key), rec.bounds)
        ui.scope.launch {
            ui.returnProgress.snapTo(0f)
            // Start right after our window's (fast) enter transition, or after 90 ms at most.
            kotlinx.coroutines.withTimeoutOrNull(90) { done.await() }
            ui.returnProgress.animateTo(1f, tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            ui.returning = null
        }
    }

    private fun playPop(key: String) {
        ui.popKey = key
        ui.scope.launch {
            ui.pop.snapTo(0f)
            ui.pop.animateTo(1f, tween(110))
            ui.pop.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 500f))
            ui.popKey = null
        }
    }

    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        enterDone?.complete(Unit)
    }

    override fun onPause() {
        super.onPause(); wasResumed = false; ui.resumed = false; ui.menu = null
        if (ui.drag.item != null) ui.cancelDrag()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (wasResumed) ui.homePresses++
            else {
                // Coming home from an app: show the home page as it was, instantly, with nothing left open.
                ui.drawer.close(animated = false); ui.menu = null; ui.openFolder = null; ui.tileEdit = null; ui.cancelDrag()
            }
            handleGestureContract(intent)
        }
    }

    /**
     * GestureNavContract (Android 11+, no permission): with gesture navigation the system's recents
     * component puts a callback into the HOME intent and asks the launcher where the closing app's icon is,
     * then animates the app window into that rectangle (Launcher3/Lawnchair do the same). Not every OEM
     * build sends it (ColorOS/realme UI often do not); then only our in-window return animation plays.
     */
    private fun handleGestureContract(intent: Intent?) {
        val extras = intent?.getBundleExtra(EXTRA_GESTURE_CONTRACT) ?: return
        intent.removeExtra(EXTRA_GESTURE_CONTRACT)
        if (!app.store.config.value.gestureContract) return
        @Suppress("DEPRECATION") val cn = extras.getParcelable<android.content.ComponentName>(Intent.EXTRA_COMPONENT_NAME) ?: return
        @Suppress("DEPRECATION") val user = extras.getParcelable<android.os.UserHandle>(Intent.EXTRA_USER) ?: return
        @Suppress("DEPRECATION") val cb = extras.getParcelable<android.os.Message>(EXTRA_REMOTE_CALLBACK) ?: return
        val replyTo = cb.replyTo ?: return
        val key = app.apps.keyFor(cn, user)
        val theme = app.store.theme.value
        val layout = app.store.config.value.layoutFor(theme.id)
        val folder = layout.folderOf(key)
        val ids = listOfNotNull(key, folder, "t_$key")
        contractKey = key
        val clockRows = if (theme.clockStyle == org.ivanivorontsov.launcher.core.ClockStyle.NONE) 0 else 2
        val firstCap = theme.columns * (theme.rows - clockRows)
        val cap = (theme.columns * theme.rows).coerceAtLeast(1)
        val holder = folder ?: key
        val idx = layout.home.indexOf(holder).takeIf { it >= 0 } ?: layout.dock.indexOf(holder)
        if (folder != null) ui.revealFolder = folder
        if (idx >= 0) {
            val appPage = if (idx < firstCap) 0 else 1 + (idx - firstCap) / cap
            ui.revealPage = appPage + 1
        }
        ui.scope.launch {
            repeat(12) {
                kotlinx.coroutines.withTimeoutOrNull(32) { androidx.compose.runtime.withFrameNanos { } }
                val r = ids.firstNotNullOfOrNull { ui.iconBounds[it]?.bounds() } ?: return@repeat
                if (r.width < 8f || r.height < 8f) return@repeat
                val loc = IntArray(2)
                window.decorView.getLocationOnScreen(loc)
                val data = Bundle()
                data.putParcelable(EXTRA_ICON_POSITION, android.graphics.RectF(r.left + loc[0], r.top + loc[1], r.right + loc[0], r.bottom + loc[1]))
                data.putParcelable(EXTRA_ICON_SURFACE, null)
                val msg = android.os.Message.obtain()
                msg.copyFrom(cb)
                msg.data = data
                try { replyTo.send(msg) } catch (_: Exception) { contractKey = null }
                return@launch
            }
            contractKey = null
        }
    }

    private companion object {
        const val EXTRA_GESTURE_CONTRACT = "gesture_nav_contract_v1"
        const val EXTRA_ICON_POSITION = "gesture_nav_contract_icon_position"
        const val EXTRA_ICON_SURFACE = "gesture_nav_contract_surface_control"
        const val EXTRA_REMOTE_CALLBACK = "android.intent.extra.REMOTE_CALLBACK"
    }

    // ---------------- LauncherActions ----------------
    private fun opts(bounds: Rect?): ActivityOptions? {
        val b = bounds ?: return null
        val v: View = window.decorView
        val spec = app.store.theme.value
        return ActivityOptions.makeScaleUpAnimation(v, b.left.toInt(), b.top.toInt(), b.width.toInt().coerceAtLeast(1), b.height.toInt().coerceAtLeast(1))
    }

    override fun launch(app: AppInfo, bounds: Rect?) {
        val spec = this.app.store.theme.value
        val doLaunch = {
            try {
                this.app.apps.launcher.startMainActivity(app.component, app.user, bounds?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }, opts(bounds)?.toBundle())
                ui.launchedApp = true
                ui.lastLaunch = LaunchRecord(app.key, bounds, System.currentTimeMillis())
                this.app.store.update { it.bumpUsage(app.key) }
            } catch (e: Exception) { Toast.makeText(this, "Не удалось открыть «${app.label}»", Toast.LENGTH_SHORT).show() }
        }
        if (spec.layout == HomeLayout.TILES && Device.animationsEnabled() && ui.drawer.progress == 0f) {
            ui.scope.launch { ui.turnstile.animateTo(1f, tween(200, easing = MotionSpecs.turnstileEase)); doLaunch() }
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
        val theme = app.store.theme.value.id
        app.store.update { c -> c.editLayout(theme) { it.pinTile(TileItem(kind.name.lowercase(), kind, size = size)) } }
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
