package com.example.homefit.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.homefit.model.FinishedSessionSet
import com.example.homefit.model.WorkoutSession
import com.example.homefit.model.WorkoutSessionExercise
import com.example.homefit.model.WorkoutSet
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WorkoutDao {

    @Insert
    abstract suspend fun insertSession(session: WorkoutSession)

    @Insert
    abstract suspend fun insertSessionItems(items: List<WorkoutSessionExercise>)

    /**
     * Inserts a session together with its frozen items atomically.
     *
     * If any item insert fails, the session row is rolled back as well.
     * No business validation and no id generation happen here.
     */
    @Transaction
    open suspend fun insertSessionGraphTx(
        session: WorkoutSession,
        items: List<WorkoutSessionExercise>,
    ) {
        insertSession(session)
        insertSessionItems(items)
    }

    @Query(
        "SELECT * FROM workout_sessions " +
            "WHERE finishedAt IS NOT NULL " +
            "ORDER BY startedAt DESC",
    )
    abstract fun observeFinishedSessions(): Flow<List<WorkoutSession>>

    /**
     * Observes every recorded set of finished sessions for statistics.
     *
     * Active (unfinished) sessions are excluded via `finishedAt IS NOT NULL`,
     * keeping statistics consistent with the History screen. Returns a plain
     * projection ([FinishedSessionSet]), not entities: no schema change.
     */
    @Query(
        "SELECT s.id AS setId, " +
            "i.exerciseId AS exerciseId, " +
            "i.exerciseName AS exerciseName, " +
            "s.actualReps AS actualReps, " +
            "s.actualWeight AS actualWeight " +
            "FROM workout_sets AS s " +
            "INNER JOIN workout_session_exercises AS i ON s.sessionExerciseId = i.id " +
            "INNER JOIN workout_sessions AS sess ON i.sessionId = sess.id " +
            "WHERE sess.finishedAt IS NOT NULL",
    )
    abstract fun observeFinishedSets(): Flow<List<FinishedSessionSet>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    abstract fun observeSession(id: String): Flow<WorkoutSession?>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    abstract suspend fun getSessionById(id: String): WorkoutSession?

    @Query(
        "SELECT * FROM workout_sessions " +
            "WHERE finishedAt IS NULL " +
            "ORDER BY startedAt DESC LIMIT 1",
    )
    abstract suspend fun getActiveSession(): WorkoutSession?

    @Query(
        "SELECT * FROM workout_sessions " +
            "WHERE finishedAt IS NULL " +
            "ORDER BY startedAt DESC LIMIT 1",
    )
    abstract fun observeActiveSession(): Flow<WorkoutSession?>

    @Query(
        "SELECT * FROM workout_session_exercises " +
            "WHERE sessionId = :sessionId ORDER BY position",
    )
    abstract fun observeItemsBySession(sessionId: String): Flow<List<WorkoutSessionExercise>>

    @Query(
        "SELECT s.* FROM workout_sets AS s " +
            "INNER JOIN workout_session_exercises AS i ON s.sessionExerciseId = i.id " +
            "WHERE i.sessionId = :sessionId " +
            "ORDER BY i.position, s.setIndex",
    )
    abstract fun observeSetsBySession(sessionId: String): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM workout_session_exercises WHERE id = :id")
    abstract suspend fun getSessionExerciseById(id: String): WorkoutSessionExercise?

    @Query("SELECT MAX(setIndex) FROM workout_sets WHERE sessionExerciseId = :sessionExerciseId")
    abstract suspend fun getMaxSetIndex(sessionExerciseId: String): Int?

    @Insert
    abstract suspend fun insertSet(set: WorkoutSet)

    @Update
    abstract suspend fun updateSet(set: WorkoutSet)

    @Delete
    abstract suspend fun deleteSet(set: WorkoutSet)

    @Query("DELETE FROM workout_sets WHERE id = :setId")
    abstract suspend fun deleteSetById(setId: String)

    @Query(
        "UPDATE workout_sessions SET finishedAt = :now " +
            "WHERE id = :id AND finishedAt IS NULL",
    )
    abstract suspend fun finishSession(id: String, now: Long): Int
}
