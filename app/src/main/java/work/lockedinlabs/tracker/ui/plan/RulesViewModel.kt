package work.lockedinlabs.tracker.ui.plan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import work.lockedinlabs.tracker.ui.SaveQueue
import work.lockedinlabs.tracker.LockedInApp
import work.lockedinlabs.tracker.data.ProgressionRule
import work.lockedinlabs.tracker.data.RuleRepository
import work.lockedinlabs.tracker.data.WorkoutRepository
import work.lockedinlabs.tracker.domain.ExerciseCatalog

/** Progression rules on the Plan tab: the list, and an editor that saves as you go. */
class RulesViewModel(private val repo: RuleRepository, workouts: WorkoutRepository) : ViewModel() {
    var rules by mutableStateOf<List<ProgressionRule>>(emptyList())
        private set
    var knownExercises by mutableStateOf<List<String>>(emptyList())
        private set

    /** Rule open in the editor (null = none). */
    var editingId by mutableStateOf<Long?>(null)
        private set
    var draft by mutableStateOf<ProgressionRule?>(null)
        private set

    // Typed text, so a half-typed number isn't reformatted under the cursor.
    val repsInputs = mutableStateListOf<String>()
    var startInput by mutableStateOf("")
        private set
    var minInput by mutableStateOf("")
        private set

    private val saves = SaveQueue<Long, ProgressionRule>(viewModelScope) { _, rule -> repo.save(rule) }

    init {
        viewModelScope.launch {
            repo.observeRules().collect { list ->
                rules = list
                val id = editingId ?: return@collect
                val fresh = list.firstOrNull { it.id == id }
                if (fresh == null) close() // deleted elsewhere
                else if (draft?.isDefault != fresh.isDefault) draft = draft?.copy(isDefault = fresh.isDefault)
            }
        }
        viewModelScope.launch {
            workouts.observeAll().collect { logs -> knownExercises = logs.map { it.exercise }.distinctBy { it.lowercase() } }
        }
    }

    fun open(id: Long) {
        rules.firstOrNull { it.id == id }?.let(::open)
    }

    private fun open(rule: ProgressionRule) {
        editingId = rule.id
        draft = rule
        repsInputs.clear()
        repsInputs.addAll(rule.targets.map { it.toString() })
        startInput = rule.startReps.toString()
        minInput = rule.minReps.toString()
    }

    fun close() {
        editingId = null
        draft = null
    }

    fun create() {
        viewModelScope.launch {
            val taken = rules.map { it.displayName.lowercase() }.toSet()
            val name = generateSequence(rules.size + 1) { it + 1 }.map { "Progression $it" }.first { it.lowercase() !in taken }
            open(repo.create(name))
        }
    }

    fun makeDefault() { editingId?.let { viewModelScope.launch { repo.makeDefault(it) } } }

    fun delete() {
        val id = editingId ?: return
        close()
        viewModelScope.launch { repo.delete(id) }
    }

    fun onName(v: String) { if (v.length <= 30) edit { it.copy(name = v) } }

    fun onSetReps(i: Int, v: String) {
        if (v.isNotEmpty() && !COUNT.matches(v)) return
        repsInputs[i] = v
        commitReps()
    }

    fun addSet() {
        if (repsInputs.size >= MAX_SETS) return
        repsInputs.add(repsInputs.lastOrNull()?.takeIf { it.isNotEmpty() } ?: "10")
        commitReps()
    }

    fun removeSet(i: Int) {
        if (repsInputs.size <= 1) return
        repsInputs.removeAt(i)
        commitReps()
    }

    private fun commitReps() {
        val targets = repsInputs.mapNotNull { it.toIntOrNull()?.takeIf { r -> r > 0 } }
        if (targets.size == repsInputs.size) edit { it.copy(setReps = ProgressionRule.encode(targets)) }
    }

    fun onIncrement(lbs: Double) = edit { it.copy(increment = lbs) }

    fun onStartReps(v: String) {
        if (v.isNotEmpty() && !COUNT.matches(v)) return
        startInput = v
        v.toIntOrNull()?.takeIf { it > 0 }?.let { r -> edit { it.copy(startReps = r) } }
    }

    fun onMinReps(v: String) {
        if (v.isNotEmpty() && !COUNT.matches(v)) return
        minInput = v
        v.toIntOrNull()?.takeIf { it > 0 }?.let { r -> edit { it.copy(minReps = r) } }
    }

    fun onDropPct(pct: Int) = edit { it.copy(dropPct = pct) }

    fun addExercise(raw: String) {
        val name = raw.trim()
        val d = draft ?: return
        if (name.isEmpty() || d.exercises.any { it.equals(name, ignoreCase = true) }) return
        // Same spelling as your logs so the rule matches them.
        val canonical = knownExercises.firstOrNull { it.equals(name, ignoreCase = true) }
            ?: ExerciseCatalog.find(name)?.name
            ?: name.replaceFirstChar { it.uppercase() }
        edit { it.copy(exercises = it.exercises + canonical) }
    }

    fun removeExercise(name: String) = edit { r -> r.copy(exercises = r.exercises.filterNot { it == name }) }

    /** Which other rule an exercise is in (it'll move here if added). */
    fun ruleOf(exercise: String): ProgressionRule? =
        rules.firstOrNull { r -> r.id != editingId && !r.isDefault && r.exercises.any { it.equals(exercise, ignoreCase = true) } }

    private fun edit(change: (ProgressionRule) -> ProgressionRule) {
        val d = draft ?: return
        val updated = change(d)
        draft = updated
        saves.submit(updated.id, updated)
    }

    companion object {
        const val MAX_SETS = 10
        private val COUNT = Regex("""\d{1,2}""")

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LockedInApp
                RulesViewModel(app.ruleRepository, app.repository)
            }
        }
    }
}
