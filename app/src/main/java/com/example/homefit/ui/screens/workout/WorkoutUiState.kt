package com.example.homefit.ui.screens.workout

import com.example.homefit.model.WorkoutSession
import com.example.homefit.model.WorkoutSessionExercise
import com.example.homefit.model.WorkoutSet

/**
 * One frozen session exercise together with its recorded sets.
 *
 * Grouping ([WorkoutSet] by `sessionExerciseId`) is done in
 * [WorkoutViewModel]; no separate mapper/DTO layer is introduced for P2.5.
 */
data class ExerciseWithSets(
    val item: WorkoutSessionExercise,
    val sets: List<WorkoutSet>,
)

/**
 * Minimal screen state for the P2.5 Active Workout screen.
 *
 * An empty sets list is a normal state of a fresh workout, not an error.
 */
sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState

    data class Active(
        val session: WorkoutSession,
        val exercises: List<ExerciseWithSets>,
    ) : WorkoutUiState

    data class Finished(
        val session: WorkoutSession,
        val exercises: List<ExerciseWithSets>,
    ) : WorkoutUiState

    data object Missing : WorkoutUiState

    data class Error(
        val message: String?,
    ) : WorkoutUiState
}
