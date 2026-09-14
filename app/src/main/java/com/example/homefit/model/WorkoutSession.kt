package com.example.homefit.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "workout_sessions",
    indices = [
        Index(value = ["finishedAt"]),
        Index(value = ["programId", "startedAt"]),
    ],
)
data class WorkoutSession(
    @PrimaryKey
    val id: String,
    val programId: String,
    val programName: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
)
