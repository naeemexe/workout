package work.lockedinlabs.tracker.domain

import work.lockedinlabs.tracker.data.PlanDay
import java.time.LocalDate

/**
 * Workout-driven rotation through the plan's named days (unnamed days are skipped):
 * - The rotation only moves past a workout day once you actually train (log something on a date).
 *   Skip the gym and tomorrow still shows the workout you missed.
 * - A planned rest day passes on its own after one calendar day.
 * - Picking a day, "Rest", or a Custom session on the Log tab overrides that date. Rest and Custom are off-plan,
 *   so they don't advance the rotation.
 */
object Rotation {
    /**
     * Days in rotation order; if two workout days share a name, only the first counts.
     * Normally only named days are used, so a 4-day plan repeats every 4 days. With [weekly], the plan is a
     * 7-day week and blank days become rest days (4 workouts → then 3 rest days).
     */
    fun cycle(plan: List<PlanDay>, weekly: Boolean = false): List<PlanDay> {
        val seen = HashSet<String>()
        val days = plan.sortedBy { it.dayIndex }
            .map { if (weekly && !it.isActive) PlanDay(it.dayIndex, isRest = true, planId = it.planId) else it }
            .filter { it.isActive }
            .filter { it.isRest || (!it.displayName.equals("rest", ignoreCase = true) && seen.add(it.displayName.lowercase())) }
        // A week of nothing but rest isn't a plan.
        return if (days.none { !it.isRest }) emptyList() else days
    }

    /**
     * @param choices epochDay → plan day index (or [PlanDay.REST_CHOICE]) picked or recorded for that date.
     * @param trainedDays dates with at least one logged exercise.
     */
    fun dayFor(date: LocalDate, cycle: List<PlanDay>, choices: Map<Long, Int>, trainedDays: Set<Long>): PlanDay? {
        if (cycle.isEmpty()) return null
        val d = date.toEpochDay()

        choices[d]?.let { idx ->
            if (idx == PlanDay.REST_CHOICE) return PlanDay.RestChoice
            if (idx == PlanDay.CUSTOM_CHOICE) return PlanDay.custom("")
            cycle.firstOrNull { it.dayIndex == idx }?.let { return it }
        }

        // Continue from the most recent day you actually trained on a plan day.
        val last = choices.entries
            .filter { (day, idx) -> day < d && day in trainedDays && idx >= 0 }
            .maxByOrNull { it.key }
            ?: return cycle.first()
        val lastPos = cycle.indexOfFirst { it.dayIndex == last.value }
        if (lastPos < 0) return cycle.first() // that day was removed from the plan

        var pos = (lastPos + 1) % cycle.size
        var day = last.key + 1
        // Planned rest days each use up one calendar day; workout days wait until you train.
        repeat(cycle.size) {
            if (day >= d || !cycle[pos].isRest) return cycle[pos]
            pos = (pos + 1) % cycle.size
            day++
        }
        return cycle[pos]
    }
}
