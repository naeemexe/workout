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
import work.lockedinlabs.tracker.data.kinds
import work.lockedinlabs.tracker.data.lookup
import work.lockedinlabs.tracker.data.PlanRepository
import work.lockedinlabs.tracker.data.SettingsStore
import work.lockedinlabs.tracker.data.SetEntry
import work.lockedinlabs.tracker.data.WorkoutRepository
import work.lockedinlabs.tracker.domain.ExerciseCatalog
import work.lockedinlabs.tracker.domain.ExerciseKind
import work.lockedinlabs.tracker.domain.Advice
import work.lockedinlabs.tracker.domain.Progression
import work.lockedinlabs.tracker.domain.Suggestion
import work.lockedinlabs.tracker.domain.Rotation
import work.lockedinlabs.tracker.domain.clock
import work.lockedinlabs.tracker.domain.estimatedMax
import work.lockedinlabs.tracker.domain.weightText
import work.lockedinlabs.tracker.domain.normalizeDecimal
import java.time.LocalDate

/**
 * What's entered for one set. A set "counts" once it has a valid weight and reps, or seconds for a hold, or minutes
 * for cardio (miles are optional).
 */
data class SetInput(
    val weight: String = "",
    val reps: String = "",
    val kind: ExerciseKind = ExerciseKind.WEIGHT,
    val seconds: String = "",
    val minutes: String = "",
    val miles: String = "",
) {
    val entry: SetEntry?
        get() = when (kind) {
            ExerciseKind.WEIGHT -> {
                val w = weight.toDoubleOrNull()?.takeIf { it >= 0.0 }
                val r = reps.toIntOrNull()?.takeIf { it > 0 }
                if (w == null || r == null) null else SetEntry(w, r)
            }
            ExerciseKind.HOLD -> seconds.toIntOrNull()?.takeIf { it > 0 }?.let { SetEntry(0.0, 0, seconds = it) }
            ExerciseKind.CARDIO -> minutes.toIntOrNull()?.takeIf { it > 0 }?.let {
                SetEntry(0.0, 0, minutes = it, miles = miles.toDoubleOrNull() ?: 0.0)
            }
        }
    val isFilled: Boolean get() = entry != null
    val isBlank: Boolean get() = weight.isEmpty() && reps.isEmpty() && seconds.isEmpty() && minutes.isEmpty() && miles.isEmpty()
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
    /** A hold or cardio: the get-ready, then the time itself. Same sounds and background service as the rest. */
    val hold = RestTimer(viewModelScope, alarm)
    /** The timer is on its get-ready; the time itself comes next. */
    var gettingReady by mutableStateOf(false)
        private set
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

    override fun onCleared() {
        rest.skip()
        hold.skip()
    }

    fun startRest() {
        restNote = if (exercise.isNotBlank() && selectedSet > savedThrough) "then set ${selectedSet + 1}" else ""
        rest.start(defaultRestSeconds)
    }

    /**
     * Save starts the rest by itself; it has already moved on to the next set or exercise.
     * @param quiet no start sound, because the end of a timed set has just played its tone.
     */
    private fun restAfterSave(note: String, quiet: Boolean = false) {
        restNote = note
        rest.start(defaultRestSeconds, quiet = quiet)
    }

    /** Length of the selected hold or cardio set. */
    private val timedSeconds: Int
        get() = when (current.kind) {
            ExerciseKind.WEIGHT -> 0
            ExerciseKind.HOLD -> current.seconds.toIntOrNull() ?: 0
            ExerciseKind.CARDIO -> (current.minutes.toIntOrNull() ?: 0) * 60
        }

    /** Get ready, count down, then the set saves itself and (for a hold) the rest starts. */
    fun startTimer() {
        if (!canSave) return
        val seconds = timedSeconds
        rest.skip()
        gettingReady = true
        hold.start(GET_READY_SECONDS, tone = false) {
            gettingReady = false
            hold.start(seconds) { finishTimed(seconds, quietRest = true) }
        }
    }

    /** Seconds on the clock when you tapped Stop, waiting for Save or Discard. */
    private var stoppedSeconds by mutableStateOf<Int?>(null)

    /** The stopped time as the popup shows it ("0:12", "12 min"); null when there's no popup. */
    val stoppedText: String?
        get() = stoppedSeconds?.let { if (current.kind == ExerciseKind.CARDIO) "${timedValue(it)} min" else clock(it) }

    /** Stop: during the get-ready, or with no time to save, it just cancels; otherwise it asks what to do. */
    fun stopHold() {
        val elapsed = if (gettingReady) 0 else hold.elapsedSeconds
        cancelHold()
        if (timedValue(elapsed) > 0) stoppedSeconds = elapsed
    }

    /** Throws the time away: nothing saved, no rest. */
    fun discardStopped() {
        stoppedSeconds = null
    }

    fun saveStopped() {
        val elapsed = stoppedSeconds ?: return
        stoppedSeconds = null
        val index = selectedSet
        val target = current.seconds
        finishTimed(elapsed, quietRest = false)
        // A set cut short doesn't shorten the next one.
        if (selectedSet == index + 1 && current.kind == ExerciseKind.HOLD) sets[selectedSet] = current.copy(seconds = target)
    }

    private fun cancelHold() {
        hold.skip()
        gettingReady = false
    }

    /** What [seconds] saves as: seconds for a hold, the nearest minute for cardio. */
    private fun timedValue(seconds: Int) = if (current.kind == ExerciseKind.CARDIO) (seconds + 30) / 60 else seconds

    private fun finishTimed(seconds: Int, quietRest: Boolean) {
        val value = timedValue(seconds)
        if (value <= 0) return
        sets[selectedSet] = if (current.kind == ExerciseKind.CARDIO) current.copy(minutes = value.toString()) else current.copy(seconds = value.toString())
        save(quietRest)
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
    private var customsLoaded by mutableStateOf(false)
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

    /** Which plan day the selected date is: what a past logged date recorded, your pick, or the next one due. */
    val planDay by derivedStateOf {
        val day = date.toEpochDay()
        val trained = allLogs.mapTo(HashSet()) { it.epochDay }
        val recorded = if (date.isBefore(LocalDate.now()) && day in trained) {
            Rotation.recordedDay(choiceRecords.firstOrNull { it.epochDay == day }, cycle)
        } else null
        recorded ?: Rotation.dayFor(date, cycle, choices, trained)?.let {
            if (it.dayIndex == PlanDay.CUSTOM_CHOICE) PlanDay.custom(dayNames[day].orEmpty()) else it
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
        cancelHold()
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
        matchedExercise?.takeIf { exerciseKind == ExerciseKind.WEIGHT }?.let { name -> Progression.suggest(allLogs.filter { it.exercise.equals(name, ignoreCase = true) }, rules.ruleFor(name)) }
            ?.takeIf { it.advice == Advice.STEP_UP }
    }

    /** A custom exercise you haven't given muscles yet (More → Exercises), so it won't count on the muscle map. */
    val isCustomExercise by derivedStateOf {
        exercise.isNotBlank() && exerciseKind != ExerciseKind.CARDIO && customs.lookup()(exercise) == null
    }

    /** What the selected exercise logs. */
    val exerciseKind: ExerciseKind by derivedStateOf { customs.kinds()(exercise) }

    /** Your own exercise with nothing logged yet: its type can be picked right here. */
    val canPickKind by derivedStateOf {
        exercise.isNotBlank() && ExerciseCatalog.find(exercise) == null && allLogs.none { it.exercise.equals(exercise.trim(), ignoreCase = true) }
    }

    fun pickKind(kind: ExerciseKind) {
        val name = exercise.trim()
        viewModelScope.launch { customExercises.setKind(name, kind) }
    }

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

    val canSave by derivedStateOf { exercise.isNotBlank() && current.isFilled && !hold.running }

    /** The main button runs a timer: a hold or cardio not saved yet, today. Past days and saved sets just save. */
    val startsTimer: Boolean by derivedStateOf {
        current.kind != ExerciseKind.WEIGHT && date == LocalDate.now() && selectedSet > savedThrough
    }

    /** What Save says: why it's disabled, "Save set 2" while working through the sets, or "Save" on the last one. */
    val saveLabel: String by derivedStateOf {
        when {
            exercise.isBlank() -> "Pick an exercise first"
            !current.isFilled -> when (current.kind) {
                ExerciseKind.WEIGHT -> "Enter weight and reps"
                ExerciseKind.HOLD -> "Enter seconds"
                ExerciseKind.CARDIO -> "Enter minutes"
            }
            startsTimer -> if (current.kind == ExerciseKind.CARDIO) "Start ${current.minutes} min" else "Start ${clock(timedSeconds)}"
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
        viewModelScope.launch { customExercises.observeAll().collect { customs = it; customsLoaded = true } }
        // Day → exercise → set: whenever the day changes, start on its next exercise, set 1.
        viewModelScope.launch {
            // A custom session's name is typed on this page, so renaming it mustn't reset the exercise.
            snapshotFlow { Triple(date, planDay?.let { it.dayIndex to if (it.isCustom) "" else it.displayName }, logsLoaded && customsLoaded) }
                .distinctUntilChanged()
                .collect { (_, _, loaded) ->
                    customName = planDay?.takeIf { it.isCustom }?.name.orEmpty()
                    if (!loaded) return@collect
                    selectNextPlanned()
                    // Opened for a specific exercise (e.g. from Home): select it once the day is set up.
                    pendingExercise?.let { selectExercise(it); pendingExercise = null }
                }
        }
        // An exercise's type changed: the form follows it.
        viewModelScope.launch {
            snapshotFlow { exerciseKind }
                .distinctUntilChanged()
                .collect { kind ->
                    if (exercise.isNotBlank() && !searching && sets.any { it.kind != kind }) selectExercise(exercise)
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
    private fun nextPlanned(): String? {
        val done = entriesForDate.map { it.exercise.lowercase() }.toSet()
        return planDay?.takeUnless { it.isRest }?.exercises.orEmpty().firstOrNull { it.lowercase() !in done }
    }

    /** After the last set: on to the next exercise in the row, or stay here at the end of it. True if it moved. */
    private fun moveOnFrom(saved: String): Boolean {
        val done = entriesForDate.map { it.exercise.lowercase() }.toSet() + saved.lowercase()
        val next = Rotation.nextInRow(dayChips.map { it.name }, saved, done)
        if (next != null) selectExercise(next) else savedThrough = sets.lastIndex
        return next != null
    }

    private fun selectNextPlanned() {
        cancelHold()
        searching = false
        val next = nextPlanned()
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
    fun onSeconds(v: Int) { sets[selectedSet] = current.copy(seconds = v.toString()) }
    fun onMinutes(v: Int) { sets[selectedSet] = current.copy(minutes = v.toString()) }
    fun onMilesChange(typed: String) {
        val v = normalizeDecimal(typed)
        if (v.isEmpty() || WEIGHT_INPUT.matches(v)) sets[selectedSet] = current.copy(miles = v)
    }

    /** Tap a set to enter or edit it: a saved one, or the next one to do. */
    fun selectSet(index: Int) {
        if (index in sets.indices && canSelectSet(index) && !hold.running) selectedSet = index
    }

    /**
     * The + after the set bubbles: one more set for today, starting with the last one's weight. Only today's log
     * gets it; next time the plan's count applies again. Selected straight away if every set before it is saved.
     */
    fun addSet() {
        if (hold.running) return
        val last = sets.last()
        if (last.isBlank) return selectSet(sets.lastIndex)
        sets.add(last.nextSet())
        selectSet(sets.lastIndex)
    }

    /** Removes the selected set; a saved one comes out of the day's log right away. */
    fun removeSelectedSet() {
        if (hold.running) return
        val index = selectedSet
        if (sets.size == 1) sets[0] = SetInput(kind = current.kind).withDefaultTime() else sets.removeAt(index)
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
        cancelHold()
        searching = false
        exercise = matchName(name)
        val kind = customs.kinds()(exercise)
        val logged = entriesForDate.filter { it.exercise.equals(exercise, ignoreCase = true) }.sortedBy { it.createdAt }
        val source = if (logged.isNotEmpty()) logged.flatMap { it.sets }
        else allLogs.filter { it.exercise.equals(exercise, ignoreCase = true) }.maxWithOrNull(compareBy({ it.epochDay }, { it.createdAt }))?.sets.orEmpty()
        progress = logged.firstOrNull()?.let { Progress(it.id, it.createdAt, logged.drop(1)) } ?: Progress()
        savedThrough = if (logged.isNotEmpty()) source.lastIndex else -1
        // A set logged today stays as it was; from last time, only what this exercise logs now carries over.
        val inputs = source.map { it.toInput(if (logged.isNotEmpty()) it.kind else kind) }.toMutableList()
        if (kind == ExerciseKind.CARDIO) {
            // One entry a day, whatever the plan says.
            if (logged.isEmpty() && inputs.size > 1) inputs.subList(1, inputs.size).clear()
        } else plannedSets?.let { n ->
            // A new day follows the plan: extra sets from last time are left out, missing ones start with the last weight.
            if (logged.isEmpty()) while (inputs.size > n) inputs.removeAt(inputs.lastIndex)
            while (inputs.size < n) inputs += inputs.lastOrNull()?.nextSet() ?: SetInput(kind = kind)
        }
        if (inputs.isEmpty()) inputs += SetInput(kind = kind)
        resetSets(inputs.map { it.withDefaultTime() })
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

    /** @param quietRest no sound as the rest starts, for a hold that just ended with its tone. */
    fun save(quietRest: Boolean = false) {
        if (!canSave) return
        val name = matchedExercise ?: exercise.trim().replaceFirstChar { it.uppercase() }
        if (savingOneSet) {
            saveSet(name, quietRest)
            return
        }
        val cardio = current.kind == ExerciseKind.CARDIO
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
        val moved = moveOnFrom(name)
        if (!cardio) restAfterSave(if (moved) "then $exercise" else "", quiet = quietRest)
    }

    /** Saves the sets done so far (so nothing is lost) and jumps to the next one, if it isn't saved yet. */
    private fun saveSet(name: String, quietRest: Boolean) {
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
        // Next set starts with this set's weight if it has none yet (usually the same), and the same hold time.
        val justDone = current
        selectSet(savedThrough + 1)
        sets[selectedSet] = current.copy(weight = current.weight.ifEmpty { justDone.weight }, seconds = justDone.seconds.ifEmpty { current.seconds })
        restAfterSave("then set ${selectedSet + 1}", quiet = quietRest)
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

    /** The next set starts from this one: same weight, same hold time. */
    private fun SetInput.nextSet() = SetInput(weight = weight, kind = kind, seconds = seconds, minutes = minutes)

    /** A slider always has a time, so a first-ever hold or cardio starts from these. */
    private fun SetInput.withDefaultTime() = when (kind) {
        ExerciseKind.WEIGHT -> this
        ExerciseKind.HOLD -> copy(seconds = seconds.ifEmpty { DEFAULT_HOLD_SECONDS.toString() })
        ExerciseKind.CARDIO -> copy(minutes = minutes.ifEmpty { DEFAULT_CARDIO_MINUTES.toString() })
    }

    /** A logged set as form values. */
    private fun SetEntry.toInput(kind: ExerciseKind) = SetInput(
        weight = if (kind == ExerciseKind.WEIGHT) weightLbs.weightText() else "",
        reps = if (reps > 0) reps.toString() else "",
        kind = kind,
        seconds = if (seconds > 0) seconds.toString() else "",
        minutes = if (minutes > 0) minutes.toString() else "",
        miles = if (miles > 0.0) miles.weightText() else "",
    )

    companion object {
        private const val GET_READY_SECONDS = 5
        private const val DEFAULT_HOLD_SECONDS = 30
        private const val DEFAULT_CARDIO_MINUTES = 30

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
