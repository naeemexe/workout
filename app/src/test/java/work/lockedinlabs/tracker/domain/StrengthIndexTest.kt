package work.lockedinlabs.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import work.lockedinlabs.tracker.data.ExerciseLog
import work.lockedinlabs.tracker.data.SetCodec
import work.lockedinlabs.tracker.data.SetEntry
import java.time.LocalDate

class StrengthIndexTest {
    private val start = LocalDate.of(2026, 1, 1)
    private fun log(dayOffset: Long, exercise: String, weight: Double, reps: Int) =
        ExerciseLog(exercise = exercise, sets = List(2) { SetEntry(weight, reps) }, epochDay = start.toEpochDay() + dayOffset)

    @Test fun `index starts at 100`() {
        val s = StrengthIndex.series(listOf(log(0, "Bench", 50.0, 10)), start)
        assertEquals(1, s.size)
        assertEquals(100.0, s.single().value, 1e-9)
    }

    @Test fun `new exercise does not jump the index`() {
        val s = StrengthIndex.series(listOf(log(0, "Bench", 50.0, 10), log(1, "Squat", 200.0, 5)), start.plusDays(1))
        assertEquals(100.0, s.last().value, 1e-9)
    }

    @Test fun `progress raises the index and trend`() {
        val logs = (0L until 8).map { w -> log(w * 3, "Bench", 50.0 + w * 5, 8) }
        val s = StrengthIndex.series(logs, start.plusDays(21))
        assertTrue(s.last().value > 100)
        assertTrue(StrengthIndex.trendPerDay(s) > 0)
    }

    @Test fun `skipping workouts decays the index and tips the trend negative`() {
        val logs = listOf(log(0, "Bench", 50.0, 10))
        val s = StrengthIndex.series(logs, start.plusDays(12))
        assertEquals(100.0, s[StrengthIndex.GRACE_DAYS].value, 1e-9) // grace period, no decay yet
        assertTrue(s.last().value < 100)
        assertTrue(StrengthIndex.trendPerDay(s) < 0)
    }

    @Test fun `projection starts at the last point`() {
        val s = StrengthIndex.series(listOf(log(0, "Bench", 50.0, 10)), start)
        val p = StrengthIndex.project(s, 1.0, 7)
        assertEquals(8, p.size)
        assertEquals(s.last(), p.first())
        assertEquals(107.0, p.last().value, 1e-9)
    }

    @Test fun `sets survive the storage round trip`() {
        val sets = listOf(SetEntry(50.0, 10), SetEntry(52.5, 8), SetEntry(0.0, 15))
        assertEquals(sets, SetCodec.decode(SetCodec.encode(sets)))
    }

    @Test fun `summary reads naturally`() {
        assertEquals("50 lb · 3 × 10", List(3) { SetEntry(50.0, 10) }.summary())
        assertEquals("50 lb · 10, 10, 8", listOf(SetEntry(50.0, 10), SetEntry(50.0, 10), SetEntry(50.0, 8)).summary())
        assertEquals("50×10, 55×8 lb", listOf(SetEntry(50.0, 10), SetEntry(55.0, 8)).summary())
    }

    @Test fun `index is the average of the exercise lines`() {
        val logs = listOf(log(0, "Bench", 50.0, 10), log(0, "Squat", 100.0, 5), log(1, "Squat", 120.0, 5))
        val c = StrengthIndex.compute(logs, start.plusDays(1))
        assertEquals(listOf("Bench", "Squat"), c.exercises.map { it.name })
        val bench = c.exercises[0].points.last().value
        val squat = c.exercises[1].points.last().value
        assertEquals(100.0, bench, 1e-9)
        assertTrue(squat > 100)
        assertEquals((bench + squat) / 2, c.index.last().value, 1e-9)
    }
}
