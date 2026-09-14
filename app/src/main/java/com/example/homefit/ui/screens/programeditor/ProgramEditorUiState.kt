package com.example.homefit.ui.screens.programeditor

import com.example.homefit.model.Exercise

/**
 * One editable program row.
 *
 * Text-based fields mirror the `SetInput` pattern from the Workout screen:
 * parsing happens on save, so typing is never blocked. [key] is stable for
 * `LazyColumn` (`itemId` for existing rows, a generated key for new ones).
 */
data class DraftRow(
    val key: String,
    val itemId: String?,
    val exerciseId: String,
    val exerciseName: String,
    val setsText: String,
    val repsText: String,
    val weightText: String,
)

/**
 * Minimal screen state for the Program Editor screen.
 *
 * `Content` carries the editable draft plus the exercise catalog for the
 * add-picker. An empty [DraftRow] list is normal (an empty program can be
 * saved; `startWorkout` rejects it later). `Missing` means the edited
 * program does not exist (e.g. deleted elsewhere).
 */
sealed interface ProgramEditorUiState {
    data object Loading : ProgramEditorUiState

    data class Content(
        val programId: String?,
        val name: String,
        val description: String,
        val rows: List<DraftRow>,
        val catalog: List<Exercise>,
        val isSaving: Boolean,
        val saveError: String?,
    ) : ProgramEditorUiState

    data object Missing : ProgramEditorUiState

    data class Error(
        val message: String?,
    ) : ProgramEditorUiState
}
