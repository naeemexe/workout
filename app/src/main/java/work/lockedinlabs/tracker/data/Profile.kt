package work.lockedinlabs.tracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** The (single, local) user's profile. Kept small on purpose — it's what will sync once accounts exist. */
@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val name: String = "",
    /** Index into the built-in avatar set (see ui/profile/Avatars.kt); null = initials / default icon. */
    val avatarId: Int? = null,
    /** Stored instead of age so it never goes stale. */
    val birthYear: Int? = null,
    val bodyweightLbs: Double? = null,
    val heightInches: Int? = null,
    val goal: String? = null,
    val experience: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val initials: String
        get() = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercase() }

    val age: Int? get() = birthYear?.let { LocalDate.now().year - it }

    companion object {
        const val SINGLETON_ID = 1
        val GOALS = listOf("Build strength", "Build muscle", "Lose fat", "Stay fit")
        val EXPERIENCE = listOf("Beginner", "Intermediate", "Advanced")
    }
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = ${Profile.SINGLETON_ID}")
    fun observe(): Flow<Profile?>

    @Query("SELECT * FROM profile WHERE id = ${Profile.SINGLETON_ID}")
    suspend fun get(): Profile?

    @Upsert
    suspend fun upsert(profile: Profile)
}
