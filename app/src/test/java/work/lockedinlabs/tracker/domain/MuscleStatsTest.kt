package work.lockedinlabs.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import work.lockedinlabs.tracker.data.ExerciseLog
import work.lockedinlabs.tracker.data.SetEntry
import work.lockedinlabs.tracker.pack.Pack
import work.lockedinlabs.tracker.pack.TestPack
import java.time.LocalDate

class MuscleStatsTest {
    init { TestPack.install() }

    private val today = LocalDate.of(2026, 9, 25)
    private val target = Pack.science.muscles.weeklyTargetSets
    private fun log(daysAgo: Long, name: String, sets: Int = 3) =
        ExerciseLog(exercise = name, sets = List(sets) { SetEntry(50.0, 8) }, epochDay = today.toEpochDay() - daysAgo)

    @Test fun `catalog matching is loose`() {
        assertEquals("Overhead Press", ExerciseCatalog.find("ohp")?.name)
        assertEquals("Pull-Up", ExerciseCatalog.find("pull ups")?.name)
        assertEquals("Romanian Deadlift", ExerciseCatalog.find("RDL")?.name)
        assertEquals("Bench Press", ExerciseCatalog.find("bench press")?.name)
        assertNull(ExerciseCatalog.find("Hamstring Extension"))
    }

    @Test fun `main muscles count full sets, assisting ones half`() {
        val m = MuscleStats.compute(listOf(log(0, "Bench Press", sets = 4)), today)
        assertEquals(4.0, m.getValue(Muscle.CHEST).weeklySets, 1e-9)
        assertEquals(2.0, m.getValue(Muscle.TRICEPS).weeklySets, 1e-9)
        assertEquals(4.0 / target, m.getValue(Muscle.CHEST).intensity, 1e-9)
        assertEquals(0.0, m.getValue(Muscle.QUADS).intensity, 0.0)
        assertNull(m.getValue(Muscle.QUADS).daysSinceTrained)
    }

    @Test fun `weekly target fills the muscle`() {
        val m = MuscleStats.compute(listOf(log(1, "Squat", 5), log(4, "Squat", 5)), today)
        assertEquals(1.0, m.getValue(Muscle.QUADS).intensity, 1e-9)
    }

    @Test fun `one huge session only counts up to the session cap`() {
        val m = MuscleStats.compute(listOf(log(0, "Leg Curl", 25)), today)
        assertEquals(Pack.science.muscles.maxSetsPerSession, m.getValue(Muscle.HAMSTRINGS).weeklySets, 1e-9)
    }

    @Test fun `sets older than a week fade out`() {
        val week = MuscleStats.compute(listOf(log(6, "Barbell Curl", 4)), today).getValue(Muscle.BICEPS).weeklySets
        val older = MuscleStats.compute(listOf(log(10, "Barbell Curl", 4)), today).getValue(Muscle.BICEPS).weeklySets
        val gone = MuscleStats.compute(listOf(log(30, "Barbell Curl", 4)), today).getValue(Muscle.BICEPS)
        assertEquals(4.0, week, 1e-9)
        assertTrue(older in 0.1..3.9)
        assertEquals(0.0, gone.weeklySets, 0.0)
        assertEquals(30, gone.daysSinceTrained)
    }

    @Test fun `custom exercises are ignored until they have muscles`() {
        val m = MuscleStats.compute(listOf(log(0, "My Secret Move")), today)
        assertTrue(m.values.all { it.intensity == 0.0 })
    }

    @Test fun `undertrained muscles need attention, untrained ones don't`() {
        val logs = (0L..27L step 3).map { log(it, "Squat") } + log(20, "Barbell Curl")
        val m = MuscleStats.compute(logs, today)
        assertTrue(m.getValue(Muscle.QUADS).intensity > 0.95)
        val attention = MuscleStats.neglected(m).map { it.first }.toSet()
        assertTrue(Muscle.BICEPS in attention)
        assertTrue(Muscle.QUADS !in attention)
        assertTrue(Muscle.CHEST !in attention)
    }
}
