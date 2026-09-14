package com.example.homefit.model

import kotlinx.serialization.Serializable

@Serializable
data class WorkoutProgram(
    val id: String,
    val name: String,
    val description: String? = null,
)
