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
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
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
    fun openCalendar(ctx: Context) {
        if (!tryStart(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)))
            tryStart(ctx, Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()))
    }
    fun openClock(ctx: Context) { tryStart(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
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
     * All animations are time-based, so this only makes them smoother, never faster.
     */
    @Suppress("DEPRECATION")
    fun applyRefreshRate(window: Window, high: Boolean) {
        val display = window.decorView.display ?: (window.context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay ?: return
        val lp = window.attributes
        if (!high) {
            if (lp.preferredDisplayModeId != 0) { lp.preferredDisplayModeId = 0; lp.preferredRefreshRate = 0f; window.attributes = lp }
            return
        }
        val cur = display.mode
        val best = display.supportedModes.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        if (lp.preferredDisplayModeId != best.modeId) {
            lp.preferredDisplayModeId = best.modeId
            lp.preferredRefreshRate = best.refreshRate
            window.attributes = lp
        }
    }

    fun maxRefreshRate(window: Window): Float = try {
        @Suppress("DEPRECATION") val d = window.decorView.display ?: (window.context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
        d.supportedModes.maxOf { it.refreshRate }
    } catch (_: Exception) { 60f }
}
