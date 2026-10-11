package org.ivanivorontsov.launcher.stop

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Device-admin registration for Stop Screen. Android requires the user to deactivate this
 * receiver in system settings before the app can be uninstalled; no wipe/reset policies are used.
 *
 * Ordinary Device Admin cannot prevent the owner of the phone from deactivating it. This callback
 * adds a clear warning, but intentionally does not pretend to be an unbreakable kiosk lock.
 */
class StopAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Защита «Стоп-экрана» будет отключена. После этого приложение можно будет удалить или сменить лаунчер. Если это сделано намеренно, подтвердите отключение в Android."
}
