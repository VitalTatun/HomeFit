package com.example.homefit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.homefit.model.Exercise
import com.example.homefit.model.ProgramExercise
import com.example.homefit.model.WorkoutProgram
import kotlinx.coroutines.flow.Flow

/**
 * Consistent read of a live program plan inside a single Room transaction.
 *
 * The repository must always read the plan through [CatalogDao.getPlanSnapshotTx]
 * instead of issuing [getProgramById] / [getProgramExercisesOrdered] /
 * [getExercisesByIds] as independent calls, so the snapshot can never be torn.
 */
data class PlanSnapshot(
    val program: WorkoutProgram,
    val items: List<ProgramExercise>,
    val exercisesById: Map<String, Exercise>,
)

@Dao
abstract class CatalogDao {

    @Query("SELECT * FROM workout_programs ORDER BY name")
    abstract fun observePrograms(): Flow<List<WorkoutProgram>>

    @Query("SELECT * FROM workout_programs WHERE id = :programId")
    abstract fun observeProgramById(programId: String): Flow<WorkoutProgram?>

    @Query("SELECT * FROM program_exercises WHERE programId = :programId ORDER BY position")
    abstract fun observeProgramExercises(programId: String): Flow<List<ProgramExercise>>

    @Query("SELECT * FROM exercises ORDER BY name")
    abstract fun observeExercises(): Flow<List<Exercise>>

    @Query("SELECT * FROM workout_programs WHERE id = :programId")
    abstract suspend fun getProgramById(programId: String): WorkoutProgram?

    @Query("SELECT * FROM program_exercises WHERE programId = :programId ORDER BY position")
    abstract suspend fun getProgramExercisesOrdered(programId: String): List<ProgramExercise>

    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    abstract suspend fun getExercisesByIds(ids: List<String>): List<Exercise>

    @Insert
    abstract suspend fun insertProgram(program: WorkoutProgram)

    @Update
    abstract suspend fun updateProgram(program: WorkoutProgram)

    @Insert
    abstract suspend fun insertProgramExercises(items: List<ProgramExercise>)

    @Insert
    abstract suspend fun insertExercise(exercise: Exercise)

    @Update
    abstract suspend fun updateProgramExercise(item: ProgramExercise)

    @Query("DELETE FROM program_exercises WHERE id = :id")
    abstract suspend fun deleteProgramExercise(id: String)

    @Query("DELETE FROM program_exercises WHERE id IN (:ids)")
    abstract suspend fun deleteProgramExercisesByIds(ids: List<String>)

    @Query("DELETE FROM program_exercises WHERE programId = :programId")
    abstract suspend fun deleteProgramExercisesByProgram(programId: String)

    @Query("UPDATE program_exercises SET position = :position WHERE id = :id")
    abstract suspend fun updateProgramExercisePosition(id: String, position: Int)

    @Query("DELETE FROM workout_programs WHERE id = :id")
    abstract suspend fun deleteProgram(id: String)

    @Transaction
    open suspend fun getPlanSnapshotTx(programId: String): PlanSnapshot? {
        val program = getProgramById(programId) ?: return null
        val items = getProgramExercisesOrdered(programId)
        val exercisesById =
            if (items.isEmpty()) {
                emptyMap()
            } else {
                getExercisesByIds(items.map { it.exerciseId }.distinct())
                    .associateBy { it.id }
            }
        return PlanSnapshot(
            program = program,
            items = items,
            exercisesById = exercisesById,
        )
    }

    /**
     * Inserts the built-in starter catalog graph atomically (P2.6).
     *
     * Part of the permanent provisioning mechanism, see
     * [WorkoutRepository.ensureDefaultProgram].
     */
    @Transaction
    open suspend fun insertDefaultProgramTx(
        exercises: List<Exercise>,
        program: WorkoutProgram,
        items: List<ProgramExercise>,
    ) {
        exercises.forEach { insertExercise(it) }
        insertProgram(program)
        insertProgramExercises(items)
    }

    /**
     * Inserts a new program graph atomically for the Program Editor.
     *
     * The [items] must already be normalized (`position` 0..n-1) by the
     * repository. A fresh program has no existing rows, so plain inserts
     * cannot violate the unique (programId, position) index.
     */
    @Transaction
    open suspend fun createProgramGraphTx(
        program: WorkoutProgram,
        items: List<ProgramExercise>,
    ) {
        insertProgram(program)
        if (items.isNotEmpty()) {
            insertProgramExercises(items)
        }
    }

    /**
     * Replaces a program graph atomically for the Program Editor.
     *
     * The [finalItems] must already be normalized (`position` 0..n-1, unique)
     * and belong to `program.id`. Kept rows are first moved to a negative
     * temporary range, so reorder/replace never collides on the unique
     * (programId, position) index. Kept row ids are preserved, so history
     * references (`programExerciseId`, SET_NULL on delete) stay intact;
     * only removed rows are deleted.
     */
    @Transaction
    open suspend fun replaceProgramGraphTx(
        program: WorkoutProgram,
        finalItems: List<ProgramExercise>,
    ) {
        updateProgram(program)
        val existing = getProgramExercisesOrdered(program.id)
        val existingIds = existing.map { it.id }.toSet()
        val finalById = finalItems.associateBy { it.id }

        val idsToDelete = existingIds - finalById.keys
        if (idsToDelete.isNotEmpty()) {
            deleteProgramExercisesByIds(idsToDelete.toList())
        }

        val keptIds = existingIds intersect finalById.keys
        keptIds.forEach { id ->
            val oldPosition = existing.first { it.id == id }.position
            updateProgramExercisePosition(id, TEMP_POSITION_OFFSET - oldPosition)
        }

        val newItems = finalItems.filter { it.id !in existingIds }
        if (newItems.isNotEmpty()) {
            insertProgramExercises(newItems)
        }

        finalItems.filter { it.id in keptIds }.forEach { item ->
            updateProgramExercise(item)
        }
    }

    /**
     * Deletes a program graph atomically for the Program Editor.
     *
     * Child rows are deleted explicitly before the parent; historical
     * sessions keep working because they store a frozen snapshot
     * (`programId`/`programName` are plain columns, `programExerciseId`
     * is SET_NULL on delete).
     */
    @Transaction
    open suspend fun deleteProgramGraphTx(programId: String) {
        deleteProgramExercisesByProgram(programId)
        deleteProgram(programId)
    }

    companion object {
        private const val TEMP_POSITION_OFFSET = -1_000_000
    }
}
