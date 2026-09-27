package work.lockedinlabs.tracker.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import work.lockedinlabs.tracker.domain.Muscle
import work.lockedinlabs.tracker.domain.ExerciseCatalog
import work.lockedinlabs.tracker.sync.ChangeTracker

/** Muscles you've set for your own exercises. They back up together with your plans. */
class CustomExerciseRepository(private val dao: CustomExerciseDao, private val changes: ChangeTracker, scope: CoroutineScope) {
    private val all = dao.observeAll().sharedIn(scope)

    fun observeAll(): Flow<List<CustomExercise>> = all

    /** Adds [name] to your exercises (no muscles yet) unless it's already there. */
    suspend fun add(name: String) {
        if (dao.get(ExerciseCatalog.key(name)) != null) return
        dao.upsert(CustomExercise.of(name))
        changes.planChanged()
    }

    suspend fun setMuscles(name: String, primary: Set<Muscle>, secondary: Set<Muscle>) {
        dao.upsert(CustomExercise.of(name, primary, secondary - primary))
        changes.planChanged()
    }
}
