package org.ivanivorontsov.launcher.data

import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.profileinstaller.ProfileInstaller
import androidx.profileinstaller.ProfileVerifier
import org.ivanivorontsov.launcher.BuildConfig

/**
 * 1.4.4: one-time work after install/update, run off the main thread once the UI is idle.
 *  - writes the Baseline Profile explicitly (in addition to ProfileInstallerInitializer) and records
 *    ProfileVerifier's verdict, shown in Settings → «Плавность»;
 *  1.4.5: class preloading removed (it could contend for class-loading locks with the UI thread during the
 *  first swipes); the work now starts later (8 s / 15 s) at minimum priority and touches only small files.
 * Nothing is drawn; no visual change. AOT compilation of the profile itself is done by Android's background
 * dexopt (idle + charging) — an app cannot force it without adb.
 */
object UpdateWarmUp {
    private const val P = "update_warmup"
    @Volatile var status: String = "проверяется…"; private set

    fun schedule(ctx: Context) {
        val app = ctx.applicationContext
        val prefs = app.getSharedPreferences(P, Context.MODE_PRIVATE)
        status = prefs.getString("status", "проверяется…") ?: ""
        val first = prefs.getInt("vc", 0) != BuildConfig.VERSION_CODE
        Looper.myQueue().addIdleHandler {
            Thread({
                try { Thread.sleep(if (first) 8000 else 15000) } catch (_: InterruptedException) {}
                if (first) {
                    try { ProfileInstaller.writeProfile(app) } catch (t: Throwable) { Log.w("UyutWarm", "writeProfile", t) }
                    prefs.edit().putInt("vc", BuildConfig.VERSION_CODE).apply()
                }
                status = try { describe(ProfileVerifier.getCompilationStatusAsync().get()) } catch (t: Throwable) { "неизвестно" }
                prefs.edit().putString("status", status).apply()
                Log.i("UyutWarm", "profile: $status first=$first")
            }, "uyut-warmup").apply { priority = Thread.MIN_PRIORITY; isDaemon = true }.start()
            false
        }
    }

    private fun describe(s: ProfileVerifier.CompilationStatus): String = when {
        s.isCompiledWithProfile -> "скомпилирован с профилем (AOT) — максимальная плавность"
        s.hasProfileEnqueuedForCompilation() -> "профиль установлен, Android скомпилирует его в простое на зарядке"
        else -> when (s.profileInstallResultCode) {
            ProfileVerifier.CompilationStatus.RESULT_CODE_NO_PROFILE -> "профиль ещё не установлен"
            else -> "профиль не применён (код ${s.profileInstallResultCode})"
        }
    }

    private fun preload() {
        val cl = UpdateWarmUp::class.java.classLoader
        listOf(
            "org.ivanivorontsov.launcher.ui.HomeKt", "org.ivanivorontsov.launcher.ui.PageFxKt", "org.ivanivorontsov.launcher.ui.WidgetsKt",
            "org.ivanivorontsov.launcher.ui.HomeWidgetsUiKt", "org.ivanivorontsov.launcher.ui.ClockKt", "org.ivanivorontsov.launcher.ui.ReturnKt",
            "androidx.compose.foundation.pager.PagerState", "androidx.compose.foundation.pager.PagerKt",
            "androidx.compose.foundation.gestures.ScrollableKt", "androidx.compose.ui.viewinterop.AndroidViewHolder",
            "android.appwidget.AppWidgetHostView", "android.widget.RemoteViews",
        ).forEach { try { Class.forName(it, true, cl) } catch (_: Throwable) {} }
    }
}
