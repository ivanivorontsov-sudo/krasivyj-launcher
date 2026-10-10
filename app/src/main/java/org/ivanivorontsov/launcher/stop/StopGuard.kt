package org.ivanivorontsov.launcher.stop

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings as SysSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.ivanivorontsov.launcher.LauncherApp
import org.ivanivorontsov.launcher.stop.core.Engine
import org.ivanivorontsov.launcher.stop.core.EngineEvent
import org.ivanivorontsov.launcher.stop.core.ExitChoice
import org.ivanivorontsov.launcher.stop.core.HistoryEntry
import org.ivanivorontsov.launcher.stop.core.Settings
import org.ivanivorontsov.launcher.stop.core.TickInput
import java.time.ZoneId

/** What the launcher needs to know about «Стоп-экран» right now. */
data class StopView(
    val enabled: Boolean = false,
    /** Limit used up or inside the schedule window: only the allowed apps stay. */
    val paused: Boolean = false,
    val reason: String = "",
    val usedMs: Long = 0,
    val remainingMs: Long? = null,
    val pauseUntil: Long = 0,
    /** Packages removed from home, drawer, search, folders, dock — and intercepted if opened another way. */
    val hidden: Set<String> = emptySet(),
)

/**
 * «Стоп-экран» as a mode of the launcher (1.2). The ScreenLimit engine (limit, schedule, trusted clock,
 * exits) is reused unchanged; instead of a separate pause screen the launcher itself keeps working in any theme
 * and simply drops the forbidden apps. Because Уют IS the home screen, going home never shows a pop-up or
 * another launcher: the accessibility part only sends forbidden apps home (= to Уют).
 */
object StopGuard {
    private val h = Handler(Looper.getMainLooper())
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var store: StopStore; private set
    private lateinit var ctx: Context
    private val _view = MutableStateFlow(StopView())
    val view: StateFlow<StopView> = _view
    private val _intercepted = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Label of an app that was just sent back home. */
    val intercepted: SharedFlow<String> = _intercepted
    @Volatile var lastForeground: String? = null
    const val TICK_MS = 10_000L

    fun init(context: Context) {
        ctx = context.applicationContext
        store = StopStore(ctx)
        val f = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED) }
        androidx.core.content.ContextCompat.registerReceiver(ctx, object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { tick(); if (i.action != Intent.ACTION_SCREEN_OFF) schedule() }
        }, f, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        io.launch { LauncherApp.instance.apps.apps.collect { publish() } }
        tick(); schedule()
    }

    private val loop = Runnable { tick(); schedule() }
    private fun schedule() {
        h.removeCallbacks(loop)
        val s = store.settings
        if (store.enabled && (s.limitEnabled || s.scheduleEnabled) && ctx.getSystemService(PowerManager::class.java).isInteractive) h.postDelayed(loop, TICK_MS)
    }

    @Synchronized
    fun tick() {
        if (!::store.isInitialized) return
        if (!store.enabled) { publish(); return }
        val s = store.settings
        val pm = ctx.getSystemService(PowerManager::class.java)
        val km = ctx.getSystemService(KeyguardManager::class.java)
        val fg = lastForeground
        val counting = pm.isInteractive && !km.isKeyguardLocked && !isCall(fg)
        val autoTime = runCatching { SysSettings.Global.getInt(ctx.contentResolver, SysSettings.Global.AUTO_TIME) == 1 }.getOrDefault(true)
        val r = Engine.step(s, store.state, TickInput(SystemClock.elapsedRealtime(), System.currentTimeMillis(), ZoneId.systemDefault(), autoTime, counting))
        store.state = r.state
        for (e in r.events) when (e) {
            is EngineEvent.Locked -> store.log(HistoryEntry.LOCK, if (e.reason == Engine.REASON_SCHEDULE) "Пауза по расписанию" else "Лимит исчерпан (${fmt(r.state.usedMs)})")
            is EngineEvent.Unlocked -> store.log(HistoryEntry.EXIT, "Пауза закончилась")
            is EngineEvent.PeriodReset -> store.log(HistoryEntry.RESET, "Новые сутки — счётчик обнулён")
            is EngineEvent.Warning -> h.post { android.widget.Toast.makeText(ctx, "Стоп-экран: через ${e.minutesLeft} мин — пауза", android.widget.Toast.LENGTH_LONG).show() }
            else -> {}
        }
        publish()
    }

    /** Recomputes the hidden set (also when the app list or settings change). */
    fun publish() {
        if (!::store.isInitialized) return
        val enabled = store.enabled
        val s = store.settings
        val st = store.state
        val paused = enabled && st.locked
        val own = ctx.packageName
        val keep = protectedPackages()
        val hidden = if (!enabled) emptySet() else {
            val base = store.blocked - keep
            if (!paused) base else {
                val allowed = s.whitelist + keep
                base + LauncherApp.instance.apps.apps.value.map { it.pkg }.filter { it !in allowed }
            }
        } - own
        val until = if (!paused) 0L else if (st.lockReason == Engine.REASON_SCHEDULE)
            Engine.windowEnd(System.currentTimeMillis(), ZoneId.systemDefault(), s.scheduleStartMin, s.scheduleEndMin) else st.periodEnd
        _view.value = StopView(enabled, paused, st.lockReason, st.usedMs,
            if (enabled && s.limitEnabled) Engine.remainingMs(s, st, org.ivanivorontsov.launcher.stop.core.EngineConfig()).coerceAtLeast(0) else null,
            until, hidden)
        schedule()
    }

    /** Never hidden: Уют itself, the phone (calls, 112) and SMS apps. */
    fun protectedPackages(): Set<String> {
        val out = HashSet<String>()
        out += ctx.packageName
        runCatching { ctx.getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()?.let { out += it }
        runCatching { android.provider.Telephony.Sms.getDefaultSmsPackage(ctx) }.getOrNull()?.let { out += it }
        return out
    }

    private fun isCall(pkg: String?) = pkg != null && (pkg == runCatching { ctx.getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage }.getOrNull() ||
        pkg in setOf("com.android.server.telecom", "com.android.incallui", "com.samsung.android.incallui", "com.android.phone"))

    fun isForbidden(pkg: String?): Boolean = pkg != null && pkg in _view.value.hidden

    fun onIntercepted(pkg: String) {
        val label = LauncherApp.instance.apps.apps.value.firstOrNull { it.pkg == pkg }?.label ?: pkg
        _intercepted.tryEmit(label)
    }

    // ---------------- changes from the settings screen ----------------
    fun update(f: StopStore.() -> Unit) { store.f(); tick() }

    fun grantExit(choice: ExitChoice) {
        synchronized(this) {
            val st = store.state
            val now = maxOf(System.currentTimeMillis(), st.lastTrusted)
            store.state = Engine.grantExit(store.settings, st, choice, now, ZoneId.systemDefault())
            store.log(HistoryEntry.EXIT, "Выход из паузы: " + when (choice) { ExitChoice.RestOfDay -> "до конца суток"; is ExitChoice.Extra -> "+${choice.ms / 60_000} мин" })
        }
        tick()
    }

    /** Settings change that makes protection weaker (needs the password, like in ScreenLimit). */
    fun isLoosening(old: Settings, new: Settings) = org.ivanivorontsov.launcher.stop.core.SettingsPolicy.isLoosening(old, new)

    fun fmt(ms: Long): String {
        val m = (ms / 60_000).coerceAtLeast(0); val hh = m / 60; val mm = m % 60
        return when { hh > 0 && mm > 0 -> "$hh ч $mm мин"; hh > 0 -> "$hh ч"; else -> "$mm мин" }
    }
}
