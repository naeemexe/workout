package work.lockedinlabs.tracker.sync

import androidx.room.withTransaction
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import work.lockedinlabs.tracker.data.AppDatabase
import work.lockedinlabs.tracker.data.DayChoice
import work.lockedinlabs.tracker.data.ExerciseLog
import work.lockedinlabs.tracker.data.Plan
import work.lockedinlabs.tracker.data.PlanDay
import work.lockedinlabs.tracker.data.ProgressionRule
import work.lockedinlabs.tracker.data.CustomExercise
import work.lockedinlabs.tracker.domain.ExerciseCatalog
import work.lockedinlabs.tracker.data.Profile
import work.lockedinlabs.tracker.data.SetEntry
import work.lockedinlabs.tracker.data.WeightEntry
import java.time.LocalDate

/** Repositories report local edits here so they get uploaded. */
interface ChangeTracker {
    fun dayChanged(epochDay: Long)
    fun profileChanged()
    fun planChanged()
}

sealed interface SyncStatus {
    data object SignedOut : SyncStatus
    data object Syncing : SyncStatus
    data class Synced(val at: Long) : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

/**
 * Local-first sync with Firestore. Room stays the source of truth; the cloud is a backup that also lets
 * another phone restore. Compact layout (one document per workout day keeps reads/writes low):
 *
 *   users/{uid}                  profile, profileU, plans [{i, n, a (active), s (active since), c, d [days]}],
 *                                rules [{i, n, df, r, inc, st, mn, dp, e, c}], cx [{n, p, s, c}] (custom exercises),
 *                                planU (plans + rules + custom exercises)
 *   users/{uid}/days/{yyyy-MM-dd} d, c (plan day index), cn (its name), ex [{n, s:[w,r,w,r…], t}], w (body weight), u, su
 *
 * Conflicts resolve per day (and for profile / plan) by last edit wins, using `u` (the edit time).
 * `su` is the server write time and only drives incremental downloads.
 */
class SyncManager(
    private val db: AppDatabase,
    private val store: SyncStore,
    private val auth: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val scope: CoroutineScope,
) : ChangeTracker {
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.SignedOut)
    val status: StateFlow<SyncStatus> = _status

    /** Emits after cloud data replaced local profile/plan, so screens holding edit state can reload. */
    private val _remoteApplied = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val remoteApplied: SharedFlow<Unit> = _remoteApplied

    private val mutex = Mutex()
    private var scheduled: Job? = null

    override fun dayChanged(epochDay: Long) { store.markDay(epochDay); requestSync() }
    override fun profileChanged() { store.markProfile(); requestSync() }
    override fun planChanged() { store.markPlan(); requestSync() }

    /** Debounced so a burst of edits becomes one upload. No-op when signed out. */
    fun requestSync(delayMs: Long = 3_000) {
        if (auth.currentUser == null) {
            _status.value = SyncStatus.SignedOut
            return
        }
        scheduled?.cancel()
        scheduled = scope.launch {
            delay(delayMs)
            syncNow()
        }
    }

    suspend fun syncNow() = mutex.withLock {
        val uid = auth.currentUser?.uid ?: run { _status.value = SyncStatus.SignedOut; return@withLock }
        _status.value = SyncStatus.Syncing
        try {
            withTimeout(30_000) {
                if (store.syncedUid != uid) adoptLocalData(uid)
                val user = firestore.collection("users").document(uid)
                val appliedUser = pullUser(user)
                val appliedDays = pullDays(user)
                pushDays(user)
                pushUser(user)
                if (appliedUser || appliedDays) _remoteApplied.tryEmit(Unit)
            }
            store.lastSyncAt = System.currentTimeMillis()
            _status.value = SyncStatus.Synced(store.lastSyncAt)
        } catch (e: Exception) {
            _status.value = SyncStatus.Failed(if (e is kotlinx.coroutines.TimeoutCancellationException) "Offline. Will retry." else e.message ?: "Sync failed")
        }
    }

