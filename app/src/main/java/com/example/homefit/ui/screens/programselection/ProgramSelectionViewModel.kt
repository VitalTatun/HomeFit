package com.example.homefit.ui.screens.programselection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.homefit.data.WorkoutRepository
import com.example.homefit.model.WorkoutProgram
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Owner of the P2.6 Program Selection screen state.
 *
 * Single source of [ProgramSelectionUiState], built only from
 * [WorkoutRepository.observePrograms]. The built-in starter catalog is
 * provisioned first via [WorkoutRepository.ensureDefaultProgram], so a fresh
 * install always has at least one selectable program; provisioning is
 * idempotent and never creates duplicates.
 *
 * Owns no navigation: the selected program id is reported via
 * `onProgramSelected`, and `HomeFitNavDisplay` performs the actual
 * `startWorkout` + back stack update.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgramSelectionViewModel(
    private val repository: WorkoutRepository,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<ProgramSelectionUiState> =
        retryTrigger
            .flatMapLatest { programs() }
            .map { programs ->
                if (programs.isEmpty()) {
                    ProgramSelectionUiState.Empty
                } else {
                    ProgramSelectionUiState.Content(programs)
                }
            }
            .catch { e ->
                emit(ProgramSelectionUiState.Error(e.message))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ProgramSelectionUiState.Loading,
            )

    private fun programs(): Flow<List<WorkoutProgram>> =
        flow {
            repository.ensureDefaultProgram()
            emitAll(repository.observePrograms())
        }

    /** Re-runs provisioning and re-subscribes after a [ProgramSelectionUiState.Error]. */
    fun retry() {
        retryTrigger.value = retryTrigger.value + 1
    }
}

/**
 * Minimal P2.6-scoped factory: passes the repository from the manual
 * composition root into [ProgramSelectionViewModel]. No DI framework is used.
 */
class ProgramSelectionViewModelFactory(
    private val repository: WorkoutRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ProgramSelectionViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return ProgramSelectionViewModel(repository) as T
    }
}
