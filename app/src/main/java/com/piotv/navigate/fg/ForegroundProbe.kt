package com.piotv.navigate.fg

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.SystemClock
import com.piotv.navigate.ForegroundSnapshot

/**
 * Ermittelt, ob hinter dem Launcher noch eine App lebt.
 *
 * Warum UsageStatsManager und nicht ActivityManager: `getRunningTasks` und
 * `getRunningAppProcesses` liefern seit Android 5 bzw. 5.1 für normale Apps nur
 * noch die eigenen Prozesse — für einen Launcher wertlos. UsageStats ist der
 * einzige offizielle Weg, und er braucht den Nutzungszugriff (App-Op
 * GET_USAGE_STATS), den ein Launcher nicht zur Laufzeit erfragen kann.
 *
 * Ohne diese Berechtigung liefert [snapshot] `null`, und der Aufrufer entscheidet
 * über die Heuristik in [com.piotv.navigate.ModeDecider].
 *
 * ## Zwei Fenster (der Performance-Kern dieser Klasse)
 *
 * `queryEvents` kostet nicht die Abfrage, sondern die **Ereignismenge**: ein
 * Fenster über sechs Stunden enthält auf einem benutzten Gerät Tausende
 * Ereignisse, und die wurden bei **jedem HOME-Druck** auf dem Main-Thread
 * durchlaufen (128 ms gesamt, gemessen — docs/DEVICE_PERF.md).
 *
 * Deshalb zwei Durchgänge:
 *  1. **[SHORT_WINDOW_MS]** (5 Minuten) — der Normalfall. Wer HOME drückt, hatte
 *     die App Sekunden vorher im Vordergrund; in diesem Fenster liegen eine
 *     Handvoll Ereignisse.
 *  2. **[LONG_WINDOW_MS]** (6 Stunden) — nur, wenn der kurze Durchgang keine
 *     fremde App gefunden hat (die App lief vor Minuten). Dann ist die Abfrage
 *     selten, und die Heuristik aus [com.piotv.navigate.data.Prefs] entscheidet
 *     praktisch immer richtig.
 *
 * Beide Fenster beginnen nie vor dem letzten Systemstart: nach einem Neustart
 * ist kein Ereignis von davor mehr gültig, und eine pausierte App von gestern
 * wäre eine falsche Antwort.
 */
class ForegroundProbe(context: Context) {

    private val appContext = context.applicationContext
    private val usageStats: UsageStatsManager? =
        appContext.getSystemService(UsageStatsManager::class.java)
    private val ownPackage: String = appContext.packageName

    /**
     * Zustand des Vordergrunds oder `null`, wenn er nicht ermittelbar ist.
     *
     * Der kurze Durchgang entscheidet im Normalfall; nur wenn er keine fremde
     * App sieht, wird das lange Fenster befragt.
     */
    fun snapshot(): ForegroundSnapshot? {
        val stats = usageStats ?: return null
        val now = System.currentTimeMillis()
        val boot = bootWallClock()

        val shortBegin = maxOf(now - SHORT_WINDOW_MS, boot)
        val recent = query(stats, shortBegin, now) ?: return null
        if (recent.foreignPackage != null) return recent

        // Nichts im kurzen Fenster: die App lief vor mehr als 5 Minuten. Das
        // lange Fenster ist die ehrliche Absicherung — und wird deshalb selten
        // bezahlt.
        val longBegin = maxOf(now - LONG_WINDOW_MS, boot)
        if (longBegin >= shortBegin) return recent
        return query(stats, longBegin, now) ?: recent
    }

    /**
     * Fragt ein Fenster ab und faltet es. `null` heißt: nicht ermittelbar (kein
     * Nutzungszugriff, unerwarteter Fehler) — der Aufrufer entscheidet dann über
     * die Heuristik.
     */
    private fun query(stats: UsageStatsManager, begin: Long, end: Long): ForegroundSnapshot? {
        val events = try {
            stats.queryEvents(begin, end)
        } catch (e: Exception) {
            // SecurityException ohne Nutzungszugriff — erwarteter Weg.
            return null
        }

        val collected = ArrayList<UsageEvent>(INITIAL_EVENT_CAPACITY)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName ?: continue
            // Der eigene Launcher zählt nicht: er steht beim HOME-Druck selbst
            // als RESUMED in der Statistik (auf dem Zielgerät gemessen). Hier
            // schon gefiltert, damit im langen Fenster keine Objekte für ihn
            // entstehen — [UsageEventFolder.fold] prüft es noch einmal, weil
            // genau dort die testbare Wahrheit steht.
            if (packageName == ownPackage) continue
            collected.add(UsageEvent(packageName, event.eventType))
        }
        return UsageEventFolder.fold(ownPackage, collected)
    }

    /** Wanduhrzeit des letzten Systemstarts — `elapsedRealtime` zählt ab dem Start. */
    private fun bootWallClock(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    private companion object {
        /**
         * 5 Minuten: lang genug für jede Folge „App ansehen → HOME drücken"
         * (auch nach einem kurzen Ausflug in die Einstellungen), kurz genug für
         * eine Ereignisliste von einer Handvoll Einträgen.
         */
        const val SHORT_WINDOW_MS = 5 * 60 * 1000L

        /**
         * 6 Stunden: lang genug für jede Fernsehsitzung, kurz genug, dass die
         * Ereignisliste klein bleibt. Nur der zweite Durchgang.
         */
        const val LONG_WINDOW_MS = 6 * 60 * 60 * 1000L

        /** Erwartungsgröße: ein kurzes Fenster füllt das ohne Nachwachsen. */
        const val INITIAL_EVENT_CAPACITY = 32
    }
}