    fun signedOut() {
        scheduled?.cancel()
        _status.value = SyncStatus.SignedOut
    }

    /** First sync for this account on this phone: queue everything local for upload and download everything. */
    private suspend fun adoptLocalData(uid: String) {
        val dirty = store.dirtyDays()
        val logs = db.logDao().getAll()
        val latestByDay = logs.groupBy { it.epochDay }.mapValues { (_, l) -> l.maxOf { it.createdAt } }
        val days = latestByDay.keys + db.planDao().choiceDays() + db.weightDao().days()
        // Keep the original time so a newer copy already in the cloud still wins.
        days.filter { it !in dirty }.forEach { store.markDay(it, latestByDay[it] ?: 1L) }
        if (db.profileDao().get() != null && !store.profileDirty()) store.markProfile(store.profileEditedAt())
        if (db.planDao().getPlans().isNotEmpty() && !store.planDirty()) store.markPlan(store.planEditedAt())
        store.pullCursor = 0
        store.syncedUid = uid
    }

    // ---- profile + plan (the user document) ----

    private suspend fun pullUser(ref: DocumentReference): Boolean {
        val snap = ref.get(Source.SERVER).await()
        if (!snap.exists()) return false
        var applied = false
        val profileU = snap.getLong("profileU") ?: 0
        if (profileU > store.profileEditedAt()) {
            @Suppress("UNCHECKED_CAST")
            (snap.get("profile") as? Map<String, Any?>)?.let { db.profileDao().upsert(profileFrom(it)) }
            store.profileSynced(profileU)
            applied = true
        }
        val planU = snap.getLong("planU") ?: 0
        if (planU > store.planEditedAt()) {
            plansFrom(snap)?.let { (plans, days) -> db.planDao().replaceAll(plans, days) }
            rulesFrom(snap)?.let { db.ruleDao().replaceAll(it) }
            @Suppress("UNCHECKED_CAST")
            (snap.get("cx") as? List<Map<String, Any?>>)?.let { remote ->
                db.customExerciseDao().deleteAll()
                db.customExerciseDao().upsertAll(
                    remote.map { m ->
                        val name = m["n"] as? String ?: ""
                        CustomExercise(
                            key = ExerciseCatalog.key(name),
                            name = name,
                            primary = m["p"] as? String ?: "",
                            secondary = m["s"] as? String ?: "",
                            createdAt = (m["c"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                        )
                    },
                )
            }
            store.planSynced(planU)
            applied = true
        }
        return applied
    }

    private suspend fun pushUser(ref: DocumentReference) {
        val data = mutableMapOf<String, Any?>()
        var profileAt = 0L
        var planAt = 0L
        if (store.profileDirty()) {
            db.profileDao().get()?.let {
                profileAt = store.profileEditedAt().takeIf { t -> t > 0 } ?: System.currentTimeMillis()
                data["profile"] = profileTo(it)
                data["profileU"] = profileAt
            }
        }
        if (store.planDirty()) {
            planAt = store.planEditedAt().takeIf { t -> t > 0 } ?: System.currentTimeMillis()
            val days = db.planDao().getAll().filter { it.isActive || it.exercises.isNotEmpty() }.groupBy { it.planId }
            data["plans"] = db.planDao().getPlans().map { p ->
                mapOf(
                    "i" to p.id, "n" to p.name, "a" to p.isActive, "s" to p.activeSince, "c" to p.createdAt, "wk" to p.weekly,
                    "d" to days[p.id].orEmpty().map(::planDayTo),
                )
            }
            data["plan"] = FieldValue.delete() // single-plan layout from before multiple plans
            data["cx"] = db.customExerciseDao().getAll().map { mapOf("n" to it.name, "p" to it.primary, "s" to it.secondary, "c" to it.createdAt) }
            data["rules"] = db.ruleDao().getAll().map { r ->
                mapOf(
                    "i" to r.id, "n" to r.name, "df" to r.isDefault, "r" to r.setReps, "inc" to r.increment,
                    "st" to r.startReps, "mn" to r.minReps, "dp" to r.dropPct, "e" to r.exercises, "c" to r.createdAt,
                )
            }
            data["planU"] = planAt
        }
        if (data.isEmpty()) return
        ref.set(data, SetOptions.merge()).await()
        if (profileAt > 0) store.profileSynced(profileAt)
        if (planAt > 0) store.planSynced(planAt)
    }

    // ---- workout days ----

    private suspend fun pullDays(user: DocumentReference): Boolean {
        val cursor = store.pullCursor
        val snaps = user.collection("days")
            .whereGreaterThan("su", Timestamp(cursor / 1_000_000, ((cursor % 1_000_000) * 1_000).toInt()))
            .get(Source.SERVER).await()
        var newest = cursor
        var applied = false
        val dirty = store.dirtyDays() // read once, not per document
        for (doc in snaps.documents) {
            val su = doc.getTimestamp("su") ?: continue
            newest = maxOf(newest, su.seconds * 1_000_000 + su.nanoseconds / 1_000)
            val day = doc.getLong("d") ?: continue
            val remoteU = doc.getLong("u") ?: 0
            val localAt = dirty[day]
            if (localAt != null && localAt >= remoteU) continue // our newer edit gets uploaded instead
            applyDay(day, doc)
            localAt?.let { store.clearDay(day, it) }
            applied = true
        }
        store.pullCursor = newest
        return applied
    }

    private suspend fun applyDay(day: Long, doc: DocumentSnapshot) {
        @Suppress("UNCHECKED_CAST")
        val entries = (doc.get("ex") as? List<Map<String, Any?>>).orEmpty().map { e ->
            ExerciseLog(
                exercise = e["n"] as String,
                sets = (e["s"] as List<*>).map { (it as Number).toDouble() }.chunked(2) { (w, r) -> SetEntry(w, r.toInt()) },
                epochDay = day,
                createdAt = (e["t"] as? Number)?.toLong() ?: 0,
            )
        }
        val choice = doc.getLong("c")?.toInt()
        val weight = (doc.get("w") as? Number)?.toDouble()
        db.withTransaction {
            if (weight != null) db.weightDao().upsert(WeightEntry(day, weight)) else db.weightDao().deleteDay(day)
            db.logDao().deleteDay(day)
            entries.forEach { db.logDao().insert(it) }
            if (choice != null) db.planDao().upsertChoice(DayChoice(day, choice, doc.getString("cn").orEmpty()))
            else db.planDao().deleteChoice(day)
        }
    }

    private suspend fun pushDays(user: DocumentReference) {
        val dirty = store.dirtyDays()
        if (dirty.isEmpty()) return
        // Batched: several days per round trip (each day is still one document write).
        for (chunk in dirty.entries.chunked(400)) {
            val batch = firestore.batch()
            for ((day, at) in chunk) {
                val logs = db.logDao().forDay(day)
                val choice = db.planDao().choiceFor(day)
                val weight = db.weightDao().forDay(day)
                batch.set(
                    user.collection("days").document(LocalDate.ofEpochDay(day).toString()),
                    mapOf(
                        "d" to day,
                        "c" to choice?.dayIndex,
                        "cn" to choice?.name,
                        "ex" to logs.sortedBy { it.createdAt }.map { l ->
                            mapOf("n" to l.exercise, "s" to l.sets.flatMap { listOf(it.weightLbs, it.reps) }, "t" to l.createdAt)
                        },
                        "w" to weight?.lbs,
                        "u" to at,
                        "su" to FieldValue.serverTimestamp(),
                    ),
                )
            }
            batch.commit().await()
            chunk.forEach { (day, at) -> store.clearDay(day, at) }
        }
    }

    // ---- compact field mapping ----

    private fun profileTo(p: Profile) = mapOf(
        "n" to p.name, "a" to p.avatarId, "by" to p.birthYear, "bw" to p.bodyweightLbs,
        "h" to p.heightInches, "g" to p.goal, "x" to p.experience, "c" to p.createdAt,
    )

    private fun profileFrom(m: Map<String, Any?>) = Profile(
        name = m["n"] as? String ?: "",
        avatarId = (m["a"] as? Number)?.toInt(),
        birthYear = (m["by"] as? Number)?.toInt(),
        bodyweightLbs = (m["bw"] as? Number)?.toDouble(),
        heightInches = (m["h"] as? Number)?.toInt(),
        goal = m["g"] as? String,
        experience = m["x"] as? String,
        createdAt = (m["c"] as? Number)?.toLong() ?: System.currentTimeMillis(),
    )

    private fun planDayTo(d: PlanDay) = mapOf("i" to d.dayIndex, "n" to d.name, "r" to d.isRest, "e" to d.exercises, "k" to d.color, "sc" to d.setCounts)

    @Suppress("UNCHECKED_CAST")
    private fun rulesFrom(snap: DocumentSnapshot): List<ProgressionRule>? =
        (snap.get("rules") as? List<Map<String, Any?>>)?.map { m ->
            ProgressionRule(
                id = (m["i"] as Number).toLong(),
                name = m["n"] as? String ?: "",
                isDefault = m["df"] as? Boolean ?: false,
                setReps = m["r"] as? String ?: "10,10",
                increment = (m["inc"] as? Number)?.toDouble() ?: 0.0,
                startReps = (m["st"] as? Number)?.toInt() ?: 8,
                minReps = (m["mn"] as? Number)?.toInt() ?: 6,
                dropPct = (m["dp"] as? Number)?.toInt() ?: 10,
                exercises = (m["e"] as? List<*>).orEmpty().filterIsInstance<String>(),
                createdAt = (m["c"] as? Number)?.toLong() ?: 0,
            )
        }?.takeIf { it.isNotEmpty() }

    /** Plans from the user document; the older single-plan layout becomes one active plan. */
    @Suppress("UNCHECKED_CAST")
    private fun plansFrom(snap: DocumentSnapshot): Pair<List<Plan>, List<PlanDay>>? {
        (snap.get("plans") as? List<Map<String, Any?>>)?.let { remote ->
            val plans = remote.map { m ->
                Plan(
                    id = (m["i"] as Number).toLong(),
                    name = m["n"] as? String ?: "",
                    isActive = m["a"] as? Boolean ?: false,
                    activeSince = (m["s"] as? Number)?.toLong() ?: 0,
                    createdAt = (m["c"] as? Number)?.toLong() ?: 0,
                    weekly = m["wk"] as? Boolean ?: false,
                )
            }
            val days = remote.flatMap { m ->
                val id = (m["i"] as Number).toLong()
                (m["d"] as? List<Map<String, Any?>>).orEmpty().map { planDayFrom(it).copy(planId = id) }
            }
            return plans to days
        }
        val legacy = snap.get("plan") as? List<Map<String, Any?>> ?: return null
        val plan = Plan(id = 1, name = "My plan", isActive = true, createdAt = 0)
        return listOf(plan) to legacy.map { planDayFrom(it).copy(planId = plan.id) }
    }

    private fun planDayFrom(m: Map<String, Any?>) = PlanDay(
        dayIndex = (m["i"] as Number).toInt(),
        name = m["n"] as? String ?: "",
        isRest = m["r"] as? Boolean ?: false,
        exercises = (m["e"] as? List<*>).orEmpty().filterIsInstance<String>(),
        color = (m["k"] as? Number)?.toInt(),
        setCounts = m["sc"] as? String ?: "",
    )
}
