package org.ivanivorontsov.launcher.stop

import android.app.admin.DeviceAdminReceiver

/**
 * Device-admin registration for Stop Screen. Android requires the user to deactivate this
 * receiver in system settings before the app can be uninstalled; no wipe/reset policies are used.
 */
class StopAdminReceiver : DeviceAdminReceiver()
