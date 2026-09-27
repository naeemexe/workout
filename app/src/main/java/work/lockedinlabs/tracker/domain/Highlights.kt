package work.lockedinlabs.tracker.domain

/**
 * Day streak that tolerates normal rest: it stays alive as long as you never go more than
 * [StrengthIndex.GRACE_DAYS] days in a row without training (the same grace the Strength Index uses).
 * Counts calendar days from the first workout of the current run through today.
 */
object Streak {
    fun days(trainedDays: Set<Long>, today: Long, maxRestDays: Int = StrengthIndex.GRACE_DAYS): Int {
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
