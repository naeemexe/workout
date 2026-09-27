package work.lockedinlabs.tracker.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A named weekly plan (you can keep several and switch between them); exactly one is active at a time. */
@Entity(tableName = "plans")
data class Plan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val isActive: Boolean = false,
    /** Epoch day the plan was made active; its rotation only looks at workouts from then on. */
    val activeSince: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** Weekly rotation: the plan is a 7-day week, and days left blank count as rest days. */
    @ColumnInfo(defaultValue = "0") val weekly: Boolean = false,
) {
    val displayName: String get() = name.trim().ifEmpty { "Untitled plan" }
}

/** One of the 7 slots in a weekly plan, e.g. Day 1 "Upper Body" with its exercises in order. */
@Entity(tableName = "plan_days", primaryKeys = ["planId", "dayIndex"])
data class PlanDay(
    val dayIndex: Int,
    val name: String = "",
    val isRest: Boolean = false,
    val exercises: List<String> = emptyList(),
    val planId: Long = 0,
    /** Color tag (index into DayColors in ui/theme); shown on the Log tab's calendar. Null = not set. */
    val color: Int? = null,
    /** Planned sets per exercise, comma-separated and in the same order as [exercises] ("3,4,3"). */
    @ColumnInfo(defaultValue = "") val setCounts: String = "",
) {
    /** Planned sets for the exercise at [index] (3 unless you changed it). */
    fun setsFor(index: Int): Int = setCounts.split(',').getOrNull(index)?.trim()?.toIntOrNull() ?: DEFAULT_SETS

    /** Planned sets for every exercise, in order. */
    val sets: List<Int> get() = exercises.indices.map(::setsFor)

    fun withSets(sets: List<Int>) = copy(setCounts = sets.joinToString(","))

    /** Only days you've named (or marked as rest) are in the rotation. */
    val isActive: Boolean get() = isRest || name.isNotBlank()

    val displayName: String get() = if (isRest) name.trim().ifEmpty { "Rest" } else name.trim()

    val isCustom: Boolean get() = dayIndex == CUSTOM_CHOICE

    companion object {
        const val DAYS = 7
        const val DEFAULT_SETS = 3

        /** [DayChoice.dayIndex] value for "I'm resting today" picked on the Log tab. */
        const val REST_CHOICE = -1

        /** [DayChoice.dayIndex] value for an off-plan session you named yourself on the Log tab. */
        const val CUSTOM_CHOICE = -2

        fun custom(name: String) = PlanDay(CUSTOM_CHOICE, name = name.trim())

        /** Stand-in plan day shown when you chose to rest on a day the plan didn't. */
        val RestChoice = PlanDay(REST_CHOICE, isRest = true)
    }
}

/** Which plan day a date was (or is) — recorded when you log on that date, or pick a day / Rest on the Log tab. */
@Entity(tableName = "day_choices")
data class DayChoice(
    @PrimaryKey val epochDay: Long,
    /** A [PlanDay.dayIndex], or [PlanDay.REST_CHOICE]. */
    val dayIndex: Int,
    /** The day's name at the time, so history keeps it even if the plan is renamed later. */
    @ColumnInfo(defaultValue = "") val name: String = "",
)

@Dao
interface PlanDao {
    @Query("SELECT * FROM plans ORDER BY createdAt")
    fun observePlans(): Flow<List<Plan>>

    @Query("SELECT * FROM plan_days ORDER BY planId, dayIndex")
    fun observeAllDays(): Flow<List<PlanDay>>

    @Upsert
    suspend fun upsertAll(days: List<PlanDay>)

    @Insert
    suspend fun insertPlan(plan: Plan): Long

    @Upsert
    suspend fun upsertPlan(plan: Plan)

    @Query("UPDATE plans SET name = :name WHERE id = :id")
    suspend fun renamePlan(id: Long, name: String)

    @Query("UPDATE plans SET isActive = (id = :id), activeSince = CASE WHEN id = :id THEN :since ELSE activeSince END")
    suspend fun activate(id: Long, since: Long)

    @Query("UPDATE plans SET weekly = :weekly WHERE id = :id")
    suspend fun setWeekly(id: Long, weekly: Boolean)

    @Query("DELETE FROM plans WHERE id = :id")
    suspend fun deletePlanRow(id: Long)

    @Query("DELETE FROM plan_days WHERE planId = :id")
    suspend fun deletePlanDays(id: Long)

    @Transaction
    suspend fun deletePlan(id: Long) {
        deletePlanDays(id)
        deletePlanRow(id)
    }

    /** Replaces every plan (used when the cloud copy wins). */
    @Transaction
    suspend fun replaceAll(plans: List<Plan>, days: List<PlanDay>) {
        deleteAllDays()
        deleteAllPlans()
        plans.forEach { upsertPlan(it) }
        upsertAll(days)
    }

    @Query("DELETE FROM plans")
    suspend fun deleteAllPlans()

    @Query("DELETE FROM plan_days")
    suspend fun deleteAllDays()

    @Query("SELECT * FROM day_choices")
    fun observeChoices(): Flow<List<DayChoice>>

    @Upsert
    suspend fun upsertChoice(choice: DayChoice)

    /** Keeps an existing choice for that date. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChoiceIfAbsent(choice: DayChoice)

    // For sync
    @Query("SELECT * FROM plans ORDER BY createdAt")
    suspend fun getPlans(): List<Plan>

    @Query("SELECT * FROM plan_days ORDER BY planId, dayIndex")
    suspend fun getAll(): List<PlanDay>

    @Query("SELECT * FROM day_choices WHERE epochDay = :epochDay")
    suspend fun choiceFor(epochDay: Long): DayChoice?

    @Query("SELECT epochDay FROM day_choices")
    suspend fun choiceDays(): List<Long>

    @Query("DELETE FROM day_choices WHERE epochDay = :epochDay")
    suspend fun deleteChoice(epochDay: Long)
}
