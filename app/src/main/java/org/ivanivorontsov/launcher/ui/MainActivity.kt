package org.ivanivorontsov.launcher.ui

import android.app.ActivityOptions
import android.hardware.display.DisplayManager
import android.os.SystemClock
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
    private var displayRefresh: DisplayManager.DisplayListener? = null
    /** System close animation owns the first frames; our in-window flight starts after it. */
    private var returnArmed = false
    /** 1.3: a HOME intent arrived since onStop → the user pressed/swiped Home; otherwise it was Back. */
    private var homeIntentSinceStop = false
    /** 1.3.1: app key whose icon rect we gave to the system through GestureNavContract (null = not asked). */
    private var contractKey: String? = null
    private fun fromBackGuess() = !homeIntentSinceStop
    private val contractFinish = android.os.Messenger(android.os.Handler(android.os.Looper.getMainLooper()) { m ->
        // The system says its fly-into-icon animation is over: reveal the real icon.
        if (m.what == 0) revealContractIcon()
        true
    })

    private fun revealContractIcon() {
        if (ui.returnStyle != org.ivanivorontsov.launcher.core.ReturnStyle.CONTRACT || ui.returning == null) return
        returnArmed = true
        ui.scope.launch {
            ui.returnProgress.animateTo(1f, tween((140 / app.store.config.value.animSpeed.coerceIn(0.4f, 2f)).toInt(), easing = androidx.compose.animation.core.LinearOutSlowInEasing))
            ui.returning = null
            contractKey = null
        }
    }
    private var resumeUptime = 0L

    /**
     * 1.3.5: unlock animation. Before, the home "enter" animation was one-shot per activity
     * (ui.hasAnimatedIn), so it only replayed when Android had killed/recreated the launcher —
     * i.e. after the phone had stayed locked for a long time. Now every screen-off arms it and
     * every unlock (ACTION_USER_PRESENT, or resume with the keyguard gone) plays it again.
     */
    private var unlockArmed = false
    private var stoppedByScreenOff = false
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> armUnlock()
                Intent.ACTION_USER_PRESENT -> playUnlock()
            }
        }
    }
    private fun homeOnTop() = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) || stoppedByScreenOff
    private fun armUnlock() {
        if (!homeOnTop() || ui.launchedApp || ui.returning != null) return
        unlockArmed = true
        // Screen is off: hiding the grid now is invisible and avoids a full-grid first frame.
        if (Device.animationsEnabled()) ui.scope.launch { ui.enter.snapTo(0f) }
    }
    private fun keyguardLocked(): Boolean =
        (getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked
    private fun playUnlock() {
        if (!unlockArmed) return
        if (keyguardLocked()) return
        unlockArmed = false
        ui.hasAnimatedIn = true
        ui.scope.launch {
            if (ui.animations && Device.animationsEnabled()) {
                ui.enter.snapTo(0f)
                ui.enter.animateTo(1f, MotionSpecs.enter(app.store.theme.value.motion, true))
            } else ui.enter.snapTo(1f)
        }
    }
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
        androidx.core.content.ContextCompat.registerReceiver(this, screenReceiver, android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
        }, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
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
                WallpaperApplier.onTheme(this@MainActivity, t)
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

    override fun onStart() {
        super.onStart()
        try { WidgetHostHolder.host(this).startListening() } catch (_: Exception) {}
        if (displayRefresh == null) {
            displayRefresh = Device.registerDisplayRefresh(this) { app.store.config.value.highRefreshRate }
        }
    }

    // Re-assert the preferred mode on every resume: Android/OEM task transitions can reset it.
    private fun applyRefreshRate(high: Boolean) = Device.applyRefreshRate(window, high)

    /** Completed when our window is really on screen (enter transition done or window focused). */
    private var visible: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override fun onResume() {
        super.onResume()
        resumeUptime = SystemClock.uptimeMillis()
        stoppedByScreenOff = false
        // No secure lock screen (or USER_PRESENT missed by the OEM): play on resume / focus.
        if (unlockArmed) window.decorView.post { playUnlock() }
        ui.returning?.let { r ->
            val cfg = app.store.config.value
            val style = org.ivanivorontsov.launcher.core.resolveReturn(cfg.homeAnim, cfg.backAnim, fromBack = !homeIntentSinceStop)
            ui.returnStyle = style
            if (contractKey != null && contractKey == r.key && !fromBackGuess()) {
                // 1.3.1: the system already flies the window into our icon — draw nothing on top.
                ui.returnStyle = org.ivanivorontsov.launcher.core.ReturnStyle.CONTRACT
                ui.scope.launch { ui.homeZoom.snapTo(1f) }
            } else if (style == org.ivanivorontsov.launcher.core.ReturnStyle.NONE) {
                // "Системная": nothing of ours on top of the system transition.
                ui.returning = null
                ui.scope.launch { ui.homeZoom.snapTo(1f); ui.returnProgress.snapTo(1f) }
            }
        }
        val comingHome = ui.returning != null
        // Cached mode id only: ColorOS drops a third-party launcher to 60 Hz for the
        // swipe-home transition unless this window has already asked for the high mode.
        applyRefreshRate(app.store.config.value.highRefreshRate)
        // Settings live in a separate task. Reconcile the persisted theme before the
        // first visible frame so wallpaper and shaped-icon caches cannot stay stale.
        // Skip it on the way back from an app: that path must stay as cheap as Back.
        if (!comingHome && app.store.refreshThemeFromPreferences()) app.icons.clear()
        wasResumed = true
        // Live tiles pause while we are stopped. This flag is not an animation key:
        // the home grid must stay composed, and `enter` / hasAnimatedIn are not reset here.
        // While the system close animation is running, leave tiles paused.
        if (!comingHome) ui.resumed = true
        val anim = Device.animationsEnabled()
        if (ui.animations != anim) ui.animations = anim
        if (comingHome) {
            if (window.decorView.hasWindowFocus()) visible?.complete(Unit)
            // onEnterAnimationComplete starts the in-window flight. If ColorOS never
            // sends it, start after the system transition would have finished.
            // 1.3: the old code only retried after 450 ms when the first callback came within the
            // 80 ms guard, which was the visible "home appears, then pauses, then animates" stutter.
            window.decorView.postDelayed({ beginReturnFlight() }, 90)
            window.decorView.postDelayed({ beginReturnFlight() }, 260)
        } else ui.scope.launch { if (ui.turnstile.value != 0f) ui.turnstile.snapTo(0f) }
        ui.launchedApp = false
        if (comingHome) window.decorView.postDelayed({
            ui.resumed = true
            app.live.refreshNow()
            DigitOverlay.poke(this)
        }, 450)
        else {
            window.decorView.post { window.decorView.post { app.live.refreshNow() } }
            DigitOverlay.poke(this)
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedByScreenOff = !ui.launchedApp && !(getSystemService(POWER_SERVICE) as android.os.PowerManager).isInteractive
        try { WidgetHostHolder.host(this).stopListening() } catch (_: Exception) {}
        resumeUptime = 0L
        Device.unregisterDisplayRefresh(this, displayRefresh)
        displayRefresh = null
        // Remember the return, but leave progress at 1 so the card stays invisible while the
        // system is still animating the closing app. Snapping to 0 here drew a full-screen
        // card on the first revealed frame and stuttered that transition.
        val rec = ui.lastLaunch
        if (ui.launchedApp && rec != null && Device.animationsEnabled() && app.store.config.value.returnAnimation &&
            System.currentTimeMillis() - rec.time < 6 * 60 * 60_000L) {
            ui.returning = ReturnAnim(rec.key, app.icons.color(rec.key), rec.bounds, folderId = rec.folderId)
            returnArmed = false
            // 1.3: pre-shrink the grid while invisible. Before, it was drawn at 100 % on the first
            // revealed frame and then jumped to 94 % when the flight started — a one-frame "twitch".
            ui.scope.launch { ui.homeZoom.snapTo(0f) }
        }
        homeIntentSinceStop = false
        contractKey = null
        visible = kotlinx.coroutines.CompletableDeferred()
    }

    /**
     * Play once the resumed home window is visible. If the OEM misses the focus/transition
     * callback, don't hold the Home gesture for 1.5 seconds: use a short 360 ms guard instead.
     * All progress is frame-clock based, not frame-count based, so 144 Hz gets every available frame.
     */
    private fun beginReturnFlight() {
        if (ui.returning == null || returnArmed || resumeUptime == 0L) return
        if (SystemClock.uptimeMillis() - resumeUptime < 80) return
        returnArmed = true
        ui.resumed = true
        playReturn()
    }

    private fun playReturn() {
        if (ui.returnStyle == org.ivanivorontsov.launcher.core.ReturnStyle.CONTRACT) {
            // Older systems never send the finish callback: reveal once our window is visible.
            window.decorView.postDelayed({ revealContractIcon() }, 120)
            return
        }
        val gate = visible
        val key = ui.returning?.key
        ui.scope.launch {
            // onStop schedules a reset on the same scope; on fast realme/ColorOS task
            // transitions that reset can race with onResume and leave progress already at 1.
            // Reset here, in the animation coroutine, so every return starts at frame zero.
            ui.returnProgress.snapTo(0f)
            ui.homeZoom.snapTo(0f)
            if (window.decorView.hasWindowFocus()) gate?.complete(Unit)
            // Do not wait for the OEM's focus callback: the overlay is already composed with
            // the launch-time icon bounds fallback. The old focus hand-off left only wallpaper
            // visible while ColorOS delayed the launcher surface.
            val speed = app.store.config.value.animSpeed
            val st = ui.returnStyle
            val (damp, stiff) = when (st) {
                org.ivanivorontsov.launcher.core.ReturnStyle.SPRING -> 0.70f to 340f
                org.ivanivorontsov.launcher.core.ReturnStyle.FADE -> 1f to 420f
                org.ivanivorontsov.launcher.core.ReturnStyle.ZOOM -> 0.90f to 300f
                org.ivanivorontsov.launcher.core.ReturnStyle.SLIDE_UP, org.ivanivorontsov.launcher.core.ReturnStyle.SLIDE_SIDE -> 0.92f to 320f
                else -> 0.84f to 270f
            }
            val k = org.ivanivorontsov.launcher.core.speedStiffness(stiff, speed)
            kotlinx.coroutines.coroutineScope {
                launch {
                    ui.homeZoom.animateTo(1f, spring(dampingRatio = maxOf(damp, 0.8f), stiffness = k, visibilityThreshold = 0.001f))
                }
                if (st.card) ui.returnProgress.animateTo(1f, spring(dampingRatio = damp, stiffness = k, visibilityThreshold = 0.001f))
                else ui.returnProgress.snapTo(1f)
            }
            ui.returning = null
            // 1.3.1: no landing "pop" — the scale bump at the end read as a twitch.
        }
    }

    private fun playPop(key: String) {
        ui.popKey = key
        ui.scope.launch {
            ui.homeZoom.snapTo(1f)
            ui.pop.snapTo(0f)
            ui.pop.animateTo(1f, tween(145, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            ui.pop.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 420f, visibilityThreshold = 0.001f))
            ui.popKey = null
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && unlockArmed) playUnlock()
        if (hasFocus) visible?.complete(Unit)
    }

    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        visible?.complete(Unit)
        beginReturnFlight()
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause(); wasResumed = false; ui.resumed = false; ui.menu = null
        if (ui.drag.item != null) ui.cancelDrag()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeIntentSinceStop = true
            // Ask for 144 Hz before the system starts compositing this window.
            applyRefreshRate(app.store.config.value.highRefreshRate)
            // homePresses only closes overlays or scrolls to the main page. It must not
            // restart the one-shot home enter animation (hasAnimatedIn / enter).
            // A swipe-up HOME while we are still marked resumed (resumeWhilePausing) is
            // still a return from an app: do not scroll the pager.
            val returning = ui.returning != null || ui.launchedApp
            if (wasResumed && !returning) ui.homePresses++
            else {
                ui.drawer.close(animated = false); ui.menu = null; ui.tileEdit = null; ui.cancelDrag()
                // An app opened from a folder must come back into that folder, not onto the grid.
                val folder = ui.lastLaunch?.folderId
                if (folder != null) ui.openFolder = folder
            }
            handleGestureContract(intent)
        }
    }

    /**
     * GestureNavContract (Android 11+, no permission): recents asks where the closing app's icon is,
     * then animates the window into that rectangle. Reply immediately with bounds we already have.
     * Waiting for a layout pass, or scrolling the pager to the icon's page, freezes the gesture
     * (Back does not send this contract, which is why Back felt fine).
     */
    private fun handleGestureContract(intent: Intent?) {
        val extras = intent?.getBundleExtra(EXTRA_GESTURE_CONTRACT) ?: return
        intent.removeExtra(EXTRA_GESTURE_CONTRACT)
        if (!app.store.config.value.gestureContract) return
        // 1.3.3: on ColorOS replying to the contract means the system skips its close animation and we
        // draw nothing either. With our own landing on, never reply: our card always plays.
        if (app.store.config.value.ownLanding) return
        @Suppress("DEPRECATION") val cn = extras.getParcelable<android.content.ComponentName>(Intent.EXTRA_COMPONENT_NAME)
        @Suppress("DEPRECATION") val cb = extras.getParcelable<android.os.Message>(EXTRA_REMOTE_CALLBACK) ?: return
        val replyTo = cb.replyTo ?: return
        // Same matching as Launcher3/Lawnchair getFirstMatchForAppClose: the launched item first,
        // then any visible icon of the same package (the closing activity is often not the
        // launcher activity, so an exact component match failed and we sent a fake centre rect).
        val pkg = cn?.packageName
        val recent = ui.lastLaunch?.takeIf { System.currentTimeMillis() - it.time < 6 * 60 * 60_000L }
        val screenW = window.decorView.width.toFloat(); val screenH = window.decorView.height.toFloat()
        fun ok(r: Rect?) = r != null && r.width >= 8f && r.height >= 8f && r.left >= 0f && r.top >= 0f && r.right <= screenW + 1f && r.bottom <= screenH + 1f
        var key: String? = null
        var rect: Rect? = null
        if (recent != null && (pkg == null || recent.key.substringBefore('/') == pkg)) {
            val r = if (recent.folderId != null) {
                ui.openFolder = recent.folderId
                ui.folderIconBounds[recent.key]?.bounds()?.takeIf(::ok) ?: ui.iconBounds[recent.folderId]?.bounds()?.takeIf(::ok)
            } else ui.iconBounds[recent.key]?.bounds()?.takeIf(::ok) ?: ui.iconBounds["t_" + recent.key]?.bounds()?.takeIf(::ok)
            if (r != null) { key = recent.key; rect = r } else if (ok(recent.bounds)) { key = recent.key; rect = recent.bounds }
        }
        if (rect == null && pkg != null) {
            for ((k, ref) in ui.iconBounds) {
                val ak = k.removePrefix("t_")
                if (ak.substringBefore('/') != pkg) continue
                val r = ref.bounds()?.takeIf(::ok) ?: continue
                key = ak; rect = r; break
            }
        }
        // No icon on screen: do not reply, so the system plays its own default close
        // (a fake rectangle made the window shrink into a meaningless spot).
        val r = rect ?: return
        val loc = IntArray(2)
        window.decorView.getLocationOnScreen(loc)
        val data = Bundle()
        data.putParcelable(EXTRA_ICON_POSITION, android.graphics.RectF(r.left + loc[0], r.top + loc[1], r.right + loc[0], r.bottom + loc[1]))
        data.putParcelable(EXTRA_ICON_SURFACE, null)
        data.putParcelable(EXTRA_ON_FINISH_CALLBACK, android.os.Message.obtain().apply { what = 0; this.replyTo = contractFinish })
        val msg = android.os.Message.obtain()
        msg.copyFrom(cb)
        msg.data = data
        try {
            replyTo.send(msg)
            contractKey = key
            // Hide the target icon right away: the incoming window lands on an empty slot,
            // then the real icon fades in (no scale/translation jump).
            if (key != null && ui.returning?.key != key) {
                ui.returning = ReturnAnim(key, app.icons.color(key), r)
            }
            ui.returnStyle = org.ivanivorontsov.launcher.core.ReturnStyle.CONTRACT
            ui.scope.launch { ui.returnProgress.snapTo(0f); ui.homeZoom.snapTo(1f) }
            returnArmed = false
        } catch (_: Exception) {}
    }

    private companion object {
        const val EXTRA_GESTURE_CONTRACT = "gesture_nav_contract_v1"
        const val EXTRA_ICON_POSITION = "gesture_nav_contract_icon_position"
        const val EXTRA_ICON_SURFACE = "gesture_nav_contract_surface_control"
        const val EXTRA_REMOTE_CALLBACK = "android.intent.extra.REMOTE_CALLBACK"
        const val EXTRA_ON_FINISH_CALLBACK = "gesture_nav_contract_finish_callback"
    }

    // ---------------- LauncherActions ----------------
    private fun opts(bounds: Rect?): ActivityOptions? {
        val b = bounds ?: return null
        val v: View = window.decorView
        val spec = app.store.theme.value
        return ActivityOptions.makeScaleUpAnimation(v, b.left.toInt(), b.top.toInt(), b.width.toInt().coerceAtLeast(1), b.height.toInt().coerceAtLeast(1))
    }

    override fun launch(app: AppInfo, bounds: Rect?, folderId: String?) {
        val spec = this.app.store.theme.value
        val doLaunch = {
            try {
                this.app.apps.launcher.startMainActivity(app.component, app.user, bounds?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }, opts(bounds)?.toBundle())
                ui.launchedApp = true
                ui.lastLaunch = LaunchRecord(app.key, bounds, System.currentTimeMillis(), folderId)
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
                Toast.makeText(this, "Включите «СуперСмарт: блокировка экрана» в Спец. возможностях", Toast.LENGTH_LONG).show()
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
        e += MenuEntry("Модули СуперСмарт") { startActivity(Intent(this, ModulesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        e += MenuEntry("Настройки лаунчера") { openSettings() }
        ui.menu = MenuState(anchor, title = "СуперСмарт", entries = e)
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
