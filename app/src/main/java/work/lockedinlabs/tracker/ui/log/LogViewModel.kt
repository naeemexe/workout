package work.lockedinlabs.tracker.ui.log

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import work.lockedinlabs.tracker.LockedInApp
import work.lockedinlabs.tracker.data.ExerciseLog
import work.lockedinlabs.tracker.data.PlanDay
import work.lockedinlabs.tracker.data.DayChoice
import work.lockedinlabs.tracker.data.PlanWithDays
import work.lockedinlabs.tracker.data.ProgressionRule
import work.lockedinlabs.tracker.data.RuleRepository
import work.lockedinlabs.tracker.data.ruleFor
import work.lockedinlabs.tracker.data.CustomExercise
import work.lockedinlabs.tracker.data.CustomExerciseRepository
import work.lockedinlabs.tracker.data.lookup
import work.lockedinlabs.tracker.data.PlanRepository
import work.lockedinlabs.tracker.data.SettingsStore
import work.lockedinlabs.tracker.data.SetEntry
import work.lockedinlabs.tracker.data.WorkoutRepository
import work.lockedinlabs.tracker.domain.ExerciseCatalog
import work.lockedinlabs.tracker.domain.Advice
import work.lockedinlabs.tracker.domain.Progression
import work.lockedinlabs.tracker.domain.Suggestion
import work.lockedinlabs.tracker.domain.Rotation
import work.lockedinlabs.tracker.domain.estimatedMax
import work.lockedinlabs.tracker.domain.weightText
import work.lockedinlabs.tracker.domain.normalizeDecimal
import java.time.LocalDate

/** What's typed for one set. A set "counts" once it has a valid weight and reps. */
data class SetInput(val weight: String = "", val reps: String = "") {
    val entry: SetEntry?
        get() {
            val w = weight.toDoubleOrNull()?.takeIf { it >= 0.0 } ?: return null
            val r = reps.toIntOrNull()?.takeIf { it > 0 } ?: return null
            return SetEntry(w, r)
        }
    val isFilled: Boolean get() = entry != null
    val isBlank: Boolean get() = weight.isEmpty() && reps.isEmpty()
}

/** A tappable exercise in the Exercise row or the "+ Other" results. */
data class ExerciseChip(
    val name: String,
    /** Already logged on the selected date. */
    val done: Boolean = false,
    /** The exercise currently being logged. */
    val selected: Boolean = false,
    /** "Create “…”" option for a typed name that matches nothing. */
    val custom: Boolean = false,
)

sealed interface LogEvent {
    data class Saved(val log: ExerciseLog, val isPr: Boolean) : LogEvent
    data class SetSaved(val exercise: String, val set: Int) : LogEvent
    data class Deleted(val log: ExerciseLog) : LogEvent
}

/**
 * Form fields are Compose state (not StateFlow) so text input stays synchronous and the cursor never jumps.
 */
