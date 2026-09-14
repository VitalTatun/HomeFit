package com.example.homefit.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String? = null,
)
