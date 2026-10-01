package work.lockedinlabs.tracker.domain

import work.lockedinlabs.tracker.data.ExerciseLog
import work.lockedinlabs.tracker.pack.MuscleScience
import work.lockedinlabs.tracker.pack.Pack
import java.time.LocalDate

data class MuscleState(
    /** Weekly sets against the target: 0 = none lately, 1 = target reached. */
    val intensity: Double,
    val daysSinceTrained: Int?,
    /** Hard sets in the last week (assisting work counts as part of a set; older sets fade out). */
    val weeklySets: Double = 0.0,
)

/**
 * Weekly training volume per muscle, for the body map. Every set counts toward its exercise's main muscles, and as part
 * of a set toward the muscles that assist; sets from the last week count fully, then fade out. The numbers (weekly
 * target, assisting share, fade, per-session cap) come from the data pack's training science.
 */
object MuscleStats {
    /** @param lookup finds an exercise's muscles (built-in catalog, plus your custom exercises). */
    fun compute(
        logs: List<ExerciseLog>,
        today: LocalDate,
        lookup: (String) -> CatalogExercise? = ExerciseCatalog::find,
        science: MuscleScience = Pack.science.muscles,
    ): Map<Muscle, MuscleState> {
        val t = today.toEpochDay()
        val lastDay = HashMap<Muscle, Long>()
        /** muscle → day → sets that day */
        val daily = HashMap<Muscle, HashMap<Long, Double>>()
        for (log in logs) {
            val ex = lookup(log.exercise) ?: continue
            val sets = log.sets.size.toDouble()
            fun add(m: Muscle, share: Double) {
                daily.getOrPut(m) { HashMap() }.merge(log.epochDay, sets * share, Double::plus)
                lastDay.merge(m, log.epochDay, ::maxOf)
            }
            ex.primary.forEach { add(it, 1.0) }
            (ex.secondary - ex.primary).forEach { add(it, science.secondaryShare) }
        }
        return Muscle.entries.associateWith { m ->
            val last = lastDay[m] ?: return@associateWith MuscleState(0.0, null)
            val weekly = daily.getValue(m).entries.sumOf { (day, sets) ->
                sets.coerceAtMost(science.maxSetsPerSession) * weight((t - day).coerceAtLeast(0), science)
            }
            MuscleState((weekly / science.weeklyTargetSets).coerceIn(0.0, 1.0), (t - last).coerceAtLeast(0).toInt(), weekly)
        }
    }

    /** 1 for sets in the last [MuscleScience.fullDays], then fading to 0 over [MuscleScience.fadeDays]. */
    private fun weight(age: Long, s: MuscleScience): Double =
        if (age < s.fullDays) 1.0 else (1 - (age - s.fullDays + 1).toDouble() / s.fadeDays).coerceAtLeast(0.0)

    /** Muscles you've trained lately that are well under their weekly target, lowest first. */
    fun neglected(
        states: Map<Muscle, MuscleState>,
        count: Int = 3,
        science: MuscleScience = Pack.science.muscles,
    ): List<Pair<Muscle, MuscleState>> =
        states.entries
            .filter { (_, s) -> s.daysSinceTrained != null && s.daysSinceTrained <= science.attentionWithinDays && s.intensity < science.attentionBelow }
            .sortedBy { it.value.intensity }
            .take(count)
            .map { it.toPair() }
}
