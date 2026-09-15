package com.example.homefit.ui.screens.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.homefit.data.StatisticsData
import com.example.homefit.data.WorkoutRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(
    private val repository: WorkoutRepository,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<StatisticsUiState> =
        retryTrigger
            .flatMapLatest { repository.observeStatistics() }
            .map<StatisticsData, StatisticsUiState> { data ->
                StatisticsUiState.Content(data)
            }
            .catch { e ->
                emit(StatisticsUiState.Error(e.message))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = StatisticsUiState.Loading,
            )

    fun retry() {
        retryTrigger.value = retryTrigger.value + 1
    }
}

class StatisticsViewModelFactory(
    private val repository: WorkoutRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(StatisticsViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return StatisticsViewModel(repository) as T
    }
}
