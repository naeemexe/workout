package work.lockedinlabs.tracker

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import work.lockedinlabs.tracker.data.AppDatabase
import work.lockedinlabs.tracker.data.PlanRepository
import work.lockedinlabs.tracker.data.ProfileRepository
import work.lockedinlabs.tracker.data.SettingsStore
import work.lockedinlabs.tracker.data.WeightRepository
import work.lockedinlabs.tracker.data.RuleRepository
import work.lockedinlabs.tracker.data.CustomExerciseRepository
import work.lockedinlabs.tracker.data.WorkoutRepository
import work.lockedinlabs.tracker.pack.Pack
import work.lockedinlabs.tracker.pack.PackParser
import work.lockedinlabs.tracker.sync.AuthRepository
import work.lockedinlabs.tracker.sync.SyncManager
import work.lockedinlabs.tracker.sync.SyncStore
import work.lockedinlabs.tracker.ui.log.RestAlarm

/** Holds app-wide singletons. Simple manual DI — swap for Hilt if the graph grows. */
class LockedInApp : Application() {
    private val db by lazy { AppDatabase.build(this) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val auth by lazy { AuthRepository(FirebaseAuth.getInstance()) }
    val sync by lazy {
        // Room is the offline store, so Firestore doesn't need its own disk cache.
        val firestore = FirebaseFirestore.getInstance().apply {
            firestoreSettings = firestoreSettings { setLocalCacheSettings(memoryCacheSettings {}) }
        }
        SyncManager(db, SyncStore(this), auth, firestore, appScope)
    }
    val logDao get() = db.logDao()
    val planDao get() = db.planDao()
    val repository by lazy { WorkoutRepository(db.logDao(), sync, appScope) }
    val planRepository by lazy { PlanRepository(db.planDao(), sync, appScope) }
    val settings by lazy { SettingsStore(this) }
    val restAlarm by lazy { RestAlarm(this) }
    val profileRepository by lazy { ProfileRepository(db.profileDao(), sync) }
    val customExercises by lazy { CustomExerciseRepository(db.customExerciseDao(), sync, appScope) }
    val ruleRepository by lazy { RuleRepository(db.ruleDao(), sync, appScope) }
    val weightRepository by lazy { WeightRepository(db.weightDao(), db.profileDao(), sync) }

    /** Erases every workout, plan and setting on this phone (cloud data is separate). The app restarts afterwards. */
    suspend fun eraseLocalData() {
        db.clearAllTables()
        SyncStore(this).clear()
        settings.clear()
    }

    override fun onCreate() {
        super.onCreate()
        // The exercise catalog, ready-made plans and training science; everything else reads from it.
        Pack.install(PackParser.fromAssets(assets))
        sync.requestSync(delayMs = 0)
    }
}
