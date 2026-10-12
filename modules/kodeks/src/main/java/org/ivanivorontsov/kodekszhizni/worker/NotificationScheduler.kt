package org.ivanivorontsov.kodekszhizni.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import org.ivanivorontsov.kodekszhizni.data.NotificationSettings
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationScheduler {
    private const val MONTHLY = "monthly_review"

    fun reschedule(context: Context, settings: NotificationSettings) {
        val wm = WorkManager.getInstance(context)
        listOf("morning", "midday", "evening").forEach {
            wm.cancelUniqueWork("tip_$it")
        }
        wm.cancelUniqueWork(MONTHLY)

        if (!settings.enabled) return

        scheduleTip(wm, "morning", settings.morningHour, settings.morningMinute)
        scheduleTip(wm, "midday", settings.middayHour, settings.middayMinute)
        scheduleTip(wm, "evening", settings.eveningHour, settings.eveningMinute)

        if (settings.monthlyReviewEnabled) {
            val delay = millisUntilNextMonthDay(1, 10, 0)
            val req = PeriodicWorkRequestBuilder<MonthlyReviewWorker>(30, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            wm.enqueueUniquePeriodicWork(MONTHLY, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }

    private fun scheduleTip(wm: WorkManager, slot: String, hour: Int, minute: Int) {
        val delay = millisUntilNext(hour, minute)
        // Chain daily via periodic 24h with initial delay to preferred local time
        val req = PeriodicWorkRequestBuilder<TipWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(TipWorker.KEY_SLOT to slot))
            .build()
        wm.enqueueUniquePeriodicWork("tip_$slot", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    private fun millisUntilNext(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now) || timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(1000L)
    }

    private fun millisUntilNextMonthDay(day: Int, hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, day.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now) || timeInMillis <= now.timeInMillis) {
                add(Calendar.MONTH, 1)
                set(Calendar.DAY_OF_MONTH, day.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
            }
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(1000L)
    }

    fun scheduleImmediateTest(context: Context) {
        val req = OneTimeWorkRequestBuilder<TipWorker>()
            .setInputData(workDataOf(TipWorker.KEY_SLOT to "test"))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("tip_test", ExistingWorkPolicy.REPLACE, req)
    }
}
