package work.lockedinlabs.tracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    /** Newest first. */
    @Query("SELECT * FROM exercise_logs ORDER BY epochDay DESC, createdAt DESC")
    fun observeAll(): Flow<List<ExerciseLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ExerciseLog): Long

    @Delete
    suspend fun delete(log: ExerciseLog)

    // For sync
    @Query("SELECT * FROM exercise_logs")
    suspend fun getAll(): List<ExerciseLog>

    @Query("SELECT * FROM exercise_logs WHERE epochDay = :epochDay")
    suspend fun forDay(epochDay: Long): List<ExerciseLog>

    @Query("DELETE FROM exercise_logs WHERE epochDay = :epochDay")
    suspend fun deleteDay(epochDay: Long)
}
