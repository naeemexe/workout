package work.lockedinlabs.tracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import work.lockedinlabs.tracker.domain.ExerciseKind
import work.lockedinlabs.tracker.domain.plainNumber

/**
 * One set: e.g. 50 lb for 10 reps. Weight 0 means bodyweight.
 * A hold has [seconds] instead of reps (and may have weight); cardio has [minutes] and maybe [miles].
 */
data class SetEntry(
    val weightLbs: Double,
    val reps: Int,
    val seconds: Int = 0,
    val minutes: Int = 0,
    val miles: Double = 0.0,
) {
    val kind: ExerciseKind
        get() = when {
            seconds > 0 -> ExerciseKind.HOLD
            minutes > 0 -> ExerciseKind.CARDIO
            else -> ExerciseKind.WEIGHT
        }
}

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

/**
 * Stores sets compactly as "50x10;50x10;52.5x8" (older rows written "50.0x10" still read fine).
 * Holds and cardio add fields only when they have them: "25x0x45", "0x0x0x30x2.5".
 */
object SetCodec {
    fun encode(sets: List<SetEntry>): String = sets.joinToString(";") {
        val base = "${plainNumber(it.weightLbs)}x${it.reps}"
        when (it.kind) {
            ExerciseKind.WEIGHT -> base
            ExerciseKind.HOLD -> "${base}x${it.seconds}"
            ExerciseKind.CARDIO -> "${base}x0x${it.minutes}x${plainNumber(it.miles)}"
        }
    }

    fun decode(value: String): List<SetEntry> =
        if (value.isBlank()) emptyList()
        else value.split(";").map { part ->
            val f = part.split("x")
            SetEntry(
                f[0].toDouble(),
                f[1].toInt(),
                f.getOrNull(2)?.toInt() ?: 0,
                f.getOrNull(3)?.toInt() ?: 0,
                f.getOrNull(4)?.toDouble() ?: 0.0,
            )
        }
}

class Converters {
    @TypeConverter fun fromSets(sets: List<SetEntry>): String = SetCodec.encode(sets)
    @TypeConverter fun toSets(value: String): List<SetEntry> = SetCodec.decode(value)

    /** Exercise names, one per line. */
    @TypeConverter fun fromNames(names: List<String>): String = names.joinToString("\n")
    @TypeConverter fun toNames(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split("\n")
}
