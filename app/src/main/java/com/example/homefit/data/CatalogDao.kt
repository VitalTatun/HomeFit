package com.example.homefit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.homefit.model.Exercise
import com.example.homefit.model.ProgramExercise
import com.example.homefit.model.WorkoutProgram

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

    @Query("SELECT * FROM workout_programs WHERE id = :programId")
    abstract suspend fun getProgramById(programId: String): WorkoutProgram?

    @Query("SELECT * FROM program_exercises WHERE programId = :programId ORDER BY position")
    abstract suspend fun getProgramExercisesOrdered(programId: String): List<ProgramExercise>

    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    abstract suspend fun getExercisesByIds(ids: List<String>): List<Exercise>

    @Insert
    abstract suspend fun insertProgram(program: WorkoutProgram)

    @Insert
    abstract suspend fun insertProgramExercises(items: List<ProgramExercise>)

    @Insert
    abstract suspend fun insertExercise(exercise: Exercise)

    @Update
    abstract suspend fun updateProgramExercise(item: ProgramExercise)

    @Query("DELETE FROM program_exercises WHERE id = :id")
    abstract suspend fun deleteProgramExercise(id: String)

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
}
