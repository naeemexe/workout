package work.lockedinlabs.tracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** One body-weight weigh-in per day (entering it again the same day replaces it). */
@Entity(tableName = "weights")
data class WeightEntry(
    @PrimaryKey val epochDay: Long,
    val lbs: Double,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
}

@Dao
interface WeightDao {
    /** Newest first. */
    @Query("SELECT * FROM weights ORDER BY epochDay DESC")
    fun observeAll(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weights ORDER BY epochDay DESC LIMIT 1")
    suspend fun latest(): WeightEntry?

    @Query("SELECT * FROM weights WHERE epochDay = :epochDay")
    suspend fun forDay(epochDay: Long): WeightEntry?

    @Query("SELECT epochDay FROM weights")
    suspend fun days(): List<Long>

    @Upsert
    suspend fun upsert(entry: WeightEntry)

    @Delete
    suspend fun delete(entry: WeightEntry)

    @Query("DELETE FROM weights WHERE epochDay = :epochDay")
    suspend fun deleteDay(epochDay: Long)
}
