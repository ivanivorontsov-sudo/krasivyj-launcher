package org.ivanivorontsov.kodekszhizni.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ivanivorontsov.kodekszhizni.data.NotificationSettings
import org.ivanivorontsov.kodekszhizni.data.SettingsStore
import org.ivanivorontsov.kodekszhizni.worker.NotificationScheduler

class SettingsViewModel(
    private val store: SettingsStore,
    private val appContext: Context
) : ViewModel() {
    val settings = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationSettings())

    fun setEnabled(v: Boolean) = persist { it.copy(enabled = v) }
    fun setMorning(h: Int, m: Int) = persist { it.copy(morningHour = h, morningMinute = m) }
    fun setMidday(h: Int, m: Int) = persist { it.copy(middayHour = h, middayMinute = m) }
    fun setEvening(h: Int, m: Int) = persist { it.copy(eveningHour = h, eveningMinute = m) }
    fun setMonthly(v: Boolean) = persist { it.copy(monthlyReviewEnabled = v) }

    fun sendTestTip() {
        NotificationScheduler.scheduleImmediateTest(appContext)
    }

    private fun persist(transform: (NotificationSettings) -> NotificationSettings) {
        viewModelScope.launch {
            store.update(transform)
            NotificationScheduler.reschedule(appContext, store.settings.first())
        }
    }

    companion object {
        fun factory(store: SettingsStore, context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(store, context.applicationContext) as T
        }
    }
}
