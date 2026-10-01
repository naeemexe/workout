package work.lockedinlabs.tracker.domain

import work.lockedinlabs.tracker.data.ExerciseLog
import work.lockedinlabs.tracker.data.SetEntry
import java.time.LocalDate
import kotlin.math.pow
import work.lockedinlabs.tracker.pack.Level
import work.lockedinlabs.tracker.pack.Pack
import work.lockedinlabs.tracker.pack.StrengthScience

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
 * Each exercise's level is its estimated 1RM relative to the first time you logged it; the index is the average
 * across exercises, so adding a new exercise doesn't cause a jump. Strength only detrains when a lift's main muscles
 * go untrained for weeks (any exercise that works them counts), at the rate in the data pack's training science.
 */
object StrengthIndex {
    /** The index alone: one point per day from the first log through [today]. */
    fun series(
        logs: List<ExerciseLog>,
        today: LocalDate,
        lookup: (String) -> CatalogExercise? = ExerciseCatalog::find,
        science: StrengthScience = Pack.science.strength,
    ): List<StrengthPoint> = compute(logs, today, lookup, science).index

    /**
     * The index plus each exercise's own line (same 100-based scale, detraining included); the index is the average
     * of the exercise lines.
     * @param lookup an exercise's muscles (catalog plus your custom exercises); unknown ones detrain on their own dates.
     */
    fun compute(
        logs: List<ExerciseLog>,
        today: LocalDate,
        lookup: (String) -> CatalogExercise? = ExerciseCatalog::find,
        science: StrengthScience = Pack.science.strength,
    ): IndexSeries {
        if (logs.isEmpty()) return IndexSeries(emptyList(), emptyList())
        val byDay = logs.groupBy { it.epochDay }
        val first = byDay.keys.min()
        val last = maxOf(today.toEpochDay(), byDay.keys.max())

        val baseline = HashMap<String, Double>()
        val level = LinkedHashMap<String, Double>() // insertion order = order exercises were started
        val names = HashMap<String, String>()
        val lines = HashMap<String, MutableList<StrengthPoint>>()
        /** The muscles that drive each exercise (null = not in the catalog: it only counts its own sessions). */
        val drivers = HashMap<String, Set<Muscle>?>()
        val lastDone = HashMap<String, Long>()
        /** Last day each muscle did any work (main or assisting). */
        val muscleTrained = HashMap<Muscle, Long>()
        val out = ArrayList<StrengthPoint>((last - first + 1).toInt())

        /** Share of its level an exercise keeps on [day]: 1 until every driver muscle has rested past the grace period. */
        fun kept(key: String, day: Long): Double {
            val muscles = drivers[key]
            val stimulus = if (muscles.isNullOrEmpty()) lastDone.getValue(key)
            else muscles.minOf { muscleTrained[it] ?: lastDone.getValue(key) }
            val idle = (day - stimulus - science.graceDays).coerceAtLeast(0)
            return (1 - science.dailyLoss).pow(idle.toDouble()).coerceAtLeast(science.floor)
        }

        for (day in first..last) {
            byDay[day]?.let { dayLogs ->
                dayLogs.groupBy { it.exercise.lowercase() }.forEach { (key, entries) ->
                    val best = entries.maxOf { it.estimatedMax() }
                    if (best <= 0.0) return@forEach
                    baseline.getOrPut(key) { best }
                    names[key] = entries.first().exercise
                    drivers.getOrPut(key) { lookup(entries.first().exercise)?.primary }
                    // Compare against what you'd kept after any time off, then a weaker session only pulls it part way down.
                    val prev = level[key]?.let { it * kept(key, day) }
                    level[key] = if (prev == null || best >= prev) best else prev * (1 - science.dipWeight) + best * science.dipWeight
                    lastDone[key] = day
                }
                dayLogs.forEach { log ->
                    lookup(log.exercise)?.let { e -> (e.primary + e.secondary).forEach { muscleTrained[it] = day } }
                }
            }
            if (level.isEmpty()) continue
            val date = LocalDate.ofEpochDay(day)
            val ratios = level.entries.map { (key, v) -> key to v * kept(key, day) / baseline.getValue(key) * 100 }
            ratios.forEach { (key, v) -> lines.getOrPut(key) { ArrayList() } += StrengthPoint(date, v) }
            out += StrengthPoint(date, ratios.map { it.second }.average())
        }
        return IndexSeries(out, level.keys.map { ExerciseSeries(names.getValue(it), lines.getValue(it)) })
    }

    /** Recency-weighted least-squares slope over the last few weeks, in index points per day. */
    fun trendPerDay(points: List<StrengthPoint>, science: StrengthScience = Pack.science.strength): Double {
        val window = points.takeLast(science.trendWindowDays)
        val n = window.size
        if (n < 2) return 0.0
        var sw = 0.0; var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
        window.forEachIndexed { i, p ->
            val w = 0.5.pow((n - 1 - i) / science.trendHalfLifeDays)
            val x = i.toDouble()
            sw += w; sx += w * x; sy += w * p.value; sxx += w * x * x; sxy += w * x * p.value
        }
        val denom = sw * sxx - sx * sx
        return if (denom == 0.0) 0.0 else (sw * sxy - sx * sy) / denom
    }

    /**
     * The trend, held to what's realistic for your experience: a few good weeks don't promise the same pace for months.
     * @param current the index today.
     */
    fun realisticTrend(perDay: Double, current: Double, level: Level?, science: StrengthScience = Pack.science.strength): Double {
        val max = current * science.maxWeeklyChange(level) / 7
        return perDay.coerceIn(-max, max)
    }

    /** Straight-line projection starting at the last point (included as element 0). */
    fun project(points: List<StrengthPoint>, perDay: Double, days: Int): List<StrengthPoint> {
        val last = points.lastOrNull() ?: return emptyList()
        return (0..days).map { i ->
            StrengthPoint(last.day.plusDays(i.toLong()), (last.value + perDay * i).coerceAtLeast(0.0))
        }
    }
}
