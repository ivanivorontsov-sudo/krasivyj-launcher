package org.ivanivorontsov.launcher.data

import android.annotation.SuppressLint
import android.app.Activity
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.Window
import android.animation.ValueAnimator
import org.ivanivorontsov.launcher.service.LockService

/** Device/OEM helpers. OEM-specific paths are tried only on the matching brand, always with safe fallbacks. */
object Device {
    enum class Oem { REALME_OPPO_ONEPLUS, XIAOMI, SAMSUNG, HUAWEI_HONOR, VIVO, OTHER }

    val oem: Oem by lazy {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        when {
            listOf("realme", "oppo", "oneplus").any { it in m } -> Oem.REALME_OPPO_ONEPLUS
            listOf("xiaomi", "redmi", "poco").any { it in m } -> Oem.XIAOMI
            "samsung" in m -> Oem.SAMSUNG
            listOf("huawei", "honor").any { it in m } -> Oem.HUAWEI_HONOR
            listOf("vivo", "iqoo").any { it in m } -> Oem.VIVO
            else -> Oem.OTHER
        }
    }

    fun oemName(): String = when (oem) {
        Oem.REALME_OPPO_ONEPLUS -> "realme / OPPO / OnePlus"
        Oem.XIAOMI -> "Xiaomi / Redmi / POCO"
        Oem.SAMSUNG -> "Samsung"
        Oem.HUAWEI_HONOR -> "Huawei / Honor"
        Oem.VIVO -> "vivo"
        Oem.OTHER -> Build.MANUFACTURER
    }

    /** “Remove animations” / animator scale 0 → we skip decorative motion. */
    fun animationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

