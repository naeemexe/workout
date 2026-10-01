package work.lockedinlabs.tracker.pack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import work.lockedinlabs.tracker.data.PlanDay
import work.lockedinlabs.tracker.domain.ExerciseCatalog
import work.lockedinlabs.tracker.domain.Rotation

/** The shipped pack parses, and its plans fit the app (the parser checks the details). */
class DataPackTest {
    private val pack = TestPack.pack.also { TestPack.install() }

    @Test fun `catalog loads`() {
        assertTrue(pack.exercises.size >= 60)
        assertEquals("Overhead Press", ExerciseCatalog.find("ohp")?.name)
    }

    @Test fun `every level has a session plan`() {
        Level.entries.forEach { level -> assertTrue("no plan for $level", pack.sessionPlans.any { level in it.levels }) }
    }

    @Test fun `session plans rotate as written`() {
        pack.sessionPlans.forEach { preset ->
            val days = preset.days.mapIndexed { i, d -> PlanDay(i, d.name, d.isRest, d.exercises.map { it.name }) }
            assertEquals(preset.name, preset.days.size, Rotation.cycle(days).size)
        }
    }

    @Test fun `push pull legs repeats with a rest day`() {
        val ppl = pack.sessionPlans.first { it.id == "push-pull-legs" }
        assertEquals("Push · Pull · Legs · Rest", ppl.rotation)
        assertEquals("3 workouts every 4 days", ppl.schedule)
    }

    @Test fun `one default progression plan`() {
        assertEquals("Standard", pack.progressionPlans.single { it.isDefault }.name)
    }

    @Test fun `science numbers are sane`() {
        val s = pack.science
        assertTrue(s.strength.graceDays in 7..42)
        assertTrue(s.strength.dailyLoss in 0.0..0.01)
        assertTrue(s.strength.floor in 0.5..1.0)
        assertTrue(s.muscles.weeklyTargetSets > 0)
        assertTrue(Level.entries.all { s.strength.maxWeeklyChange(it) > 0 })
    }

    @Test(expected = IllegalStateException::class)
    fun `a plan with an unknown exercise is rejected`() {
        PackParser.parse { name ->
            val text = java.io.File("src/main/assets/${PackParser.DIR}/$name").readText()
            if (name == PackParser.SESSION_PLANS) text.replace("\"Bench Press\"", "\"Bench Pressing Machine 9000\"") else text
        }
    }
}
