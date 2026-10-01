package work.lockedinlabs.tracker.pack

import android.content.res.AssetManager
import org.json.JSONArray
import org.json.JSONObject
import work.lockedinlabs.tracker.data.PlanDay
import work.lockedinlabs.tracker.domain.CatalogExercise
import work.lockedinlabs.tracker.domain.ExerciseCatalog
import work.lockedinlabs.tracker.domain.Muscle

/**
 * Reads the data pack's JSON files and checks they fit together (muscle ids exist, plan exercises are in the catalog,
 * day names are unique, ...). A broken pack fails loudly here, and the unit tests run this on the real files.
 */
object PackParser {
    const val DIR = "pack"
    const val EXERCISES = "exercises.json"
    const val SESSION_PLANS = "session-plans.json"
    const val PROGRESSION_PLANS = "progression-plans.json"
    const val SCIENCE = "training-science.json"

    fun fromAssets(assets: AssetManager): DataPack =
        parse { name -> assets.open("$DIR/$name").bufferedReader().use { it.readText() } }

    /** @param read returns a pack file's text by name. */
    fun parse(read: (String) -> String): DataPack {
        val exercises = exercises(JSONObject(read(EXERCISES)))
        val known = HashMap<String, String>()
        exercises.forEach { e -> (listOf(e.name) + e.aliases).forEach { known.putIfAbsent(ExerciseCatalog.key(it), e.name) } }
        return DataPack(
            exercises = exercises,
            sessionPlans = sessionPlans(JSONObject(read(SESSION_PLANS)), known),
            progressionPlans = progressionPlans(JSONObject(read(PROGRESSION_PLANS))),
            science = science(JSONObject(read(SCIENCE))),
        )
    }

    private fun exercises(root: JSONObject): List<CatalogExercise> {
        val list = root.getJSONArray("exercises").objects().map { o ->
            val name = o.getString("name")
            fun muscles(field: String) = o.optJSONArray(field).strings().map { id ->
                Muscle.entries.firstOrNull { it.name == id } ?: fail("$EXERCISES: \"$name\" has unknown muscle \"$id\"")
            }.toSet()
            val primary = muscles("primary")
            check(primary.isNotEmpty()) { "$EXERCISES: \"$name\" needs at least one primary muscle" }
            CatalogExercise(name, primary, muscles("secondary") - primary, o.optJSONArray("aliases").strings(), o.optString("group"))
        }
        val clashes = list.flatMap { e -> (listOf(e.name) + e.aliases).map { ExerciseCatalog.key(it) to e.name } }
            .groupBy({ it.first }, { it.second }).filterValues { it.distinct().size > 1 }
        check(clashes.isEmpty()) { "$EXERCISES: names or aliases clash: $clashes" }
        return list
    }

    private fun sessionPlans(root: JSONObject, known: Map<String, String>): List<PresetPlan> =
        root.getJSONArray("plans").objects().map { o ->
            val name = o.getString("name")
            val where = "$SESSION_PLANS: \"$name\""
            val days = o.getJSONArray("days").objects().map { d ->
                if (d.optBoolean("rest")) PresetDay("", isRest = true, exercises = emptyList())
                else {
                    val dayName = d.getString("name")
                    PresetDay(dayName, isRest = false, exercises = d.getJSONArray("exercises").objects().map { e ->
                        val ex = e.getString("name")
                        val sets = e.optInt("sets", PlanDay.DEFAULT_SETS)
                        check(sets in 1..10) { "$where, $dayName: $ex has $sets sets (1 to 10)" }
                        // Catalog spelling, so the plan, your logs and the muscle map line up.
                        PresetExercise(known[ExerciseCatalog.key(ex)] ?: fail("$where, $dayName: \"$ex\" isn't in $EXERCISES"), sets)
                    })
                }
            }
            check(days.size in 1..PlanDay.DAYS) { "$where has ${days.size} days (1 to ${PlanDay.DAYS})" }
            val names = days.filterNot { it.isRest }.map { it.name.trim().lowercase() }
            check(names.isNotEmpty()) { "$where has no workout days" }
            check(names.toSet().size == names.size) { "$where repeats a day name" }
            check("rest" !in names) { "$where: \"Rest\" is reserved, use {\"rest\": true}" }
            val levels = o.getJSONArray("levels").strings().map { Level.of(it) ?: fail("$where: unknown level \"$it\"") }
            check(levels.isNotEmpty()) { "$where needs a level" }
            PresetPlan(o.getString("id"), name, levels, o.optString("summary"), days)
        }.also { plans -> check(plans.map { it.id }.toSet().size == plans.size) { "$SESSION_PLANS: plan ids must be unique" } }

    private fun progressionPlans(root: JSONObject): List<PresetProgression> =
        root.getJSONArray("plans").objects().map { o ->
            val name = o.getString("name")
            val reps = o.getJSONArray("setReps").ints()
            check(reps.isNotEmpty() && reps.all { it in 1..99 }) { "$PROGRESSION_PLANS: \"$name\" needs set reps of 1 to 99" }
            PresetProgression(
                id = o.getString("id"),
                name = name,
                isDefault = o.optBoolean("default"),
                summary = o.optString("summary"),
                setReps = reps,
                increment = o.optDouble("increment", 0.0),
                startReps = o.getInt("startReps"),
                minReps = o.getInt("minReps"),
                dropPct = o.getInt("dropPct"),
            )
        }.also { plans -> check(plans.count { it.isDefault } == 1) { "$PROGRESSION_PLANS: exactly one plan must be the default" } }

    private fun science(root: JSONObject): Science {
        val s = root.getJSONObject("strength")
        val change = s.getJSONObject("projectionMaxWeeklyChange")
        val m = root.getJSONObject("muscles")
        return Science(
            strength = StrengthScience(
                graceDays = s.getInt("detrainingGraceDays"),
                dailyLoss = s.getDouble("detrainingDailyLoss"),
                floor = s.getDouble("detrainingFloor"),
                dipWeight = s.getDouble("dipWeight"),
                trendWindowDays = s.getInt("trendWindowDays"),
                trendHalfLifeDays = s.getDouble("trendHalfLifeDays"),
                maxWeeklyChange = Level.entries.associateWith { change.getDouble(it.label) },
                unknownLevel = Level.of(change.getString("unknown")) ?: fail("$SCIENCE: unknown level for \"unknown\""),
            ),
            muscles = MuscleScience(
                weeklyTargetSets = m.getDouble("weeklyTargetSets"),
                secondaryShare = m.getDouble("secondaryShare"),
                fullDays = m.getInt("fullDays"),
                fadeDays = m.getInt("fadeDays"),
                maxSetsPerSession = m.getDouble("maxSetsPerSession"),
                attentionBelow = m.getDouble("attentionBelow"),
                attentionWithinDays = m.getInt("attentionWithinDays"),
            ),
            streak = StreakScience(root.getJSONObject("streak").getInt("maxRestDays")),
        )
    }

    private fun fail(message: String): Nothing = throw IllegalStateException(message)
    private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
    private fun JSONArray?.strings() = if (this == null) emptyList() else List(length()) { getString(it) }
    private fun JSONArray.ints() = List(length()) { getInt(it) }
}
