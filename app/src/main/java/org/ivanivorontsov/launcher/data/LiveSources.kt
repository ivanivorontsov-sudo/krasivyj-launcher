package org.ivanivorontsov.launcher.data

import android.Manifest
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.CalendarContract
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** On-device live data for tiles and widgets. Nothing leaves the phone (no INTERNET permission). */
class LiveSources(private val context: Context) {
    private val power = context.getSystemService(PowerManager::class.java)
    private val _powerSave = MutableStateFlow(power.isPowerSaveMode)
    val powerSave: StateFlow<Boolean> = _powerSave

    private val _minute = MutableStateFlow(System.currentTimeMillis())
    /** Ticks every minute (ACTION_TIME_TICK) — the clock never polls. */
    val minute: StateFlow<Long> = _minute

    init {
        val f = IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED); addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_DATE_CHANGED)
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                _powerSave.value = power.isPowerSaveMode
                if (i?.action != Intent.ACTION_TIME_TICK) alarmStale = true
                _minute.value = System.currentTimeMillis()
            }
        }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED) else @Suppress("UnspecifiedRegisterReceiverFlag") context.registerReceiver(r, f)
    }

    fun refreshNow() { _minute.value = System.currentTimeMillis(); _powerSave.value = power.isPowerSaveMode }

    // 1.4.4: the next alarm is read from AlarmManager only when it (or the clock/zone) changes, not every minute;
    // the formatted text is cached per trigger time and only the "> 24 h" switch is re-checked each tick.
    @Volatile private var alarmStale = true
    private var alarmAt = 0L
    private var alarmTxt: String? = null; private var alarmTxtFor = -1L; private var alarmTxtLong = false
    private val fmtShort = SimpleDateFormat("HH:mm", Locale("ru")); private val fmtLong = SimpleDateFormat("EE HH:mm", Locale("ru"))
    fun nextAlarm(): String? {
        if (alarmStale) { alarmStale = false; alarmAt = try { context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime ?: 0L } catch (_: Exception) { 0L } }
        val at = alarmAt
        if (at == 0L) return null
        if (at < System.currentTimeMillis()) { alarmStale = true; return nextAlarm().takeIf { alarmAt != at } }
        val long = at - System.currentTimeMillis() > 24 * 3600_000L
        if (at != alarmTxtFor || long != alarmTxtLong) { alarmTxt = (if (long) fmtLong else fmtShort).format(Date(at)); alarmTxtFor = at; alarmTxtLong = long }
        return alarmTxt
    }

    // ---------------- Photos ----------------
    fun photoPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun hasPhotoAccess(): Boolean =
        if (org.ivanivorontsov.launcher.BuildConfig.PLAY) pickedPhotos().isNotEmpty()
        else photoPermissions().any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private val picks get() = context.getSharedPreferences("picked_photos", Context.MODE_PRIVATE)
    fun pickedPhotos(): List<Uri> = picks.getString("uris", "").orEmpty().split('\n').filter { it.isNotBlank() }.map { Uri.parse(it) }
    fun setPickedPhotos(uris: List<Uri>) {
        uris.forEach { u -> try { context.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {} }
        picks.edit().putString("uris", uris.joinToString("\n")).apply()
        photoListTime = 0L; photoUris = emptyList()
    }

    private val thumbs = LruCache<String, ImageBitmap>(6)
    private var photoUris: List<Uri> = emptyList()
    private var photoListTime = 0L

    suspend fun photos(): List<Uri> = withContext(Dispatchers.IO) {
        if (!hasPhotoAccess()) return@withContext emptyList()
        if (org.ivanivorontsov.launcher.BuildConfig.PLAY) return@withContext pickedPhotos().shuffled()
        if (System.currentTimeMillis() - photoListTime < 10 * 60_000 && photoUris.isNotEmpty()) return@withContext photoUris
        val out = ArrayList<Uri>()
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID), null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (c.moveToNext() && out.size < 40) out += ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(idCol))
            }
        } catch (_: Exception) {}
        out.shuffle()
        photoUris = out; photoListTime = System.currentTimeMillis()
        out
    }

    /** Downsampled thumbnail (≤ 512 px) — never decodes full-size photos. */
    suspend fun thumbnail(uri: Uri, maxPx: Int = 512): ImageBitmap? = withContext(Dispatchers.IO) {
        thumbs.get(uri.toString())?.let { return@withContext it }
        val bmp: Bitmap? = try {
            if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(uri, Size(maxPx, maxPx), null)
            else {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
                var s = 1; while (o.outWidth / (s * 2) >= maxPx && o.outHeight / (s * 2) >= maxPx) s *= 2
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
            }
        } catch (_: Exception) { null }
        bmp?.asImageBitmap()?.also { thumbs.put(uri.toString(), it) }
    }

    // ---------------- Calendar ----------------
    data class Event(val title: String, val whenText: String)

    fun hasCalendarAccess() = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun nextEvent(): Event? = withContext(Dispatchers.IO) {
        if (!hasCalendarAccess()) return@withContext null
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now); ContentUris.appendId(it, now + 7 * 24 * 3600_000L)
        }.build()
        try {
            context.contentResolver.query(uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
                CalendarContract.Instances.END + " >= ?", arrayOf(now.toString()), CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
                if (c.moveToFirst()) {
                    val title = c.getString(0) ?: "Событие"
                    val begin = c.getLong(1); val allDay = c.getInt(2) == 1
                    val sameDay = SimpleDateFormat("yyyyMMdd", Locale.ROOT).let { it.format(Date(begin)) == it.format(Date(now)) }
                    val whenText = when {
                        allDay && sameDay -> "сегодня"
                        allDay -> SimpleDateFormat("d MMMM", Locale("ru")).format(Date(begin))
                        sameDay -> "сегодня в " + SimpleDateFormat("HH:mm", Locale("ru")).format(Date(begin))
                        else -> SimpleDateFormat("EE, d MMM HH:mm", Locale("ru")).format(Date(begin))
                    }
                    return@withContext Event(title, whenText)
                }
            }
        } catch (_: Exception) {}
        null
    }
}
