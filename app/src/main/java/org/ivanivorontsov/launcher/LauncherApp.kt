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
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) icons.trim()
    }

    companion object {
        lateinit var instance: LauncherApp; private set
    }
}
