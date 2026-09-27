package work.lockedinlabs.tracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import work.lockedinlabs.tracker.domain.plainNumber

/** One set: e.g. 50 lb for 10 reps. Weight 0 means bodyweight. */
data class SetEntry(val weightLbs: Double, val reps: Int)

/** One exercise on one day, with each set recorded individually. */
@Entity(
    tableName = "exercise_logs",
    // Only looked up by date; exercise-name filtering happens in memory on the shared list.
    indices = [Index("epochDay")],
)
data class ExerciseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exercise: String,
    val sets: List<SetEntry>,
    /** Workout date as LocalDate.toEpochDay(). */
    val epochDay: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Stores sets compactly as "50x10;50x10;52.5x8" (older rows written "50.0x10" still read fine). */
object SetCodec {
    fun encode(sets: List<SetEntry>): String = sets.joinToString(";") { "${plainNumber(it.weightLbs)}x${it.reps}" }

    fun decode(value: String): List<SetEntry> =
        if (value.isBlank()) emptyList()
        else value.split(";").map { part ->
            val (w, r) = part.split("x")
            SetEntry(w.toDouble(), r.toInt())
        }
}

class Converters {
    @TypeConverter fun fromSets(sets: List<SetEntry>): String = SetCodec.encode(sets)
    @TypeConverter fun toSets(value: String): List<SetEntry> = SetCodec.decode(value)

    /** Exercise names, one per line. */
    @TypeConverter fun fromNames(names: List<String>): String = names.joinToString("\n")
    @TypeConverter fun toNames(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split("\n")
}
