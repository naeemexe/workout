package work.lockedinlabs.tracker.domain

import work.lockedinlabs.tracker.pack.Pack

/**
 * Day streak that tolerates normal rest: it stays alive as long as you never go more than [maxRestDays] days in a row
 * without training (from the data pack). Counts calendar days from the first workout of the current run through today.
 */
object Streak {
    fun days(trainedDays: Set<Long>, today: Long, maxRestDays: Int = Pack.science.streak.maxRestDays): Int {
        val days = trainedDays.filter { it <= today }.sortedDescending()
        val last = days.firstOrNull() ?: return 0
        if (today - last > maxRestDays) return 0
        var start = last
        for (d in days.drop(1)) {
            if (start - d - 1 > maxRestDays) break
            start = d
        }
        return (today - start + 1).toInt()
    }
}
