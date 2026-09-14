package com.example.homefit.model

import kotlinx.serialization.Serializable

@Serializable
data class ProgramExercise(
    val id: String,
    val programId: String,
    val exerciseId: String,
    val position: Int,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeight: Double? = null,
)
