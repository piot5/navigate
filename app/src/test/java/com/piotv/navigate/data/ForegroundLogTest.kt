package com.piotv.navigate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Die Faltung der Vordergrund-Ereignisse: „welche App war zuletzt vorne?"
 *
 * Das ist die Grundlage für „HOME oder BACK auf der Home-Seite → zurück in die
 * letzte App". Ein Fehler hier ist sofort spürbar: es würde die falsche App
 * (oder die Home-Seite selbst) nach vorne geholt.
 */
class ForegroundLogTest {

    private val own = "com.piotv.navigate"

    @Test
    fun `ohne Ereignisse gibt es keine letzte App`() {
        assertNull(ForegroundLog.lastResumed(own, emptyList()))
    }

    @Test
    fun `das letzte Vordergrund-Ereignis gewinnt`() {
        val events = listOf(
            "com.netflix.ninja" to ForegroundLog.RESUMED,
            "com.disney.disneyplus" to ForegroundLog.RESUMED,
        )
        assertEquals("com.disney.disneyplus", ForegroundLog.lastResumed(own, events))
    }

    @Test
    fun `der eigene Launcher zaehlt nicht`() {
        // Beim HOME-Druck steht der Launcher selbst als RESUMED in der
        // Statistik. Ohne diesen Filter käme man nie in die App zurück.
        val events = listOf(
            "com.netflix.ninja" to ForegroundLog.RESUMED,
            own to ForegroundLog.RESUMED,
        )
        assertEquals("com.netflix.ninja", ForegroundLog.lastResumed(own, events))
    }

    @Test
    fun `andere Ereignistypen aendern nichts`() {
        // PAUSED/STOPPED (hier als andere Zahlen) sagen nichts darüber, welche
        // App vorne war.
        val events = listOf(
            "com.netflix.ninja" to ForegroundLog.RESUMED,
            "com.disney.disneyplus" to 2,
            "com.disney.disneyplus" to 23,
        )
        assertEquals("com.netflix.ninja", ForegroundLog.lastResumed(own, events))
    }

    @Test
    fun `nur der eigene Launcher ergibt nichts`() {
        val events = listOf(own to ForegroundLog.RESUMED, own to ForegroundLog.RESUMED)
        assertNull(ForegroundLog.lastResumed(own, events))
    }

    // --- „aktiv?" (grüne Schrift im Raster) -------------------------------
    //    Die Frage selbst beantwortet die Nutzungsstatistik über Zeitstempel
    //    (LastApp.recentlyUsed) — hier steht nur die Faltung für „welche App war
    //    zuletzt vorne?".

    @Test
    fun `das letzte Vordergrund-Ereignis gewinnt auch bei vielen Paketen`() {
        val events = listOf(
            "com.netflix.ninja" to ForegroundLog.RESUMED,
            "com.wbd.stream" to ForegroundLog.RESUMED,
            "com.disney.disneyplus" to ForegroundLog.RESUMED,
        )
        assertEquals("com.disney.disneyplus", ForegroundLog.lastResumed(own, events))
    }
}
