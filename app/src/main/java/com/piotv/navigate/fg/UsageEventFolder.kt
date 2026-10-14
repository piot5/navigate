package com.piotv.navigate.fg

import com.piotv.navigate.ForegroundSnapshot

/**
 * Ein Nutzungsereignis, reduziert auf das, was Navigate braucht.
 *
 * Warum eine eigene Klasse statt `UsageEvents.Event`: die Faltung darunter ist
 * die einzige Stelle mit echter Logik in der Vordergrund-Erkennung, und sie soll
 * ohne Gerät prüfbar sein (UsageEventFolderTest).
 */
data class UsageEvent(
    val packageName: String,
    val eventType: Int,
)

/**
 * Faltet eine Ereignisliste zum Vordergrund-Zustand.
 *
 * Die Regel in Worten: die letzte **fremde** App, die in den Vordergrund kam,
 * ist die Kandidatin. Ihr neuestes Lebenszyklus-Ereignis entscheidet, ob sie
 * noch lebt — `PAUSED` und `RESUMED` heißen „lebt" (pausiert ist der Normalfall,
 * wenn das Dock darüber liegt), `STOPPED` heißt „beendet".
 *
 * Warum nicht „das neueste Vordergrund-Ereignis": beim HOME-Druck steht der
 * Launcher selbst schon als `RESUMED` in der Statistik (auf dem Zielgerät
 * gemessen). Deshalb fällt der eigene Eintrag hier grundsätzlich weg.
 *
 * Reines Kotlin ohne Android-Typen → testbar ohne Gerät.
 */
object UsageEventFolder {

    /**
     * `MOVE_TO_FOREGROUND` (API 21) und `ACTIVITY_RESUMED` (API 29) sind
     * derselbe Wert; ebenso `MOVE_TO_BACKGROUND` und `ACTIVITY_PAUSED`.
     * `ACTIVITY_STOPPED` gibt es erst ab API 29 — die Zahlen sind stabil.
     */
    const val EVENT_RESUMED = 1
    const val EVENT_PAUSED = 2
    const val EVENT_STOPPED = 23

    fun fold(ownPackage: String, events: List<UsageEvent>): ForegroundSnapshot {
        var foreignPackage: String? = null
        var foreignAlive = false
        for (event in events) {
            if (event.packageName == ownPackage) continue
            when (event.eventType) {
                EVENT_RESUMED -> {
                    foreignPackage = event.packageName
                    foreignAlive = true
                }

                EVENT_PAUSED -> if (event.packageName == foreignPackage) foreignAlive = true
                EVENT_STOPPED -> if (event.packageName == foreignPackage) foreignAlive = false
            }
        }
        return ForegroundSnapshot(foreignPackage, foreignAlive)
    }
}
