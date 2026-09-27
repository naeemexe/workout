package work.lockedinlabs.tracker.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveQueueTest {
    @Test fun `keeps the latest value per key and never drops another key's edit`() = runBlocking {
        val saved = mutableListOf<Pair<String, Int>>()
        val firstSaveRunning = CompletableDeferred<Unit>()
        val job = Job()
        val queue = SaveQueue<String, Int>(CoroutineScope(coroutineContext + job)) { k, v ->
            if (saved.isEmpty() && !firstSaveRunning.isCompleted) firstSaveRunning.await()
            saved += k to v
        }
        queue.submit("A", 1)
        yield() // the first save starts and waits
        // While it runs: more edits to plan A, then one to plan B.
        queue.submit("A", 2)
        queue.submit("B", 1)
        queue.submit("A", 3)
        firstSaveRunning.complete(Unit)
        repeat(10) { yield() }
        assertEquals(listOf("A" to 1, "A" to 3, "B" to 1), saved)
        job.cancel()
    }
}