class LogViewModel(
    private val repository: WorkoutRepository,
    private val plans: PlanRepository,
    private val ruleRepository: RuleRepository,
    private val customExercises: CustomExerciseRepository,
    private val settings: SettingsStore,
    alarm: RestAlarm,
) : ViewModel() {
    /** Rest between sets (Save starts it; the Rest button restarts it). */
    val rest = RestTimer(viewModelScope, alarm)
    /** What comes after the rest, shown on the rest panel ("then set 2", "then Squat"). */
    var restNote by mutableStateOf("")
        private set
    /** Default rest between sets, picked from the ⋮ menu next to the Rest button. */
    var defaultRestSeconds by mutableIntStateOf(settings.restSeconds.value)
        private set

    fun setDefaultRest(seconds: Int) {
        defaultRestSeconds = seconds
        settings.setRestSeconds(seconds)
    }

    fun startRest() {
        restNote = if (exercise.isNotBlank() && selectedSet > savedThrough) "then set ${selectedSet + 1}" else ""
        rest.start(defaultRestSeconds)
    }

    /** Save starts the rest by itself; it has already moved on to the next set, or to the next exercise. */
    private fun restAfterSave(nextExercise: Boolean) {
        restNote = when {
            exercise.isBlank() -> ""
            nextExercise -> "then $exercise"
            else -> "then set ${selectedSet + 1}"
        }
        rest.start(defaultRestSeconds)
    }

    var allLogs by mutableStateOf<List<ExerciseLog>>(emptyList())
        private set
    private var logsLoaded by mutableStateOf(false)
    private var plan by mutableStateOf<List<PlanDay>>(emptyList())
    private var choices by mutableStateOf<Map<Long, Int>>(emptyMap())
    private var planLoaded by mutableStateOf(false)
    private var weekly by mutableStateOf(false)
    private var allPlans by mutableStateOf<List<PlanWithDays>>(emptyList())
    private var choiceRecords by mutableStateOf<List<DayChoice>>(emptyList())
    private var rules by mutableStateOf<List<ProgressionRule>>(emptyList())
    private var customs by mutableStateOf<List<CustomExercise>>(emptyList())
    private var choicesLoaded by mutableStateOf(false)
    private var dayNames by mutableStateOf<Map<Long, String>>(emptyMap())
    var date by mutableStateOf(LocalDate.now())
        private set
    var exercise by mutableStateOf("")
        private set

    /** Name of a custom session, typed in the box at the top of the page (optional). */
    var customName by mutableStateOf("")
        private set

    /** Exercise to select once a date change has been handled (see [logToday]). */
    private var pendingExercise: String? = null

    /** Extra exercises picked with "+ Other", per date and session; kept in memory only (gone when the app closes). */
    private val sessionExtras = mutableStateMapOf<String, List<String>>()

    /** Sets being entered; always at least one. */
    val sets = mutableStateListOf(SetInput())
    var selectedSet by mutableIntStateOf(0)
        private set
    val current: SetInput get() = sets[selectedSet]

    private val _events = Channel<LogEvent>(Channel.BUFFERED)
    val events: Flow<LogEvent> = _events.receiveAsFlow()

    /** Distinct exercise names, most recently used first. */
    val knownExercises by derivedStateOf { allLogs.map { it.exercise }.distinctBy { it.lowercase() } }

    /** Plan days in rotation order (empty days skipped). */
    val cycle by derivedStateOf { Rotation.cycle(plan, weekly) }

    /** Workout days to pick from on the Log tab; planned rest days are covered by the Rest chip. */
    val workoutDays by derivedStateOf { cycle.filterNot { it.isRest } }

    /** Which plan day the selected date is: your pick, or the next one due in the rotation. */
    val planDay by derivedStateOf {
        Rotation.dayFor(date, cycle, choices, allLogs.mapTo(HashSet()) { it.epochDay })?.let { day ->
            if (day.dayIndex == PlanDay.CUSTOM_CHOICE) PlanDay.custom(dayNames[date.toEpochDay()].orEmpty()) else day
        }
    }

    val entriesForDate by derivedStateOf { allLogs.filter { it.epochDay == date.toEpochDay() } }

    /**
     * Color tag for each date you logged something, for the calendar: the plan day's color (matched by name,
     * active plan first), [CUSTOM_TAG] for a custom session, or null when there's no plan day to go by.
     */
    val loggedDayTags: Map<Long, Int?> by derivedStateOf {
        val choiceByDay = choiceRecords.associateBy { it.epochDay }
        val byName = HashMap<String, Int>()
        (allPlans.sortedByDescending { it.plan.isActive }).forEach { p ->
            p.days.filter { it.isActive && !it.isRest }.forEach { d -> byName.putIfAbsent(d.name.trim().lowercase(), d.color ?: d.dayIndex) }
        }
        allLogs.map { it.epochDay }.distinct().associateWith { day ->
            val choice = choiceByDay[day]
            when {
                choice == null -> null
                choice.dayIndex == PlanDay.CUSTOM_CHOICE -> CUSTOM_TAG
                else -> byName[choice.name.trim().lowercase()]
            }
        }
    }

    /** The selected date is a rest day and it's been recorded as one. */
    val restLogged by derivedStateOf { planDay?.isRest == true && date.toEpochDay() in choices }

    /** Record the selected date as a rest day. */
    fun saveRestDay() {
        planDay?.takeIf { it.isRest }?.let(::choosePlanDay)
    }

    /** Search box (opened by "+ Other") is showing instead of the day's chips. */
    var searching by mutableStateOf(false)
        private set

    fun openSearch() {
        searching = true
        exercise = ""
    }

    fun closeSearch() {
        searching = false
    }

    /**
     * The Exercise row: the session's planned exercises (with no plan or in a custom session, only the ones logged
     * that day), then any added with "+ Other", with the one being logged highlighted.
     */
    val dayChips by derivedStateOf {
        val done = entriesForDate.map { it.exercise.lowercase() }.toSet()
        val planned = planDay?.takeUnless { it.isRest }?.exercises.orEmpty()
        val base = (if (cycle.isEmpty() || planDay?.isCustom == true) entriesForDate.sortedBy { it.createdAt }.map { it.exercise }.distinctBy { it.lowercase() } else planned)
            .map { ExerciseChip(it, done = it.lowercase() in done) }
        // Exercises you added with "+ Other" stay in this session's row until the app is closed.
        val added = sessionExtras[sessionKey].orEmpty()
            .filter { e -> base.none { it.name.equals(e, ignoreCase = true) } }
            .map { ExerciseChip(it, done = it.lowercase() in done) }
        val current = exercise.trim()
        val marked = (base + added).map { it.copy(selected = current.isNotEmpty() && it.name.equals(current, ignoreCase = true)) }
        if (current.isEmpty() || marked.any { it.selected }) marked
        else listOf(ExerciseChip(current, selected = true, done = current.lowercase() in done)) + marked
    }

    /** Search results under the "+ Other" box. */
    val chipOptions by derivedStateOf {
        val q = exercise.trim()
        val done = entriesForDate.map { it.exercise.lowercase() }.toSet()
        val planned = planDay?.takeUnless { it.isRest }?.exercises.orEmpty()
        val plannedChips = planned.map { ExerciseChip(it, done = it.lowercase() in done) }
        val plannedKeys = planned.map { it.lowercase() }.toSet()
        val others = (knownExercises + plan.flatMap { it.exercises })
            .distinctBy { it.lowercase() }
            .filter { it.lowercase() !in plannedKeys }
            .map { ExerciseChip(it) }
        if (q.isEmpty()) {
            (others + plannedChips).take(10)
        } else {
            // Yours first, then built-in catalog exercises you haven't used yet.
            val mine = (plannedChips + others).filter { it.name.contains(q, ignoreCase = true) }
            val mineKeys = mine.map { ExerciseCatalog.key(it.name) }.toSet()
            val catalog = ExerciseCatalog.search(q).filter { ExerciseCatalog.key(it.name) !in mineKeys }.map { ExerciseChip(it.name) }
            val matches = (mine + catalog).take(10)
            // Typed something that isn't a known name or nickname: offer to log it as a custom exercise.
            val known = ExerciseCatalog.find(q) != null || matches.any { ExerciseCatalog.key(it.name) == ExerciseCatalog.key(q) }
            if (known) matches else matches + ExerciseChip(q, custom = true)
        }
    }

    /** Spelling to save under: your existing name if it matches, else the catalog name (so "ohp" → "Overhead Press"). */
    private val matchedExercise by derivedStateOf {
        val q = exercise.trim()
        (knownExercises + plan.flatMap { it.exercises }).firstOrNull { it.equals(q, ignoreCase = true) }
            ?: ExerciseCatalog.find(q)?.name
    }

    private fun matchName(name: String): String {
        val q = name.trim()
        return (knownExercises + plan.flatMap { it.exercises }).firstOrNull { it.equals(q, ignoreCase = true) }
            ?: ExerciseCatalog.find(q)?.name
            ?: q.replaceFirstChar { it.uppercase() }
    }

    /** The selected exercise is ready to step up (same rule as Home's "Next session"); null otherwise. */
    val stepUp: Suggestion? by derivedStateOf {
        matchedExercise?.let { name -> Progression.suggest(allLogs.filter { it.exercise.equals(name, ignoreCase = true) }, rules.ruleFor(name)) }
            ?.takeIf { it.advice == Advice.STEP_UP }
    }

    /** A custom exercise you haven't given muscles yet (More → Exercises), so it won't count on the muscle map. */
    val isCustomExercise by derivedStateOf { exercise.isNotBlank() && customs.lookup()(exercise) == null }

    /** Sets planned for the selected exercise in today's session (More → Session plans), or null if none. */
    val plannedSets: Int? by derivedStateOf {
        val day = planDay?.takeUnless { it.isRest || it.isCustom } ?: return@derivedStateOf null
        day.exercises.indexOfFirst { it.equals(exercise.trim(), ignoreCase = true) }.takeIf { it >= 0 }?.let(day::setsFor)
    }

    /** Save saves one set at a time and moves on; the last set (planned or added with +) finishes the exercise. */
    val savingOneSet: Boolean by derivedStateOf { selectedSet < sets.lastIndex }

    /**
     * The log being written for the selected exercise on this date: null id until the first save. Saves keep a
     * reference, so switching exercises can't mix them up. [merged] are older duplicates folded into this one.
     */
    private class Progress(var id: Long? = null, val createdAt: Long = System.currentTimeMillis(), var merged: List<ExerciseLog> = emptyList())
    private var progress = Progress()
    /** Index of the last saved set of the exercise being logged (-1 = none yet). */
    var savedThrough by mutableIntStateOf(-1)
        private set

    /** Sets open to tap: the saved ones and the next one. Later sets wait until the one before is saved. */
    private fun canSelectSet(index: Int): Boolean = index <= savedThrough + 1
    /** Saves run one at a time so quick taps can't create two logs for the same exercise. */
    private val saving = Mutex()

    val canSave by derivedStateOf { exercise.isNotBlank() && current.isFilled }

    /** What Save says: why it's disabled, "Save set 2" while working through the sets, or "Save" on the last one. */
    val saveLabel: String by derivedStateOf {
        when {
            exercise.isBlank() -> "Pick an exercise first"
            !current.isFilled -> "Enter weight and reps"
            savingOneSet -> "Save set ${selectedSet + 1}"
            else -> "Save"
        }
    }

    init {
        viewModelScope.launch { repository.observeAll().collect { allLogs = it; logsLoaded = true } }
        viewModelScope.launch { plans.observePlan().collect { plan = it; planLoaded = true } }
        viewModelScope.launch { plans.observeChoices().collect { choices = it; choicesLoaded = true } }
        viewModelScope.launch { plans.observeDayNames().collect { dayNames = it } }
        viewModelScope.launch { plans.observePlans().collect { allPlans = it } }
        viewModelScope.launch { plans.observeChoiceRecords().collect { choiceRecords = it } }
        viewModelScope.launch { plans.observeWeekly().collect { weekly = it } }
        viewModelScope.launch { ruleRepository.observeRules().collect { rules = it } }
        viewModelScope.launch { customExercises.observeAll().collect { customs = it } }
        // Day → exercise → set: whenever the day changes, start on its next exercise, set 1.
        viewModelScope.launch {
            // A custom session's name is typed on this page, so renaming it mustn't reset the exercise.
            snapshotFlow { Triple(date, planDay?.let { it.dayIndex to if (it.isCustom) "" else it.displayName }, logsLoaded) }
                .distinctUntilChanged()
                .collect { (_, _, loaded) ->
                    customName = planDay?.takeIf { it.isCustom }?.name.orEmpty()
                    if (!loaded) return@collect
                    selectNextPlanned()
                    // Opened for a specific exercise (e.g. from Home): select it once the day is set up.
                    pendingExercise?.let { selectExercise(it); pendingExercise = null }
                }
        }
        // A rest day in the rotation logs itself today; picking a workout or Custom replaces it.
        viewModelScope.launch {
            snapshotFlow { date to if (logsLoaded && planLoaded && choicesLoaded) planDay?.takeIf { it.isRest } else null }
                .distinctUntilChanged()
                .collect { (date, rest) ->
                    val today = LocalDate.now()
                    // Insert-if-absent, so it never overwrites a day you already picked.
                    if (rest != null && date == today && today.toEpochDay() !in choices) plans.recordIfAbsent(today, rest)
                }
        }
    }

    /** The day's first planned exercise not logged yet (none on rest / custom days, or when all are done). */
    private fun nextPlanned(justSaved: String? = null): String? {
        val done = entriesForDate.map { it.exercise.lowercase() }.toSet() + listOfNotNull(justSaved?.lowercase())
        return planDay?.takeUnless { it.isRest }?.exercises.orEmpty().firstOrNull { it.lowercase() !in done }
    }

    private fun selectNextPlanned(justSaved: String? = null) {
        searching = false
        val next = nextPlanned(justSaved)
        if (next != null) selectExercise(next) else { exercise = ""; progress = Progress(); savedThrough = -1; resetSets() }
    }

    /** A custom session can't reuse a plan day's name or "Rest", so your history stays unambiguous. */
    fun customNameError(raw: String): String? {
        val name = raw.trim()
        return when {
            name.isEmpty() -> null
            name.equals("rest", ignoreCase = true) -> "\"Rest\" is taken. Use the Rest chip."
            plan.any { !it.isRest && it.name.trim().equals(name, ignoreCase = true) } -> "That's a plan day. Pick its chip instead."
            else -> null
        }
    }

    /** Custom chip: an off-plan session, logged like any other day; name it in the box if you like. */
    fun chooseCustom() {
        if (planDay?.isCustom != true) choosePlanDay(PlanDay.custom(""))
    }

    fun onCustomName(v: String) {
        if (v.length > 30) return
        customName = v
        // Saved as you type, unless it clashes with a plan day's name (then the old name stays).
        if (customNameError(v) == null) choosePlanDay(PlanDay.custom(v.trim().replaceFirstChar { it.uppercase() }))
    }

    fun choosePlanDay(day: PlanDay) {
        choices = choices + (date.toEpochDay() to day.dayIndex)
        dayNames = dayNames + (date.toEpochDay() to day.displayName)
        viewModelScope.launch { plans.choose(date, day) }
    }

    fun onDateChange(d: LocalDate) { date = d }

    /** Log [name] today (from Home's "Next session"), whatever date the Log tab was showing. */
    fun logToday(name: String) {
        val today = LocalDate.now()
        if (date == today) selectExercise(name)
        else {
            pendingExercise = name
            date = today
        }
    }
    fun onExerciseChange(v: String) { if (v.length <= 40) exercise = v }
    fun onWeightChange(typed: String) {
        val v = normalizeDecimal(typed)
        if (v.isEmpty() || WEIGHT_INPUT.matches(v)) sets[selectedSet] = current.copy(weight = v)
    }
    fun onRepsChange(v: String) { if (v.isEmpty() || COUNT_INPUT.matches(v)) sets[selectedSet] = current.copy(reps = v) }

    /** Tap a set to enter or edit it: a saved one, or the next one to do. */
    fun selectSet(index: Int) {
        if (index in sets.indices && canSelectSet(index)) selectedSet = index
    }

    /**
     * The + after the set bubbles: one more set for today, starting with the last one's weight. Only today's log
     * gets it; next time the plan's count applies again. Selected straight away if every set before it is saved.
     */
    fun addSet() {
        val last = sets.last()
        if (last.isBlank) return selectSet(sets.lastIndex)
        sets.add(SetInput(weight = last.weight))
        selectSet(sets.lastIndex)
    }

    /** Removes the selected set; a saved one comes out of the day's log right away. */
    fun removeSelectedSet() {
        val index = selectedSet
        if (sets.size == 1) sets[0] = SetInput() else sets.removeAt(index)
        selectedSet = index.coerceAtMost(sets.lastIndex)
        if (index > savedThrough) return
        savedThrough--
        val p = progress
        val id = p.id ?: return
        val log = ExerciseLog(id = id, exercise = exercise, sets = sets.take(savedThrough + 1).mapNotNull { it.entry }, epochDay = date.toEpochDay())
        viewModelScope.launch {
            saving.withLock {
                if (log.sets.isEmpty()) { repository.delete(log); p.id = null } else repository.add(log.copy(createdAt = p.createdAt))
            }
        }
        selectedSet = minOf(selectedSet, savedThrough + 1)
    }

    private val sessionKey: String get() = "${date.toEpochDay()}|${planDay?.let { if (it.isCustom) "custom" else it.displayName }.orEmpty()}"

    /**
     * A pick from the "+ Other" search. A brand-new name ("Create …") becomes one of your exercises right away
     * (More → Exercises); either way it stays in this session's row.
     */
    fun pickFromSearch(chip: ExerciseChip) {
        val name = matchName(chip.name)
        if (chip.custom) viewModelScope.launch { customExercises.add(name) }
        val key = sessionKey
        val list = sessionExtras[key].orEmpty()
        if (list.none { it.equals(name, ignoreCase = true) }) sessionExtras[key] = list + name
        selectExercise(name)
    }

    /**
     * Pick an exercise. Already logged on this date: that day's sets load (however many you did) and Save updates the
     * same log, with the next set selected. Otherwise the plan sets the number of sets, pre-filled from last time;
     * with no plan, last time's sets.
     */
    fun selectExercise(name: String) {
        searching = false
        exercise = matchName(name)
        val logged = entriesForDate.filter { it.exercise.equals(exercise, ignoreCase = true) }.sortedBy { it.createdAt }
        val source = if (logged.isNotEmpty()) logged.flatMap { it.sets }
        else allLogs.filter { it.exercise.equals(exercise, ignoreCase = true) }.maxWithOrNull(compareBy({ it.epochDay }, { it.createdAt }))?.sets.orEmpty()
        progress = logged.firstOrNull()?.let { Progress(it.id, it.createdAt, logged.drop(1)) } ?: Progress()
        savedThrough = if (logged.isNotEmpty()) source.lastIndex else -1
        val inputs = source.map { SetInput(it.weightLbs.weightText(), it.reps.toString()) }.toMutableList()
        plannedSets?.let { n ->
            // A new day follows the plan: extra sets from last time are left out, missing ones start with the last weight.
            if (logged.isEmpty()) while (inputs.size > n) inputs.removeAt(inputs.lastIndex)
            while (inputs.size < n) inputs += SetInput(weight = inputs.lastOrNull()?.weight.orEmpty())
        }
        resetSets(inputs)
        // Continue where you left off: the first set not saved yet, else the last one (tap + for another).
        if (logged.isNotEmpty()) selectedSet = minOf(source.size, sets.lastIndex)
    }

    /** Writes [log] as the exercise's one log for the day (replacing earlier saves) and drops any merged duplicates. */
    private suspend fun write(p: Progress, log: ExerciseLog) = saving.withLock {
        p.id = repository.add(log.copy(id = p.id ?: 0, createdAt = p.createdAt))
        p.merged.forEach { repository.delete(it) }
        p.merged = emptyList()
    }

    private fun resetSets(inputs: List<SetInput> = listOf(SetInput())) {
        selectedSet = 0
        sets.clear()
        sets.addAll(inputs.ifEmpty { listOf(SetInput()) })
    }

    fun save() {
        if (!canSave) return
        val name = matchedExercise ?: exercise.trim().replaceFirstChar { it.uppercase() }
        if (savingOneSet) {
            saveSet(name)
            return
        }
        val log = ExerciseLog(exercise = name, sets = sets.mapNotNull { it.entry }, epochDay = date.toEpochDay())
        val p = progress
        val mine = setOfNotNull(p.id) + p.merged.map { it.id }
        val previousBest = allLogs.filter { it.exercise.equals(name, ignoreCase = true) && it.id !in mine }.maxOfOrNull { it.estimatedMax() }
        val isPr = previousBest != null && log.estimatedMax() > previousBest + 1e-6
        val day = planDay
        val logDate = date
        viewModelScope.launch {
            // Updates the day's log for this exercise if there is one, else adds it.
            write(p, log)
            // Lock in which plan day this was so the rotation moves on from it.
            day?.let { plans.recordIfAbsent(logDate, it) }
            _events.send(LogEvent.Saved(log, isPr))
        }
        selectNextPlanned(justSaved = name)
        restAfterSave(nextExercise = true)
    }

    /** Saves the sets done so far (so nothing is lost) and jumps to the next one, if it isn't saved yet. */
    private fun saveSet(name: String) {
        val advancing = selectedSet > savedThrough
        savedThrough = maxOf(savedThrough, selectedSet)
        val done = sets.take(savedThrough + 1).mapNotNull { it.entry }
        val log = ExerciseLog(exercise = name, sets = done, epochDay = date.toEpochDay())
        val day = planDay
        val logDate = date
        val setNumber = selectedSet + 1
        val p = progress
        viewModelScope.launch {
            write(p, log)
            day?.let { plans.recordIfAbsent(logDate, it) }
            _events.send(LogEvent.SetSaved(name, setNumber))
        }
        // Editing an earlier set keeps you where you are; otherwise on to the next set and rest.
        if (!advancing) return
        // Next set starts with this set's weight if it has none yet (usually the same).
        val weight = current.weight
        selectSet(savedThrough + 1)
        if (current.weight.isEmpty()) sets[selectedSet] = current.copy(weight = weight)
        restAfterSave(nextExercise = false)
    }

    fun delete(log: ExerciseLog) {
        // Deleting the log being filled in: start that exercise over, so a later save doesn't bring it back.
        if (log.id == progress.id) { progress = Progress(); savedThrough = -1 }
        viewModelScope.launch {
            repository.delete(log)
            _events.send(LogEvent.Deleted(log))
        }
    }

    fun restore(log: ExerciseLog) {
        viewModelScope.launch { repository.add(log) }
    }

    companion object {
        /** [loggedDayTags] value for an off-plan custom session. */
        const val CUSTOM_TAG = -1
        private val WEIGHT_INPUT = Regex("""\d{0,4}(\.\d{0,2})?""")
        private val COUNT_INPUT = Regex("""\d{1,3}""")

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LockedInApp
                LogViewModel(app.repository, app.planRepository, app.ruleRepository, app.customExercises, app.settings, app.restAlarm)
            }
        }
    }
}
