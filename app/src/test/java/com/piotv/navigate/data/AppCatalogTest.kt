package com.piotv.navigate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Regeln der App-Liste: welche Kandidaten gewinnen, welche wegfallen, wie
 * sortiert wird.
 *
 * Der wichtigste Fall ist der erste: Apps, die **nur**
 * `CATEGORY_LEANBACK_LAUNCHER` tragen, fehlten im Raster — auf dem Zielgerät
 * waren das Prime Video, WOW und ARD (docs/DEVICE_PERF.md). Diese Regeln
 * standen bis 0.4 zwischen PackageManager-Aufrufen und waren ungetestet.
 */
class AppCatalogTest {

    /** Fester Vergleicher statt Collator: der Test soll nicht von der
     *  Gerätesprache abhängen. */
    private val order = String.CASE_INSENSITIVE_ORDER

    private fun candidate(
        packageName: String,
        label: String = packageName,
        enabled: Boolean = true,
        activityName: String = "$packageName.Main",
    ) = AppCatalog.Candidate(
        packageName = packageName,
        activityName = activityName,
        label = label,
        enabled = enabled,
    )

    private fun select(
        vararg candidates: AppCatalog.Candidate,
        own: String = "com.piotv.navigate",
    ): List<AppCatalog.Candidate> = AppCatalog.select(candidates.toList(), own, order)

    @Test
    fun `nur leanback deklarierte Apps stehen in der Liste`() {
        val selected = select(
            candidate("com.netflix.ninja", "Netflix"),
            candidate("com.amazon.amazonvideo.livingroom", "Prime Video"),
            candidate("com.wbd.stream", "WOW"),
            candidate("de.swr.avp.ard.tv", "ARD"),
        )
        assertEquals(4, selected.size)
        assertTrue(selected.any { it.packageName == "com.amazon.amazonvideo.livingroom" })
        assertTrue(selected.any { it.packageName == "com.wbd.stream" })
        assertTrue(selected.any { it.packageName == "de.swr.avp.ard.tv" })
    }

    @Test
    fun `der erste Kandidat je Paket gewinnt`() {
        // Der Aufrufer sammelt LAUNCHER vor LEANBACK_LAUNCHER — der Eintrag des
        // Herstellers gewinnt damit gegen den TV-Eintrag.
        val selected = select(
            candidate("com.netflix.ninja", "Netflix", activityName = "com.netflix.ninja.MainActivity"),
            candidate("com.netflix.ninja", "Netflix (TV)", activityName = "com.netflix.ninja.TvActivity"),
        )
        assertEquals(1, selected.size)
        assertEquals("com.netflix.ninja.MainActivity", selected.single().activityName)
        assertEquals("Netflix", selected.single().label)
    }

    @Test
    fun `deaktivierte Apps fallen weg`() {
        val selected = select(
            candidate("com.termux", "Termux"),
            candidate("com.disabled.app", "Deaktiviert", enabled = false),
        )
        assertEquals(listOf("com.termux"), selected.map { it.packageName })
    }

    @Test
    fun `der eigene Launcher faellt weg — Release und Debug`() {
        val selected = select(
            candidate("com.piotv.navigate", "Navigate"),
            candidate("com.piotv.navigate.debug", "Navigate (Debug)"),
            candidate("com.termux", "Termux"),
        )
        assertEquals(listOf("com.termux"), selected.map { it.packageName })
    }

    @Test
    fun `auch der Debug-Build erkennt sich selbst`() {
        // Der Debug-Build hat den Paketnamen com.piotv.navigate.debug; beide
        // Varianten müssen aus seinem Raster fallen.
        val selected = select(
            candidate("com.piotv.navigate", "Navigate"),
            candidate("com.piotv.navigate.debug", "Navigate (Debug)"),
            own = "com.piotv.navigate.debug",
        )
        assertTrue(selected.isEmpty())
    }

    @Test
    fun `leere Beschriftung wird durch den Paketnamen ersetzt`() {
        val selected = select(candidate("com.ohne.name", label = "   "))
        assertEquals("com.ohne.name", selected.single().label)
    }

    @Test
    fun `sortiert wird ueber den uebergebenen Vergleicher`() {
        val selected = select(
            candidate("com.z", "Zebra"),
            candidate("com.a", "apfel"),
            candidate("com.m", "Mango"),
        )
        // CASE_INSENSITIVE_ORDER: apfel, Mango, Zebra — nicht „Mango, Zebra,
        // apfel" (Zeichencode).
        assertEquals(listOf("apfel", "Mango", "Zebra"), selected.map { it.label })
    }

    @Test
    fun `leere Eingabe ergibt eine leere Liste`() {
        assertTrue(select().isEmpty())
    }

    @Test
    fun `Reihenfolge der Ausgabe ist stabil`() {
        // Zweimal dieselbe Eingabe → dieselbe Ausgabe (die Sortierung ist
        // total; bei gleichen Beschriftungen entscheidet die Einfügereihenfolge).
        val input = arrayOf(
            candidate("com.b", "Gleich"),
            candidate("com.a", "Gleich"),
            candidate("com.c", "Anders"),
        )
        assertEquals(select(*input).map { it.packageName }, select(*input).map { it.packageName })
    }
}
