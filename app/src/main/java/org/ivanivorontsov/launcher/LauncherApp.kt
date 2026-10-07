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
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // 1.0 trimmed at TRIM_MEMORY_BACKGROUND, i.e. every time an app was opened → icons reloaded on the way home.
        // Keep them unless the system is really short of memory (the disk cache makes a reload cheap anyway).
        @Suppress("DEPRECATION")
        if (level >= TRIM_MEMORY_COMPLETE || level == TRIM_MEMORY_RUNNING_CRITICAL) icons.trim()
    }

    companion object {
        lateinit var instance: LauncherApp; private set
    }
}
