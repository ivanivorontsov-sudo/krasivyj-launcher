package org.ivanivorontsov.launcher

import android.app.Activity
import android.util.Log
import androidx.metrics.performance.JankStats

/** Debug builds only: logs janky frames (tag UyutJank) so animation regressions are visible in logcat. */
object JankMonitor {
    private var stats: JankStats? = null
    fun attach(activity: Activity) {
        stats = JankStats.createAndTrack(activity.window) { f ->
            if (f.isJank) Log.d("UyutJank", "jank ${f.frameDurationUiNanos / 1_000_000} ms states=${f.states}")
        }
    }
}
