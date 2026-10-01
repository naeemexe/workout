package work.lockedinlabs.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import work.lockedinlabs.tracker.data.ExerciseLog
import work.lockedinlabs.tracker.data.SetCodec
import work.lockedinlabs.tracker.data.SetEntry
import java.time.LocalDate
import work.lockedinlabs.tracker.pack.Level
import work.lockedinlabs.tracker.pack.Pack
import work.lockedinlabs.tracker.pack.TestPack

class StrengthIndexTest {
    init { TestPack.install() }

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

    @Test fun `a few weeks off keeps your strength, longer breaks lose it slowly`() {
        val science = Pack.science.strength
        val logs = listOf(log(0, "Bench Press", 50.0, 10))
        val s = StrengthIndex.series(logs, start.plusDays(120))
        assertEquals(100.0, s[science.graceDays].value, 1e-9) // grace period, no detraining yet
        val afterTwelveWeeks = s[science.graceDays + 84].value
        assertTrue("lost ${100 - afterTwelveWeeks}%", afterTwelveWeeks in 85.0..95.0)
        assertTrue(s.last().value >= 100 * science.floor - 1e-9)
    }

    @Test fun `training the same muscles another way keeps a lift from detraining`() {
        val days = 60L
        val logs = listOf(log(0, "Bench Press", 50.0, 10)) + (7L..days step 7).map { log(it, "Dumbbell Bench Press", 40.0, 10) }
        val c = StrengthIndex.compute(logs, start.plusDays(days))
        assertEquals(100.0, c.exercises.first { it.name == "Bench Press" }.points.last().value, 1e-9)
    }

    @Test fun `coming back after a break starts from what you kept`() {
        val science = Pack.science.strength
        val off = science.graceDays + 60L
        val kept = 100 * Math.pow(1 - science.dailyLoss, 60.0)
        // A weaker comeback session than before the break, but above what you kept: that's your new level.
        val comeback = 50.0 * (1 + 10 / 30.0) * (kept / 100 + 0.01)
        val logs = listOf(log(0, "Bench Press", 50.0, 10), log(off, "Bench Press", comeback / (1 + 10 / 30.0), 10))
        val s = StrengthIndex.series(logs, start.plusDays(off))
        assertEquals(kept + 1, s.last().value, 1e-6)
    }

    @Test fun `projection is held to a realistic pace`() {
        val science = Pack.science.strength
        val fast = 5.0 // index points a day
        val capped = StrengthIndex.realisticTrend(fast, 100.0, Level.ADVANCED)
        assertEquals(100 * science.maxWeeklyChange(Level.ADVANCED) / 7, capped, 1e-9)
        assertEquals(0.01, StrengthIndex.realisticTrend(0.01, 100.0, Level.BEGINNER), 1e-12)
        assertTrue(StrengthIndex.realisticTrend(-fast, 100.0, null) < 0)
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
