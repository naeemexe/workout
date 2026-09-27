package work.lockedinlabs.tracker.domain

import work.lockedinlabs.tracker.domain.Muscle.*

enum class Muscle(val label: String) {
    CHEST("Chest"),
    SHOULDERS("Shoulders"),
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    FOREARMS("Forearms"),
    ABS("Abs"),
    OBLIQUES("Obliques"),
    TRAPS("Traps"),
    UPPER_BACK("Upper back"),
    LATS("Lats"),
    LOWER_BACK("Lower back"),
    GLUTES("Glutes"),
    QUADS("Quads"),
    HAMSTRINGS("Hamstrings"),
    CALVES("Calves"),
}

/** A known exercise: [primary] muscles do most of the work, [secondary] ones assist. */
data class CatalogExercise(
    val name: String,
    val primary: Set<Muscle>,
    val secondary: Set<Muscle> = emptySet(),
    val aliases: List<String> = emptyList(),
)

/**
 * Built-in exercises that count toward the muscle map. Names typed by the user are matched loosely
 * (case, spaces, hyphens, plural "s" and common aliases don't matter). Anything not found is a custom
 * exercise and doesn't affect the map (a later version will let users set muscles for their own).
 */
object ExerciseCatalog {
    private fun ex(name: String, primary: Set<Muscle>, secondary: Set<Muscle> = emptySet(), vararg aliases: String) =
        CatalogExercise(name, primary, secondary, aliases.toList())

