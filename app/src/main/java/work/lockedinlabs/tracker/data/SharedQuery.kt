package work.lockedinlabs.tracker.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn

/**
 * One live database query shared by every screen that watches it (instead of one query each), kept running
 * for a few seconds after the last screen stops so quick navigation doesn't re-query.
 */
fun <T> Flow<T>.sharedIn(scope: CoroutineScope): Flow<T> =
    shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 1)
