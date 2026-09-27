package work.lockedinlabs.tracker.ui.plan

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import work.lockedinlabs.tracker.ui.SaveQueue
import work.lockedinlabs.tracker.LockedInApp
import work.lockedinlabs.tracker.data.PlanDay
import work.lockedinlabs.tracker.data.PlanRepository
import work.lockedinlabs.tracker.data.PlanWithDays
import work.lockedinlabs.tracker.data.WorkoutRepository
import work.lockedinlabs.tracker.data.CustomExerciseRepository
import kotlinx.coroutines.flow.combine
import work.lockedinlabs.tracker.domain.ExerciseCatalog
import work.lockedinlabs.tracker.domain.Rotation
import work.lockedinlabs.tracker.sync.SyncManager
import work.lockedinlabs.tracker.ui.theme.DayColors

/**
 * The Plan tab: your list of plans, and the editor for one of them.
 * Edits apply to local state immediately and save in the background (latest edit wins).
 */
class PlanViewModel(
    private val plans: PlanRepository,
    workouts: WorkoutRepository,
    private val customExercises: CustomExerciseRepository,
    sync: SyncManager,
) : ViewModel() {
    var allPlans by mutableStateOf<List<PlanWithDays>>(emptyList())
        private set
    var plansLoaded by mutableStateOf(false)
        private set

    /** Plan open in the editor (null = the list). */
    var editingId by mutableStateOf<Long?>(null)
        private set
    var planName by mutableStateOf("")
        private set
    val days = mutableStateListOf<PlanDay>()
    var loaded by mutableStateOf(false)
        private set
    var expanded by mutableStateOf<Int?>(null)
        private set
    var knownExercises by mutableStateOf<List<String>>(emptyList())
        private set

    val editingPlan by derivedStateOf { allPlans.firstOrNull { it.plan.id == editingId }?.plan }
    val cycle by derivedStateOf { Rotation.cycle(days, editingPlan?.weekly == true) }

    private val daySaves = SaveQueue<Long, List<PlanDay>>(viewModelScope) { id, d -> plans.savePlan(id, d) }
    private val nameSaves = SaveQueue<Long, String>(viewModelScope) { id, n -> plans.renamePlan(id, n) }

    init {
        viewModelScope.launch {
            plans.observePlans().collect {
                allPlans = it
                plansLoaded = true
                // The plan being edited was deleted (e.g. on another phone): back to the list.
                if (editingId != null && it.none { p -> p.plan.id == editingId }) closeEditor()
            }
        }
        viewModelScope.launch {
            // Your exercises: ones you've logged plus ones you created (More → Exercises).
            combine(workouts.observeAll(), customExercises.observeAll()) { logs, cx -> logs.map { it.exercise } + cx.map { it.name } }
                .collect { names -> knownExercises = names.distinctBy { it.lowercase() } }
        }
        viewModelScope.launch { sync.remoteApplied.collect { editingId?.let { load(it) } } }
    }

    fun openPlan(id: Long) {
        editingId = id
        expanded = null
        loaded = false
        viewModelScope.launch { load(id) }
    }

    private suspend fun load(id: Long) {
        // A just-created plan may take a moment to show up in the shared list.
        val p = withTimeoutOrNull(2_000) { plans.observePlans().first { list -> list.any { it.plan.id == id } } }
            ?.firstOrNull { it.plan.id == id } ?: return closeEditor()
        planName = p.plan.name
        days.clear()
        days.addAll(p.days)
        // Days set up before color tags existed get one now.
        var colored = false
        for (i in days.indices) {
            val c = withAutoColor(days[i])
            if (c != days[i]) { days[i] = c; colored = true }
        }
        if (colored) daySaves.submit(id, days.toList())
        loaded = true
    }

    fun closeEditor() {
        editingId = null
        loaded = false
    }

    /** A new, empty plan — active straight away if you don't have one yet — opened for editing. */
    fun createPlan() {
        viewModelScope.launch {
            val taken = allPlans.map { it.plan.displayName.lowercase() }.toSet()
            val name = generateSequence(allPlans.size + 1) { it + 1 }.map { "Plan $it" }.first { it.lowercase() !in taken }
            val id = plans.createPlan(name, activate = allPlans.none { it.plan.isActive })
            openPlan(id)
        }
    }

    fun onPlanName(v: String) {
        if (v.length > 30) return
        planName = v
        editingId?.let { nameSaves.submit(it, v.trim()) }
    }

    fun setWeekly(on: Boolean) { editingId?.let { viewModelScope.launch { plans.setWeekly(it, on) } } }

    fun activate(id: Long) { viewModelScope.launch { plans.activate(id) } }

    fun deletePlan(id: Long) {
        if (editingId == id) closeEditor()
        viewModelScope.launch { plans.deletePlan(id) }
    }

    fun toggleExpanded(i: Int) { expanded = if (expanded == i) null else i }

    fun rename(i: Int, name: String) { if (name.length <= 30) update(i) { it.copy(name = name) } }

    /** Each workout day needs its own name — it's what the Log tab and your history show. */
    fun nameError(i: Int): String? {
        val day = days[i]
        val name = day.name.trim()
        if (day.isRest || name.isEmpty()) return null
        if (name.equals("rest", ignoreCase = true)) return "\"Rest\" is reserved. Use the Rest day switch."
        val clash = days.firstOrNull { it.dayIndex != i && !it.isRest && it.name.trim().equals(name, ignoreCase = true) }
        return clash?.let { "Day ${it.dayIndex + 1} already uses this name" }
    }

    fun setRest(i: Int, rest: Boolean) = update(i) { it.copy(isRest = rest) }

    fun addExercise(i: Int, raw: String) {
        val name = raw.trim()
        if (name.isEmpty() || days[i].exercises.any { it.equals(name, ignoreCase = true) }) return
        // Reuse the spelling from your history so logs and plan line up.
        val canonical = knownExercises.firstOrNull { it.equals(name, ignoreCase = true) }
            ?: ExerciseCatalog.find(name)?.name
            ?: name.replaceFirstChar { it.uppercase() }
        // A brand-new name becomes one of your exercises too, so you can set its muscles.
        if (ExerciseCatalog.find(canonical) == null) viewModelScope.launch { customExercises.add(canonical) }
        update(i) { it.copy(exercises = it.exercises + canonical).withSets(it.sets + PlanDay.DEFAULT_SETS) }
    }

    fun removeExercise(i: Int, j: Int) = update(i) { d ->
        d.copy(exercises = d.exercises.filterIndexed { k, _ -> k != j }).withSets(d.sets.filterIndexed { k, _ -> k != j })
    }

    /** How many sets you plan for exercise [j] of day [i]. */
    fun setSets(i: Int, j: Int, count: Int) = update(i) { d -> d.withSets(d.sets.toMutableList().also { it[j] = count }) }

    fun moveExercise(i: Int, from: Int, to: Int) {
        if (from == to) return
        update(i) { d ->
            val l = d.exercises.toMutableList()
            val c = d.sets.toMutableList()
            l.add(to, l.removeAt(from))
            c.add(to, c.removeAt(from))
            d.copy(exercises = l).withSets(c)
        }
    }

    fun setColor(i: Int, color: Int) = update(i) { it.copy(color = color) }

    private fun update(i: Int, change: (PlanDay) -> PlanDay) {
        val id = editingId ?: return
        days[i] = withAutoColor(change(days[i]))
        daySaves.submit(id, days.toList())
    }

    /** A workout day gets a color as soon as it's named or given an exercise: the first one no other day uses. */
    private fun withAutoColor(day: PlanDay): PlanDay {
        if (day.color != null || day.isRest || (day.name.isBlank() && day.exercises.isEmpty())) return day
        val used = days.filter { it.dayIndex != day.dayIndex }.mapNotNull { it.color }.toSet()
        val pick = DayColors.indices.firstOrNull { it !in used } ?: (day.dayIndex % DayColors.size)
        return day.copy(color = pick)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LockedInApp
                PlanViewModel(app.planRepository, app.repository, app.customExercises, app.sync)
            }
        }
    }
}
