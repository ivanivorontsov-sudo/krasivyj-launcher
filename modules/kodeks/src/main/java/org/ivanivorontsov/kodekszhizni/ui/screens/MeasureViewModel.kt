package org.ivanivorontsov.kodekszhizni.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ivanivorontsov.kodekszhizni.data.Measure
import org.ivanivorontsov.kodekszhizni.data.MeasureStatus
import org.ivanivorontsov.kodekszhizni.data.MeasureType
import org.ivanivorontsov.kodekszhizni.data.Repository
import java.util.concurrent.TimeUnit

class MeasureViewModel(private val repo: Repository) : ViewModel() {
    val measures = repo.measures()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { repo.completeExpiredMeasures() }
    }

    fun start(
        type: MeasureType,
        title: String,
        description: String,
        durationHours: Long,
        endAtAbsolute: Long?
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val end = when {
                endAtAbsolute != null -> endAtAbsolute
                durationHours > 0 -> now + TimeUnit.HOURS.toMillis(durationHours)
                else -> null
            }
            repo.startMeasure(
                Measure(
                    type = type,
                    title = title.ifBlank { defaultTitle(type) },
                    description = description,
                    durationHours = durationHours,
                    startedAt = now,
                    endAt = end,
                    status = MeasureStatus.ACTIVE
                )
            )
        }
    }

    fun stop(id: Long, completed: Boolean) {
        viewModelScope.launch { repo.stopMeasure(id, completed) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.deleteMeasure(id) }
    }

    private fun defaultTitle(type: MeasureType) = when (type) {
        MeasureType.DIGITAL_DETOX -> "Цифровой детокс"
        MeasureType.EARLY_WAKE -> "Ранний подъём"
        MeasureType.NO_SOCIAL -> "Без соцсетей"
        MeasureType.CUSTOM -> "Своя мера"
    }

    companion object {
        fun factory(repo: Repository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MeasureViewModel(repo) as T
        }
    }
}
