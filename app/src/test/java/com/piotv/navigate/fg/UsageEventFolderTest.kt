package com.piotv.navigate.fg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Faltung der Nutzungsereignisse — die einzige Logik in der
 * Vordergrund-Erkennung. Die Fälle entsprechen dem, was auf dem Chromecast
 * gemessen wurde (siehe docs/ARCHITECTURE.md, Tabelle „Situation → Ereignisse").
 */
class UsageEventFolderTest {

    private val own = "com.piotv.navigate"

    private fun fold(vararg events: UsageEvent) =
        UsageEventFolder.fold(own, events.toList())

    @Test
    fun `leere Liste ergibt keine fremde App`() {
        val snapshot = UsageEventFolder.fold(own, emptyList())
        assertNull(snapshot.foreignPackage)
        assertFalse(snapshot.foreignAlive)
    }

    @Test
    fun `eigene Ereignisse werden ignoriert`() {
        // Genau der HOME-Druck: der Launcher steht selbst als RESUMED drin.
        val snapshot = fold(
            UsageEvent(own, UsageEventFolder.EVENT_RESUMED),
            UsageEvent(own, UsageEventFolder.EVENT_PAUSED),
        )
        assertNull(snapshot.foreignPackage)
        assertFalse(snapshot.foreignAlive)
    }

    @Test
    fun `gestartete App lebt`() {
        val snapshot = fold(
            UsageEvent(own, UsageEventFolder.EVENT_RESUMED),
            UsageEvent("com.netflix.ninja", UsageEventFolder.EVENT_RESUMED),
        )
        assertEquals("com.netflix.ninja", snapshot.foreignPackage)
        assertTrue(snapshot.foreignAlive)
    }

    @Test
    fun `pausierte App zaehlt als lebendig`() {
        // Der Normalfall, wenn das Dock über der App liegt.
        val snapshot = fold(
            UsageEvent("com.netflix.ninja", UsageEventFolder.EVENT_RESUMED),
            UsageEvent(own, UsageEventFolder.EVENT_RESUMED),
            UsageEvent("com.netflix.ninja", UsageEventFolder.EVENT_PAUSED),
        )
        assertEquals("com.netflix.ninja", snapshot.foreignPackage)
        assertTrue(snapshot.foreignAlive)
    }

    @Test
    fun `beendete App lebt nicht mehr`() {
        val snapshot = fold(
            UsageEvent("com.netflix.ninja", UsageEventFolder.EVENT_RESUMED),
            UsageEvent("com.netflix.ninja", UsageEventFolder.EVENT_PAUSED),
            UsageEvent("com.netflix.ninja", UsageEventFolder.EVENT_STOPPED),
        )
        assertEquals("com.netflix.ninja", snapshot.foreignPackage)
        assertFalse(snapshot.foreignAlive)
    }

    @Test
    fun `STOPPED einer anderen App aendert nichts`() {
        // Nur das neueste Leben der Kandidatin zählt, nicht das Ende eines
        // anderen Pakets.
        val snapshot = fold(
            UsageEvent("com.netflix.ninja", UsageEventFolder.EVENT_RESUMED),
            UsageEvent("com.termux", UsageEventFolder.EVENT_STOPPED),
        )
        assertEquals("com.netflix.ninja", snapshot.foreignPackage)
        assertTrue(snapshot.foreignAlive)
    }

    @Test
    fun `die letzte gestartete App gewinnt`() {
        val snapshot = fold(
            UsageEvent("com.netflix.ninja", UsageEventFolder.EVENT_RESUMED),
            UsageEvent("com.netflix.ninja", UsageEventFolder.EVENT_STOPPED),
            UsageEvent("com.termux", UsageEventFolder.EVENT_RESUMED),
        )
        assertEquals("com.termux", snapshot.foreignPackage)
        assertTrue(snapshot.foreignAlive)
    }

    @Test
    fun `PAUSED ohne vorheriges RESUMED ergibt keine fremde App`() {
        // Ereignisse vor dem Fensterbeginn sind unsichtbar — die Heuristik
        // (ModeDecider) übernimmt dann. Bewusst so: lieber keine Aussage als
        // eine geratene.
        val snapshot = fold(UsageEvent("com.netflix.ninja", UsageEventFolder.EVENT_PAUSED))
        assertNull(snapshot.foreignPackage)
        assertFalse(snapshot.foreignAlive)
    }

    @Test
    fun `unbekannte Ereignistypen stoeren nicht`() {
        val snapshot = fold(
            UsageEvent("com.netflix.ninja", UsageEventFolder.EVENT_RESUMED),
            UsageEvent("com.termux", 7),
        )
        assertEquals("com.netflix.ninja", snapshot.foreignPackage)
        assertTrue(snapshot.foreignAlive)
    }
}
