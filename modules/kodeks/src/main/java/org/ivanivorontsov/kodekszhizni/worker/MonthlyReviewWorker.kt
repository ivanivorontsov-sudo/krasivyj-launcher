package org.ivanivorontsov.kodekszhizni.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.ivanivorontsov.kodekszhizni.data.SettingsStore
import kotlinx.coroutines.flow.first

class MonthlyReviewWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext).settings.first()
        if (!settings.monthlyReviewEnabled) return Result.success()
        NotificationHelper.showMonthlyReview(applicationContext)
        return Result.success()
    }
}
