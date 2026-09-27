package work.lockedinlabs.tracker.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import work.lockedinlabs.tracker.sync.ChangeTracker

/** Single source of truth for workout data; edits are reported to [changes] for cloud backup. */
class WorkoutRepository(private val dao: LogDao, private val changes: ChangeTracker, scope: CoroutineScope) {
    private val all = dao.observeAll().sharedIn(scope)

    /** Every log, newest first (one shared query for all screens). */
    fun observeAll(): Flow<List<ExerciseLog>> = all

    /** Adds or (same id) replaces [log]; returns its id. */
    suspend fun add(log: ExerciseLog): Long {
        val id = dao.insert(log)
        changes.dayChanged(log.epochDay)
        return id
    }

    suspend fun delete(log: ExerciseLog) {
        dao.delete(log)
        changes.dayChanged(log.epochDay)
    }
}
