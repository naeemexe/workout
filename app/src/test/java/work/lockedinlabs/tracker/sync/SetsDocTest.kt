package work.lockedinlabs.tracker.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import work.lockedinlabs.tracker.data.SetEntry

class SetsDocTest {
    @Test fun `weight logs keep the old doc shape`() {
        val doc = setsTo(listOf(SetEntry(50.0, 10)))
        assertEquals(listOf(50.0, 10), doc["s"])
        assertFalse("x" in doc)
    }

    @Test fun `older docs still read`() {
        assertEquals(listOf(SetEntry(50.0, 10), SetEntry(0.0, 8)), setsFrom(mapOf("s" to listOf(50.0, 10L, 0L, 8L))))
    }

    @Test fun `holds and cardio round trip, with Firestore's number types`() {
        val sets = listOf(SetEntry(25.0, 0, seconds = 45), SetEntry(0.0, 0, minutes = 30, miles = 2.5))
        assertEquals(sets, setsFrom(setsTo(sets)))
        val stored = mapOf("s" to listOf(25.0, 0L), "x" to listOf(45L, 0L, 0.0))
        assertEquals(listOf(SetEntry(25.0, 0, seconds = 45)), setsFrom(stored))
    }
}
