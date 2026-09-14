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
 * Reactive projection of a live program plan for the Program Editor.
 *
 * Mirrors [PlanSnapshot] but stays reactive: `items` are ordered by
 * `position`, `exercisesById` covers only referenced exercises.
 */
data class ProgramDetail(
    val program: WorkoutProgram,
    val items: List<ProgramExercise>,
    val exercisesById: Map<String, Exercise>,
)

/**
 * Editor input for one program row.
 *
 * `id == null` means "new row" (a fresh id is generated on save);
 * non-null ids must belong to the saved program, otherwise save fails.
 * Positions are never taken from the caller: the repository normalizes
 * them to 0..n-1 in list order before writing.
 */
data class ProgramItemInput(
    val id: String? = null,
    val exerciseId: String,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeight: Double? = null,
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

    /**
     * Stable ids of the built-in starter catalog (P2.6).
     *
     * The catalog is provisioned on demand by [ensureDefaultProgram] and is a
     * permanent part of the app: a fresh install always has at least one
     * selectable program. Ids are stable so existing installs and historical
     * sessions keep working.
     */
    private companion object {
        private const val DEFAULT_PROGRAM_ID = "default-program"

        private const val DEFAULT_EXERCISE_1_ID = "default-exercise-1"

        private const val DEFAULT_EXERCISE_2_ID = "default-exercise-2"

        private const val DEFAULT_EXERCISE_3_ID = "default-exercise-3"

        private const val DEFAULT_PROGRAM_EXERCISE_1_ID = "default-program-exercise-1"

        private const val DEFAULT_PROGRAM_EXERCISE_2_ID = "default-program-exercise-2"

        private const val DEFAULT_PROGRAM_EXERCISE_3_ID = "default-program-exercise-3"
    }

    /**
     * Observes the program catalog for the P2.6 program selection screen.
     *
     * Plain delegation to [CatalogDao.observePrograms]: no validation and no
     * provisioning happen here. An empty list is a normal state (rendered as
     * the selection empty state), not an error.
     */
    fun observePrograms(): Flow<List<WorkoutProgram>> =
        catalogDao.observePrograms()

    /**
     * Observes one program graph for the Program Editor.
     *
     * Emits `null` when the program does not exist. Items are already
     * ordered by `position` by the DAO query.
     */
    fun observeProgramDetail(programId: String): Flow<ProgramDetail?> =
        combine(
            catalogDao.observeProgramById(programId),
            catalogDao.observeProgramExercises(programId),
            catalogDao.observeExercises(),
        ) { program, items, exercises ->
            if (program == null) {
                null
            } else {
                val byId = exercises.associateBy { it.id }
                    .filterKeys { id -> items.any { it.exerciseId == id } }
                ProgramDetail(program, items, byId)
            }
        }

    /**
     * Observes the exercise catalog for the Program Editor picker.
     */
    fun observeExercises(): Flow<List<Exercise>> =
        catalogDao.observeExercises()

    /**
     * Creates a new program graph atomically.
     *
     * Positions are normalized to 0..n-1 in list order; callers never set
     * them. Targets use the same rules as [startWorkout].
     */
    suspend fun createProgram(
        name: String,
        description: String? = null,
        items: List<ProgramItemInput> = emptyList(),
    ): String {
        require(name.isNotBlank()) { "Invalid program name: blank" }
        require(items.all { it.id == null }) {
            "Invalid program item: new program cannot reuse existing ids"
        }
        validateEditorInputs(items)
        val exerciseIds = items.map { it.exerciseId }.distinct()
        if (exerciseIds.isNotEmpty()) {
            val found = catalogDao.getExercisesByIds(exerciseIds).map { it.id }.toSet()
            exerciseIds.forEach { id ->
                check(found.contains(id)) { "Cannot save program: exercise not found: $id" }
            }
        }

        val programId = UUID.randomUUID().toString()
        val program = WorkoutProgram(id = programId, name = name, description = description)
        val rows = items.mapIndexed { index, input ->
            ProgramExercise(
                id = UUID.randomUUID().toString(),
                programId = programId,
                exerciseId = input.exerciseId,
                position = index,
                targetSets = input.targetSets,
                targetReps = input.targetReps,
                targetWeight = input.targetWeight,
            )
        }
        catalogDao.createProgramGraphTx(program, rows)
        return programId
    }

    /**
     * Saves a program graph atomically (rename + add/update/remove/reorder).
     *
     * Rows absent from [items] are deleted, `id == null` rows are inserted,
     * kept rows are updated in place with normalized positions. The DAO moves
     * kept rows through a negative temporary range first, so reorder never
     * violates the unique (programId, position) index.
     */
    suspend fun saveProgram(
        program: WorkoutProgram,
        items: List<ProgramItemInput>,
    ) {
        require(program.name.isNotBlank()) { "Invalid program name: blank" }
        catalogDao.getProgramById(program.id)
            ?: throw IllegalArgumentException("Program not found: ${program.id}")
        validateEditorInputs(items)

        val nonNullIds = items.mapNotNull { it.id }
        require(nonNullIds.size == nonNullIds.toSet().size) {
            "Invalid program items: duplicate ids"
        }
        val existingIds = catalogDao.getProgramExercisesOrdered(program.id)
            .map { it.id }.toSet()
        nonNullIds.forEach { id ->
            require(existingIds.contains(id)) {
                "Invalid program item id: $id does not belong to program: ${program.id}"
            }
        }

        val exerciseIds = items.map { it.exerciseId }.distinct()
        if (exerciseIds.isNotEmpty()) {
            val found = catalogDao.getExercisesByIds(exerciseIds).map { it.id }.toSet()
            exerciseIds.forEach { id ->
                check(found.contains(id)) { "Cannot save program: exercise not found: $id" }
            }
        }

        val rows = items.mapIndexed { index, input ->
            ProgramExercise(
                id = input.id ?: UUID.randomUUID().toString(),
                programId = program.id,
                exerciseId = input.exerciseId,
                position = index,
                targetSets = input.targetSets,
                targetReps = input.targetReps,
                targetWeight = input.targetWeight,
            )
        }
        catalogDao.replaceProgramGraphTx(program, rows)
    }

    /**
     * Deletes a program graph atomically.
     *
     * Historical sessions are unaffected: they store a frozen snapshot.
     * Throws [IllegalArgumentException] when the program does not exist.
     */
    suspend fun deleteProgram(programId: String) {
        catalogDao.getProgramById(programId)
            ?: throw IllegalArgumentException("Program not found: $programId")
        catalogDao.deleteProgramGraphTx(programId)
    }

    /**
     * Shared editor validation, mirroring the [startWorkout] target rules.
     */
    private fun validateEditorInputs(items: List<ProgramItemInput>) {
        items.forEach { input ->
            require(input.targetSets >= 1) {
                "Invalid targetSets (${input.targetSets}) in program exercise: ${input.id ?: input.exerciseId}"
            }
            require(input.targetReps >= 1) {
                "Invalid targetReps (${input.targetReps}) in program exercise: ${input.id ?: input.exerciseId}"
            }
            require(input.targetWeight == null || input.targetWeight > 0) {
                "Invalid targetWeight (${input.targetWeight}) in program exercise: ${input.id ?: input.exerciseId}"
            }
        }
    }

    /**
     * Returns the id of the built-in starter program, creating it on first call.
     *
     * Idempotent: when the program already exists nothing is inserted.
     * The whole catalog graph is created atomically via
     * [CatalogDao.insertDefaultProgramTx].
     */
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
     * Deletes one recorded set by id. Deleting a missing id is a no-op.
     *
     * The UI only exposes this for active sessions; existing sets are never
     * renumbered after a delete.
     */
    suspend fun deleteSet(setId: String) {
        workoutDao.deleteSetById(setId)
    }

    /**
     * Marks a session finished. Re-finishing is a no-op: the first
     * `finishedAt` value is never overwritten.
     */
    suspend fun finishWorkout(sessionId: String) {
        workoutDao.finishSession(sessionId, System.currentTimeMillis())
    }
}
