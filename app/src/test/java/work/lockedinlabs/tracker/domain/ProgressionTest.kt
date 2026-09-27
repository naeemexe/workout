package work.lockedinlabs.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import work.lockedinlabs.tracker.data.ExerciseLog
import work.lockedinlabs.tracker.data.SetEntry

class ProgressionTest {
    private fun log(day: Long, weight: Double, reps: Int, sets: Int = 2) =
        log(day, *Array(sets) { SetEntry(weight, reps) })

    private fun log(day: Long, vararg sets: SetEntry) =
        ExerciseLog(exercise = "Bench Press", sets = sets.toList(), epochDay = day)

    @Test fun `no history gives no suggestion`() = assertNull(Progression.suggest(emptyList()))

    @Test fun `hitting top reps steps weight up`() {
        val s = Progression.suggest(listOf(log(0, 50.0, 10)))!!
        assertEquals(Advice.STEP_UP, s.advice)
        assertEquals(55.0, s.nextWeight, 0.0)
        assertEquals(Rule.Default.startReps, s.targetReps)
        assertEquals(List(2) { SetEntry(55.0, 8) }, s.plan)
    }

    @Test fun `every required set must hit its reps before stepping up`() {
        val s = Progression.suggest(listOf(log(0, SetEntry(50.0, 10), SetEntry(50.0, 8))))!!
        assertEquals(Advice.ADD_REPS, s.advice)
        assertEquals(9, s.targetReps)
        assertEquals(listOf(SetEntry(50.0, 10), SetEntry(50.0, 9)), s.plan)
    }

    @Test fun `sets after the rule's sets are only logged`() {
        val rule = Rule(setReps = listOf(10, 8))
        val sets = listOf(SetEntry(50.0, 10), SetEntry(50.0, 8), SetEntry(50.0, 4), SetEntry(50.0, 3))
        val s = Progression.suggest(listOf(log(0, *sets.toTypedArray())), rule)!!
        assertEquals(Advice.STEP_UP, s.advice)
    }

    @Test fun `sets are matched in order`() {
        // Set 1 needs 10 and set 2 needs 8: doing 8 then 10 isn't enough.
        val rule = Rule(setReps = listOf(10, 8))
        val s = Progression.suggest(listOf(log(0, SetEntry(50.0, 8), SetEntry(50.0, 10))), rule)!!
        assertEquals(Advice.ADD_REPS, s.advice)
        assertEquals(listOf(SetEntry(50.0, 9), SetEntry(50.0, 10)), s.plan)
    }

    @Test fun `weak extra sets don't cause a step down`() {
        val rule = Rule(setReps = listOf(10, 8))
        val day = { d: Long -> log(d, SetEntry(100.0, 9), SetEntry(100.0, 8), SetEntry(100.0, 3)) }
        val s = Progression.suggest(listOf(day(0), day(3)), rule)!!
        assertEquals(Advice.ADD_REPS, s.advice)
    }

    @Test fun `lighter warm-up sets are ignored and kept in the plan`() {
        val s = Progression.suggest(listOf(log(0, SetEntry(30.0, 5), SetEntry(50.0, 10), SetEntry(50.0, 10))))!!
        assertEquals(Advice.STEP_UP, s.advice)
        assertEquals(listOf(SetEntry(30.0, 5), SetEntry(55.0, 8), SetEntry(55.0, 8)), s.plan)
    }

    @Test fun `light weights step up by 2_5`() {
        val s = Progression.suggest(listOf(log(0, 20.0, 12)))!!
        assertEquals(22.5, s.nextWeight, 0.0)
    }

    @Test fun `mid range reps means add a rep`() {
        val s = Progression.suggest(listOf(log(0, 50.0, 8)))!!
        assertEquals(Advice.ADD_REPS, s.advice)
        assertEquals(50.0, s.nextWeight, 0.0)
        assertEquals(9, s.targetReps)
    }

    @Test fun `uses the most recent session`() {
        val s = Progression.suggest(listOf(log(7, 55.0, 7), log(0, 50.0, 10)))!!
        assertEquals(Advice.ADD_REPS, s.advice)
        assertEquals(55.0, s.nextWeight, 0.0)
    }

    @Test fun `two rough sessions at the same weight deloads`() {
        val s = Progression.suggest(listOf(log(0, 100.0, 4), log(3, 100.0, 5)))!!
        assertEquals(Advice.DELOAD, s.advice)
        assertEquals(90.0, s.nextWeight, 0.0)
    }

    @Test fun `one rough session holds`() {
        val s = Progression.suggest(listOf(log(0, 100.0, 8), log(3, 100.0, 5)))!!
        assertEquals(Advice.HOLD, s.advice)
    }

    @Test fun `a rule's set table decides when to step up`() {
        val rule = Rule(setReps = listOf(12, 12, 10), increment = 10.0, startReps = 6)
        val s = Progression.suggest(listOf(log(0, SetEntry(100.0, 12), SetEntry(100.0, 12), SetEntry(100.0, 10))), rule)!!
        assertEquals(Advice.STEP_UP, s.advice)
        assertEquals(110.0, s.nextWeight, 0.0)
        assertEquals(List(3) { SetEntry(110.0, 6) }, s.plan)
    }

    @Test fun `a set short of its own target holds the weight`() {
        val rule = Rule(setReps = listOf(12, 12, 10))
        val s = Progression.suggest(listOf(log(0, SetEntry(100.0, 12), SetEntry(100.0, 11), SetEntry(100.0, 10))), rule)!!
        assertEquals(Advice.ADD_REPS, s.advice)
    }

    @Test fun `too few sets means add the next one`() {
        val rule = Rule(setReps = listOf(10, 10, 10))
        val s = Progression.suggest(listOf(log(0, 50.0, 10)), rule)!!
        assertEquals(Advice.ADD_REPS, s.advice)
        assertEquals(List(3) { SetEntry(50.0, 10) }, s.plan)
    }

    @Test fun `step down uses the rule's minimum and drop`() {
        val rule = Rule(minReps = 8, dropPct = 20)
        val s = Progression.suggest(listOf(log(0, 100.0, 7), log(3, 100.0, 7)), rule)!!
        assertEquals(Advice.DELOAD, s.advice)
        assertEquals(80.0, s.nextWeight, 0.0)
    }

    @Test fun `step down looks at the first sets only`() {
        // First 2 sets under the minimum twice → step down, even though a later set was strong.
        val rule = Rule(setReps = listOf(10, 8), minReps = 6)
        val day = { d: Long -> log(d, SetEntry(100.0, 5), SetEntry(100.0, 5), SetEntry(100.0, 10)) }
        assertEquals(Advice.DELOAD, Progression.suggest(listOf(day(0), day(3)), rule)!!.advice)
    }
}
