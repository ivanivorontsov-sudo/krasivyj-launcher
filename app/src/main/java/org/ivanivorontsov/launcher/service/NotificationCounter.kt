package org.ivanivorontsov.launcher.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Optional (user enables it in system settings): counts unread notifications per app for tile badges.
 * Only package names and counts are kept, in memory; notification text is never stored.
 */
class NotificationCounter : NotificationListenerService() {
    override fun onListenerConnected() = recount()
    override fun onListenerDisconnected() { _counts.value = emptyMap() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = recount()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()

    private fun recount() {
        val list = try { activeNotifications } catch (_: Exception) { null } ?: return
        val m = HashMap<String, Int>()
        for (n in list) {
            if (n.isOngoing || !n.isClearable) continue
            if (n.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            val num = n.notification.number.takeIf { it > 0 } ?: 1
            m[n.packageName] = (m[n.packageName] ?: 0) + num
        }
        _counts.value = m
    }

    companion object {
        private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
        val counts: StateFlow<Map<String, Int>> = _counts
    }
}
