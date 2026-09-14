package com.example.homefit.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "workout_session_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProgramExercise::class,
            parentColumns = ["id"],
            childColumns = ["programExerciseId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["sessionId", "position"], unique = true),
        Index(value = ["exerciseId"]),
        Index(value = ["programExerciseId"]),
    ],
)
data class WorkoutSessionExercise(
    @PrimaryKey
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
