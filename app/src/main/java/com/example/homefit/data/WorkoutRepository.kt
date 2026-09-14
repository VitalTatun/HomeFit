package com.example.homefit.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.example.homefit.model.Exercise
import com.example.homefit.model.ProgramExercise
import com.example.homefit.model.WorkoutProgram
import com.example.homefit.model.WorkoutSession
import com.example.homefit.model.WorkoutSessionExercise
import com.example.homefit.model.WorkoutSet
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Combined projection of a workout session execution state.
 *
 * Built only from history tables ([WorkoutSession], [WorkoutSessionExercise],
 * [WorkoutSet]); the live catalog is never read here.
 */
data class ObservedWorkoutSession(
    val session: WorkoutSession,
    val items: List<WorkoutSessionExercise>,
    val sets: List<WorkoutSet>,
)

/**
 * Application boundary between the execution layer and Room.
 *
 * Owns application-level validation and id generation. DAOs are kept private
 * and are never exposed through this API.
 */
class WorkoutRepository(
    private val database: HomeFitDatabase,
    private val catalogDao: CatalogDao,
    private val workoutDao: WorkoutDao,
) {

    // TODO [P2.6]: TEMPORARY P2.3 scaffolding — REMOVE AFTER P2.6 (real program selection replaces this).
    private companion object {
        // TODO [P2.6]: TEMPORARY P2.3 scaffolding — REMOVE AFTER P2.6 (real program selection replaces this).
        private const val DEFAULT_PROGRAM_ID = "default-program"

        // TODO [P2.6]: TEMPORARY P2.3 scaffolding — REMOVE AFTER P2.6 (real program selection replaces this).
        private const val DEFAULT_EXERCISE_1_ID = "default-exercise-1"

        // TODO [P2.6]: TEMPORARY P2.3 scaffolding — REMOVE AFTER P2.6 (real program selection replaces this).
        private const val DEFAULT_EXERCISE_2_ID = "default-exercise-2"

        // TODO [P2.6]: TEMPORARY P2.3 scaffolding — REMOVE AFTER P2.6 (real program selection replaces this).
        private const val DEFAULT_EXERCISE_3_ID = "default-exercise-3"

        // TODO [P2.6]: TEMPORARY P2.3 scaffolding — REMOVE AFTER P2.6 (real program selection replaces this).
        private const val DEFAULT_PROGRAM_EXERCISE_1_ID = "default-program-exercise-1"

        // TODO [P2.6]: TEMPORARY P2.3 scaffolding — REMOVE AFTER P2.6 (real program selection replaces this).
        private const val DEFAULT_PROGRAM_EXERCISE_2_ID = "default-program-exercise-2"

        // TODO [P2.6]: TEMPORARY P2.3 scaffolding — REMOVE AFTER P2.6 (real program selection replaces this).
        private const val DEFAULT_PROGRAM_EXERCISE_3_ID = "default-program-exercise-3"
    }

    /**
     * Returns the id of the temporary default program, creating it on first call.
     *
     * Idempotent: when the program already exists nothing is inserted.
     * The whole seed graph is created atomically via
     * [CatalogDao.insertDefaultProgramTx].
     *
     * TODO [P2.6]: TEMPORARY P2.3 scaffolding — REMOVE AFTER P2.6 (real program selection replaces this).
     */
    // TODO [P2.6]: TEMPORARY P2.3 scaffolding — REMOVE AFTER P2.6 (real program selection replaces this).
    suspend fun ensureDefaultProgram(): String {
        if (catalogDao.getProgramById(DEFAULT_PROGRAM_ID) != null) {
            return DEFAULT_PROGRAM_ID
        }
        try {
            catalogDao.insertDefaultProgramTx(
                exercises = listOf(
                    Exercise(
                        id = DEFAULT_EXERCISE_1_ID,
                        name = "Default Exercise 1",
                    ),
                    Exercise(
                        id = DEFAULT_EXERCISE_2_ID,
                        name = "Default Exercise 2",
                    ),
                    Exercise(
                        id = DEFAULT_EXERCISE_3_ID,
                        name = "Default Exercise 3",
                    ),
                ),
                program = WorkoutProgram(
                    id = DEFAULT_PROGRAM_ID,
                    name = "Default Program",
                ),
                items = listOf(
                    ProgramExercise(
                        id = DEFAULT_PROGRAM_EXERCISE_1_ID,
                        programId = DEFAULT_PROGRAM_ID,
                        exerciseId = DEFAULT_EXERCISE_1_ID,
                        position = 0,
                        targetSets = 3,
                        targetReps = 10,
                    ),
                    ProgramExercise(
                        id = DEFAULT_PROGRAM_EXERCISE_2_ID,
                        programId = DEFAULT_PROGRAM_ID,
                        exerciseId = DEFAULT_EXERCISE_2_ID,
                        position = 1,
                        targetSets = 3,
                        targetReps = 12,
                    ),
                    ProgramExercise(
                        id = DEFAULT_PROGRAM_EXERCISE_3_ID,
                        programId = DEFAULT_PROGRAM_ID,
                        exerciseId = DEFAULT_EXERCISE_3_ID,
                        position = 2,
                        targetSets = 3,
                        targetReps = 8,
                        targetWeight = 10.0,
                    ),
                ),
            )
        } catch (e: SQLiteConstraintException) {
            // Concurrent ensureDefaultProgram() won: the graph already exists.
            check(catalogDao.getProgramById(DEFAULT_PROGRAM_ID) != null) {
                "Default program seed failed"
            }
        }
        return DEFAULT_PROGRAM_ID
    }

    /**
     * Freezes a live program plan into a new historical session graph.
     *
     * The plan is read via [CatalogDao.getPlanSnapshotTx] and the session is
     * written via [WorkoutDao.insertSessionGraphTx]. After this call the
     * execution layer only observes the frozen snapshot, never the live plan.
     */
    suspend fun startWorkout(programId: String): String {
        check(workoutDao.getActiveSession() == null) {
            "Cannot start workout: another session is already active"
        }

        val snapshot = catalogDao.getPlanSnapshotTx(programId)
            ?: throw IllegalArgumentException("Program not found: $programId")
        check(snapshot.items.isNotEmpty()) {
            "Cannot start workout: program is empty: $programId"
        }
        snapshot.items.forEach { item ->
            require(item.targetSets >= 1) {
                "Invalid targetSets (${item.targetSets}) in program exercise: ${item.id}"
            }
            require(item.targetReps >= 1) {
                "Invalid targetReps (${item.targetReps}) in program exercise: ${item.id}"
            }
            require(item.targetWeight == null || item.targetWeight > 0) {
                "Invalid targetWeight (${item.targetWeight}) in program exercise: ${item.id}"
            }
            check(snapshot.exercisesById.containsKey(item.exerciseId)) {
                "Cannot start workout: exercise not found: ${item.exerciseId}"
            }
        }

        val sessionId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        val session = WorkoutSession(
            id = sessionId,
            programId = snapshot.program.id,
            programName = snapshot.program.name,
            startedAt = startedAt,
            finishedAt = null,
        )
        val sessionExercises = snapshot.items.map { item ->
            WorkoutSessionExercise(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                programExerciseId = item.id,
                exerciseId = item.exerciseId,
                exerciseName = requireNotNull(snapshot.exercisesById[item.exerciseId]).name,
                position = item.position,
                targetSets = item.targetSets,
                targetReps = item.targetReps,
                targetWeight = item.targetWeight,
            )
        }

        workoutDao.insertSessionGraphTx(session, sessionExercises)
        return sessionId
    }

    fun observeWorkoutSession(sessionId: String): Flow<ObservedWorkoutSession?> =
        combine(
            workoutDao.observeSession(sessionId),
            workoutDao.observeItemsBySession(sessionId),
            workoutDao.observeSetsBySession(sessionId),
        ) { session, items, sets ->
            session?.let { ObservedWorkoutSession(it, items, sets) }
        }

    fun observeActiveSession(): Flow<WorkoutSession?> =
        workoutDao.observeActiveSession()

    /**
     * Records one performed set. The new set gets `MAX(setIndex) + 1`
     * (`0` when there are no sets yet); existing sets are never renumbered.
     *
     * On a [SQLiteConstraintException] from a concurrent insert race the
     * operation is retried exactly once with a re-read maximum.
     */
    suspend fun recordSet(
        sessionExerciseId: String,
        actualReps: Int,
        actualWeight: Double?,
    ): String {
        require(actualReps >= 0) {
            "Invalid actualReps: $actualReps"
        }
        require(actualWeight == null || actualWeight > 0) {
            "Invalid actualWeight: $actualWeight"
        }
        try {
            return recordSetOnce(sessionExerciseId, actualReps, actualWeight)
        } catch (e: SQLiteConstraintException) {
            return recordSetOnce(sessionExerciseId, actualReps, actualWeight)
        }
    }

    private suspend fun recordSetOnce(
        sessionExerciseId: String,
        actualReps: Int,
        actualWeight: Double?,
    ): String {
        val setId = UUID.randomUUID().toString()
        database.withTransaction {
            val item = workoutDao.getSessionExerciseById(sessionExerciseId)
                ?: throw IllegalArgumentException(
                    "Session exercise not found: $sessionExerciseId",
                )
            val session = workoutDao.getSessionById(item.sessionId)
                ?: throw IllegalStateException(
                    "Session not found for exercise: $sessionExerciseId",
                )
            check(session.finishedAt == null) {
                "Cannot record set: session is already finished: ${session.id}"
            }
            val nextIndex = (workoutDao.getMaxSetIndex(sessionExerciseId) ?: -1) + 1
            workoutDao.insertSet(
                WorkoutSet(
                    id = setId,
                    sessionExerciseId = sessionExerciseId,
                    setIndex = nextIndex,
                    actualReps = actualReps,
                    actualWeight = actualWeight,
                ),
            )
        }
        return setId
    }

    /**
     * Marks a session finished. Re-finishing is a no-op: the first
     * `finishedAt` value is never overwritten.
     */
    suspend fun finishWorkout(sessionId: String) {
        workoutDao.finishSession(sessionId, System.currentTimeMillis())
    }
}
