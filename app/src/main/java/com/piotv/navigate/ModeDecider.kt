package com.piotv.navigate

/**
 * Entscheidet, was ein HOME-Druck zeigt.
 *
 * Reines Kotlin ohne Android-Typen: das ist die einzige Stelle im Launcher mit
 * echter Verzweigungslogik und damit die einzige, die ohne Gerät prüfbar ist
 * (siehe ModeDeciderTest).
 */
object ModeDecider {

    /**
     * @param foreground Zustand des Vordergrunds aus der Nutzungsstatistik —
     *   `null`, wenn er nicht ermittelbar ist (kein Nutzungszugriff).
     * @param appRunning Merker aus früheren Starts: wurde zuletzt über Navigate
     *   eine App gestartet und ist seither nichts anderes passiert?
     */
    fun decide(
        foreground: ForegroundSnapshot?,
        appRunning: Boolean,
    ): LauncherMode {
        // Fall 1: Nutzungsstatistik vorhanden — die Antwort kommt von dort.
        if (foreground != null && foreground.foreignPackage != null) {
            // Läuft die App noch (pausiert zählt als laufend, z. B. weil unser
            // Dock darüber liegt) → Zeile. Ist sie beendet → Home-Seite.
            return if (foreground.foreignAlive) LauncherMode.DOCK else LauncherMode.GRID
        }

        // Fall 2: Heuristik ohne Nutzungszugriff. Die Home-Seite ist über das
        // Home-Symbol in der Zeile erreichbar, deshalb braucht diese
        // Entscheidung keinen Sonderfall für den zweiten HOME-Druck.
        return if (appRunning) LauncherMode.DOCK else LauncherMode.GRID
    }
}
