package org.ivanivorontsov.launcher.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAdminGuardTest {
    @Test fun onlySettingsDeviceAdminPages() {
        assertTrue(isSettingsDeviceAdminPage("com.android.settings", "com.android.settings.DeviceAdminSettings", ""))
        assertTrue(isSettingsDeviceAdminPage("com.android.settings", "com.android.settings.Settings\$DeviceAdminSettingsActivity", ""))
        assertTrue(isSettingsDeviceAdminPage("com.android.settings", "com.android.settings.SubSettings", "Администраторы устройства"))
        assertTrue(isSettingsDeviceAdminPage("com.android.settings", null, "Device admin apps"))
        assertFalse(isSettingsDeviceAdminPage("com.android.settings", "com.android.settings.SubSettings", "О телефоне"))
        assertFalse(isSettingsDeviceAdminPage("com.android.settings", "com.android.settings.SubSettings", "Администраторы"))
        assertFalse(isSettingsDeviceAdminPage("com.whatsapp", "com.whatsapp.DeviceAdmin", "Device admin apps"))
        assertFalse(isSettingsDeviceAdminPage("com.android.permissioncontroller", "com.android.permissioncontroller.DeviceAdmin", "Администратор устройства"))
        assertFalse(isSettingsDeviceAdminPage("com.miui.securitycenter", null, "Администраторы устройства"))
    }
}
