package com.piotv.navigate.data

/**
 * Folds foreground events into "which app was in front last?" — pure rules
 * without Android types, so testable without a device (ForegroundLogTest).
 *
 * The events come from the usage stats (`UsageEvents`); only what follows
 * from them lives here. The question underpins "HOME or BACK on the home
 * screen → back to the last app".
 */
object ForegroundLog {

    /**
     * Event type for "this activity came to the foreground".
     *
     * Deliberately the **number** and not `UsageEvents.Event.ACTIVITY_RESUMED`:
     * the constant has that name only from API 29 (before that it was
     * `MOVE_TO_FOREGROUND`), and on an older device (minSdk 23) touching it
     * would raise a `NoSuchFieldError`. The value is the same under both names.
     */
    const val RESUMED = 1

    /**
     * @param events pairs of package name and event type, in chronological
     *   order.
     * @return the package of the **last** foreground event that is not our
     *   own — or `null` when there is none.
     */
    fun lastResumed(ownPackage: String, events: List<Pair<String, Int>>): String? {
        var last: String? = null
        for ((packageName, type) in events) {
            if (type != RESUMED) continue
            if (packageName == ownPackage) continue
            last = packageName
        }
        return last
    }
}