    private fun tryStart(ctx: Context, i: Intent): Boolean = try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i); true
    } catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false } catch (_: Exception) { false }

    private fun comp(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))
    private fun launch(ctx: Context, pkg: String) = ctx.packageManager.getLaunchIntentForPackage(pkg)

    /**
     * Android has no public API to change the system font. We open the phone's own font screen where the
     * manufacturer provides one, falling back to the theme store, then to Display settings.
     */
    fun openSystemFontSettings(ctx: Context): Boolean {
        val candidates = mutableListOf<Intent?>()
        when (oem) {
            Oem.REALME_OPPO_ONEPLUS -> candidates += listOf(
                comp("com.android.settings", "com.android.settings.Settings\$FontSettingsActivity"),
                comp("com.oplus.themestore", "com.nearme.themespace.activities.FontActivity"),
                launch(ctx, "com.heytap.themestore"), launch(ctx, "com.oplus.themestore"), launch(ctx, "com.nearme.themespace"),
            )
            Oem.XIAOMI -> candidates += listOf(
                comp("com.android.settings", "com.android.settings.Settings\$FontSettingsActivity"),
                comp("com.android.thememanager", "com.android.thememanager.activity.FontSettingsActivity"),
                launch(ctx, "com.android.thememanager"),
            )
            Oem.SAMSUNG -> candidates += listOf(
                comp("com.android.settings", "com.android.settings.Settings\$FontStyleSettingsActivity"),
                comp("com.android.settings", "com.android.settings.Settings\$FontSizeAndStyleSettingsActivity"),
                launch(ctx, "com.samsung.android.themestore"),
            )
            Oem.HUAWEI_HONOR -> candidates += listOf(launch(ctx, "com.huawei.android.thememanager"), launch(ctx, "com.hihonor.android.thememanager"))
            Oem.VIVO -> candidates += listOf(launch(ctx, "com.bbk.theme"))
            Oem.OTHER -> candidates += listOf(comp("com.android.settings", "com.android.settings.Settings\$FontSettingsActivity"))
        }
        candidates += Intent(Settings.ACTION_DISPLAY_SETTINGS)
        candidates += Intent(Settings.ACTION_SETTINGS)
        return candidates.filterNotNull().any { tryStart(ctx, it) }
    }

    fun openDefaultLauncherChooser(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = activity.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                try { activity.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 42); return } catch (_: Exception) {}
            }
        }
        if (!tryStart(activity, Intent(Settings.ACTION_HOME_SETTINGS))) tryStart(activity, Intent(Settings.ACTION_SETTINGS))
    }

    fun isDefaultLauncher(ctx: Context): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return ctx.packageManager.resolveActivity(i, 0)?.activityInfo?.packageName == ctx.packageName
    }

    @SuppressLint("WrongConstant", "PrivateApi")
    fun expandNotifications(ctx: Context): Boolean {
        try {
            val sbm = ctx.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbm)
            return true
        } catch (_: Throwable) {}
        return LockService.notifications()
    }

    fun isIgnoringBatteryOptimizations(ctx: Context) =
        ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(ctx: Context) {
        if (!tryStart(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName))))
            tryStart(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    fun openAccessibilitySettings(ctx: Context) = tryStart(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    fun openNotificationAccess(ctx: Context) = tryStart(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    fun openAppDetails(ctx: Context) = tryStart(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))

    fun openGallery(ctx: Context) {
        if (!tryStart(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)))
            tryStart(ctx, Intent(Intent.ACTION_VIEW).setType("image/*"))
    }
    fun openCalendar(ctx: Context) = openCalendarNow(ctx)   // 1.4.5: same scoped resolution everywhere
    fun openClock(ctx: Context) { tryStart(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS)) }

    // 1.4.4: clock-widget zones. Everything is resolved lazily on tap; nothing is cached or listened for.
    private val clockPkgs = listOf("com.coloros.alarmclock", "com.oplus.alarmclock", "com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage")
    /** Opens the clock app itself (OEM clock first), falling back to the alarm list. */
    fun openClockApp(ctx: Context) {
        for (p in clockPkgs) { val i = launch(ctx, p) ?: continue; if (tryStart(ctx, i)) return }
        if (!tryStart(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS))) openCalendarless(ctx)
    }
    private fun openCalendarless(ctx: Context) { tryStart(ctx, Intent(Settings.ACTION_DATE_SETTINGS)) }
    /** ColorOS Clock has no public deep link to its World clock tab, so this opens the clock app. */
    fun openWorldClock(ctx: Context) = openClockApp(ctx)
    fun openAlarms(ctx: Context) {
        val pi = try { ctx.getSystemService(android.app.AlarmManager::class.java)?.nextAlarmClock?.showIntent } catch (_: Exception) { null }
        if (pi != null) try {
            if (Build.VERSION.SDK_INT >= 34) pi.send(ctx, 0, null, null, null, null,
                android.app.ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle())
            else pi.send(); return
        } catch (_: Exception) {}
        if (!tryStart(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS))) openClockApp(ctx)
    }
    // 1.4.5: never an unscoped ACTION_VIEW — on realme the content:// time URI was caught by a file manager.
    private val calendarPkgs = listOf("com.coloros.calendar", "com.oplus.calendar", "com.android.calendar", "com.google.android.calendar",
        "com.samsung.android.calendar", "com.miui.calendar", "com.huawei.calendar", "com.xiaomi.calendar", "com.oneplus.calendar",
        "ws.xsoh.etar", "org.fossify.calendar", "com.simplemobiletools.calendar.pro")
    /** Packages that really are calendar apps: they declare CATEGORY_APP_CALENDAR or are a known calendar package. */
    private fun calendarApps(ctx: Context): Set<String> {
        val pm = ctx.packageManager
        val out = LinkedHashSet<String>()
        try { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR), 0).forEach { out += it.activityInfo.packageName } } catch (_: Exception) {}
        calendarPkgs.forEach { if (pm.getLaunchIntentForPackage(it) != null) out += it }
        return out
    }
    fun openCalendarNow(ctx: Context) {
        val cals = calendarApps(ctx)
        val pm = ctx.packageManager
        // 1) system "calendar app" selector, only if it resolves to a real calendar app
        val sel = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
        val r = try { pm.resolveActivity(sel, 0)?.activityInfo?.packageName } catch (_: Exception) { null }
        if (r != null && r in cals && tryStart(ctx, sel)) return
        // 2) known calendar packages, explicitly
        for (p in calendarPkgs + cals) { if (p !in cals) continue; val i = launch(ctx, p) ?: continue; if (tryStart(ctx, i)) return }
        // 3) the time URI, scoped to a calendar package
        val u = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(System.currentTimeMillis().toString()).build()
        for (p in cals) if (tryStart(ctx, Intent(Intent.ACTION_VIEW, u).setPackage(p))) return
        openClockApp(ctx)
    }
    fun webSearch(ctx: Context, q: String) {
        if (!tryStart(ctx, Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, q)))
            tryStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://yandex.ru/search/?text=" + Uri.encode(q))))
    }
    fun marketSearch(ctx: Context, q: String) { tryStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(q)))) }
    fun openWallpaperPicker(ctx: Context) {
        if (!tryStart(ctx, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Обои"))) tryStart(ctx, Intent(Settings.ACTION_DISPLAY_SETTINGS))
    }
    fun uninstall(ctx: Context, pkg: String) { tryStart(ctx, Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))) }

    /** Sets a generated theme image as the phone wallpaper; on Android 12+ the system re-derives its accent colours from it. */
    fun setSystemWallpaper(ctx: Context, bmp: Bitmap): Boolean = try {
        val wm = WallpaperManager.getInstance(ctx)
        wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK); true
    } catch (_: Exception) { false }

    /**
     * Ask for the highest refresh rate at the current resolution (60/90/120/144 Hz all work).
     * Does not switch resolution. The mode id is cached so a swipe-home can re-apply it
     * without listing modes again. Logs the live mode as `UyutHz`.
     */
    @Suppress("DEPRECATION")
    fun applyRefreshRate(window: Window, high: Boolean) {
        val display = window.decorView.display ?: windowManagerDisplay(window) ?: return
        val lp = window.attributes
        if (!high) {
            if (lp.preferredDisplayModeId != 0 || lp.preferredRefreshRate != 0f) {
                lp.preferredDisplayModeId = 0
                lp.preferredRefreshRate = 0f
                window.attributes = lp
            }
            hintFrameRate(window, 0f)
            return
        }
        val best = bestMode(display) ?: return
        if (lp.preferredDisplayModeId != best.id || lp.preferredRefreshRate != best.rate) {
            lp.preferredDisplayModeId = best.id
            lp.preferredRefreshRate = best.rate
            window.attributes = lp
            // 1.3.9: log only real changes (it formatted a string and queried the display mode on every resume).
            logRefresh("request ${fmtHz(best.rate)} mode ${best.id}", display)
        }
        hintFrameRate(window, best.rate)
    }

    /** Drop the cached mode after a display change so the next apply re-reads supported modes. */
    fun invalidateRefreshCache() { refreshPick = null }

    fun registerDisplayRefresh(activity: android.app.Activity, busy: () -> Boolean = { false }, high: () -> Boolean): DisplayManager.DisplayListener {
        val dm = activity.getSystemService(DisplayManager::class.java)
        val main = Handler(Looper.getMainLooper())
        var pending = false
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                val current = activity.display?.displayId ?: Display.DEFAULT_DISPLAY
                if (displayId != current && displayId != Display.DEFAULT_DISPLAY) return
                // 1.3.9: ColorOS switches 60↔120/144 Hz around every Home transition and each switch fires this.
                // Before, every callback dropped the mode cache (binder query of all modes) and re-applied window
                // attributes in the middle of the return animation. The cache is keyed by resolution already, so it
                // stays; and while a transition runs we re-apply once, after it.
                if (busy()) {
                    if (!pending) { pending = true; main.postDelayed({ pending = false; applyRefreshRate(activity.window, high()) }, 700) }
                    return
                }
                applyRefreshRate(activity.window, high())
            }
        }
        dm.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        return listener
    }

    fun unregisterDisplayRefresh(activity: android.app.Activity, listener: DisplayManager.DisplayListener?) {
        if (listener == null) return
        runCatching { activity.getSystemService(DisplayManager::class.java).unregisterDisplayListener(listener) }
    }

    /** Active mode after the system has applied (or ignored) our request. For the settings screen and logcat. */
    fun activeRefreshRate(window: Window): Float = try {
        window.decorView.display?.mode?.refreshRate ?: windowManagerDisplay(window)?.mode?.refreshRate ?: 0f
    } catch (_: Exception) { 0f }

    fun maxRefreshRate(window: Window): Float = try {
        val d = window.decorView.display ?: windowManagerDisplay(window) ?: return 60f
        d.supportedModes.maxOf { it.refreshRate }
    } catch (_: Exception) { 60f }

    private data class ModePick(val id: Int, val rate: Float, val w: Int, val h: Int)

    @Volatile private var refreshPick: ModePick? = null

    @Suppress("DEPRECATION")
    private fun windowManagerDisplay(window: Window): Display? =
        (window.context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay

    private fun bestMode(display: Display): ModePick? {
        val cur = display.mode
        val cached = refreshPick
        if (cached != null && cached.w == cur.physicalWidth && cached.h == cur.physicalHeight) return cached
        val best = display.supportedModes
            .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return null
        return ModePick(best.modeId, best.refreshRate, cur.physicalWidth, cur.physicalHeight).also { refreshPick = it }
    }

    private fun hintFrameRate(window: Window, rate: Float) {
        val view = window.decorView
        if (Build.VERSION.SDK_INT >= 35) {
            val requested = if (rate > 1f) rate else android.view.View.REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE
            runCatching { view.setRequestedFrameRate(requested) }
            runCatching { window.setFrameRatePowerSavingsBalanced(false) }
        }
        if (Build.VERSION.SDK_INT >= 30 && rate > 1f) hintSurface(view, rate)
    }

    /** Hidden ViewRootImpl surface, when the platform still lets us see it. Public mode id is the real request. */
    // 1.3.9: reflection is resolved once. Before, every resume did getMethod/getDeclaredField on hidden API
    // (with the runtime's hidden-API checks and, where blocked, an exception) on the main thread at the
    // exact moment the Home transition starts.
    @Volatile private var surfaceReflectFailed = false
    private var getRootMethod: java.lang.reflect.Method? = null
    private var surfaceField: java.lang.reflect.Field? = null

    private fun hintSurface(view: android.view.View, rate: Float) {
        if (surfaceReflectFailed) return
        try {
            val m = getRootMethod ?: android.view.View::class.java.getMethod("getViewRootImpl").also { getRootMethod = it }
            val root = m.invoke(view) ?: return
            val field = surfaceField ?: root.javaClass.getDeclaredField("mSurface").also { it.isAccessible = true; surfaceField = it }
            val surface = field.get(root) as? Surface ?: return
            if (!surface.isValid) return
            if (Build.VERSION.SDK_INT >= 31) {
                surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT, Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS)
            } else {
                surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
            }
        } catch (_: Throwable) { surfaceReflectFailed = true }
    }

    private fun logRefresh(request: String, display: Display) {
        val mode = display.mode
        Log.i("UyutHz", "$request; active ${fmtHz(mode.refreshRate)} mode ${mode.modeId}")
    }

    private fun fmtHz(rate: Float): String = "${"%.1f".format(rate)}Hz"
}
