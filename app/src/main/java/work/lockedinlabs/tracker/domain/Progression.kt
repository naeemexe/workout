package work.lockedinlabs.tracker.domain

import work.lockedinlabs.tracker.data.ExerciseLog
import work.lockedinlabs.tracker.data.SetEntry
import kotlin.math.roundToInt

enum class Advice { STEP_UP, ADD_REPS, HOLD, DELOAD }

data class Suggestion(
    val exercise: String,
    /** Best entry from the most recent session of this exercise. */
    val last: ExerciseLog,
    val advice: Advice,
    val nextWeight: Double,
    val targetReps: Int,
    /** Set-by-set plan for next session; used to pre-fill the log form. */
    val plan: List<SetEntry>,
)

/** Heaviest weight used in the entry; sets at this weight are the "work sets" (lighter ones are warm-ups). */
fun ExerciseLog.topWeight(): Double = sets.maxOfOrNull { it.weightLbs } ?: 0.0

/**
 * How an exercise progresses (set up on the Plan tab). Double progression: stay at a weight and add reps until
 * every work set hits its target in [setReps], then add weight and restart at [startReps].
 * Two sessions in a row with a set under [minReps] → back off by [dropPct]%.
 */
data class Rule(
    /**
     * Target reps for your first work sets, in order (set 1 → 10, set 2 → 8). Sets after these are only logged:
     * they never help or hurt a step up or step down.
     */
    val setReps: List<Int> = listOf(10, 10),
    /** Pounds to add on a step up; [AUTO_INCREMENT] = 2.5 lb under 30 lb, otherwise 5 lb. */
    val increment: Double = AUTO_INCREMENT,
    val startReps: Int = 8,
    val minReps: Int = 6,
    val dropPct: Int = 10,
) {
    fun target(set: Int): Int = setReps.getOrElse(set) { setReps.lastOrNull() ?: 10 }

    fun stepFor(weight: Double): Double = if (increment > 0) increment else if (weight < 30.0) 2.5 else 5.0

    companion object {
        const val AUTO_INCREMENT = 0.0
        val Default = Rule()
    }
}

object Progression {
    fun roundToPlate(weight: Double): Double = (weight / 2.5).roundToInt() * 2.5

    /** @param history every log for a single exercise, any order. */
    fun suggest(history: List<ExerciseLog>, rule: Rule = Rule.Default): Suggestion? {
        val sessions = history
            .filter { it.sets.isNotEmpty() }
            .groupBy { it.epochDay }
            .toSortedMap(reverseOrder())
            .values
            .map { day -> day.maxBy { it.estimatedMax() } }
        val last = sessions.firstOrNull() ?: return null
        val prev = sessions.getOrNull(1)
        val top = last.topWeight()
        val needed = rule.setReps
        // Work sets (at the top weight; lighter ones are warm-ups) in the order you did them. Only the first
        // [needed.size] count; any after that are just logged.
        val countedIdx = last.sets.indices.filter { last.sets[it].weightLbs == top }.take(needed.size)
        val counted = countedIdx.map { last.sets[it] }
        /** Set 1 against target 1, set 2 against target 2, … */
        fun meets(sets: List<SetEntry>) = sets.withIndex().all { (i, s) -> s.reps >= needed[i] }
        val weakest = counted.minOf { it.reps }
        val prevWeakest = prev?.takeIf { it.topWeight() == top }?.sets?.filter { it.weightLbs == top }?.take(needed.size)?.minOf { it.reps }
        val allHit = counted.size == needed.size && meets(counted)
        // Every set so far is on target but there aren't enough yet: add the next one.
        val setsShort = counted.size < needed.size && meets(counted)

        fun make(advice: Advice, weight: Double, reps: Int, plan: List<SetEntry>) =
            Suggestion(last.exercise, last, advice, weight, reps, plan)

        /** Keep warm-ups, move every work set to [weight] × [reps]. */
        fun reset(weight: Double) = last.sets.map { if (it.weightLbs == top) SetEntry(weight, rule.startReps) else it }

        /** The first counted set short of its target (else the weakest): the one to beat next time. */
        val focus = countedIdx.withIndex().firstOrNull { (k, i) -> last.sets[i].reps < needed[k] }?.value
            ?: countedIdx.minBy { last.sets[it].reps }
        val focusReps = last.sets[focus].reps + 1

        /** Same sets as last time, with that set bumped by one rep. */
        fun beatWeakest(): List<SetEntry> = last.sets.mapIndexed { j, s -> if (j == focus) s.copy(reps = s.reps + 1) else s }

        return when {
            top <= 0.0 -> make(Advice.ADD_REPS, 0.0, focusReps, beatWeakest())
            allHit -> roundToPlate(top + rule.stepFor(top)).let { make(Advice.STEP_UP, it, rule.startReps, reset(it)) }
            // Targets met but not enough sets yet: add the next one.
            setsShort -> rule.target(counted.size).let { make(Advice.ADD_REPS, top, it, last.sets + SetEntry(top, it)) }
            weakest < rule.minReps && prevWeakest != null && prevWeakest < rule.minReps ->
                roundToPlate(top * (1 - rule.dropPct / 100.0)).let { make(Advice.DELOAD, it, rule.startReps, reset(it)) }
            weakest < rule.minReps -> make(Advice.HOLD, top, rule.minReps, last.sets)
            else -> make(Advice.ADD_REPS, top, focusReps, beatWeakest())
        }
    }
}

fun Suggestion.headline(): String = when (advice) {
    Advice.STEP_UP -> "Ready to step up: ${last.topWeight().lb()} → ${nextWeight.lb()}"
    Advice.ADD_REPS -> if (nextWeight <= 0.0) "Push for $targetReps reps" else "Stay at ${nextWeight.lb()}, push for $targetReps reps"
    Advice.HOLD -> "Stay at ${nextWeight.lb()}, build to $targetReps+ reps"
    Advice.DELOAD -> "Back off to ${nextWeight.lb()} and rebuild"
}
