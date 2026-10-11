package org.ivanivorontsov.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.util.TypedValue
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import org.ivanivorontsov.launcher.LauncherApp
import org.ivanivorontsov.launcher.core.DigitAnchor
import org.ivanivorontsov.launcher.core.DigitFormat
import org.ivanivorontsov.launcher.core.DigitMode
import org.ivanivorontsov.launcher.core.LauncherConfig
import org.ivanivorontsov.launcher.data.Store
import org.ivanivorontsov.launcher.service.ForegroundClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * Huge black digits drawn in a separate window above other apps.
 * The window is not focusable and not touchable, so taps go to the app underneath.
 * Nothing is added, and the overlay permission is never read, until [LauncherConfig.digitOverlay] is true.
 */
object DigitOverlay {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var view: DigitView? = null
    private var params: WindowManager.LayoutParams? = null
    private var attached = false

    fun attach(app: Context, store: Store) {
        scope.launch {
            store.config.collect { sync(app.applicationContext, it) }
        }
    }

    /** If the process started in the background and the window was rejected, try again once home is up. */
    fun poke(context: Context) {
        if (view != null) return
        val cfg = LauncherApp.instance.store.config.value
        if (!cfg.digitOverlay) return
        main.post { sync(context.applicationContext, cfg) }
    }

    private fun sync(context: Context, cfg: LauncherConfig) {
        if (!cfg.digitOverlay) {
            hide(context)
            return
        }
        if (Build.VERSION.SDK_INT < 23 || !Settings.canDrawOverlays(context)) {
            hide(context)
            return
        }
        val wm = context.getSystemService(WindowManager::class.java) ?: return
        val v = view ?: DigitView(context).also { view = it }
        v.look(cfg)
        val lp = params ?: layoutParams(cfg).also { params = it }
        applyAnchor(lp, cfg.digitAnchor, context)
        try {
            if (!attached) {
                wm.addView(v, lp)
                attached = true
                ScreenToday.ensure(context)
                main.removeCallbacks(tick)
                main.post(tick)
            } else {
                wm.updateViewLayout(v, lp)
                v.invalidate()
            }
        } catch (_: Exception) {
            attached = false
            runCatching { if (v.parent != null) wm.removeView(v) }
        }
    }

    private fun hide(context: Context) {
        main.removeCallbacks(tick)
        val v = view ?: return
        if (attached) {
            ScreenToday.flush(context)
            runCatching { context.getSystemService(WindowManager::class.java)?.removeView(v) }
        }
        attached = false
        view = null
        params = null
    }

    private val tick = object : Runnable {
        override fun run() {
            val v = view ?: return
            if (!attached) return
            val cfg = LauncherApp.instance.store.config.value
            if (!cfg.digitOverlay) {
                hide(v.context)
                return
            }
            v.text = label(v.context, cfg.digitMode)
            v.requestLayout()
            v.invalidate()
            main.postDelayed(this, 1000)
        }
    }

    private fun label(context: Context, mode: DigitMode): String = when (mode) {
        DigitMode.CLOCK -> LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        DigitMode.TODAY -> DigitFormat.duration(ScreenToday.advance(context, screenOn(context)))
        DigitMode.APP -> DigitFormat.duration(ForegroundClock.sessionMs())
    }

    private fun screenOn(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isInteractive == true

    private fun layoutParams(cfg: LauncherConfig) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        applyAnchor(this, cfg.digitAnchor, null)
    }

    private fun applyAnchor(lp: WindowManager.LayoutParams, anchor: DigitAnchor, context: Context?) {
        val margin = ((context?.resources?.displayMetrics?.density ?: 3f) * 28f).toInt()
        lp.gravity = when (anchor) {
            DigitAnchor.TOP -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            DigitAnchor.CENTER -> Gravity.CENTER
            DigitAnchor.BOTTOM -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        lp.x = 0
        lp.y = if (anchor == DigitAnchor.CENTER) 0 else margin
    }
}

/** Screen-on time accumulated while the digit overlay is enabled, for the current calendar day. */
private object ScreenToday {
    private const val PREFS = "uyut_digits"
    private const val K_DAY = "day"
    private const val K_MS = "ms"
    private var day = ""
    private var accumulated = 0L
    private var runningSince = 0L
    private var loaded = false
    private var dirtyTicks = 0

    fun ensure(context: Context) {
        if (loaded) {
            roll(context)
            return
        }
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        day = p.getString(K_DAY, "").orEmpty()
        accumulated = p.getLong(K_MS, 0L)
        loaded = true
        roll(context)
    }

    fun advance(context: Context, interactive: Boolean): Long {
        ensure(context)
        val now = SystemClock.elapsedRealtime()
        if (interactive) {
            if (runningSince == 0L) runningSince = now
            else {
                accumulated += (now - runningSince).coerceIn(0L, 120_000L)
                runningSince = now
            }
        } else if (runningSince != 0L) {
            accumulated += (now - runningSince).coerceIn(0L, 120_000L)
            runningSince = 0L
        }
        if (++dirtyTicks >= 10) flush(context)
        return accumulated
    }

    fun flush(context: Context) {
        if (!loaded) return
        dirtyTicks = 0
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(K_DAY, day).putLong(K_MS, accumulated).apply()
    }

    private fun roll(context: Context) {
        val today = LocalDate.now().toString()
        if (day == today) return
        day = today
        accumulated = 0L
        runningSince = 0L
        flush(context)
    }
}

private class DigitView(context: Context) : View(context) {
    var text: String = "00:00"
    private var textSp = 72f
    private var alphaFraction = 1f
    private var outline = true
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    fun look(cfg: LauncherConfig) {
        textSp = cfg.digitSizeSp.coerceIn(28, 160).toFloat()
        alphaFraction = cfg.digitAlpha.coerceIn(0.15f, 1f)
        outline = cfg.digitOutline
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val px = sp()
        fill.textSize = px
        val sample = text.ifEmpty { "00:00" }
        val w = fill.measureText(sample) + px * 0.45f
        setMeasuredDimension(w.toInt().coerceAtLeast(1), (px * 1.35f).toInt().coerceAtLeast(1))
    }

    override fun onDraw(canvas: Canvas) {
        val px = sp()
        fill.textSize = px
        stroke.textSize = px
        stroke.strokeWidth = px * 0.045f
        val a = (alphaFraction * 255f).toInt().coerceIn(0, 255)
        fill.alpha = a
        stroke.alpha = a
        val x = width / 2f
        val y = height / 2f - (fill.descent() + fill.ascent()) / 2f
        if (outline) canvas.drawText(text, x, y, stroke)
        canvas.drawText(text, x, y, fill)
    }

    private fun sp(): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSp, resources.displayMetrics)
}
