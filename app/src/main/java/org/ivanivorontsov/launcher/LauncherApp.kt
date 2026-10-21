package org.ivanivorontsov.launcher

import android.app.Application
import org.ivanivorontsov.launcher.data.AppRepository
import org.ivanivorontsov.launcher.data.IconCache
import org.ivanivorontsov.launcher.data.LiveSources
import org.ivanivorontsov.launcher.data.Store

class LauncherApp : Application() {
    lateinit var store: Store; private set
    lateinit var apps: AppRepository; private set
    lateinit var icons: IconCache; private set
    lateinit var live: LiveSources; private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = Store(this)
        icons = IconCache(this)
        apps = AppRepository(this, store)
        live = LiveSources(this)
        apps.onPackageGone = { icons.forgetPackage(it) }
        org.ivanivorontsov.launcher.stop.StopGuard.init(this)
        org.ivanivorontsov.launcher.ui.DigitOverlay.attach(this, store)
        // СуперСмарт: «Боец поневоле» module state + channels (its alarm receivers also self-init).
        // Called reflectively so the «Лёгкий» flavor (no modules) still compiles.
        if (BuildConfig.HAS_MODULES) runCatching {
            Class.forName("org.ivanivorontsov.boec.data.Store").getMethod("init", android.content.Context::class.java).let { m -> m.invoke(m.declaringClass.getField("INSTANCE").get(null), this) }
            Class.forName("org.ivanivorontsov.boec.alarm.Notifier").getMethod("createChannels", android.content.Context::class.java).let { m -> m.invoke(m.declaringClass.getField("INSTANCE").get(null), this) }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // 1.0 trimmed at TRIM_MEMORY_BACKGROUND, i.e. every time an app was opened → icons reloaded on the way home.
        // Keep them unless the system is really short of memory (the disk cache makes a reload cheap anyway).
        @Suppress("DEPRECATION")
        // 1.3.6: heavy apps push the launcher to TRIM_MEMORY_COMPLETE while it waits in the background; trimming
        // then made the very next return reload icons mid-animation (the "heavy app" freeze). Only trim when
        // the launcher itself is running critically low; never on UI_HIDDEN / BACKGROUND / MODERATE / COMPLETE.
        if (level == TRIM_MEMORY_RUNNING_CRITICAL) icons.trim()
    }

    companion object {
        lateinit var instance: LauncherApp; private set
    }
}
