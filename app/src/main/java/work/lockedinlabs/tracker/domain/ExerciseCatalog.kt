package work.lockedinlabs.tracker.domain

import work.lockedinlabs.tracker.pack.Pack

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
    /** Section in the library ("Chest", "Legs", ...). */
    val group: String = "",
)

/**
 * Built-in exercises that count toward the muscle map, from the data pack (assets/pack/exercises.json). Names typed by
 * the user are matched loosely (case, spaces, hyphens, plural "s" and common aliases don't matter). Anything not found
 * is a custom exercise; it counts once you give it muscles (More → Exercises).
 */
object ExerciseCatalog {
    private class Index(val all: List<CatalogExercise>) {
        val byKey: Map<String, CatalogExercise> = buildMap {
            all.forEach { e -> (listOf(e.name) + e.aliases).forEach { putIfAbsent(key(it), e) } }
        }
    }

    @Volatile private var index: Pair<List<CatalogExercise>, Index>? = null

    /** Rebuilt only if a different pack is installed. */
    private fun index(): Index {
        val list = Pack.current.exercises
        index?.let { (l, i) -> if (l === list) return i }
        return Index(list).also { index = list to it }
    }

    val all: List<CatalogExercise> get() = index().all

    /** Loose key: lowercase letters/digits only, trailing plural "s" dropped. */
    fun key(name: String): String {
        val k = name.lowercase().filter { it.isLetterOrDigit() }
        return if (k.length > 3 && k.endsWith("s")) k.dropLast(1) else k
    }

    fun find(name: String): CatalogExercise? = index().byKey[key(name)]

    /** Catalog exercises whose name or an alias contains [query] (loosely). */
    fun search(query: String): List<CatalogExercise> {
        val q = key(query)
        if (q.isEmpty()) return emptyList()
        return all.filter { e -> (listOf(e.name) + e.aliases).any { key(it).contains(q) } }
            .sortedBy { e -> if (key(e.name).startsWith(q)) 0 else 1 }
    }
}
