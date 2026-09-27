package work.lockedinlabs.tracker.data

import kotlinx.coroutines.flow.Flow
import work.lockedinlabs.tracker.sync.ChangeTracker
import java.time.LocalDate

/**
 * The weight log is the source of truth for body weight; the profile's weight just mirrors the latest
 * weigh-in (so the profile document stays self-contained in the cloud).
 */
class WeightRepository(
    private val dao: WeightDao,
    private val profiles: ProfileDao,
    private val changes: ChangeTracker,
) {
    fun observeAll(): Flow<List<WeightEntry>> = dao.observeAll()

    suspend fun record(date: LocalDate, lbs: Double) {
        dao.upsert(WeightEntry(date.toEpochDay(), lbs))
        changes.dayChanged(date.toEpochDay())
        mirrorLatest()
    }

    suspend fun delete(entry: WeightEntry) {
        dao.delete(entry)
        changes.dayChanged(entry.epochDay)
        mirrorLatest()
    }

    suspend fun restore(entry: WeightEntry) {
        dao.upsert(entry)
        changes.dayChanged(entry.epochDay)
        mirrorLatest()
    }

    /** Weight typed in the profile before the log existed becomes its first entry. */
    suspend fun seedFromProfile() {
        if (dao.latest() != null) return
        profiles.get()?.bodyweightLbs?.let { record(LocalDate.now(), it) }
    }

    private suspend fun mirrorLatest() {
        val latest = dao.latest() ?: return
        val profile = profiles.get() ?: Profile()
        if (profile.bodyweightLbs == latest.lbs) return
        profiles.upsert(profile.copy(bodyweightLbs = latest.lbs))
        changes.profileChanged()
    }
}
