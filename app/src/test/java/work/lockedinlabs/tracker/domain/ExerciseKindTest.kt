package work.lockedinlabs.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import work.lockedinlabs.tracker.data.CustomExercise
import work.lockedinlabs.tracker.data.ExerciseLog
import work.lockedinlabs.tracker.data.SetCodec
import work.lockedinlabs.tracker.data.SetEntry
import work.lockedinlabs.tracker.data.kinds
import work.lockedinlabs.tracker.data.lookup
import work.lockedinlabs.tracker.pack.TestPack
import java.time.LocalDate

class ExerciseKindTest {
    private val today = LocalDate.of(2026, 11, 5)
    private fun log(name: String, sets: List<SetEntry>, day: LocalDate = today) = ExerciseLog(exercise = name, sets = sets, epochDay = day.toEpochDay())

    @Before fun pack() = TestPack.install()

    @Test fun `codec round trips every kind and keeps the old format`() {
        val sets = listOf(SetEntry(52.5, 8), SetEntry(25.0, 0, seconds = 45), SetEntry(0.0, 0, minutes = 30, miles = 2.5), SetEntry(0.0, 0, minutes = 20))
        assertEquals(sets, SetCodec.decode(SetCodec.encode(sets)))
        assertEquals("50x10;52.5x8", SetCodec.encode(listOf(SetEntry(50.0, 10), SetEntry(52.5, 8))))
        assertEquals(listOf(SetEntry(50.0, 10)), SetCodec.decode("50.0x10"))
    }

    @Test fun `next in row only looks forward`() {
        val row = listOf("Squat", "Curl", "Plank")
        assertEquals("Curl", Rotation.nextInRow(row, "Squat", setOf("squat")))
        assertEquals("Plank", Rotation.nextInRow(row, "Squat", setOf("squat", "curl")))
        assertNull(Rotation.nextInRow(row, "Plank", setOf("plank")))
        assertNull(Rotation.nextInRow(row, "plank", setOf("plank", "curl")))
    }

    @Test fun `summaries show time`() {
        assertEquals("3 × 0:45", List(3) { SetEntry(0.0, 0, seconds = 45) }.summary())
        assertEquals("0:45, 0:37", listOf(SetEntry(0.0, 0, seconds = 45), SetEntry(0.0, 0, seconds = 37)).summary())
        assertEquals("0:45", listOf(SetEntry(0.0, 0, seconds = 45)).summary())
        assertEquals("25 lb · 2 × 1:00", List(2) { SetEntry(25.0, 0, seconds = 60) }.summary())
        assertEquals("30 min · 2.5 mi", listOf(SetEntry(0.0, 0, minutes = 30, miles = 2.5)).summary())
        assertEquals("30 min", listOf(SetEntry(0.0, 0, minutes = 30)).summary())
    }

    @Test fun `catalog and custom kinds`() {
        val kinds = listOf(CustomExercise.of("Towel hang", kind = ExerciseKind.HOLD)).kinds()
        assertEquals(ExerciseKind.HOLD, kinds("plank"))
        assertEquals(ExerciseKind.CARDIO, kinds("Treadmill"))
        assertEquals(ExerciseKind.WEIGHT, kinds("Bench Press"))
        assertEquals(ExerciseKind.HOLD, kinds("towel hangs"))
        assertEquals(ExerciseKind.WEIGHT, kinds("Something new"))
    }

    @Test fun `unknown saved kind reads as weight`() {
        assertEquals(ExerciseKind.WEIGHT, ExerciseKind.of(null))
        assertEquals(ExerciseKind.WEIGHT, ExerciseKind.of("nonsense"))
        assertEquals(ExerciseKind.CARDIO, ExerciseKind.of("CARDIO"))
    }

    @Test fun `holds and cardio add no strength index line`() {
        val index = StrengthIndex.compute(
            listOf(
                log("Plank", listOf(SetEntry(25.0, 0, seconds = 45))),
                log("Running", listOf(SetEntry(0.0, 0, minutes = 30, miles = 3.0))),
                log("Squat", listOf(SetEntry(100.0, 5))),
            ),
            today,
        )
        assertEquals(listOf("Squat"), index.exercises.map { it.name })
    }

    @Test fun `only holds keep muscles from detraining in the index`() {
        val start = today.minusDays(60)
        val lift = log("Crunch", listOf(SetEntry(50.0, 10)), start)
        val plankEachWeek = (1..8).map { log("Plank", listOf(SetEntry(0.0, 0, seconds = 45)), start.plusWeeks(it.toLong())) }
        val runEachWeek = (1..8).map { log("Running", listOf(SetEntry(0.0, 0, minutes = 30)), start.plusWeeks(it.toLong())) }
        assertEquals(100.0, StrengthIndex.series(listOf(lift) + plankEachWeek, today).last().value, 0.001)
        assert(StrengthIndex.series(listOf(lift) + runEachWeek, today).last().value < 100.0)
    }

    @Test fun `hold sets count on the muscle map, cardio does not`() {
        val custom = listOf(CustomExercise.of("Sprints", setOf(Muscle.QUADS), kind = ExerciseKind.CARDIO))
        val states = MuscleStats.compute(
            listOf(
                log("Plank", List(3) { SetEntry(0.0, 0, seconds = 45) }),
                log("Running", listOf(SetEntry(0.0, 0, minutes = 30))),
                log("Sprints", listOf(SetEntry(0.0, 0, minutes = 10))),
            ),
            today,
            custom.lookup(),
        )
        assertEquals(3.0, states.getValue(Muscle.ABS).weeklySets, 0.001)
        assertEquals(0.0, states.getValue(Muscle.QUADS).weeklySets, 0.001)
    }

    @Test fun `no progression advice for holds or cardio`() {
        assertNull(Progression.suggest(listOf(log("Plank", List(3) { SetEntry(0.0, 0, seconds = 45) }))))
        assertNull(Progression.suggest(listOf(log("Running", listOf(SetEntry(0.0, 0, minutes = 30))))))
    }
}
