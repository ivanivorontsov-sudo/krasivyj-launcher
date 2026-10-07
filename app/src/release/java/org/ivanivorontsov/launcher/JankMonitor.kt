package org.ivanivorontsov.launcher

import android.app.Activity

/** Release: no-op (jank tracking lives in the debug source set). */
object JankMonitor { @Suppress("UNUSED_PARAMETER") fun attach(activity: Activity) {} }