    val all: List<CatalogExercise> = listOf(
        // Chest
        ex("Bench Press", setOf(CHEST), setOf(TRICEPS, SHOULDERS), "Bench", "Flat Bench", "Barbell Bench Press"),
        ex("Incline Bench Press", setOf(CHEST, SHOULDERS), setOf(TRICEPS), "Incline Bench"),
        ex("Decline Bench Press", setOf(CHEST), setOf(TRICEPS)),
        ex("Dumbbell Bench Press", setOf(CHEST), setOf(TRICEPS, SHOULDERS), "DB Bench", "Dumbbell Press"),
        ex("Incline Dumbbell Press", setOf(CHEST, SHOULDERS), setOf(TRICEPS), "Incline DB Press"),
        ex("Machine Chest Press", setOf(CHEST), setOf(TRICEPS, SHOULDERS), "Chest Press"),
        ex("Chest Fly", setOf(CHEST), setOf(SHOULDERS), "Dumbbell Fly", "Pec Deck", "Machine Fly"),
        ex("Cable Crossover", setOf(CHEST), setOf(SHOULDERS), "Cable Fly"),
        ex("Push-Up", setOf(CHEST), setOf(TRICEPS, SHOULDERS, ABS), "Pushup"),
        ex("Dips", setOf(CHEST, TRICEPS), setOf(SHOULDERS), "Chest Dip", "Tricep Dip"),
        // Shoulders & traps
        ex("Overhead Press", setOf(SHOULDERS), setOf(TRICEPS, TRAPS), "OHP", "Military Press", "Barbell Shoulder Press"),
        ex("Dumbbell Shoulder Press", setOf(SHOULDERS), setOf(TRICEPS), "Shoulder Press", "DB Shoulder Press", "Seated Shoulder Press"),
        ex("Arnold Press", setOf(SHOULDERS), setOf(TRICEPS)),
        ex("Lateral Raise", setOf(SHOULDERS), aliases = arrayOf("Side Raise", "Lat Raise", "Dumbbell Lateral Raise")),
        ex("Front Raise", setOf(SHOULDERS)),
        ex("Rear Delt Fly", setOf(SHOULDERS, UPPER_BACK), aliases = arrayOf("Reverse Fly", "Rear Delt Raise")),
        ex("Face Pull", setOf(UPPER_BACK, SHOULDERS), setOf(TRAPS)),
        ex("Upright Row", setOf(SHOULDERS, TRAPS), setOf(BICEPS)),
        ex("Shrugs", setOf(TRAPS), setOf(FOREARMS), "Shrug", "Dumbbell Shrug", "Barbell Shrug"),
        // Back
        ex("Deadlift", setOf(HAMSTRINGS, GLUTES, LOWER_BACK), setOf(TRAPS, FOREARMS, QUADS, UPPER_BACK), "Conventional Deadlift"),
        ex("Barbell Row", setOf(UPPER_BACK, LATS), setOf(BICEPS, LOWER_BACK, FOREARMS), "Bent Over Row", "Bent-Over Row"),
        ex("Dumbbell Row", setOf(LATS, UPPER_BACK), setOf(BICEPS), "One Arm Row", "Single Arm Row"),
        ex("Seated Cable Row", setOf(UPPER_BACK, LATS), setOf(BICEPS), "Cable Row"),
        ex("T-Bar Row", setOf(UPPER_BACK, LATS), setOf(BICEPS)),
        ex("Lat Pulldown", setOf(LATS), setOf(BICEPS, UPPER_BACK), "Pulldown", "Lat Pull Down"),
        ex("Pull-Up", setOf(LATS), setOf(BICEPS, UPPER_BACK, FOREARMS), "Pullup"),
        ex("Chin-Up", setOf(LATS, BICEPS), setOf(UPPER_BACK), "Chinup"),
        ex("Back Extension", setOf(LOWER_BACK), setOf(GLUTES, HAMSTRINGS), "Hyperextension"),
        ex("Good Morning", setOf(HAMSTRINGS, LOWER_BACK), setOf(GLUTES)),
        // Arms
        ex("Barbell Curl", setOf(BICEPS), setOf(FOREARMS), "Curl", "EZ Bar Curl"),
        ex("Dumbbell Curl", setOf(BICEPS), setOf(FOREARMS), "Bicep Curl", "Biceps Curl"),
        ex("Hammer Curl", setOf(BICEPS, FOREARMS)),
        ex("Preacher Curl", setOf(BICEPS)),
        ex("Cable Curl", setOf(BICEPS), setOf(FOREARMS)),
        ex("Tricep Pushdown", setOf(TRICEPS), aliases = arrayOf("Triceps Pushdown", "Cable Pushdown", "Rope Pushdown")),
        ex("Skull Crusher", setOf(TRICEPS), aliases = arrayOf("Skullcrusher", "Lying Tricep Extension")),
        ex("Overhead Tricep Extension", setOf(TRICEPS), aliases = arrayOf("Tricep Extension", "Triceps Extension")),
        ex("Close-Grip Bench Press", setOf(TRICEPS, CHEST), setOf(SHOULDERS), "Close Grip Bench"),
        ex("Tricep Kickback", setOf(TRICEPS)),
        ex("Wrist Curl", setOf(FOREARMS)),
        ex("Farmer's Walk", setOf(FOREARMS, TRAPS), setOf(ABS), "Farmers Walk", "Farmer Carry"),
        // Legs
        ex("Squat", setOf(QUADS, GLUTES), setOf(HAMSTRINGS, LOWER_BACK, ABS), "Back Squat", "Barbell Squat"),
        ex("Front Squat", setOf(QUADS), setOf(GLUTES, ABS)),
        ex("Goblet Squat", setOf(QUADS, GLUTES), setOf(ABS)),
        ex("Hack Squat", setOf(QUADS), setOf(GLUTES)),
        ex("Leg Press", setOf(QUADS, GLUTES), setOf(HAMSTRINGS)),
        ex("Lunge", setOf(QUADS, GLUTES), setOf(HAMSTRINGS), "Walking Lunge", "Dumbbell Lunge"),
        ex("Bulgarian Split Squat", setOf(QUADS, GLUTES), setOf(HAMSTRINGS), "Split Squat"),
        ex("Step-Up", setOf(QUADS, GLUTES)),
        ex("Leg Extension", setOf(QUADS)),
        ex("Romanian Deadlift", setOf(HAMSTRINGS, GLUTES), setOf(LOWER_BACK), "RDL", "Stiff Leg Deadlift"),
        ex("Leg Curl", setOf(HAMSTRINGS), aliases = arrayOf("Hamstring Curl", "Lying Leg Curl", "Seated Leg Curl")),
        ex("Hip Thrust", setOf(GLUTES), setOf(HAMSTRINGS), "Barbell Hip Thrust"),
        ex("Glute Bridge", setOf(GLUTES), setOf(HAMSTRINGS)),
        ex("Calf Raise", setOf(CALVES), aliases = arrayOf("Standing Calf Raise")),
        ex("Seated Calf Raise", setOf(CALVES)),
        // Core
        ex("Plank", setOf(ABS), setOf(OBLIQUES)),
        ex("Side Plank", setOf(OBLIQUES), setOf(ABS)),
        ex("Crunch", setOf(ABS)),
        ex("Sit-Up", setOf(ABS), aliases = arrayOf("Situp")),
        ex("Cable Crunch", setOf(ABS)),
        ex("Hanging Leg Raise", setOf(ABS), setOf(OBLIQUES, FOREARMS), "Leg Raise"),
        ex("Russian Twist", setOf(OBLIQUES), setOf(ABS)),
        ex("Ab Wheel Rollout", setOf(ABS), setOf(LATS, OBLIQUES), "Ab Wheel", "Ab Rollout"),
    )

    /** Loose key: lowercase letters/digits only, trailing plural "s" dropped. */
    fun key(name: String): String {
        val k = name.lowercase().filter { it.isLetterOrDigit() }
        return if (k.length > 3 && k.endsWith("s")) k.dropLast(1) else k
    }

    private val byKey: Map<String, CatalogExercise> = buildMap {
        all.forEach { e -> (listOf(e.name) + e.aliases).forEach { putIfAbsent(key(it), e) } }
    }

    fun find(name: String): CatalogExercise? = byKey[key(name)]

    /** Catalog exercises whose name or an alias contains [query] (loosely). */
    fun search(query: String): List<CatalogExercise> {
        val q = key(query)
        if (q.isEmpty()) return emptyList()
        return all.filter { e -> (listOf(e.name) + e.aliases).any { key(it).contains(q) } }
            .sortedBy { e -> if (key(e.name).startsWith(q)) 0 else 1 }
    }
}
