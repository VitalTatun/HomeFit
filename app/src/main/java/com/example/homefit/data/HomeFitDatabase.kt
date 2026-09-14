package com.example.homefit.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.homefit.model.Exercise
import com.example.homefit.model.ProgramExercise
import com.example.homefit.model.WorkoutProgram
import com.example.homefit.model.WorkoutSession
import com.example.homefit.model.WorkoutSessionExercise
import com.example.homefit.model.WorkoutSet

@Database(
    entities = [
        Exercise::class,
        WorkoutProgram::class,
        ProgramExercise::class,
        WorkoutSession::class,
        WorkoutSessionExercise::class,
        WorkoutSet::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class HomeFitDatabase : RoomDatabase() {

    abstract fun catalogDao(): CatalogDao

    abstract fun workoutDao(): WorkoutDao
}
