package org.ivanivorontsov.launcher.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.ivanivorontsov.launcher.stop.StopGuard

/**
 * Optional accessibility service of Уют:
 *  • double-tap to lock and opening the notification shade;
 *  • «Стоп-экран» (1.2): when a forbidden app comes to the front by any route (notification, recents, another app,
 *    a shortcut), it is sent HOME — and home is Уют itself, so there is no pause pop-up and no other launcher;
 *  • strict mode: closes the system settings pages that could switch the protection off (Уют's app info,
 *    accessibility, default-home picker, developer options) — the same check ScreenLimit uses.
 * It reads only the package name of the front window, plus page text of the system Settings in strict mode.
 */
class LockService : AccessibilityService() {
    private var lastHome = 0L
    private var lastScan = 0L
    private val ime by lazy { runCatching { getSystemService(android.view.inputmethod.InputMethodManager::class.java).inputMethodList.map { it.packageName }.toSet() }.getOrDefault(emptySet()) }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; return super.onUnbind(intent) }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        e ?: return
        val pkg = e.packageName?.toString() ?: return
        val isState = e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (isState && pkg != "com.android.systemui" && pkg !in ime) StopGuard.lastForeground = pkg
        if (isState && StopGuard.isForbidden(pkg)) {
            val now = SystemClock.uptimeMillis()
            if (now - lastHome > 400) {
                lastHome = now
                performGlobalAction(GLOBAL_ACTION_HOME)
                StopGuard.onIntercepted(pkg)
            }
            return
        }
        val v = StopGuard.view.value
        if (v.enabled && StopGuard.store.settings.strictMode && pkg in GUARDED_PKGS) {
            val now = SystemClock.uptimeMillis()
            if (!isState && now - lastScan < 250) return
            lastScan = now
            val root = runCatching { rootInActiveWindow }.getOrNull() ?: return

            // Device Admin cannot veto deactivation by itself. In strict mode, immediately
            // leave its management page, even during grace periods for other permissions.
            if (isDeviceAdminManagement(root)) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                StopGuard.store.log(org.ivanivorontsov.launcher.stop.core.HistoryEntry.TAMPER, "Попытка открыть управление администратором устройства")
                return
            }

            if (now > grantUntil && isSensitive(root)) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                StopGuard.store.log(org.ivanivorontsov.launcher.stop.core.HistoryEntry.TAMPER, "Попытка открыть защищённую страницу настроек")
            }
        }
    }

    private fun isSensitive(root: AccessibilityNodeInfo): Boolean {
        val label = runCatching { getString(applicationInfo.labelRes) }.getOrDefault("Уют")
        var hit = false
        walk(root, 0) { t ->
            if (t.contains(label, ignoreCase = true) || t.contains("Стоп-экран", ignoreCase = true) || DEV_TITLES.any { t.equals(it, ignoreCase = true) }) hit = true
            hit
        }
        return hit
    }

    private fun isDeviceAdminManagement(root: AccessibilityNodeInfo): Boolean {
        var hit = false
        walk(root, 0) { t ->
            if (DEVICE_ADMIN_TITLES.any { t.contains(it, ignoreCase = true) }) hit = true
            hit
        }
        return hit
    }

    private fun walk(n: AccessibilityNodeInfo?, depth: Int, f: (String) -> Boolean): Boolean {
        if (n == null || depth > 30) return false
        val t = (n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")
        if (t.isNotBlank() && f(t.trim())) return true
        for (i in 0 until n.childCount) if (walk(n.getChild(i), depth + 1, f)) return true
        return false
    }

    override fun onInterrupt() {}

    companion object {
        @Volatile private var instance: LockService? = null
        val isEnabled: Boolean get() = instance != null
        /** Until then (uptime) strict mode leaves the settings pages open: the user is granting a permission. */
        @Volatile var grantUntil = 0L
        fun lock(): Boolean = Build.VERSION.SDK_INT >= 28 && instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true
        fun notifications(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) == true

        val GUARDED_PKGS = setOf(
            "com.android.settings", "com.google.android.packageinstaller", "com.android.packageinstaller",
            "com.samsung.android.packageinstaller", "com.miui.securitycenter", "com.miui.packageinstaller",
            "com.huawei.systemmanager", "com.coloros.safecenter", "com.oplus.safecenter", "com.android.permissioncontroller",
            "com.google.android.permissioncontroller", "com.samsung.accessibility", "com.google.android.accessibility.suite",
            "com.oplus.securitypermission", "com.coloros.securitypermission",
        )
        val DEV_TITLES = listOf("Для разработчиков", "Параметры разработчика", "Developer options", "Режим разработчика")
        val DEVICE_ADMIN_TITLES = listOf(
            "Администраторы устройства", "Администратор устройства", "Администрирование устройства",
            "Приложения-администраторы", "Управление администраторами", "Администраторы",
            "Device admin apps", "Device administrators", "Device admin", "Manage device admins",
        )
    }
}
