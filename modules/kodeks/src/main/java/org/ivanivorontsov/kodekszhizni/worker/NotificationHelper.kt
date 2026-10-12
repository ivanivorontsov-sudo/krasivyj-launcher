package org.ivanivorontsov.kodekszhizni.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.ivanivorontsov.kodekszhizni.MainActivity
import org.ivanivorontsov.kodekszhizni.R

object NotificationHelper {
    const val CHANNEL_TIPS = "kodeks_tips"
    const val CHANNEL_REVIEW = "kodeks_review"

    fun ensureChannels(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TIPS,
                context.getString(R.string.channel_tips),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.channel_tips_desc) }
        )
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REVIEW,
                context.getString(R.string.channel_review),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.channel_review_desc) }
        )
    }

    fun showTip(context: Context, title: String, body: String, id: Int) {
        ensureChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val preview = body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .firstOrNull()
            ?.take(120)
            ?: body.take(120)
        val n = NotificationCompat.Builder(context, CHANNEL_TIPS)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(title)
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted
        }
    }

    fun showMonthlyReview(context: Context) {
        ensureChannels(context)
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 9001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_REVIEW)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("Пересмотри нормы и меры")
            .setContentText("Раз в месяц стоит обновить кодекс и активные меры пресечения.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(9001, n)
        } catch (_: SecurityException) {
        }
    }
}
