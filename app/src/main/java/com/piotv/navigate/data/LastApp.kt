package com.piotv.navigate.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.SystemClock

/**
 * "Which app was in the foreground last?" — the question behind "HOME or BACK
 * on the home screen → back to the last app".
 *
 * Two ways, in this order:
 *
 *  1. **Usage stats** (5-minute window, else 6 hours): exact. It needs the
 *     app op `GET_USAGE_STATS`, which a launcher cannot request for itself
 *     (`adb shell appops set com.piotv.navigate GET_USAGE_STATS allow`) —
 *     without it the query throws and the second way kicks in.
 *  2. **Marker**: the last app started through Navigate. Without access this
 *     is the honest approximation: right when you started the app from here;
 *     otherwise `null`, and the home screen stays put.
 *
 * Why not `ActivityManager.getRunningTasks`: since Android 5 it returns only
 * the caller's own tasks to normal apps — useless for a launcher.
 *
 * Why two windows: it is not the query that costs, but the **event set**. A
 * 6-hour window holds thousands of events on a used device, and those would
 * sit on the HOME path. The normal case ("watch app → HOME") falls in the
 * 5-minute window; the long one is only the safety net.
 */
class LastApp(context: Context) {

    private val appContext = context.applicationContext
    private val usageStats: UsageStatsManager? =
        appContext.getSystemService(UsageStatsManager::class.java)
    private val ownPackage: String = appContext.packageName
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        // After a system start the marker is worthless: the app is no longer
        // running. Without this check the first HOME press after boot would
        // launch an app nobody had open.
        val bootMarker = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        if (prefs.getLong(KEY_BOOT, -1L) != bootMarker) {
            prefs.edit().putLong(KEY_BOOT, bootMarker).remove(KEY_LAST_APP).apply()
        }
    }

    /** Remembers the app that was just started through Navigate. */
    fun remember(packageName: String) {
        prefs.edit().putString(KEY_LAST_APP, packageName).apply()
    }

    /** Package of the foreign app last seen in the foreground — or `null`. */
    fun packageName(): String? {
        val stats = usageStats
        if (stats != null) {
            val now = System.currentTimeMillis()
            val boot = now - SystemClock.elapsedRealtime()
            latest(stats, maxOf(now - SHORT_WINDOW_MS, boot), now)?.let { return it }
            latest(stats, maxOf(now - LONG_WINDOW_MS, boot), now)?.let { return it }
        }
        return prefs.getString(KEY_LAST_APP, null)
    }

    /** The last foreign foreground event in this window; `null` when there
     *  is none — or when the query is not permitted. */
    private fun latest(stats: UsageStatsManager, begin: Long, end: Long): String? {
        val collected = collect(stats, begin, end) ?: return null
        return ForegroundLog.lastResumed(ownPackage, collected)
    }

    /** Queries a window and returns the events in chronological order;
     *  `null` when the query is not permitted. */
    private fun collect(stats: UsageStatsManager, begin: Long, end: Long): List<Pair<String, Int>>? {
        val events = try {
            stats.queryEvents(begin, end)
        } catch (e: Exception) {
            // SecurityException without the app op — the expected path.
            return null
        }
        val collected = ArrayList<Pair<String, Int>>(INITIAL_CAPACITY)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName ?: continue
            collected.add(packageName to event.eventType)
        }
        return collected
    }

    private companion object {
        const val PREFS = "navigate"
        const val KEY_LAST_APP = "last_app"
        const val KEY_BOOT = "boot_marker"

        /** 5 minutes: the normal case "watch app → press HOME". */
        const val SHORT_WINDOW_MS = 5 * 60 * 1000L

        /** 6 hours: the app ran minutes ago. Only the second pass. */
        const val LONG_WINDOW_MS = 6 * 60 * 60 * 1000L

        /** Expected size: a short window fills this without needing to grow. */
        const val INITIAL_CAPACITY = 32
    }
}
