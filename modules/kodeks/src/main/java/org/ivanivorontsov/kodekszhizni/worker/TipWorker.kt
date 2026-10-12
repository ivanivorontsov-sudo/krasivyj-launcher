package org.ivanivorontsov.kodekszhizni.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.ivanivorontsov.kodekszhizni.data.AppDatabase
import org.ivanivorontsov.kodekszhizni.data.SettingsStore
import kotlinx.coroutines.flow.first

class TipWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext).settings.first()
        if (!settings.enabled) return Result.success()
        val db = AppDatabase.get(applicationContext)
        db.measureDao() // touch
        val article = db.articleDao().randomOne() ?: return Result.success()
        val slot = inputData.getString(KEY_SLOT) ?: "tip"
        NotificationHelper.showTip(
            applicationContext,
            "Кодекс: ${article.title}",
            article.body,
            slot.hashCode()
        )
        return Result.success()
    }

    companion object {
        const val KEY_SLOT = "slot"
    }
}
