package work.lockedinlabs.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import work.lockedinlabs.tracker.pack.TestPack

class HighlightsTest {
    init { TestPack.install() }

    private val today = 20_000L

    @Test fun `streak survives normal rest days`() {
        // trained 6 days ago, 4 days ago, 1 day ago → 7-day streak incl. today
        assertEquals(7, Streak.days(setOf(today - 6, today - 4, today - 1), today))
    }

    @Test fun `streak breaks after too many days off`() {
        assertEquals(0, Streak.days(setOf(today - 3), today))
        // a 3-day gap earlier cuts the run: counts only from day -2
        assertEquals(3, Streak.days(setOf(today - 10, today - 6, today - 2), today))
    }

    @Test fun `no workouts no streak`() = assertEquals(0, Streak.days(emptySet(), today))
}
