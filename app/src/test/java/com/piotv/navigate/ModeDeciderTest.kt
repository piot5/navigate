package com.piotv.navigate

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Die Modus-Entscheidung ist die einzige Stelle im Launcher mit echter
 * Verzweigungslogik — und die einzige, die sich ohne Gerät prüfen lässt.
 * Genau dafür ist [ModeDecider] frei von Android-Typen.
 *
 * Die Werte sind nicht ausgedacht: sie entsprechen den Fällen, die auf dem
 * Chromecast gemessen wurden (Netflix oben, HOME auf der Home-Seite, App über
 * Navigate gestartet, Dock steht schon).
 */
class ModeDeciderTest {

    private fun decide(
        foreground: ForegroundSnapshot? = null,
        appRunning: Boolean = false,
    ): LauncherMode = ModeDecider.decide(
        foreground = foreground,
        appRunning = appRunning,
    )

    // --- Mit Nutzungsstatistik: die Antwort kommt von dort -----------------

    @Test
    fun `laufende App ergibt die Zeile`() {
        assertEquals(
            LauncherMode.DOCK,
            decide(foreground = ForegroundSnapshot("com.netflix.ninja", foreignAlive = true)),
        )
    }

    @Test
    fun `pausierte App zaehlt als laufend`() {
        // Genau der Normalfall: die App ist pausiert, weil unser Dock darüber
        // liegt — sie läuft aber weiter (Video, Ton).
        assertEquals(
            LauncherMode.DOCK,
            decide(foreground = ForegroundSnapshot("com.netflix.ninja", foreignAlive = true)),
        )
    }

    @Test
    fun `beendete App ergibt die Home-Seite`() {
        assertEquals(
            LauncherMode.GRID,
            decide(foreground = ForegroundSnapshot("com.netflix.ninja", foreignAlive = false)),
        )
    }

    @Test
    fun `Statistik schlaegt die Heuristik`() {
        // Der Merker sagt "keine App gestartet", die Statistik sagt "läuft" —
        // wichtig für Apps, die NICHT über Navigate gestartet wurden.
        assertEquals(
            LauncherMode.DOCK,
            decide(
                foreground = ForegroundSnapshot("com.termux", foreignAlive = true),
                appRunning = false,
            ),
        )
        // Und umgekehrt: Merker sagt "läuft", die Statistik sagt "beendet".
        assertEquals(
            LauncherMode.GRID,
            decide(
                foreground = ForegroundSnapshot("com.termux", foreignAlive = false),
                appRunning = true,
            ),
        )
    }

    @Test
    fun `leere Statistik faellt auf die Heuristik zurueck`() {
        assertEquals(
            LauncherMode.GRID,
            decide(foreground = ForegroundSnapshot(null, foreignAlive = false), appRunning = false),
        )
        assertEquals(
            LauncherMode.DOCK,
            decide(foreground = ForegroundSnapshot(null, foreignAlive = false), appRunning = true),
        )
    }

    // --- Ohne Nutzungszugriff: Heuristik ---------------------------------

    @Test
    fun `ohne Merker ergibt sich die Home-Seite`() {
        assertEquals(LauncherMode.GRID, decide(appRunning = false))
    }

    @Test
    fun `nach dem Start einer App ergibt HOME die Zeile`() {
        assertEquals(LauncherMode.DOCK, decide(appRunning = true))
    }
}
