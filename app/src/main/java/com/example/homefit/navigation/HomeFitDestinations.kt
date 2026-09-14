package com.example.homefit.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Home : NavKey

@Serializable
data class Workout(
    val sessionId: String,
) : NavKey
