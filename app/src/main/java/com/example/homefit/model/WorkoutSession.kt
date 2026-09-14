package com.example.homefit.model

import kotlinx.serialization.Serializable

@Serializable
data class WorkoutSession(
    val id: String,
    val programId: String,
    val programName: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
)
