package com.example.homefit.model

import kotlinx.serialization.Serializable

@Serializable
data class WorkoutSessionExercise(
    val id: String,
    val sessionId: String,
    val programExerciseId: String? = null,
    val exerciseId: String,
    val exerciseName: String,
    val position: Int,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeight: Double? = null,
)
