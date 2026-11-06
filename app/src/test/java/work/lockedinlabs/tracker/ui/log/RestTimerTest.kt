package work.lockedinlabs.tracker.ui.log

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class RestTimerTest {
    private class Counts : RestSounds {
        @Volatile var started = 0
        @Volatile var quietStarts = 0
        @Volatile var ended = 0
        @Volatile var skipped = 0
        override fun started(quiet: Boolean) { if (quiet) quietStarts++ else started++ }
        override fun skipped() { skipped++ }
        override fun fire() { ended++ }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sounds = Counts()
    private val timer = RestTimer(scope, sounds, System::currentTimeMillis)

    @After fun tearDown() = scope.cancel()

    @Test fun `plays start then end`() {
        timer.start(1)
        assertEquals(1, sounds.started)
        assertEquals(0, sounds.ended)
        Thread.sleep(1400)
        assertEquals(1, sounds.ended)
    }

    @Test fun `no tone hands over to a quiet timer`() {
        var ended = false
        timer.start(0, tone = false) { ended = true; timer.start(1, quiet = true) }
        Thread.sleep(200)
        assertEquals(true, ended)
        assertEquals(1, sounds.started)
        assertEquals(1, sounds.quietStarts)
        assertEquals(0, sounds.ended)
        Thread.sleep(1200)
        assertEquals(1, sounds.ended)
    }

    @Test fun `on end does not run after skip`() {
        var ended = false
        timer.start(1) { ended = true }
        timer.skip()
        Thread.sleep(1300)
        assertEquals(false, ended)
    }

    @Test fun `no end tone after skip`() {
        timer.start(1)
        timer.skip()
        Thread.sleep(1400)
        assertEquals(1, sounds.started)
        assertEquals(1, sounds.skipped)
        assertEquals(0, sounds.ended)
    }

    @Test fun `skip after the end does nothing`() {
        timer.start(1)
        Thread.sleep(1400)
        timer.skip()
        assertEquals(0, sounds.skipped)
    }

    @Test fun `restart plays start again and ends once, on the new time`() {
        timer.start(1)
        Thread.sleep(600)
        timer.start(1)
        assertEquals(2, sounds.started)
        Thread.sleep(700) // past the first end time
        assertEquals(0, sounds.ended)
        Thread.sleep(700)
        assertEquals(1, sounds.ended)
    }
}
