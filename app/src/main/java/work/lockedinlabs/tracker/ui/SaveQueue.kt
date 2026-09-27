package work.lockedinlabs.tracker.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Background saves for edit-as-you-type screens. Each key (e.g. a plan's id) keeps only its latest value while a
 * save is running, so typing doesn't queue up writes, but an edit to one item is never dropped because another
 * item was edited right after.
 */
class SaveQueue<K, V>(scope: CoroutineScope, private val save: suspend (K, V) -> Unit) {
    private val pending = Channel<Pair<K, V>>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (first in pending) {
                val latest = linkedMapOf(first)
                while (true) {
                    val next = pending.tryReceive().getOrNull() ?: break
                    latest[next.first] = next.second
                }
                latest.forEach { (k, v) -> save(k, v) }
            }
        }
    }

    fun submit(key: K, value: V) {
        pending.trySend(key to value)
    }
}
