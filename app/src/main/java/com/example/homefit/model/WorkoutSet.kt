package com.example.homefit.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionExercise::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sessionExerciseId", "setIndex"], unique = true),
    ],
)
data class WorkoutSet(
    @PrimaryKey
    val id: String,
    val sessionExerciseId: String,
    val setIndex: Int,
    val actualReps: Int,
    val actualWeight: Double? = null,
)
