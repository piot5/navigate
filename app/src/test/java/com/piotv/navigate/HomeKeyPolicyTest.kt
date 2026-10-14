package com.piotv.navigate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die drei Regeln, die ein HOME-Druck auslöst.
 *
 * Die Fälle sind nicht ausgedacht: sie sind die Fehler, die auf dem Chromecast
 * aufgetreten sind (CHANGELOG, „Behoben") — Sackgasse nach dem ersten
 * HOME-Druck, nicht erkannter zweiter HOME-Druck, Home-Seite statt Zeile.
 * Bis 0.4 steckten diese Regeln in [LauncherActivity] und waren damit nur auf
 * einem Gerät prüfbar.
 */
class HomeKeyPolicyTest {

    // --- Zweiter HOME-Druck schließt die Zeile ----------------------------

    @Test
    fun `HOME bei stehender Zeile schliesst sie`() {
        assertTrue(HomeKeyPolicy.dismissesDock(intentIsHome = true, dockOnScreen = true))
    }

    @Test
    fun `HOME ohne Zeile schliesst nichts`() {
        assertFalse(HomeKeyPolicy.dismissesDock(intentIsHome = true, dockOnScreen = false))
    }

    @Test
    fun `ein App-Start schliesst die Zeile nicht`() {
        // Der Start über einen Favoriten oder aus der TV-Apps-Zeile
        // (LEANBACK_LAUNCHER) trägt die HOME-Kategorie nicht.
        assertFalse(HomeKeyPolicy.dismissesDock(intentIsHome = false, dockOnScreen = true))
    }

    @Test
    fun `zweiter HOME schliesst dritter HOME baut wieder auf`() {
        // Der Ablauf, der auf dem Gerät zweimal schiefging: HOME → Zeile,
        // HOME → zurück zur App, HOME → Zeile. Die Zeile ist dazwischen weg,
        // deshalb darf der dritte Druck nicht wieder schließen.
        var dockOnScreen = true
        assertTrue(HomeKeyPolicy.dismissesDock(intentIsHome = true, dockOnScreen = dockOnScreen))
        dockOnScreen = false // dismissDock()
        assertFalse(HomeKeyPolicy.dismissesDock(intentIsHome = true, dockOnScreen = dockOnScreen))
        dockOnScreen = true // showDock()
        assertTrue(HomeKeyPolicy.dismissesDock(intentIsHome = true, dockOnScreen = dockOnScreen))
    }

    @Test
    fun `die Entscheidung kennt den Lebenszyklus nicht`() {
        // Auf dem Zielgerät gemessen: onPause → onNewIntent → onResume. Ein
        // isResumed-Flag wäre in onNewIntent schon false. Diese Funktion hat
        // gar keinen Lebenszyklus als Eingabe — der Test hält fest, dass das
        // so bleibt.
        assertTrue(HomeKeyPolicy.dismissesDock(intentIsHome = true, dockOnScreen = true))
    }

    // --- Rückfall auf die Home-Seite in onResume --------------------------

    @Test
    fun `ohne HOME-Druck im Dock faellt onResume auf die Home-Seite zurueck`() {
        // Der Nutzer kommt ohne HOME zurück (die App davor wurde beendet) —
        // dann ist die Home-Seite die ehrliche Antwort.
        assertTrue(
            HomeKeyPolicy.fallsBackToGrid(
                homeIntentPending = false,
                launchedInternally = false,
                mode = LauncherMode.DOCK,
            )
        )
    }

    @Test
    fun `ein konsumierter HOME-Intent faellt nicht zurueck`() {
        assertFalse(
            HomeKeyPolicy.fallsBackToGrid(
                homeIntentPending = true,
                launchedInternally = false,
                mode = LauncherMode.DOCK,
            )
        )
    }

    @Test
    fun `der Rueckweg aus den eigenen Einstellungen faellt nicht zurueck`() {
        // Die Zeile war beim Öffnen der Einstellungen sichtbar und soll es
        // danach wieder sein.
        assertFalse(
            HomeKeyPolicy.fallsBackToGrid(
                homeIntentPending = false,
                launchedInternally = true,
                mode = LauncherMode.DOCK,
            )
        )
    }

    @Test
    fun `auf der Home-Seite gibt es nichts zurueckzufallen`() {
        assertFalse(
            HomeKeyPolicy.fallsBackToGrid(
                homeIntentPending = false,
                launchedInternally = false,
                mode = LauncherMode.GRID,
            )
        )
    }

    // --- Startart ---------------------------------------------------------

    @Test
    fun `aus der TV-Apps-Zeile gestartet zeigt die Home-Seite`() {
        // LEANBACK_LAUNCHER: der Nutzer will den Launcher sehen, nicht die
        // Zeile über der laufenden App — und die teure Vordergrund-Abfrage
        // entfällt.
        assertTrue(HomeKeyPolicy.mustShowGrid(isHomeIntent = false))
    }

    @Test
    fun `ein HOME-Intent entscheidet ueber den Vordergrund`() {
        assertFalse(HomeKeyPolicy.mustShowGrid(isHomeIntent = true))
    }

    @Test
    fun `ohne Intent entscheidet der Vordergrund`() {
        assertFalse(HomeKeyPolicy.mustShowGrid(isHomeIntent = null))
    }
}
