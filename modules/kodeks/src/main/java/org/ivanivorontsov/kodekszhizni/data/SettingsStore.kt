package org.ivanivorontsov.kodekszhizni.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("kodeks_settings")

data class NotificationSettings(
    val enabled: Boolean = true,
    val morningHour: Int = 8,
    val morningMinute: Int = 0,
    val middayHour: Int = 13,
    val middayMinute: Int = 0,
    val eveningHour: Int = 20,
    val eveningMinute: Int = 0,
    val monthlyReviewEnabled: Boolean = true
)

class SettingsStore(private val context: Context) {
    private val enabled = booleanPreferencesKey("notif_enabled")
    private val mH = intPreferencesKey("morning_h")
    private val mM = intPreferencesKey("morning_m")
    private val dH = intPreferencesKey("midday_h")
    private val dM = intPreferencesKey("midday_m")
    private val eH = intPreferencesKey("evening_h")
    private val eM = intPreferencesKey("evening_m")
    private val monthly = booleanPreferencesKey("monthly_review")

    val settings: Flow<NotificationSettings> = context.dataStore.data.map { p ->
        NotificationSettings(
            enabled = p[enabled] ?: true,
            morningHour = p[mH] ?: 8,
            morningMinute = p[mM] ?: 0,
            middayHour = p[dH] ?: 13,
            middayMinute = p[dM] ?: 0,
            eveningHour = p[eH] ?: 20,
            eveningMinute = p[eM] ?: 0,
            monthlyReviewEnabled = p[monthly] ?: true
        )
    }

    suspend fun update(transform: (NotificationSettings) -> NotificationSettings) {
        context.dataStore.edit { p ->
            val cur = NotificationSettings(
                enabled = p[enabled] ?: true,
                morningHour = p[mH] ?: 8,
                morningMinute = p[mM] ?: 0,
                middayHour = p[dH] ?: 13,
                middayMinute = p[dM] ?: 0,
                eveningHour = p[eH] ?: 20,
                eveningMinute = p[eM] ?: 0,
                monthlyReviewEnabled = p[monthly] ?: true
            )
            val n = transform(cur)
            p[enabled] = n.enabled
            p[mH] = n.morningHour
            p[mM] = n.morningMinute
            p[dH] = n.middayHour
            p[dM] = n.middayMinute
            p[eH] = n.eveningHour
            p[eM] = n.eveningMinute
            p[monthly] = n.monthlyReviewEnabled
        }
    }
}
