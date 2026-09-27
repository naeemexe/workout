package work.lockedinlabs.tracker.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import work.lockedinlabs.tracker.sync.ChangeTracker

/** Progression rules. They back up together with your plans. */
class RuleRepository(private val dao: ProgressionRuleDao, private val changes: ChangeTracker, scope: CoroutineScope) {
    private val rules = dao.observeAll().onStart { ensureDefault() }.sharedIn(scope)

    /** All rules, default first; creates the starter default the first time. */
    fun observeRules(): Flow<List<ProgressionRule>> = rules

    // Several screens start observing at once; only one of them may create the starter rule.
    private val seeding = Mutex()

    private suspend fun ensureDefault() = seeding.withLock {
        if (dao.getAll().isEmpty()) dao.insert(ProgressionRule.starterDefault())
    }

    /** A new rule named [name], as saved (with its id). */
    suspend fun create(name: String): ProgressionRule {
        val rule = ProgressionRule(name = name)
        val id = dao.insert(rule)
        changes.planChanged()
        return rule.copy(id = id)
    }

    /**
     * Saves [rule]. An exercise follows one rule only, so exercises it gained are removed from the others.
     */
    suspend fun save(rule: ProgressionRule) {
        val keys = rule.exercises.map { it.lowercase() }.toSet()
        val all = dao.getAll()
        all.filter { it.id != rule.id && it.exercises.any { e -> e.lowercase() in keys } }.forEach { other ->
            dao.upsert(other.copy(exercises = other.exercises.filter { it.lowercase() !in keys }))
        }
        // Which rule is default is only changed by makeDefault, never by an edit that raced it.
        dao.upsert(rule.copy(isDefault = all.firstOrNull { it.id == rule.id }?.isDefault ?: rule.isDefault))
        changes.planChanged()
    }

    suspend fun makeDefault(id: Long) {
        dao.makeDefault(id)
        changes.planChanged()
    }

    /** Its exercises go back to the default rule. The default itself can't be deleted. */
    suspend fun delete(id: Long) {
        if (dao.getAll().firstOrNull { it.id == id }?.isDefault == true) return
        dao.delete(id)
        changes.planChanged()
    }
}
