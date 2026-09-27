package work.lockedinlabs.tracker.sync

import android.content.Context
import androidx.core.content.edit

/**
 * Small bookkeeping for sync, kept in SharedPreferences:
 * which dates / profile / plan changed locally since the last upload (with when), and the pull cursor.
 */
class SyncStore(context: Context) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)

    /** epochDay → local change time (ms). */
    @Synchronized fun dirtyDays(): Map<Long, Long> =
        prefs.getStringSet(DIRTY_DAYS, emptySet())!!.associate { e ->
            val (day, at) = e.split(":")
            day.toLong() to at.toLong()
        }

    @Synchronized fun markDay(epochDay: Long, at: Long = System.currentTimeMillis()) =
        writeDays(dirtyDays() + (epochDay to at))

    /** Clears a day only if it hasn't changed again since [at] (e.g. while the upload was in flight). */
    @Synchronized fun clearDay(epochDay: Long, at: Long) {
        val days = dirtyDays()
        if (days[epochDay] == at) writeDays(days - epochDay)
    }

    private fun writeDays(days: Map<Long, Long>) =
        prefs.edit { putStringSet(DIRTY_DAYS, days.map { (d, at) -> "$d:$at" }.toSet()) }

    // Profile and plan each track when they were last edited locally, and whether that edit is uploaded.
    @Synchronized fun profileEditedAt(): Long = prefs.getLong(PROFILE_AT, 0)
    @Synchronized fun profileDirty(): Boolean = prefs.getBoolean(PROFILE_DIRTY, false)
    @Synchronized fun markProfile(at: Long = System.currentTimeMillis()) =
        prefs.edit { putLong(PROFILE_AT, at); putBoolean(PROFILE_DIRTY, true) }
    @Synchronized fun profileSynced(at: Long) = prefs.edit {
        if (prefs.getLong(PROFILE_AT, 0) <= at) { putLong(PROFILE_AT, at); putBoolean(PROFILE_DIRTY, false) }
    }

    @Synchronized fun planEditedAt(): Long = prefs.getLong(PLAN_AT, 0)
    @Synchronized fun planDirty(): Boolean = prefs.getBoolean(PLAN_DIRTY, false)
    @Synchronized fun markPlan(at: Long = System.currentTimeMillis()) =
        prefs.edit { putLong(PLAN_AT, at); putBoolean(PLAN_DIRTY, true) }
    @Synchronized fun planSynced(at: Long) = prefs.edit {
        if (prefs.getLong(PLAN_AT, 0) <= at) { putLong(PLAN_AT, at); putBoolean(PLAN_DIRTY, false) }
    }

    /** Server write time (microseconds) of the newest remote day already downloaded. */
    var pullCursor: Long
        get() = prefs.getLong(PULL_CURSOR, 0)
        set(v) = prefs.edit { putLong(PULL_CURSOR, v) }

    /** Account this phone last synced with; a different sign-in re-uploads everything local. */
    var syncedUid: String?
        get() = prefs.getString(SYNCED_UID, null)
        set(v) = prefs.edit { putString(SYNCED_UID, v) }

    var lastSyncAt: Long
        get() = prefs.getLong(LAST_SYNC, 0)
        set(v) = prefs.edit { putLong(LAST_SYNC, v) }

    private companion object {
        const val DIRTY_DAYS = "dirty_days"
        const val PROFILE_AT = "profile_at"
        const val PROFILE_DIRTY = "profile_dirty"
        const val PLAN_AT = "plan_at"
        const val PLAN_DIRTY = "plan_dirty"
        const val PULL_CURSOR = "pull_cursor"
        const val SYNCED_UID = "synced_uid"
        const val LAST_SYNC = "last_sync"
    }
}
