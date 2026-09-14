package com.example.homefit

import android.app.Application
import androidx.room.Room
import com.example.homefit.data.HomeFitDatabase
import com.example.homefit.data.WorkoutRepository

/**
 * Manual composition root: Room database singleton -> DAOs -> repository.
 *
 * No DI framework is used. No ViewModels are created here yet.
 */
class HomeFitApplication : Application() {

    val database: HomeFitDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            HomeFitDatabase::class.java,
            "homefit.db",
        ).build()
    }

    val workoutRepository: WorkoutRepository by lazy {
        WorkoutRepository(
            database = database,
            catalogDao = database.catalogDao(),
            workoutDao = database.workoutDao(),
        )
    }
}
