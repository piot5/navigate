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

    private var activeCache: Set<String> = emptySet()

    /**
     * Laufzeituhr der letzten „läuft noch?“-Abfrage.
     *
     * Der Startwert ist **nicht** `Long.MIN_VALUE`: `now - Long.MIN_VALUE` läuft
     * über und wird negativ — die Bedingung „innerhalb des Intervalls" wäre damit
     * immer wahr, die Abfrage fände nie statt. Genau dieser Fehler steckte bis
     * 0.4 in der Import-Drossel (siehe CHANGELOG); ein negativer Wert in der
     * Größe des Intervalls heißt „lange her" und kann nicht überlaufen.
     */
    private var activeAt = -ACTIVE_CACHE_MS

    init {
        // Nach einem Systemstart ist der Merker wertlos: die App läuft nicht
        // mehr. Ohne diese Prüfung würde der erste HOME-Druck nach dem
        // Einschalten eine App starten, die niemand offen hatte.
        val bootMarker = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        if (prefs.getLong(KEY_BOOT, -1L) != bootMarker) {
            prefs.edit().putLong(KEY_BOOT, bootMarker).remove(KEY_LAST_APP).apply()
        }
    }

    /**
     * Die zuletzt über Navigate gestartete App — **ohne** Abfrage.
     *
     * Für die laufende Aktualisierung der grünen Schrift (Sekundentakt) ist das
     * der billige Weg: die Nutzungsstatistik wird dort nur für „aktiv?“
     * befragt, der Grünton der zuletzt geöffneten App kommt aus dem Merker.
     */
    fun remembered(): String? = prefs.getString(KEY_LAST_APP, null)

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

    /**
     * Welche Apps sind **aktiv**? — die Grundlage der grünen Schrift im Raster.
     *
     * Ein Paket gilt als aktiv, wenn es in den letzten [ACTIVE_WINDOW_MS] im
     * Vordergrund war.
     *
     * Warum nicht „läuft gerade“: auf dem Zielgerät stoppt unser **opakes**
     * Fenster die App darunter — ihre Activity bekommt `ACTIVITY_STOPPED`, ihr
     * **Prozess** lebt aber weiter. Nachgemessen nach einem HOME-Druck: Netflix,
     * Disney+, WOW und der Play Store waren alle noch da, obwohl die Statistik
     * für sie `ACTIVITY_STOPPED` meldete. „Zuletzt im Vordergrund“ trifft genau
     * diesen Zustand — und die Nutzungsstatistik liefert ihn billig:
     * `UsageStats.getLastTimeUsed()` ist ein Zeitstempel je Paket, kein
     * Durchlauf über alle Ereignisse.
     *
     * Das Fenster beginnt nie vor dem letzten Systemstart: nach einem Neustart
     * ist keine App von davor noch aktiv.
     */
    fun recentlyUsed(): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (now - activeAt < ACTIVE_CACHE_MS) return activeCache
        val stats = usageStats ?: return emptySet()
        val wallNow = System.currentTimeMillis()
        val begin = maxOf(wallNow - ACTIVE_WINDOW_MS, wallNow - now)
        val usages = try {
            stats.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, wallNow)
        } catch (e: Exception) {
            // SecurityException ohne den App-Op — erwarteter Weg: alten Stand
            // behalten, statt alles grün oder alles grau zu machen.
            return activeCache
        }
        activeCache = usages
            .filter { it.lastTimeUsed >= begin && it.packageName != ownPackage }
            .map { it.packageName }
            .toSet()
        activeAt = now
        return activeCache
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

        /** So lange darf der „läuft noch?“-Stand wiederverwendet werden. */
        const val ACTIVE_CACHE_MS = 2_000L

        /** Fenster für „aktiv": 24 Stunden — nie vor dem letzten Systemstart. */
        const val ACTIVE_WINDOW_MS = 24 * 60 * 60 * 1000L

        /** Erwartungsgröße: ein kurzes Fenster füllt das ohne Nachwachsen. */
        const val INITIAL_CAPACITY = 32
    }
}
