package com.piotv.navigate.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Das sichtbare Fenster über den Now-Playing-Zeilen.
 *
 * Die Zahlen sind die des Zielgeräts in dp: eine Zeile ist 52 dp hoch, zwischen
 * zwei Zeilen liegen 4 dp, und der Block ist auf 260 dp begrenzt
 * (`now_playing_max_height`) — also passen genau fünf Zeilen ins Fenster. Genau
 * diese fünf sind die, die der Fokus-Sucher anbieten darf; alles darunter liegt
 * im Layout weiter unten und damit geometrisch dort, wo das Raster anfängt.
 *
 * Der Fehler aus 0.15.0 steht hier als Test: die Zeilen unterhalb des Fensters
 * waren Fokus-Kandidaten, ein ▲ aus dem Raster landete auf einer Zeile, die
 * niemand sieht.
 */
class RowWindowTest {

    /** Acht Zeilen à 52 dp mit 4 dp Abstand — der Fall aus dem echten Block. */
    private val rows = (0 until 8).map { RowWindow.Row(top = it * 56, height = 52) }

    /** Der Inhalt: acht Zeilen mit Abstand, ohne den Abstand nach der letzten. */
    private val content = rows.size * 56 - 4

    private val viewport = 260

    @Test
    fun `eine Zeile ganz im Fenster aendert den Stand nicht`() {
        assertEquals(0, RowWindow.scrollFor(rows[3], viewport, content, scrollY = 0))
        // Unterkante 220 dp, Fenster bis 260 dp: es gibt nichts zu tun.
        assertEquals(0, RowWindow.scrollFor(RowWindow.Row(168, 52), viewport, content, 0))
    }

    @Test
    fun `eine Zeile unterhalb des Fensters wird an die Unterkante geholt`() {
        // Zeile 4 liegt bei 224..276; das Fenster endet bei 260 → 16 dp weiter.
        assertEquals(16, RowWindow.scrollFor(rows[4], viewport, content, scrollY = 0))
    }

    @Test
    fun `eine Zeile oberhalb des Fensters wird an die Oberkante geholt`() {
        assertEquals(168, RowWindow.scrollFor(rows[3], viewport, content, scrollY = 184))
    }

    @Test
    fun `der Stand bleibt innerhalb des Inhalts`() {
        // Die letzte Zeile (392..444) bei 260 dp Fenster: 184 dp sind das Ende,
        // weiter kann ein ScrollView nicht scrollen.
        assertEquals(184, RowWindow.scrollFor(rows[7], viewport, content, scrollY = 0))
        assertEquals(184, RowWindow.scrollFor(rows[7], viewport, content, scrollY = 999))
    }

    @Test
    fun `ein Inhalt kuerzer als das Fenster scrollt nie`() {
        // Drei Zeilen in einem 260-dp-Fenster: es gibt nichts zu scrollen.
        val short = RowWindow.Row(top = 112, height = 52)
        assertEquals(0, RowWindow.scrollFor(short, viewport, contentHeight = 164, scrollY = 0))
        assertEquals(0, RowWindow.scrollFor(short, viewport, contentHeight = 164, scrollY = 40))
    }

    @Test
    fun `ohne Fenster wird nicht gescrollt`() {
        // Vor dem ersten Messen ist die Höhe 0 — dann gibt es keine Ausrichtung,
        // und ein geratener Wert wäre schlimmer als keiner.
        assertEquals(0, RowWindow.scrollFor(rows[4], viewportHeight = 0, content, scrollY = 100))
        assertFalse(RowWindow.isVisible(rows[0], viewportHeight = 0, scrollY = 0))
    }

    @Test
    fun `ein Durchlauf nach unten und wieder hinauf trifft dieselben Staende`() {
        // Die vier ersten Zeilen brauchen keinen Scroll; ab Zeile 5 wandert das
        // Fenster mit, und beim Zurückgehen kommt derselbe Stand wieder.
        var scrollY = 0
        val down = rows.indices.map { index ->
            scrollY = RowWindow.scrollFor(rows[index], viewport, content, scrollY)
            scrollY
        }
        assertEquals(listOf(0, 0, 0, 0, 16, 72, 128, 184), down)
        val up = intArrayOf(0, 56, 112, 168, 184, 184, 184, 184)
        scrollY = 184
        for (index in rows.indices.reversed()) {
            scrollY = RowWindow.scrollFor(rows[index], viewport, content, scrollY)
            assertEquals("Zeile $index", up[index], scrollY)
        }
    }

    @Test
    fun `nur eine ganz sichtbare Zeile ist ein Fokus-Kandidat`() {
        // Fenster 0..260: Zeile 3 (168..220) liegt ganz darin, Zeile 4
        // (224..276) ragt heraus.
        assertTrue(RowWindow.isVisible(rows[3], viewport, scrollY = 0))
        assertFalse(RowWindow.isVisible(rows[4], viewport, scrollY = 0))
        // Oben dasselbe: bei Stand 184 ragt Zeile 3 (168..220) oben heraus.
        assertTrue(RowWindow.isVisible(rows[4], viewport, scrollY = 184))
        assertFalse(RowWindow.isVisible(rows[3], viewport, scrollY = 184))
    }

    @Test
    fun `die Raender des Fensters zaehlen als sichtbar`() {
        // Genau auf der Kante ist „ganz sichtbar“ — sonst fiele die erste Zeile
        // eines ungescrollten Blocks aus der Auswahl.
        assertTrue(RowWindow.isVisible(RowWindow.Row(0, 52), viewport, scrollY = 0))
        assertTrue(RowWindow.isVisible(RowWindow.Row(208, 52), viewport, scrollY = 0))
        assertFalse(RowWindow.isVisible(RowWindow.Row(209, 52), viewport, scrollY = 0))
    }
}
