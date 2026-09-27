package work.lockedinlabs.tracker.domain

import work.lockedinlabs.tracker.data.ExerciseLog
import work.lockedinlabs.tracker.data.SetEntry
import java.time.LocalDate
import kotlin.math.pow

data class StrengthPoint(val day: LocalDate, val value: Double)

/** One exercise's progress on the index scale (100 = the first time you logged it). */
data class ExerciseSeries(val name: String, val points: List<StrengthPoint>)

data class IndexSeries(val index: List<StrengthPoint>, val exercises: List<ExerciseSeries>)

/** Estimated one-rep max (Epley). Bodyweight moves (0 lb) fall back to reps so they still trend. */
fun SetEntry.estimatedMax(): Double =
    if (weightLbs <= 0.0) reps.toDouble() else weightLbs * (1 + reps / 30.0)

/** Best set of the entry. */
fun ExerciseLog.estimatedMax(): Double = sets.maxOfOrNull { it.estimatedMax() } ?: 0.0

/**
 * A stock-style "Strength Index" that starts at 100.
 *
 * Each exercise's level is its estimated 1RM relative to the first time you logged it; the index is the
 * average across exercises, so adding a new exercise doesn't cause a jump. Missing workouts beyond a short
 * grace period decays the index a little each day, which is what makes the line (and projection) tip down.
 */
object StrengthIndex {
    const val GRACE_DAYS = 2
    const val DAILY_DECAY = 0.008
    const val MIN_DECAY_FACTOR = 0.75

    /** How much a worse-than-usual session pulls an exercise's level down (one off day shouldn't crater it). */
    private const val DIP_WEIGHT = 0.3

    private const val TREND_WINDOW_DAYS = 28
    private const val TREND_HALF_LIFE_DAYS = 7.0

    /** The index alone: one point per day from the first log through [today]. */
    fun series(logs: List<ExerciseLog>, today: LocalDate): List<StrengthPoint> = compute(logs, today).index

    /**
     * The index plus each exercise's own line (same 100-based scale, no idle decay). The index is the
     * average of the exercise lines, times the idle decay.
     */
    fun compute(logs: List<ExerciseLog>, today: LocalDate): IndexSeries {
        if (logs.isEmpty()) return IndexSeries(emptyList(), emptyList())
        val byDay = logs.groupBy { it.epochDay }
        val first = byDay.keys.min()
        val last = maxOf(today.toEpochDay(), byDay.keys.max())

        val baseline = HashMap<String, Double>()
        val level = LinkedHashMap<String, Double>() // insertion order = order exercises were started
        val names = HashMap<String, String>()
        val lines = HashMap<String, MutableList<StrengthPoint>>()
        var lastWorkout = first
        val out = ArrayList<StrengthPoint>((last - first + 1).toInt())

        for (day in first..last) {
            byDay[day]?.let { dayLogs ->
                lastWorkout = day
                dayLogs.groupBy { it.exercise.lowercase() }.forEach { (key, entries) ->
                    val best = entries.maxOf { it.estimatedMax() }
                    if (best <= 0.0) return@forEach
                    baseline.getOrPut(key) { best }
                    names[key] = entries.first().exercise
                    val prev = level[key]
                    level[key] = if (prev == null || best >= prev) best else prev * (1 - DIP_WEIGHT) + best * DIP_WEIGHT
                }
            }
            if (level.isEmpty()) continue
            val date = LocalDate.ofEpochDay(day)
            val ratios = level.entries.map { (key, v) -> key to v / baseline.getValue(key) * 100 }
            ratios.forEach { (key, v) -> lines.getOrPut(key) { ArrayList() } += StrengthPoint(date, v) }
            val idleDays = (day - lastWorkout - GRACE_DAYS).coerceAtLeast(0).toInt()
            val decay = (1 - DAILY_DECAY).pow(idleDays).coerceAtLeast(MIN_DECAY_FACTOR)
            out += StrengthPoint(date, ratios.map { it.second }.average() * decay)
        }
        return IndexSeries(out, level.keys.map { ExerciseSeries(names.getValue(it), lines.getValue(it)) })
    }

    /** Recency-weighted least-squares slope over the last few weeks, in index points per day. */
    fun trendPerDay(points: List<StrengthPoint>): Double {
        val window = points.takeLast(TREND_WINDOW_DAYS)
        val n = window.size
        if (n < 2) return 0.0
        var sw = 0.0; var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
        window.forEachIndexed { i, p ->
            val w = 0.5.pow((n - 1 - i) / TREND_HALF_LIFE_DAYS)
            val x = i.toDouble()
            sw += w; sx += w * x; sy += w * p.value; sxx += w * x * x; sxy += w * x * p.value
        }
        val denom = sw * sxx - sx * sx
        return if (denom == 0.0) 0.0 else (sw * sxy - sx * sy) / denom
    }

    /** Straight-line projection starting at the last point (included as element 0). */
    fun project(points: List<StrengthPoint>, perDay: Double, days: Int): List<StrengthPoint> {
        val last = points.lastOrNull() ?: return emptyList()
        return (0..days).map { i ->
            StrengthPoint(last.day.plusDays(i.toLong()), (last.value + perDay * i).coerceAtLeast(0.0))
        }
    }
}
