package work.lockedinlabs.tracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import work.lockedinlabs.tracker.domain.Rule
import work.lockedinlabs.tracker.pack.Pack
import work.lockedinlabs.tracker.pack.PresetProgression

/**
 * A named progression rule (Plan tab) and the exercises that follow it. Exactly one is the default,
 * used for every exercise not assigned to another rule.
 */
@Entity(tableName = "progression_rules")
data class ProgressionRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val isDefault: Boolean = false,
    /** Target reps per set, comma-separated ("10,10,8"). */
    val setReps: String = "10,10",
    /** Pounds added on a step up; 0 = auto (2.5 lb under 30 lb, otherwise 5 lb). */
    val increment: Double = Rule.AUTO_INCREMENT,
    val startReps: Int = 8,
    val minReps: Int = 6,
    val dropPct: Int = 10,
    val exercises: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val targets: List<Int> get() = setReps.split(',').mapNotNull { it.trim().toIntOrNull() }.ifEmpty { listOf(10) }

    val displayName: String get() = name.trim().ifEmpty { "Untitled plan" }

    fun toRule() = Rule(targets, increment, startReps, minReps, dropPct)

    companion object {
        fun encode(targets: List<Int>) = targets.joinToString(",")

        /** A rule set up like a ready-made plan from the data pack. */
        fun from(preset: PresetProgression, name: String = preset.name, isDefault: Boolean = false) = ProgressionRule(
            name = name,
            isDefault = isDefault,
            setReps = encode(preset.setReps),
            increment = preset.increment,
            startReps = preset.startReps,
            minReps = preset.minReps,
            dropPct = preset.dropPct,
        )

        /** The data pack's default plan, created on first launch. */
        fun starterDefault() = from(Pack.current.progressionPlans.first { it.isDefault }, isDefault = true)
    }
}

/** The rule [exercise] follows: the rule it's assigned to, else the default. */
fun List<ProgressionRule>.ruleFor(exercise: String): Rule =
    (firstOrNull { r -> !r.isDefault && r.exercises.any { it.equals(exercise, ignoreCase = true) } }
        ?: firstOrNull { it.isDefault })?.toRule() ?: Rule.Default

@Dao
interface ProgressionRuleDao {
    @Query("SELECT * FROM progression_rules ORDER BY isDefault DESC, createdAt")
    fun observeAll(): Flow<List<ProgressionRule>>

    @Query("SELECT * FROM progression_rules ORDER BY isDefault DESC, createdAt")
    suspend fun getAll(): List<ProgressionRule>

    @Insert
    suspend fun insert(rule: ProgressionRule): Long

    @Upsert
    suspend fun upsert(rule: ProgressionRule)

    @Upsert
    suspend fun upsertAll(rules: List<ProgressionRule>)

    @Query("DELETE FROM progression_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE progression_rules SET isDefault = (id = :id)")
    suspend fun makeDefault(id: Long)

    @Query("DELETE FROM progression_rules")
    suspend fun deleteAll()

    /** Replaces every rule (used when the cloud copy wins). */
    @Transaction
    suspend fun replaceAll(rules: List<ProgressionRule>) {
        deleteAll()
        upsertAll(rules)
    }
}
