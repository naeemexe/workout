package work.lockedinlabs.tracker.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import work.lockedinlabs.tracker.sync.ChangeTracker
import java.time.LocalDate

/** A plan with its 7 day slots (unsaved slots filled with empty ones). */
data class PlanWithDays(val plan: Plan, val days: List<PlanDay>)

class PlanRepository(private val dao: PlanDao, private val changes: ChangeTracker, scope: CoroutineScope) {
    private val plans = combine(dao.observePlans(), dao.observeAllDays()) { plans, days ->
        val byPlan = days.groupBy { it.planId }
        plans.map { p -> PlanWithDays(p, fillWeek(p.id, byPlan[p.id].orEmpty())) }
    }.sharedIn(scope)
    private val choices = dao.observeChoices().sharedIn(scope)

    /** Every plan, oldest first. */
    fun observePlans(): Flow<List<PlanWithDays>> = plans

    private val active: Flow<PlanWithDays?> = plans.map { list -> list.firstOrNull { it.plan.isActive } }

    /** The active plan's 7 days (all empty when no plan is active — the Log tab then works without a plan). */
    fun observePlan(): Flow<List<PlanDay>> = active.map { it?.days ?: fillWeek(0, emptyList()) }

    suspend fun savePlan(planId: Long, days: List<PlanDay>) {
        dao.upsertAll(days.map { it.copy(planId = planId) })
        changes.planChanged()
    }

    /** New empty plan; it becomes active when no other plan is. Returns its id. */
    suspend fun createPlan(name: String, activate: Boolean): Long {
        val id = dao.insertPlan(Plan(name = name))
        if (activate) dao.activate(id, LocalDate.now().toEpochDay())
        changes.planChanged()
        return id
    }

    /** Whether the active plan uses a weekly rotation (blank days = rest). */
    fun observeWeekly(): Flow<Boolean> = active.map { it?.plan?.weekly ?: false }

    suspend fun setWeekly(id: Long, weekly: Boolean) {
        dao.setWeekly(id, weekly)
        changes.planChanged()
    }

    suspend fun renamePlan(id: Long, name: String) {
        dao.renamePlan(id, name)
        changes.planChanged()
    }

    /** Switch plans; the new plan's rotation starts fresh from its first day. */
    suspend fun activate(id: Long) {
        dao.activate(id, LocalDate.now().toEpochDay())
        changes.planChanged()
    }

    suspend fun deletePlan(id: Long) {
        dao.deletePlan(id)
        changes.planChanged()
    }

    /**
     * epochDay → chosen plan day index (or [PlanDay.REST_CHOICE]) — only since the active plan was switched on,
     * since earlier choices point at another plan's days.
     */
    fun observeChoices(): Flow<Map<Long, Int>> = combine(choices, active) { list, plan ->
        val since = plan?.plan?.activeSince ?: Long.MAX_VALUE
        list.filter { it.epochDay >= since }.associate { it.epochDay to it.dayIndex }
    }

    /** Every recorded day choice, from any plan. */
    fun observeChoiceRecords(): Flow<List<DayChoice>> = choices

    /** epochDay → name of the plan day done on that date. */
    fun observeDayNames(): Flow<Map<Long, String>> =
        choices.map { list -> list.associate { it.epochDay to it.name } }

    /** Explicit pick on the Log tab; replaces any earlier pick for that date. */
    suspend fun choose(date: LocalDate, day: PlanDay) {
        dao.upsertChoice(DayChoice(date.toEpochDay(), day.dayIndex, day.displayName))
        changes.dayChanged(date.toEpochDay())
    }

    /** Remember which plan day you trained on, unless you already picked one for that date. */
    suspend fun recordIfAbsent(date: LocalDate, day: PlanDay) {
        dao.insertChoiceIfAbsent(DayChoice(date.toEpochDay(), day.dayIndex, day.displayName))
        changes.dayChanged(date.toEpochDay())
    }

    companion object {
        fun fillWeek(planId: Long, saved: List<PlanDay>): List<PlanDay> =
            List(PlanDay.DAYS) { i -> saved.firstOrNull { it.dayIndex == i } ?: PlanDay(i, planId = planId) }
    }
}
