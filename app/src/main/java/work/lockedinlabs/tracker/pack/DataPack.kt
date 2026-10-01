package work.lockedinlabs.tracker.pack

import work.lockedinlabs.tracker.domain.CatalogExercise
import work.lockedinlabs.tracker.domain.Rule

/**
 * Everything the app knows about training, kept apart from the logic that uses it: the exercise catalog, ready-made
 * session and progression plans, and the numbers behind the Strength Index, muscle map and streak.
 * Loaded from `assets/pack/` at start-up (see [PackParser] and docs/DATA_PACK.md), so it can change without code changes.
 */
data class DataPack(
    val exercises: List<CatalogExercise>,
    val sessionPlans: List<PresetPlan>,
    val progressionPlans: List<PresetProgression>,
    val science: Science,
)

/** Training experience, from the profile; picks which plans are suggested and how fast a projection may climb. */
enum class Level(val label: String) {
    BEGINNER("Beginner"), INTERMEDIATE("Intermediate"), ADVANCED("Advanced");

    companion object {
        fun of(label: String?): Level? = entries.firstOrNull { it.label.equals(label?.trim(), ignoreCase = true) }
    }
}

/** A ready-made session plan. Days run in order and repeat; rest days pass on their own. */
data class PresetPlan(
    val id: String,
    val name: String,
    /** Who it suits; the first is the main fit. */
    val levels: List<Level>,
    val summary: String,
    val days: List<PresetDay>,
) {
    val workouts: List<PresetDay> get() = days.filterNot { it.isRest }

    /** "Push · Pull · Legs · Rest" */
    val rotation: String get() = days.joinToString(" · ") { if (it.isRest) "Rest" else it.name }

    /** "4 workouts a week", or "3 workouts every 4 days" when the plan isn't a 7 day week. */
    val schedule: String get() {
        val n = workouts.size
        val w = if (n == 1) "workout" else "workouts"
        return if (days.size == 7) "$n $w a week" else "$n $w every ${days.size} days"
    }
}

data class PresetDay(val name: String, val isRest: Boolean, val exercises: List<PresetExercise>)

data class PresetExercise(val name: String, val sets: Int)

/** A ready-made progression plan (see [Rule]). */
data class PresetProgression(
    val id: String,
    val name: String,
    val isDefault: Boolean,
    val summary: String,
    val setReps: List<Int>,
    val increment: Double,
    val startReps: Int,
    val minReps: Int,
    val dropPct: Int,
) {
    fun toRule() = Rule(setReps, increment, startReps, minReps, dropPct)
}

data class Science(val strength: StrengthScience, val muscles: MuscleScience, val streak: StreakScience)

/** Strength Index: detraining, how much a bad session counts, and how the trend and projection are drawn. */
data class StrengthScience(
    /** Days a muscle can go untrained before its exercises start to lose strength. */
    val graceDays: Int,
    /** Share of the level lost per day after the grace period. */
    val dailyLoss: Double,
    /** Detraining never takes an exercise below this share of its level. */
    val floor: Double,
    /** How much a worse-than-usual session pulls an exercise's level down. */
    val dipWeight: Double,
    val trendWindowDays: Int,
    val trendHalfLifeDays: Double,
    /** Fastest the projection may climb or fall per week, as a share of the index, by experience. */
    val maxWeeklyChange: Map<Level, Double>,
    /** Used when the profile has no experience set. */
    val unknownLevel: Level,
) {
    fun maxWeeklyChange(level: Level?): Double = maxWeeklyChange.getValue(level ?: unknownLevel)
}

/** Muscle map: weekly hard sets per muscle against a target. */
data class MuscleScience(
    val weeklyTargetSets: Double,
    /** A muscle that only assists counts as this share of a set. */
    val secondaryShare: Double,
    /** Sets count fully for this many days... */
    val fullDays: Int,
    /** ...then fade out over this many. */
    val fadeDays: Int,
    /** Sets for one muscle in one session count up to this. */
    val maxSetsPerSession: Double,
    /** Under this share of the target (and trained within [attentionWithinDays]), a muscle "needs attention". */
    val attentionBelow: Double,
    val attentionWithinDays: Int,
)

data class StreakScience(val maxRestDays: Int)

/** The loaded pack. Installed once at start-up (LockedInApp), before any screen reads it. */
object Pack {
    @Volatile private var installed: DataPack? = null

    val current: DataPack get() = installed ?: error("Data pack not loaded. Install it at start-up.")
    val science: Science get() = current.science

    fun install(pack: DataPack) { installed = pack }
}
