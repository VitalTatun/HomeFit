package com.example.homefit.ui.screens.history

import com.example.homefit.model.WorkoutSession

sealed interface HistoryUiState {
    data object Loading : HistoryUiState

    data class Content(
        val sessions: List<WorkoutSession>,
    ) : HistoryUiState

    data class Error(
        val message: String?,
    ) : HistoryUiState
}
