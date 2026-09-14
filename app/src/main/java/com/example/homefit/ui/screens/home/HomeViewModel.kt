package com.example.homefit.ui.screens.home

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

/**
 * Owner of the P2.7 Home screen state.
 *
 * Single source of [HomeUiState], built only from
 * [WorkoutRepository.observeActiveSession]. A null
 * [HomeUiState.Content.activeSession] is the normal "no active workout"
 * state (rendered as the Start CTA), not an error.
 *
 * Owns no navigation: `HomeFitNavDisplay` collects [uiState] and performs
 * the actual `ProgramSelection` / `Workout(existingSessionId)` back stack
 * updates. Resume never calls `startWorkout`: the existing session id comes
 * from Room, which is the single source of truth for Resume.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: WorkoutRepository,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<HomeUiState> =
        retryTrigger
            .flatMapLatest { repository.observeActiveSession() }
            .map<WorkoutSession?, HomeUiState> { activeSession ->
                HomeUiState.Content(activeSession)
            }
            .catch { e ->
                emit(HomeUiState.Error(e.message))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HomeUiState.Loading,
            )

    /** Re-subscribes to the active session flow after a [HomeUiState.Error]. */
    fun retry() {
        retryTrigger.value = retryTrigger.value + 1
    }
}

/**
 * Minimal screen state for the P2.7 Home screen.
 *
 * `Content(null)` means no active workout: Home renders "Начать тренировку".
 * `Content(session)` means an unfinished session exists: Home renders
 * "Продолжить тренировку", resuming `session.id` without creating a session.
 */
sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Content(
        val activeSession: WorkoutSession?,
    ) : HomeUiState

    data class Error(
        val message: String?,
    ) : HomeUiState
}

/**
 * Minimal P2.7-scoped factory: passes the repository from the manual
 * composition root into [HomeViewModel]. No DI framework is used.
 */
class HomeViewModelFactory(
    private val repository: WorkoutRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return HomeViewModel(repository) as T
    }
}
