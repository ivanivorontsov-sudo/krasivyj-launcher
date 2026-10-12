package org.ivanivorontsov.kodekszhizni

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.ivanivorontsov.kodekszhizni.data.AppDatabase
import org.ivanivorontsov.kodekszhizni.data.Repository
import org.ivanivorontsov.kodekszhizni.data.SettingsStore
import org.ivanivorontsov.kodekszhizni.worker.NotificationHelper
import org.ivanivorontsov.kodekszhizni.worker.NotificationScheduler

/** СуперСмарт: «Кодекс жизни» runs as a module inside the launcher process, so the former
 *  Application subclass is now a lazily initialised singleton. */
object KodeksApp {
    lateinit var repository: Repository
        private set
    lateinit var settingsStore: SettingsStore
        private set
    @Volatile private var ready = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Synchronized
    fun init(context: Context): KodeksApp {
        if (ready) return this
        val ctx = context.applicationContext
        val db = AppDatabase.get(ctx)
        repository = Repository(db.articleDao(), db.measureDao())
        settingsStore = SettingsStore(ctx)
        NotificationHelper.ensureChannels(ctx)
        ready = true
        scope.launch {
            repository.completeExpiredMeasures()
            val settings = settingsStore.settings.first()
            NotificationScheduler.reschedule(ctx, settings)
        }
        return this
    }
}
