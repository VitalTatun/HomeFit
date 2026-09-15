package com.example.homefit.model

/**
 * One recorded set of a finished workout session, projected for statistics.
 *
 * Plain Room `@Query` result, not an `@Entity`: it introduces no table and
 * never changes the database schema. `exerciseName` is the frozen snapshot
 * stored on [WorkoutSessionExercise], so renames or deletions in the live
 * catalog never rewrite history.
 */
data class FinishedSessionSet(
    val setId: String,
    val exerciseId: String,
    val exerciseName: String,
    val actualReps: Int,
    val actualWeight: Double?,
)
