package work.lockedinlabs.tracker.ui.profile

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import work.lockedinlabs.tracker.LockedInApp
import work.lockedinlabs.tracker.data.ExerciseLog
import work.lockedinlabs.tracker.data.Profile
import work.lockedinlabs.tracker.data.ProfileRepository
import work.lockedinlabs.tracker.data.WorkoutRepository
import work.lockedinlabs.tracker.sync.AuthRepository
import work.lockedinlabs.tracker.sync.SignInCancelled
import work.lockedinlabs.tracker.sync.SyncManager
import work.lockedinlabs.tracker.sync.SyncStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Signed-in Google account shown in the Account card. */
data class Account(val name: String?, val email: String?)

/** Profile edits apply locally at once and save in the background (latest edit wins), like the plan editor. */
class ProfileViewModel(
    private val profiles: ProfileRepository,
    workouts: WorkoutRepository,
    private val auth: AuthRepository,
    private val sync: SyncManager,
    private val eraseLocalData: suspend () -> Unit,
) : ViewModel() {
    var profile by mutableStateOf(Profile())
        private set
    var loaded by mutableStateOf(false)
        private set
    private var logs by mutableStateOf<List<ExerciseLog>>(emptyList())

    // Text fields kept as typed so partial input like "18" or "5." isn't reformatted under the cursor.
    var heightFt by mutableStateOf("")
        private set
    var heightIn by mutableStateOf("")
        private set
    var age by mutableStateOf("")
        private set

    val workoutDays by derivedStateOf { logs.map { it.epochDay }.distinct().size }
    val exercisesLogged by derivedStateOf { logs.map { it.exercise.lowercase() }.distinct().size }
    val totalSets by derivedStateOf { logs.sumOf { it.sets.size } }
    val memberSince: LocalDate by derivedStateOf {
        val created = Instant.ofEpochMilli(profile.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
        logs.minOfOrNull { it.epochDay }?.let { minOf(created, LocalDate.ofEpochDay(it)) } ?: created
    }

    var account by mutableStateOf<Account?>(null)
        private set
    var signingIn by mutableStateOf(false)
        private set
    var signInError by mutableStateOf<String?>(null)
        private set
    val syncStatus: StateFlow<SyncStatus> = sync.status

    private val pendingSave = MutableStateFlow<Profile?>(null)

    init {
        viewModelScope.launch {
            val saved = profiles.observe().first()
            load(saved ?: Profile())
            // Persist right away so "member since" is fixed from the first visit (not a user edit → never synced over a cloud copy).
            if (saved == null) profiles.save(profile, userEdit = false)
        }
        viewModelScope.launch { workouts.observeAll().collect { logs = it } }
        viewModelScope.launch { pendingSave.filterNotNull().collect { profiles.save(it) } }
        viewModelScope.launch { auth.user.collect { u -> account = u?.let { Account(it.displayName, it.email) } } }
        // Cloud copy replaced the local profile (e.g. first sign-in on a new phone): show it.
        viewModelScope.launch { sync.remoteApplied.collect { profiles.observe().first()?.let(::load) } }
    }

    private fun load(p: Profile) {
        profile = p
        heightFt = p.heightInches?.let { (it / 12).toString() }.orEmpty()
        heightIn = p.heightInches?.let { (it % 12).toString() }.orEmpty()
        age = p.age?.toString().orEmpty()
        loaded = true
    }

    /** @param activityContext needed to show Google's account picker. */
    fun signIn(activityContext: Context) {
        if (signingIn) return
        signingIn = true
        signInError = null
        viewModelScope.launch {
            auth.signInWithGoogle(activityContext)
                .onSuccess { user ->
                    sync.syncNow()
                    // Fill in the name from Google if the profile (local or restored) doesn't have one.
                    if (profile.name.isBlank()) user.displayName?.let(::onName)
                }
                .onFailure { e -> if (e !is SignInCancelled) signInError = e.message }
            signingIn = false
        }
    }

    fun syncNow() { viewModelScope.launch { sync.syncNow() } }

    /** Data stays on this phone; it just stops backing up. */
    fun signOut(context: Context) {
        viewModelScope.launch {
            auth.signOut(context)
            sync.signedOut()
        }
    }

    var deleting by mutableStateOf(false)
        private set
    var deleteError by mutableStateOf<String?>(null)
        private set
    /** This phone's data was erased too; the app restarts from the welcome screen. */
    var erased by mutableStateOf(false)
        private set

    /**
     * Deletes the account: confirms with Google, removes every backup in the cloud, then the sign-in itself.
     * With [eraseLocal], this phone's workouts, plans and settings go as well.
     */
    fun deleteAccount(activityContext: Context, eraseLocal: Boolean) {
        if (deleting) return
        deleting = true
        deleteError = null
        viewModelScope.launch {
            runCatching {
                auth.reauthenticate(activityContext)
                val uid = auth.currentUser?.uid ?: error("Not signed in")
                sync.deleteCloudData(uid)
                auth.deleteUser()
                auth.signOut(activityContext)
                sync.signedOut()
                if (eraseLocal) {
                    eraseLocalData()
                    erased = true
                }
            }.onFailure { e -> if (e !is SignInCancelled) deleteError = e.message ?: "Couldn't delete the account. Try again." }
            deleting = false
        }
    }

    fun onName(v: String) { if (v.length <= 40) update { it.copy(name = v) } }

    fun onHeightFt(v: String) { if (v.isEmpty() || FEET_INPUT.matches(v)) { heightFt = v; updateHeight() } }
    fun onHeightIn(v: String) { if (v.isEmpty() || (INCH_INPUT.matches(v) && v.toInt() < 12)) { heightIn = v; updateHeight() } }

    private fun updateHeight() {
        val ft = heightFt.toIntOrNull()
        val inches = heightIn.toIntOrNull() ?: 0
        update { it.copy(heightInches = ft?.let { f -> f * 12 + inches }?.takeIf { h -> h > 0 }) }
    }

    /** Tapping the selected chip again clears it. */
    fun onGoal(g: String) = update { p -> p.copy(goal = g.takeUnless { g == p.goal }) }
    fun onExperience(e: String) = update { p -> p.copy(experience = e.takeUnless { e == p.experience }) }

    /** Tapping the selected avatar again goes back to initials. */
    fun onAvatar(id: Int) = update { p -> p.copy(avatarId = id.takeUnless { it == p.avatarId }) }

    fun onAge(v: String) {
        if (v.isNotEmpty() && !AGE_INPUT.matches(v)) return
        age = v
        update { p -> p.copy(birthYear = v.toIntOrNull()?.takeIf { it in 10..100 }?.let { LocalDate.now().year - it }) }
    }

    private fun update(change: (Profile) -> Profile) {
        profile = change(profile)
        pendingSave.value = profile
    }

    companion object {
        private val FEET_INPUT = Regex("""\d""")
        private val INCH_INPUT = Regex("""\d{1,2}""")
        private val AGE_INPUT = Regex("""\d{1,3}""")

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LockedInApp
                ProfileViewModel(app.profileRepository, app.repository, app.auth, app.sync, app::eraseLocalData)
            }
        }
    }
}
