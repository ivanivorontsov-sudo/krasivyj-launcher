package org.ivanivorontsov.launcher.stop

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import org.ivanivorontsov.launcher.stop.core.GuardState
import org.ivanivorontsov.launcher.stop.core.HistoryEntry
import org.ivanivorontsov.launcher.stop.core.Passcode
import org.ivanivorontsov.launcher.stop.core.Settings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent data of the «Стоп-экран» mode inside Уют (own SharedPreferences file, separate from the launcher
 * look-and-feel config, so themes/effects never touch it and vice versa). Same password scheme as the standalone
 * ScreenLimit app: only PBKDF2-SHA256 hash + salt, a one-time recovery code (hash only), escalating cooldown.
 */
class StopStore(context: Context) {
    private val p = context.getSharedPreferences("uyut_stop", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = p.getBoolean(K_ENABLED, false)
        set(v) { p.edit().putBoolean(K_ENABLED, v).apply() }

    /** Apps that are always removed (home, drawer, search, folders, dock) and intercepted while the mode is on. */
    var blocked: Set<String>
        get() = p.getStringSet(K_BLOCKED, emptySet())!!.toSet()
        set(v) { p.edit().putStringSet(K_BLOCKED, v.toSet()).apply() }

    /** Limit / schedule / «allowed during the pause» / strict mode — the ScreenLimit settings model. */
    var settings: Settings
        get() = p.getString(K_SETTINGS, null)?.let { runCatching { Settings.fromJson(JSONObject(it), DEFAULTS) }.getOrNull() } ?: DEFAULTS
        set(v) { p.edit().putString(K_SETTINGS, v.toJson().toString()).apply() }

    var state: GuardState
        get() = p.getString(K_STATE, null)?.let { runCatching { GuardState.fromJson(JSONObject(it)) }.getOrNull() } ?: GuardState()
        set(v) { p.edit().putString(K_STATE, v.toJson().toString()).apply() }

    val history: List<HistoryEntry>
        get() = p.getString(K_HISTORY, null)?.let { s -> runCatching { JSONArray(s).let { a -> (0 until a.length()).map { HistoryEntry.fromJson(a.getJSONObject(it)) } } }.getOrNull() } ?: emptyList()

    fun log(type: String, detail: String) {
        val list = (listOf(HistoryEntry(System.currentTimeMillis(), type, detail)) + history).take(200)
        p.edit().putString(K_HISTORY, JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()).apply()
    }

    fun mergeHistory(h: List<HistoryEntry>) {
        val list = HistoryEntry.merge(history, h).take(200)
        p.edit().putString(K_HISTORY, JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()).apply()
    }

    // ---------------- password (ported from ScreenLimit 1.0.2+) ----------------
    val hasPassword: Boolean get() = p.contains(K_PW_HASH)

    /** Sets a new password and returns the one-time recovery code. */
    fun setPassword(pw: String): String {
        val salt = Passcode.newSalt(); val hash = Passcode.hash(pw, salt)
        val code = Passcode.newRecoveryCode(); val rsalt = Passcode.newSalt()
        val rhash = Passcode.hash(Passcode.normalizeRecovery(code), rsalt)
        p.edit().putString(K_PW_HASH, b64(hash)).putString(K_PW_SALT, b64(salt)).putInt(K_PW_ITERS, Passcode.ITERATIONS)
            .putString(K_RC_HASH, b64(rhash)).putString(K_RC_SALT, b64(rsalt)).putInt(K_FAILS, 0).putLong(K_LOCK_UNTIL, 0).apply()
        return code
    }

    fun clearPassword() { p.edit().remove(K_PW_HASH).remove(K_PW_SALT).remove(K_PW_ITERS).remove(K_RC_HASH).remove(K_RC_SALT).putInt(K_FAILS, 0).apply() }

    /** Milliseconds until the next attempt is allowed (0 = now). */
    fun cooldownLeft(): Long = (p.getLong(K_LOCK_UNTIL, 0) - SystemClock.elapsedRealtime()).coerceAtLeast(0)

    sealed class Check { object Ok : Check(); object Wrong : Check(); data class Wait(val ms: Long) : Check() }

    fun check(pw: String): Check {
        cooldownLeft().let { if (it > 0) return Check.Wait(it) }
        val hash = p.getString(K_PW_HASH, null) ?: return Check.Ok
        val ok = Passcode.verify(pw, unb64(p.getString(K_PW_SALT, "")!!), unb64(hash), p.getInt(K_PW_ITERS, Passcode.ITERATIONS))
        return if (ok) { p.edit().putInt(K_FAILS, 0).putLong(K_LOCK_UNTIL, 0).apply(); Check.Ok } else fail()
    }

    /** Forgotten password: the recovery code removes it (a new one can then be set). */
    fun recover(code: String): Check {
        cooldownLeft().let { if (it > 0) return Check.Wait(it) }
        val h = p.getString(K_RC_HASH, null) ?: return Check.Wrong
        val ok = Passcode.verify(Passcode.normalizeRecovery(code), unb64(p.getString(K_RC_SALT, "")!!), unb64(h))
        return if (ok) { clearPassword(); Check.Ok } else fail()
    }

    private fun fail(): Check {
        val n = p.getInt(K_FAILS, 0) + 1
        val cd = Passcode.cooldownMsForFails(n)
        p.edit().putInt(K_FAILS, n).putLong(K_LOCK_UNTIL, if (cd > 0) SystemClock.elapsedRealtime() + cd else 0).apply()
        return if (cd > 0) Check.Wait(cd) else Check.Wrong
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        /** In Уют the limit starts switched off: the mode is often used only to hide forbidden apps. */
        val DEFAULTS = Settings(limitEnabled = false, warningsEnabled = true, finalWarning = false)
        private const val K_ENABLED = "enabled"; private const val K_BLOCKED = "blocked"; private const val K_SETTINGS = "settings"
        private const val K_STATE = "state"; private const val K_HISTORY = "history"
        private const val K_PW_HASH = "pw_hash"; private const val K_PW_SALT = "pw_salt"; private const val K_PW_ITERS = "pw_iters"
        private const val K_RC_HASH = "rc_hash"; private const val K_RC_SALT = "rc_salt"
        private const val K_FAILS = "pw_fails"; private const val K_LOCK_UNTIL = "pw_lock_until"
    }
}
