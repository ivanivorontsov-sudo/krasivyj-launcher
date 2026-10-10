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

    private var refreshApplied: Boolean? = null
    private fun applyRefreshRate(high: Boolean) {
        if (refreshApplied == high) return
        refreshApplied = high
        Device.applyRefreshRate(window, high)
    }

    /** Key of the app whose window the system is flying into our icon (gesture-nav contract answered). */
    private var contractKey: String? = null
    /** Completed when our window is really on screen (enter transition done or window focused). */
    private var visible: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    private var stoppedAt = 0L

    override fun onResume() {
        super.onResume()
        // Settings live in a separate task. Reconcile the persisted theme before the
        // first visible frame so wallpaper and shaped-icon caches cannot stay stale.
        if (app.store.refreshThemeFromPreferences()) app.icons.clear()
        wasResumed = true
        ui.resumed = true
        val anim = Device.animationsEnabled()
        if (ui.animations != anim) ui.animations = anim
        // 1.2: zero work here. The return card was already composed (invisible) in onStop, so the very first
        // visible frame can animate. Everything else is deferred until after that frame.
        val pending = ui.returning
        if (pending != null) {
            if (contractKey == pending.key) { ui.returning = null; playPop(pending.key) } else playReturn()
        } else ui.scope.launch { if (ui.turnstile.value != 0f) ui.turnstile.snapTo(0f) }
        ui.launchedApp = false
        contractKey = null
        window.decorView.post { window.decorView.post { app.live.refreshNow() } }
    }

    override fun onStop() {
        super.onStop()
        try { WidgetHostHolder.host(this).stopListening() } catch (_: Exception) {}
        stoppedAt = android.os.SystemClock.uptimeMillis()
        // Pre-warm the return animation while we are invisible: compose the full-screen card now, so the
        // first frame after any way back home (gesture, Home button, Back, recents) already shows it.
        val rec = ui.lastLaunch
        if (ui.launchedApp && rec != null && Device.animationsEnabled() && app.store.config.value.returnAnimation &&
            System.currentTimeMillis() - rec.time < 6 * 60 * 60_000L) {
            ui.returning = ReturnAnim(rec.key, app.icons.color(rec.key), rec.bounds)
            ui.scope.launch { ui.returnProgress.snapTo(0f); ui.homeZoom.snapTo(0f); if (ui.turnstile.value != 0f) ui.turnstile.snapTo(0f) }
        }
        visible = kotlinx.coroutines.CompletableDeferred()
    }

    /**
     * Starts as soon as our window is actually visible. On ColorOS / realme UI with gesture navigation the system
     * keeps a third-party home hidden for up to ~1 s (wallpaper only) after the gesture; a fixed 90 ms start (1.1)
     * made the whole animation run while nobody could see it. Now it waits for window focus / the end of the
     * enter transition (whichever comes first, at most 1.5 s) and then plays a spring.
     */
    private fun playReturn() {
        val gate = visible
        val key = ui.returning?.key
        ui.scope.launch {
            if (gate != null) kotlinx.coroutines.withTimeoutOrNull(1_500) { gate.await() }
            kotlinx.coroutines.coroutineScope {
                launch { ui.homeZoom.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 320f)) }
                // Medium-low stiffness, slightly under-damped: ~420 ms with a soft overshoot into the icon.
                ui.returnProgress.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 340f, visibilityThreshold = 0.002f))
            }
            ui.returning = null
            // The real icon takes over with a small spring "pop" (scale overshoot).
            if (key != null) playPop(key)
        }
    }

    private fun playPop(key: String) {
        ui.popKey = key
        ui.scope.launch {
            ui.homeZoom.snapTo(1f)
            ui.pop.snapTo(0f)
            ui.pop.animateTo(1f, tween(110))
            ui.pop.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 500f))
            ui.popKey = null
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) visible?.complete(Unit)
    }

    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        visible?.complete(Unit)
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
            // The home pager may need several frames to reveal the page containing the
            // target icon after a gesture-home intent. Give Compose up to ~750 ms to lay it out.
            fun replyWithBounds(r: Rect): Boolean {
                val loc = IntArray(2)
                window.decorView.getLocationOnScreen(loc)
                val data = Bundle()
                data.putParcelable(EXTRA_ICON_POSITION, android.graphics.RectF(r.left + loc[0], r.top + loc[1], r.right + loc[0], r.bottom + loc[1]))
                data.putParcelable(EXTRA_ICON_SURFACE, null)
                val msg = android.os.Message.obtain()
                msg.copyFrom(cb)
                msg.data = data
                return try { replyTo.send(msg); true } catch (_: Exception) { contractKey = null; false }
            }

            repeat(45) {
                kotlinx.coroutines.withTimeoutOrNull(32) { androidx.compose.runtime.withFrameNanos { } }
                val r = ids.firstNotNullOfOrNull { ui.iconBounds[it]?.bounds() } ?: return@repeat
                if (r.width < 8f || r.height < 8f) return@repeat
                replyWithBounds(r)
                return@launch
            }

            // If the icon never reports fresh bounds (some OEM launchers skip a layout
            // pass during the gesture), answer with the launch-time icon rectangle instead
            // of silently dropping the system's app-to-icon transition.
            val fallback = ui.lastLaunch?.takeIf { it.key == key }?.bounds
            if (fallback != null && fallback.width >= 8f && fallback.height >= 8f) {
                replyWithBounds(fallback)
            } else contractKey = null
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
