package org.ivanivorontsov.launcher.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.widget.RemoteViews

/**
 * 1.4.5: "the pager is moving" flag. Non-urgent main-thread work (minute tick, widget RemoteViews updates)
 * is held back while a swipe is in progress and run once the pager settles — so it can't land as a one-off
 * hitch in the middle of a swipe. Main thread only.
 */
object ScrollGate {
    var busy: Boolean = false
        private set
    private val pending = LinkedHashMap<Any, () -> Unit>()
    fun set(b: Boolean) {
        if (busy == b) return
        busy = b
        if (!b && pending.isNotEmpty()) { val l = pending.values.toList(); pending.clear(); l.forEach { runCatching(it) } }
    }
    /** Runs now when idle, otherwise once the swipe ends (latest call per [key] wins). */
    fun runWhenIdle(key: Any, r: () -> Unit) { if (!busy) r() else pending[key] = r }
}

class GatedWidgetHost(ctx: Context, id: Int) : AppWidgetHost(ctx, id) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView = GatedHostView(context)
}

class GatedHostView(ctx: Context) : AppWidgetHostView(ctx) {
    override fun updateAppWidget(remoteViews: RemoteViews?) {
        if (!ScrollGate.busy) { super.updateAppWidget(remoteViews); return }
        ScrollGate.runWhenIdle(this) { super.updateAppWidget(remoteViews) }
    }
}
