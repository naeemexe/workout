package work.lockedinlabs.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import work.lockedinlabs.tracker.data.ExerciseLog
import work.lockedinlabs.tracker.data.SetEntry
import java.time.LocalDate

class MuscleStatsTest {
    private val today = LocalDate.of(2026, 9, 25)
    private fun log(daysAgo: Long, name: String, sets: Int = 3) =
        ExerciseLog(exercise = name, sets = List(sets) { SetEntry(50.0, 8) }, epochDay = today.toEpochDay() - daysAgo)

    @Test fun `catalog matching is loose`() {
        assertEquals("Overhead Press", ExerciseCatalog.find("ohp")?.name)
        assertEquals("Pull-Up", ExerciseCatalog.find("pull ups")?.name)
        assertEquals("Romanian Deadlift", ExerciseCatalog.find("RDL")?.name)
        assertEquals("Bench Press", ExerciseCatalog.find("bench press")?.name)
        assertNull(ExerciseCatalog.find("Hamstring Extension"))
    }

    @Test fun `every catalog key is unique`() {
        val keys = ExerciseCatalog.all.flatMap { e -> (listOf(e.name) + e.aliases).map { ExerciseCatalog.key(it) to e.name } }
        val clashes = keys.groupBy({ it.first }, { it.second }).filterValues { it.distinct().size > 1 }
        assertTrue("clashing names: $clashes", clashes.isEmpty())
    }

    @Test fun `trained today lights up primary and secondary`() {
        val m = MuscleStats.compute(listOf(log(0, "Bench Press")), today)
        assertTrue(m.getValue(Muscle.CHEST).intensity > 0.7)
        assertTrue(m.getValue(Muscle.TRICEPS).intensity > 0.6)
        assertEquals(0.0, m.getValue(Muscle.QUADS).intensity, 0.0)
        assertNull(m.getValue(Muscle.QUADS).daysSinceTrained)
    }

    @Test fun `custom exercises are ignored`() {
        val m = MuscleStats.compute(listOf(log(0, "My Secret Move")), today)
        assertTrue(m.values.all { it.intensity == 0.0 })
    }

    @Test fun `consistent training stays full, skipped muscles fade`() {
        val logs = (0L..27L step 3).map { log(it, "Squat") } + log(20, "Barbell Curl")
        val m = MuscleStats.compute(logs, today)
        assertTrue(m.getValue(Muscle.QUADS).intensity > 0.95)
        assertTrue(m.getValue(Muscle.BICEPS).intensity < 0.2)
        // Curls hit forearms only as a secondary, so they fade even more than biceps.
        assertEquals(setOf(Muscle.FOREARMS, Muscle.BICEPS), MuscleStats.neglected(m).map { it.first }.toSet())
    }
}
