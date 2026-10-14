package org.ivanivorontsov.launcher

import android.app.Activity
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState

/**
 * 1.3.7: «Счётчик кадров». Measures every frame for ~1.5 s after each return home and keeps a short
 * history with what the launcher was doing (state labels set via [mark]). Always on in release, cheap.
 */
object JankMonitor {
    class Window(val label: String, val start: Long) {
        var frames = 0; var janky = 0; var worstMs = 0L; var worstWhat = ""; var totalMs = 0L
        val events = StringBuilder()
    }
    private var stats: JankStats? = null
    private var holder: PerformanceMetricsState.Holder? = null
    private var cur: Window? = null
    private val history = ArrayDeque<Window>()
    /** Short summary of the last return, observed by the overlay. */
    var summary by mutableStateOf("")
        private set

    /** Off by default: when off nothing is tracked and [begin]/[mark] return immediately. */
    fun setEnabled(activity: Activity, on: Boolean) {
        if (on == (stats != null)) return
        if (on) attach(activity) else { stats?.isTrackingEnabled = false; stats = null; holder = null; cur = null }
    }
    private fun attach(activity: Activity) {
        holder = PerformanceMetricsState.getHolderForHierarchy(activity.window.decorView)
        stats = JankStats.createAndTrack(activity.window) { f ->
            val w = cur ?: return@createAndTrack
            val now = SystemClock.uptimeMillis()
            if (now - w.start > 1500) { finish(); return@createAndTrack }
            val ms = f.frameDurationUiNanos / 1_000_000
            w.frames++; w.totalMs += ms
            if (f.isJank) w.janky++
            if (ms > w.worstMs) { w.worstMs = ms; w.worstWhat = f.states.joinToString { it.key + "=" + it.value }.ifEmpty { "—" } }
        }
    }

    /** Starts a measuring window (call on resume when coming back from an app). */
    fun begin(label: String) {
        if (stats == null) return
        finish()
        cur = Window(label, SystemClock.uptimeMillis())
        mark("phase", "resume")
    }

    /** Records what is happening now (shown next to the longest frame) and logs it with a timestamp. */
    fun mark(key: String, value: String?) {
        if (stats == null) return
        val h = holder?.state ?: return
        if (value == null) h.removeState(key) else h.putState(key, value)
        cur?.let { w -> w.events.append("+").append(SystemClock.uptimeMillis() - w.start).append("мс ").append(key).append('=').append(value ?: "∅").append('\n') }
    }

    private fun finish() {
        val w = cur ?: return
        cur = null
        holder?.state?.removeState("phase")
        history.addLast(w); while (history.size > 10) history.removeFirst()
        summary = "${w.label}: кадров ${w.frames}, пропущено ${w.janky}, самый длинный ${w.worstMs} мс"
    }

    fun report(extra: String): String = buildString {
        finish()
        append("СуперСмарт ").append(BuildConfig.VERSION_NAME).append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append(" · Android ").append(Build.VERSION.RELEASE).append('\n').append(extra).append("\n\n")
        if (history.isEmpty()) append("Нет данных: сверните приложение домой несколько раз.\n")
        for (w in history.reversed()) {
            append("== ").append(w.label).append(": кадров ").append(w.frames).append(", пропущено ").append(w.janky)
                .append(", самый длинный ").append(w.worstMs).append(" мс (").append(w.worstWhat).append("), в среднем ")
                .append(if (w.frames > 0) w.totalMs / w.frames else 0).append(" мс\n").append(w.events)
        }
    }
}
