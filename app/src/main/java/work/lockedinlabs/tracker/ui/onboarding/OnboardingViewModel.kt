package work.lockedinlabs.tracker.ui.onboarding

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import work.lockedinlabs.tracker.LockedInApp
import work.lockedinlabs.tracker.data.LogDao
import work.lockedinlabs.tracker.data.PlanDao
import work.lockedinlabs.tracker.data.PlanRepository
import work.lockedinlabs.tracker.data.Profile
import work.lockedinlabs.tracker.data.ProfileRepository
import work.lockedinlabs.tracker.data.SettingsStore
import work.lockedinlabs.tracker.pack.Level
import work.lockedinlabs.tracker.pack.Pack
import work.lockedinlabs.tracker.pack.PresetPlan
import work.lockedinlabs.tracker.sync.AuthRepository
import work.lockedinlabs.tracker.sync.SignInCancelled
import work.lockedinlabs.tracker.sync.SyncManager

enum class OnboardingStep { WELCOME, ABOUT, PLAN, TOUR }

/**
 * First launch: welcome (or restore a backup), experience level, a ready-made plan, and how the app works.
 * Skipped for anyone who already has workouts or plans (an update, or a restored backup).
 */
class OnboardingViewModel(
    private val settings: SettingsStore,
    private val logs: LogDao,
    private val planDao: PlanDao,
    private val plans: PlanRepository,
    private val profiles: ProfileRepository,
    private val auth: AuthRepository,
    private val sync: SyncManager,
) : ViewModel() {
    /** null while checking, then whether to show the welcome. */
    var needed by mutableStateOf<Boolean?>(if (settings.onboarded) false else null)
        private set
    var step by mutableStateOf(OnboardingStep.WELCOME)
        private set

    var name by mutableStateOf("")
        private set
    var level by mutableStateOf<Level?>(null)
        private set
    var goal by mutableStateOf<String?>(null)
        private set
    /** Chosen ready-made plan; null = set one up later. */
    var plan by mutableStateOf<PresetPlan?>(null)
        private set

    var restoring by mutableStateOf(false)
        private set
    var restoreError by mutableStateOf<String?>(null)
        private set

    val presets: List<PresetPlan> get() = Pack.current.sessionPlans

    init {
        if (needed == null) viewModelScope.launch {
            if (hasData()) finishQuietly() else needed = true
        }
    }

    private suspend fun hasData() = logs.getAll().isNotEmpty() || planDao.getPlans().isNotEmpty()

    private fun finishQuietly() {
        settings.onboarded = true
        needed = false
    }

    fun next() {
        step = OnboardingStep.entries.getOrElse(step.ordinal + 1) { step }
    }

    fun back() {
        step = OnboardingStep.entries.getOrElse(step.ordinal - 1) { step }
    }

    fun onName(v: String) { if (v.length <= 40) name = v }

    fun onLevel(l: Level) {
        level = l
        // Suggest the plan that suits this level best, unless you already picked one.
        if (plan == null || plan?.levels?.contains(l) != true) plan = presets.firstOrNull { it.levels.first() == l }
    }

    fun onGoal(g: String) { goal = g.takeUnless { it == goal } }

    fun onPlan(p: PresetPlan?) { plan = p }

    /** Sign in and download a backup; with workouts in it, you're straight in. */
    fun restore(activityContext: Context) {
        if (restoring) return
        restoring = true
        restoreError = null
        viewModelScope.launch {
            auth.signInWithGoogle(activityContext)
                .onSuccess {
                    sync.syncNow()
                    if (hasData()) finishQuietly()
                    else restoreError = "No backup yet for this account. Let's set you up."
                }
                .onFailure { e -> if (e !is SignInCancelled) restoreError = e.message }
            restoring = false
        }
    }

    fun finish() {
        val chosen = plan
        viewModelScope.launch {
            val saved = profiles.observe().first() ?: Profile()
            profiles.save(
                saved.copy(
                    name = name.trim().ifEmpty { saved.name },
                    experience = level?.label ?: saved.experience,
                    goal = goal ?: saved.goal,
                ),
            )
            if (chosen != null) plans.createFromPreset(chosen, chosen.name, activate = true)
            finishQuietly()
        }
    }

    companion object {
        val GOALS = Profile.GOALS

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LockedInApp
                OnboardingViewModel(app.settings, app.logDao, app.planDao, app.planRepository, app.profileRepository, app.auth, app.sync)
            }
        }
    }
}
