package work.lockedinlabs.tracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import work.lockedinlabs.tracker.LockedInApp
import work.lockedinlabs.tracker.data.ExerciseLog
import work.lockedinlabs.tracker.data.WorkoutRepository
import work.lockedinlabs.tracker.data.RuleRepository
import work.lockedinlabs.tracker.data.ProgressionRule
import work.lockedinlabs.tracker.data.ruleFor
import work.lockedinlabs.tracker.data.CustomExercise
import work.lockedinlabs.tracker.data.CustomExerciseRepository
import work.lockedinlabs.tracker.data.lookup
import work.lockedinlabs.tracker.domain.Advice
import work.lockedinlabs.tracker.domain.Muscle
import work.lockedinlabs.tracker.domain.MuscleState
import work.lockedinlabs.tracker.domain.MuscleStats
import work.lockedinlabs.tracker.domain.ExerciseSeries
import work.lockedinlabs.tracker.domain.Streak
import work.lockedinlabs.tracker.domain.Progression
import work.lockedinlabs.tracker.domain.StrengthIndex
import work.lockedinlabs.tracker.domain.StrengthPoint
import work.lockedinlabs.tracker.domain.Suggestion
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

enum class ChartRange(val label: String, val days: Int?, val caption: String) {
    WEEK("1W", 7, "past week"),
    MONTH("1M", 30, "past month"),
    QUARTER("3M", 90, "past 3 months"),
    YEAR("1Y", 365, "past year"),
    ALL("All", null, "all time"),
}


data class HomeUiState(
    val loading: Boolean = true,
    val range: ChartRange = ChartRange.MONTH,
    val points: List<StrengthPoint> = emptyList(),
    val projection: List<StrengthPoint> = emptyList(),
    /** Chart x-axis: [chartStart] … today (at 60%) … [chartEnd]. */
    val chartStart: LocalDate = LocalDate.now(),
    val chartEnd: LocalDate = LocalDate.now(),
    val current: Double = 100.0,
    /** Change in index points across the visible range. */
    val change: Double = 0.0,
    val changePct: Double = 0.0,
    val trendPerWeek: Double = 0.0,
    val streakDays: Int = 0,
    /** Exercises ready to step up (per their progression plan), most recently trained first. */
    val readyToStepUp: List<Suggestion> = emptyList(),
    /** Each exercise's own line within the chart window, oldest-started first. */
    val exercises: List<ExerciseSeries> = emptyList(),
    val muscles: Map<Muscle, MuscleState> = emptyMap(),
) {
    val hasData: Boolean get() = points.isNotEmpty()
}

/** Home reads your logs, plus the progression rules that decide what's ready to step up. */
class HomeViewModel(repository: WorkoutRepository, rules: RuleRepository, customs: CustomExerciseRepository) : ViewModel() {
    private val range = MutableStateFlow(ChartRange.MONTH)

    val state: StateFlow<HomeUiState> =
        combine(repository.observeAll(), rules.observeRules(), customs.observeAll(), range) { logs, rs, cx, r ->
            buildHomeState(logs, r, LocalDate.now(), rs, cx)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setRange(r: ChartRange) {
        range.value = r
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LockedInApp
                HomeViewModel(app.repository, app.ruleRepository, app.customExercises)
            }
        }
    }
}

internal fun buildHomeState(
    logs: List<ExerciseLog>,
    range: ChartRange,
    today: LocalDate,
    rules: List<ProgressionRule> = emptyList(),
    customs: List<CustomExercise> = emptyList(),
): HomeUiState {
    if (logs.isEmpty()) return HomeUiState(loading = false, range = range)

    val computed = StrengthIndex.compute(logs, today)
    val series = computed.index
    val perDay = StrengthIndex.trendPerDay(series)
    // Fixed time window: the past fills the left 60% and the projection the right 40%, so today sits at 60%.
    // "All" spans from your first log (at least a week).
    val pastDays = range.days?.toLong() ?: ChronoUnit.DAYS.between(series.first().day, today).coerceAtLeast(7)
    val futureDays = ceil(pastDays * 2 / 3.0).toLong()
    val windowStart = today.minusDays(pastDays)
    val visible = series.filter { !it.day.isBefore(windowStart) }.ifEmpty { series.takeLast(1) }
    val projection = StrengthIndex.project(series, perDay, futureDays.toInt())

    val current = series.last().value
    val start = visible.first().value
    val t = today.toEpochDay()
    val activeSince = today.minusDays(30).toEpochDay()

    val suggestions = logs.groupBy { it.exercise.lowercase() }.values
        .filter { history -> history.any { it.epochDay >= activeSince } }
        .mapNotNull { Progression.suggest(it, rules.ruleFor(it.first().exercise)) }
        // Home only shows what you're ready to step up on.
        .filter { it.advice == Advice.STEP_UP }
        .sortedByDescending { it.last.epochDay }
    val trainedDays = logs.mapTo(HashSet()) { it.epochDay }

    return HomeUiState(
        loading = false,
        range = range,
        points = visible,
        projection = projection,
        chartStart = windowStart,
        chartEnd = today.plusDays(futureDays),
        current = current,
        change = current - start,
        changePct = if (start > 0) (current - start) / start * 100 else 0.0,
        trendPerWeek = perDay * 7,
        streakDays = Streak.days(trainedDays, t),
        readyToStepUp = suggestions,
        exercises = computed.exercises.mapNotNull { e ->
            e.points.filter { !it.day.isBefore(windowStart) }.takeIf { it.isNotEmpty() }?.let { e.copy(points = it) }
        },
        muscles = MuscleStats.compute(logs, today, customs.lookup()),
    )
}
