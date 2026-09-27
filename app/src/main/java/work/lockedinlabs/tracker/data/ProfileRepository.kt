package work.lockedinlabs.tracker.data

import kotlinx.coroutines.flow.Flow
import work.lockedinlabs.tracker.sync.ChangeTracker

class ProfileRepository(private val dao: ProfileDao, private val changes: ChangeTracker) {
    fun observe(): Flow<Profile?> = dao.observe()

    /** @param userEdit false for the automatic blank profile, so it never overwrites a cloud copy. */
    suspend fun save(profile: Profile, userEdit: Boolean = true) {
        // Weight belongs to the weight log (see WeightRepository); keep whatever it last set.
        dao.upsert(profile.copy(bodyweightLbs = dao.get()?.bodyweightLbs ?: profile.bodyweightLbs))
        if (userEdit) changes.profileChanged()
    }
}
