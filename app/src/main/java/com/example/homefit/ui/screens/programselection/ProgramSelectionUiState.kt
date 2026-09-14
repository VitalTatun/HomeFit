package com.example.homefit.ui.screens.programselection

import com.example.homefit.model.WorkoutProgram

/**
 * Minimal screen state for the P2.6 Program Selection screen.
 *
 * An empty list is a normal state (rendered as the empty state), not an error:
 * starting a workout from here is simply unavailable until a program exists.
 */
sealed interface ProgramSelectionUiState {
    data object Loading : ProgramSelectionUiState

    data class Content(
        val programs: List<WorkoutProgram>,
    ) : ProgramSelectionUiState

    data object Empty : ProgramSelectionUiState

    data class Error(
        val message: String?,
    ) : ProgramSelectionUiState
}
