package com.example.homefit.model

import kotlinx.serialization.Serializable

@Serializable
data class WorkoutSet(
    val id: String,
    val sessionExerciseId: String,
    val setIndex: Int,
    val actualReps: Int,
    val actualWeight: Double? = null,
)
