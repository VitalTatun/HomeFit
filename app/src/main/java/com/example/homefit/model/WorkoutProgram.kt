package com.example.homefit.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "workout_programs")
data class WorkoutProgram(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String? = null,
)
