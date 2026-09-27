package work.lockedinlabs.tracker.ui.more

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import work.lockedinlabs.tracker.LockedInApp
import work.lockedinlabs.tracker.data.CustomExercise
import work.lockedinlabs.tracker.data.CustomExerciseRepository
import work.lockedinlabs.tracker.data.PlanRepository
import work.lockedinlabs.tracker.data.WorkoutRepository
import work.lockedinlabs.tracker.domain.CatalogExercise
import work.lockedinlabs.tracker.domain.ExerciseCatalog
import work.lockedinlabs.tracker.domain.Muscle

/** One row of the exercise library: a built-in exercise ([catalog]) or one of yours ([custom] muscles, if set). */
data class LibraryEntry(val name: String, val catalog: CatalogExercise?, val custom: CustomExercise?) {
    val isCustom: Boolean get() = catalog == null
    val primary: Set<Muscle> get() = catalog?.primary ?: custom?.primaryMuscles.orEmpty()
    val secondary: Set<Muscle> get() = catalog?.secondary ?: custom?.secondaryMuscles.orEmpty()
}

class ExercisesViewModel(
    private val customs: CustomExerciseRepository,
    workouts: WorkoutRepository,
    plans: PlanRepository,
) : ViewModel() {
    var query by mutableStateOf("")
        private set

    /** Exercise open in the detail view (by name). */
    var selected by mutableStateOf<String?>(null)
        private set

    private var customRows by mutableStateOf<List<CustomExercise>>(emptyList())
    /** Exercise names you've logged, newest first, with when you last logged each. */
    private var logged by mutableStateOf<List<String>>(emptyList())
    private var lastLogged by mutableStateOf<Map<String, Long>>(emptyMap())
    private var planned by mutableStateOf<List<String>>(emptyList())

    /** Your own exercises (from logs, plans, or created here), newest first, then the built-in catalog. */
    val entries: List<LibraryEntry> by derivedStateOf {
        val byKey = customRows.associateBy { it.key }
        // Newest = created here or last logged, whichever is later.
        fun addedAt(name: String): Long {
            val k = ExerciseCatalog.key(name)
            return maxOf(byKey[k]?.createdAt ?: 0L, lastLogged[k] ?: 0L)
        }
        val mine = (customRows.map { it.name } + logged + planned)
            .filter { ExerciseCatalog.find(it) == null }
            .distinctBy { ExerciseCatalog.key(it) }
            .sortedByDescending { addedAt(it) }
            .map { LibraryEntry(it, null, byKey[ExerciseCatalog.key(it)]) }
        mine + ExerciseCatalog.all.sortedBy { it.name }.map { LibraryEntry(it.name, it, null) }
    }

    val filtered: List<LibraryEntry> by derivedStateOf {
        val q = query.trim()
        if (q.isEmpty()) entries
        else {
            val catalogHits = ExerciseCatalog.search(q).map { ExerciseCatalog.key(it.name) }.toSet()
            entries.filter { it.name.contains(q, ignoreCase = true) || ExerciseCatalog.key(it.name) in catalogHits }
        }
    }

    /** Why [raw] can't be a new exercise (already exists), or null if it can. */
    fun nameError(raw: String): String? {
        val q = raw.trim()
        if (q.isEmpty()) return null
        val existing = ExerciseCatalog.find(q)?.name ?: entries.firstOrNull { ExerciseCatalog.key(it.name) == ExerciseCatalog.key(q) }?.name
        return existing?.let { "“$it” is already in the list" }
    }

    /** Typed a name that isn't in the library yet: offer to create it. */
    val canCreate: Boolean by derivedStateOf {
        val q = query.trim()
        q.isNotEmpty() && ExerciseCatalog.find(q) == null && entries.none { ExerciseCatalog.key(it.name) == ExerciseCatalog.key(q) }
    }

    val selectedEntry: LibraryEntry? by derivedStateOf {
        selected?.let { name -> entries.firstOrNull { ExerciseCatalog.key(it.name) == ExerciseCatalog.key(name) } }
    }

    val customCount: Int by derivedStateOf { entries.count { it.isCustom } }

    init {
        viewModelScope.launch { customs.observeAll().collect { customRows = it } }
        viewModelScope.launch {
            workouts.observeAll().collect { logs ->
                logged = logs.map { it.exercise }
                lastLogged = logs.groupBy { ExerciseCatalog.key(it.exercise) }.mapValues { (_, l) -> l.maxOf { it.createdAt } }
            }
        }
        viewModelScope.launch { plans.observePlans().collect { ps -> planned = ps.flatMap { p -> p.days.flatMap { it.exercises } } } }
    }

    fun onQuery(v: String) { if (v.length <= 40) query = v }

    fun open(name: String) { selected = name }

    fun close() { selected = null }

    /** A new custom exercise, opened so you can pick its muscles. */
    fun create(raw: String) {
        val name = raw.trim().replaceFirstChar { it.uppercase() }
        if (name.isEmpty()) return
        viewModelScope.launch { customs.setMuscles(name, emptySet(), emptySet()) }
        query = ""
        selected = name
    }

    /** Tap cycles a muscle: off → primary → secondary → off. */
    fun cycleMuscle(m: Muscle) {
        val entry = selectedEntry?.takeIf { it.isCustom } ?: return
        var p = entry.primary
        var s = entry.secondary
        when (m) {
            in p -> { p = p - m; s = s + m }
            in s -> s = s - m
            else -> p = p + m
        }
        viewModelScope.launch { customs.setMuscles(entry.name, p, s) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LockedInApp
                ExercisesViewModel(app.customExercises, app.repository, app.planRepository)
            }
        }
    }
}
