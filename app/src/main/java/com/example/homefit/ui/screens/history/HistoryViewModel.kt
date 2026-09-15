package com.example.homefit.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.homefit.data.WorkoutRepository
import com.example.homefit.model.WorkoutSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repository: WorkoutRepository,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<HistoryUiState> =
        retryTrigger
            .flatMapLatest { repository.observeHistory() }
            .map<List<WorkoutSession>, HistoryUiState> { sessions ->
                HistoryUiState.Content(sessions)
            }
            .catch { e ->
                emit(HistoryUiState.Error(e.message))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HistoryUiState.Loading,
            )

    fun retry() {
        retryTrigger.value = retryTrigger.value + 1
    }
}

class HistoryViewModelFactory(
    private val repository: WorkoutRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return HistoryViewModel(repository) as T
    }
}
