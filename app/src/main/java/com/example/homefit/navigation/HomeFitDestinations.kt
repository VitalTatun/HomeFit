package com.example.homefit.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Home : NavKey

@Serializable
data object ProgramSelection : NavKey

@Serializable
data class ProgramEditor(
    val programId: String?,
) : NavKey

@Serializable
data class Workout(
    val sessionId: String,
) : NavKey
