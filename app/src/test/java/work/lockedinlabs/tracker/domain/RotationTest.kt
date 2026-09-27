package work.lockedinlabs.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import work.lockedinlabs.tracker.data.PlanDay
import java.time.LocalDate

class RotationTest {
    private val mon = LocalDate.of(2026, 9, 21)

    // Upper → Chest & Tri → Legs → Rest, days 5–7 left empty.
    private val cycle = Rotation.cycle(
        listOf(
            PlanDay(0, "Upper Body", exercises = listOf("Bench Press", "Row")),
            PlanDay(1, "Chest & Tri"),
            PlanDay(2, "Legs"),
            PlanDay(3, isRest = true),
        ) + (4..6).map { PlanDay(it) },
    )

    /** Days after Monday → plan day index recorded/picked, and which of those days had logs. */
    private fun dayOn(offset: Long, choices: Map<Long, Int> = emptyMap(), trained: Set<Long> = choices.keys) =
        Rotation.dayFor(
            mon.plusDays(offset),
            cycle,
            choices.mapKeys { mon.toEpochDay() + it.key },
            trained.mapTo(HashSet()) { mon.toEpochDay() + it },
        )?.displayName

    @Test fun `empty days are skipped`() = assertEquals(listOf(0, 1, 2, 3), cycle.map { it.dayIndex })

    @Test fun `starts at day one`() = assertEquals("Upper Body", dayOn(0))

    @Test fun `training moves to the next day`() = assertEquals("Chest & Tri", dayOn(1, mapOf(0L to 0)))

    @Test fun `skipped days wait for you`() {
        // Upper on Monday, nothing Tue–Thu: Friday is still Chest & Tri.
        assertEquals("Chest & Tri", dayOn(4, mapOf(0L to 0)))
    }

    @Test fun `picking a day without training does not advance`() {
        // Tapped "Chest & Tri" on Tuesday but never logged: Wednesday is still Chest & Tri.
        assertEquals("Chest & Tri", dayOn(2, mapOf(0L to 0, 1L to 1), trained = setOf(0L)))
    }

    @Test fun `planned rest passes after one day`() {
        val legsOnWed = mapOf(2L to 2)
        assertEquals("Rest", dayOn(3, legsOnWed))
        assertEquals("Upper Body", dayOn(4, legsOnWed))
        assertEquals("Upper Body", dayOn(9, legsOnWed)) // still waiting on Upper after a long break
    }

    @Test fun `choosing rest shows rest and does not advance`() {
        val choices = mapOf(0L to 0, 1L to PlanDay.REST_CHOICE)
        assertEquals("Rest", dayOn(1, choices))
        assertEquals("Chest & Tri", dayOn(2, choices))
    }

    @Test fun `custom session shows as custom and does not advance`() {
        val choices = mapOf(0L to 0, 1L to PlanDay.CUSTOM_CHOICE) // Upper Monday, custom "Arms" Tuesday
        assertEquals(PlanDay.CUSTOM_CHOICE, Rotation.dayFor(mon.plusDays(1), cycle, choices.mapKeys { mon.toEpochDay() + it.key }, setOf(mon.toEpochDay(), mon.toEpochDay() + 1))?.dayIndex)
        assertEquals("Chest & Tri", dayOn(2, choices))
    }

    @Test fun `picking a different workout continues from it`() {
        val choices = mapOf(0L to 2) // did Legs on Monday instead
        assertEquals("Legs", dayOn(0, choices))
        assertEquals("Rest", dayOn(1, choices))
    }

    @Test fun `unnamed days and duplicate names are left out`() {
        val c = Rotation.cycle(
            listOf(
                PlanDay(0, "Upper Body"),
                PlanDay(1, exercises = listOf("Squat")), // no name
                PlanDay(2, "upper body "), // duplicate
                PlanDay(3, "Legs"),
            ),
        )
        assertEquals(listOf("Upper Body", "Legs"), c.map { it.displayName })
    }

    @Test fun `no plan means no day`() = assertNull(Rotation.dayFor(mon, emptyList(), emptyMap(), emptySet()))

    // Four workouts, days 5–7 blank.
    private val fourDays = listOf("A", "B", "C", "D").mapIndexed { i, n -> PlanDay(i, n) } + (4..6).map { PlanDay(it) }

    @Test fun `without weekly rotation 4 workouts repeat every 4`() =
        assertEquals(listOf("A", "B", "C", "D"), Rotation.cycle(fourDays).map { it.displayName })

    @Test fun `weekly rotation turns blank days into rest`() =
        assertEquals(listOf("A", "B", "C", "D", "Rest", "Rest", "Rest"), Rotation.cycle(fourDays, weekly = true).map { it.displayName })

    @Test fun `weekly rest days pass on their own, then the week starts over`() {
        val c = Rotation.cycle(fourDays, weekly = true)
        val choices = (0L..3L).associate { mon.toEpochDay() + it to it.toInt() }
        fun on(offset: Long) = Rotation.dayFor(mon.plusDays(offset), c, choices, choices.keys)?.displayName
        assertEquals("Rest", on(4))
        assertEquals("Rest", on(6))
        assertEquals("A", on(7))
    }

    @Test fun `a week of only blank days is no plan`() =
        assertEquals(emptyList<PlanDay>(), Rotation.cycle((0..6).map { PlanDay(it) }, weekly = true))
}
