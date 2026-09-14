package com.example.homefit.ui.screens.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.homefit.data.WorkoutRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owner of the P2.5 Active Workout screen state.
 *
 * Single source of [WorkoutUiState], built only from
 * [WorkoutRepository.observeWorkoutSession] (session snapshot + sets).
 * The live catalog is never read here.
 *
 * Owns all coroutine work ([viewModelScope]), per-exercise recording guards
 * and the finish guard, so fast double-taps cannot create duplicate sets or
 * duplicate finish operations. Navigation itself stays in
 * `HomeFitNavDisplay`: completion is signalled via [navigateHome].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModel(
    private val sessionId: String,
    private val repository: WorkoutRepository,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<WorkoutUiState> =
        retryTrigger
            .flatMapLatest { repository.observeWorkoutSession(sessionId) }
            .map { observed ->
                if (observed == null) {
                    WorkoutUiState.Missing
                } else {
                    val setsByExercise = observed.sets.groupBy { it.sessionExerciseId }
                    val exercises = observed.items.map { item ->
                        ExerciseWithSets(
                            item = item,
                            sets = setsByExercise[item.id].orEmpty(),
                        )
                    }
                    if (observed.session.finishedAt == null) {
                        WorkoutUiState.Active(
                            session = observed.session,
                            exercises = exercises,
                        )
                    } else {
                        WorkoutUiState.Finished(
                            session = observed.session,
                            exercises = exercises,
                        )
                    }
                }
            }
            .catch { e ->
                emit(WorkoutUiState.Error(e.message))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = WorkoutUiState.Loading,
            )

    private val _recordingExercises = MutableStateFlow<Set<String>>(emptySet())

    /** Ids of session exercises with an in-flight [recordSet] call. */
    val recordingExercises: StateFlow<Set<String>> = _recordingExercises

    private val _isFinishing = MutableStateFlow(false)

    /** True while a [finishWorkout] call is in flight. */
    val isFinishing: StateFlow<Boolean> = _isFinishing

    private val _recordErrors = MutableStateFlow<Map<String, String?>>(emptyMap())

    /** Per-exercise record errors, keyed by `sessionExerciseId`. */
    val recordErrors: StateFlow<Map<String, String?>> = _recordErrors

    private val _finishError = MutableStateFlow<String?>(null)

    /** Last finish error, if any. */
    val finishError: StateFlow<String?> = _finishError

    private val navigateHomeChannel = Channel<Unit>(Channel.BUFFERED)

    /**
     * One-shot signal emitted after a successful [finishWorkout].
     * Collected by the screen to pop back to Home.
     */
    val navigateHome = navigateHomeChannel.receiveAsFlow()

    /**
     * Records one performed set. The `setIndex` is computed inside the
     * repository (`MAX(setIndex) + 1`); the UI never computes it.
     *
     * Per-exercise guarded: a second call for the same exercise while one
     * is in flight is ignored.
     */
    fun recordSet(
        sessionExerciseId: String,
        actualReps: Int,
        actualWeight: Double?,
    ) {
        if (_recordingExercises.value.contains(sessionExerciseId)) return
        viewModelScope.launch {
            _recordingExercises.value = _recordingExercises.value + sessionExerciseId
            _recordErrors.value = _recordErrors.value - sessionExerciseId
            try {
                repository.recordSet(sessionExerciseId, actualReps, actualWeight)
            } catch (e: Exception) {
                _recordErrors.value =
                    _recordErrors.value + (sessionExerciseId to (e.message ?: "Не удалось записать подход"))
            } finally {
                _recordingExercises.value = _recordingExercises.value - sessionExerciseId
            }
        }
    }

    /** Clears a displayed per-exercise record error. */
    fun consumeRecordError(sessionExerciseId: String) {
        _recordErrors.value = _recordErrors.value - sessionExerciseId
    }

    private val _deletingSets = MutableStateFlow<Set<String>>(emptySet())

    /** Ids of sets with an in-flight [deleteSet] call. */
    val deletingSets: StateFlow<Set<String>> = _deletingSets

    /**
     * Deletes one recorded set.
     *
     * Guarded against double-tap: a second call for the same set while one
     * is in flight is ignored. A failure is surfaced through the existing
     * per-exercise [recordErrors] slot of the exercise owning the set.
     */
    fun deleteSet(setId: String) {
        if (_deletingSets.value.contains(setId)) return
        viewModelScope.launch {
            _deletingSets.value = _deletingSets.value + setId
            try {
                repository.deleteSet(setId)
            } catch (e: Exception) {
                val exerciseId = (uiState.value as? WorkoutUiState.Active)
                    ?.exercises
                    ?.firstOrNull { exercise -> exercise.sets.any { it.id == setId } }
                    ?.item?.id
                if (exerciseId != null) {
                    _recordErrors.value =
                        _recordErrors.value + (exerciseId to (e.message ?: "Не удалось удалить подход"))
                }
            } finally {
                _deletingSets.value = _deletingSets.value - setId
            }
        }
    }

    /**
     * Marks the session finished. Guarded against double-tap: a second call
     * while one is in flight is ignored. On success emits [navigateHome].
     */
    fun finishWorkout() {
        if (_isFinishing.value) return
        viewModelScope.launch {
            _isFinishing.value = true
            _finishError.value = null
            try {
                repository.finishWorkout(sessionId)
                navigateHomeChannel.send(Unit)
            } catch (e: Exception) {
                _finishError.value = e.message ?: "Не удалось завершить тренировку"
            } finally {
                _isFinishing.value = false
            }
        }
    }

    /** Re-subscribes to the session flow after a [WorkoutUiState.Error]. */
    fun retry() {
        retryTrigger.value = retryTrigger.value + 1
    }
}

/**
 * Minimal P2.5-scoped factory: passes the [Workout] NavKey argument and the
 * repository from the manual composition root into [WorkoutViewModel].
 * No DI framework is used.
 */
class WorkoutViewModelFactory(
    private val sessionId: String,
    private val repository: WorkoutRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(WorkoutViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return WorkoutViewModel(sessionId, repository) as T
    }
}
