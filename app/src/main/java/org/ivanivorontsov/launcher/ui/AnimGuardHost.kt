package org.ivanivorontsov.launcher.ui

import android.app.Activity
import android.content.SharedPreferences
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import org.ivanivorontsov.launcher.core.AnimGuard
import org.ivanivorontsov.launcher.core.PageEffect
import org.ivanivorontsov.launcher.data.Store

/**
 * Runtime side of [AnimGuard]: remembers the page effect used at start, and if the process
 * dies before the first seconds of frames settle, the next start falls back to «Без эффекта».
 * A live stall after the first frame also disables the effect for this session and persists it.
 *
 * Frame watching starts only once the window is attached, so a HOME intent cannot start
 * compositor work against a detached window.
 */
object AnimGuardHost {
    fun attach(activity: Activity, store: Store) {
        val prefs = activity.getSharedPreferences(AnimGuard.PREFS, Activity.MODE_PRIVATE)
        val themeId = store.theme.value.id
        AnimGuard.consumeFailedStart(prefs.getString(AnimGuard.KEY_PENDING, null))?.let { bad ->
            if (AnimGuard.needsStartGuard(bad)) {
                store.update { cfg -> cfg.copy(pageEffects = cfg.pageEffects + (themeId to PageEffect.NONE)) }
                AnimGuard.disable()
            }
            prefs.edit().remove(AnimGuard.KEY_PENDING).apply()
        }
        val effect = store.config.value.pageEffectFor(themeId, store.theme.value.layout)
        if (AnimGuard.needsStartGuard(effect) && AnimGuard.effectsEnabled) {
            prefs.edit().putString(AnimGuard.KEY_PENDING, effect.name).apply()
        } else {
            prefs.edit().remove(AnimGuard.KEY_PENDING).apply()
        }
        val startWatch = Runnable { watchFrames(activity, store, themeId, prefs) }
        val decor = activity.window.decorView
        if (decor.isAttachedToWindow) startWatch.run()
        else {
            decor.post {
                if (decor.isAttachedToWindow) startWatch.run()
                else decor.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        v.removeOnAttachStateChangeListener(this)
                        startWatch.run()
                    }
                    override fun onViewDetachedFromWindow(v: View) {}
                })
            }
        }
    }

    private fun watchFrames(activity: Activity, store: Store, themeId: String, prefs: SharedPreferences) {
        val choreographer = Choreographer.getInstance()
        var started = 0L
        var last = 0L
        val watch = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (activity.isDestroyed || activity.isFinishing) return
                val now = SystemClock.uptimeMillis()
                if (started == 0L) {
                    started = now
                    last = now
                    choreographer.postFrameCallback(this)
                    return
                }
                val dt = now - last
                last = now
                if (AnimGuard.shouldFallback(dt)) {
                    AnimGuard.disable()
                    store.update { cfg -> cfg.copy(pageEffects = cfg.pageEffects + (themeId to PageEffect.NONE)) }
                    prefs.edit().remove(AnimGuard.KEY_PENDING).apply()
                    return
                }
                if (now - started >= AnimGuard.SETTLE_MS) {
                    prefs.edit().remove(AnimGuard.KEY_PENDING).apply()
                    return
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(watch)
    }
}
