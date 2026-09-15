package com.example.homefit.ui.screens.statistics

import com.example.homefit.data.StatisticsData

sealed interface StatisticsUiState {
    data object Loading : StatisticsUiState

    data class Content(
        val data: StatisticsData,
    ) : StatisticsUiState

    data class Error(
        val message: String?,
    ) : StatisticsUiState
}
