package com.piotv.navigate.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.SystemClock

/**
 * „Welche App war zuletzt im Vordergrund?" — die Frage hinter „HOME oder BACK
 * auf der Home-Seite → zurück in die letzte App".
 *
 * Zwei Wege, in dieser Reihenfolge:
 *
 *  1. **Nutzungsstatistik** (5-Minuten-Fenster, sonst 6 Stunden): exakt. Sie
 *     braucht den App-Op `GET_USAGE_STATS`, den ein Launcher nicht selbst
 *     erfragen kann (`adb shell appops set com.piotv.navigate GET_USAGE_STATS
 *     allow`) — ohne ihn wirft die Abfrage, und der zweite Weg greift.
 *  2. **Merker**: die letzte App, die über Navigate gestartet wurde. Ohne
 *     Zugriff ist das die ehrliche Näherung: richtig, wenn man die App von hier
 *     gestartet hat; sonst `null`, und die Home-Seite bleibt stehen.
 *
 * Warum nicht `ActivityManager.getRunningTasks`: seit Android 5 liefert das
 * normalen Apps nur noch die eigenen Tasks — für einen Launcher wertlos.
 *
 * Warum zwei Fenster: nicht die Abfrage kostet, sondern die **Ereignismenge**.
 * Ein 6-Stunden-Fenster enthält auf einem benutzten Gerät Tausende Ereignisse,
 * und die lägen auf dem HOME-Pfad. Der Normalfall („App ansehen → HOME") liegt
 * im 5-Minuten-Fenster; das lange ist nur die Absicherung.
 */
class LastApp(context: Context) {

    private val appContext = context.applicationContext
    private val usageStats: UsageStatsManager? =
        appContext.getSystemService(UsageStatsManager::class.java)
    private val ownPackage: String = appContext.packageName
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        // Nach einem Systemstart ist der Merker wertlos: die App läuft nicht
        // mehr. Ohne diese Prüfung würde der erste HOME-Druck nach dem
        // Einschalten eine App starten, die niemand offen hatte.
        val bootMarker = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        if (prefs.getLong(KEY_BOOT, -1L) != bootMarker) {
            prefs.edit().putLong(KEY_BOOT, bootMarker).remove(KEY_LAST_APP).apply()
        }
    }

    /** Merkt sich die App, die gerade über Navigate gestartet wurde. */
    fun remember(packageName: String) {
        prefs.edit().putString(KEY_LAST_APP, packageName).apply()
    }

    /** Paket der zuletzt im Vordergrund stehenden fremden App — oder `null`. */
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

    /** Das letzte fremde Vordergrund-Ereignis in diesem Fenster; `null`, wenn
     *  keines da ist — oder wenn die Abfrage nicht erlaubt ist. */
    private fun latest(stats: UsageStatsManager, begin: Long, end: Long): String? {
        val collected = collect(stats, begin, end) ?: return null
        return ForegroundLog.lastResumed(ownPackage, collected)
    }

    /** Fragt ein Fenster ab und gibt die Ereignisse in zeitlicher Reihenfolge
     *  zurück; `null`, wenn die Abfrage nicht erlaubt ist. */
    private fun collect(stats: UsageStatsManager, begin: Long, end: Long): List<Pair<String, Int>>? {
        val events = try {
            stats.queryEvents(begin, end)
        } catch (e: Exception) {
            // SecurityException ohne den App-Op — erwarteter Weg.
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

        /** 5 Minuten: der Normalfall „App ansehen → HOME drücken". */
        const val SHORT_WINDOW_MS = 5 * 60 * 1000L

        /** 6 Stunden: die App lief vor Minuten. Nur der zweite Durchgang. */
        const val LONG_WINDOW_MS = 6 * 60 * 60 * 1000L

        /** Erwartungsgröße: ein kurzes Fenster füllt das ohne Nachwachsen. */
        const val INITIAL_CAPACITY = 32
    }
}
