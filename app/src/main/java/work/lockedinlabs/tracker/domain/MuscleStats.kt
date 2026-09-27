package work.lockedinlabs.tracker.domain

import work.lockedinlabs.tracker.data.ExerciseLog
import java.time.LocalDate
import kotlin.math.pow

data class MuscleState(
    /** 0 = untouched / long forgotten, 1 = trained recently and consistently. */
    val intensity: Double,
    val daysSinceTrained: Int?,
)

/**
 * How "fresh" each muscle is, for the body map. Only catalog exercises count (custom names are ignored).
 *
 * intensity = 60% recency (halves every [RECENCY_HALF_LIFE] days since the muscle was last trained)
 *           + 40% consistency (recency-weighted amount of work over the last few weeks, capped at 1).
 * So a muscle you just trained lights up right away, one you train every week stays full,
 * and one you skip fades toward gray over a couple of weeks.
 */
object MuscleStats {
    const val RECENCY_HALF_LIFE = 5.0
    private const val WORK_HALF_LIFE = 7.0
    /** Weighted work that counts as "fully consistent" — about 2 solid sessions a week. */
    private const val CONSISTENT_WORK = 2.5
    private const val SECONDARY_SHARE = 0.5
    private const val SETS_PER_UNIT = 3.0

    /** @param lookup finds an exercise's muscles (built-in catalog, plus your custom exercises). */
    fun compute(
        logs: List<ExerciseLog>,
        today: LocalDate,
        lookup: (String) -> CatalogExercise? = ExerciseCatalog::find,
    ): Map<Muscle, MuscleState> {
        val t = today.toEpochDay()
        val lastDay = HashMap<Muscle, Long>()
        val work = HashMap<Muscle, Double>()
        for (log in logs) {
            val ex = lookup(log.exercise) ?: continue
            val age = (t - log.epochDay).coerceAtLeast(0)
            val amount = (log.sets.size.coerceAtMost(6) / SETS_PER_UNIT) * 0.5.pow(age / WORK_HALF_LIFE)
            fun add(m: Muscle, share: Double) {
                work.merge(m, amount * share, Double::plus)
                lastDay.merge(m, log.epochDay, ::maxOf)
            }
            ex.primary.forEach { add(it, 1.0) }
            (ex.secondary - ex.primary).forEach { add(it, SECONDARY_SHARE) }
        }
        return Muscle.entries.associateWith { m ->
            val last = lastDay[m] ?: return@associateWith MuscleState(0.0, null)
            val days = (t - last).coerceAtLeast(0).toInt()
            val recency = 0.5.pow(days / RECENCY_HALF_LIFE)
            val consistency = ((work[m] ?: 0.0) / CONSISTENT_WORK).coerceAtMost(1.0)
            MuscleState((0.6 * recency + 0.4 * consistency).coerceIn(0.0, 1.0), days)
        }
    }

    /** Trained-before muscles that are fading the most (never-trained ones are left out — they may not be in your routine). */
    fun neglected(states: Map<Muscle, MuscleState>, count: Int = 3): List<Pair<Muscle, MuscleState>> {
        return states.entries
            .filter { it.value.daysSinceTrained != null && it.value.intensity < 0.6 }
            .sortedBy { it.value.intensity }
            .take(count)
            .map { it.toPair() }
    }
}
