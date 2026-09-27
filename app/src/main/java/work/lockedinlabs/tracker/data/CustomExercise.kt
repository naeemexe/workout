package work.lockedinlabs.tracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import work.lockedinlabs.tracker.domain.CatalogExercise
import work.lockedinlabs.tracker.domain.ExerciseCatalog
import work.lockedinlabs.tracker.domain.Muscle

/** An exercise you named yourself, with the muscles you picked for it (so it counts on the muscle map). */
@Entity(tableName = "custom_exercises")
data class CustomExercise(
    /** Normalized name ([ExerciseCatalog.key]) so "Cable curl" and "cable curls" are the same exercise. */
    @PrimaryKey val key: String,
    val name: String,
    /** Muscle enum names, comma-separated. */
    val primary: String = "",
    val secondary: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val primaryMuscles: Set<Muscle> get() = decode(primary)
    val secondaryMuscles: Set<Muscle> get() = decode(secondary)
    val hasMuscles: Boolean get() = primaryMuscles.isNotEmpty()

    fun toCatalog() = CatalogExercise(name, primaryMuscles, secondaryMuscles - primaryMuscles)

    companion object {
        fun of(name: String, primary: Set<Muscle> = emptySet(), secondary: Set<Muscle> = emptySet()) =
            CustomExercise(ExerciseCatalog.key(name), name.trim(), encode(primary), encode(secondary))

        fun encode(muscles: Set<Muscle>) = muscles.joinToString(",") { it.name }

        private fun decode(s: String): Set<Muscle> =
            s.split(',').mapNotNull { n -> Muscle.entries.firstOrNull { it.name == n.trim() } }.toSet()
    }
}

/** Looks an exercise up in the built-in catalog, then in your custom ones (only if you gave it muscles). */
fun List<CustomExercise>.lookup(): (String) -> CatalogExercise? {
    val byKey = associateBy { it.key }
    return { name -> ExerciseCatalog.find(name) ?: byKey[ExerciseCatalog.key(name)]?.takeIf { it.hasMuscles }?.toCatalog() }
}

@Dao
interface CustomExerciseDao {
    @Query("SELECT * FROM custom_exercises ORDER BY name")
    fun observeAll(): Flow<List<CustomExercise>>

    @Query("SELECT * FROM custom_exercises ORDER BY name")
    suspend fun getAll(): List<CustomExercise>

    @Query("SELECT * FROM custom_exercises WHERE `key` = :key")
    suspend fun get(key: String): CustomExercise?

    @Upsert
    suspend fun upsert(exercise: CustomExercise)

    @Upsert
    suspend fun upsertAll(exercises: List<CustomExercise>)

    @Query("DELETE FROM custom_exercises")
    suspend fun deleteAll()
}
