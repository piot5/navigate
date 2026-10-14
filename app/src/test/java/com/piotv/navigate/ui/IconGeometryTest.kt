package com.piotv.navigate.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Einpassung eines Symbols in eine quadratische Fläche.
 *
 * Sichtbar, aber bis 0.4 ungetestet: ein 16:9-TV-Banner (leanback-only Apps
 * haben oft keinen quadratischen Icon) darf nicht verzerrt werden, und ein
 * Symbol ohne Größenangabe muss die Fläche füllen.
 */
class IconGeometryTest {

    @Test
    fun `quadratische Quelle fuellt die Flaeche exakt`() {
        assertEquals(IconGeometry.Fit(0, 0, 128, 128), IconGeometry.fit(128, 128, 128))
    }

    @Test
    fun `kleinere Quelle wird vergroessert`() {
        // Ein 48-px-Symbol wird auf die Zielgröße skaliert, nicht in der Mitte
        // kleingelassen.
        assertEquals(IconGeometry.Fit(0, 0, 128, 128), IconGeometry.fit(48, 48, 128))
    }

    @Test
    fun `ein 16 zu 9 Banner wird eingepasst und zentriert`() {
        // 160×90 in 128: scale = 0,8 → 128×72, oben und unten je 28 px Luft.
        assertEquals(IconGeometry.Fit(0, 28, 128, 72), IconGeometry.fit(160, 90, 128))
    }

    @Test
    fun `ein hochkantes Symbol wird links und rechts zentriert`() {
        assertEquals(IconGeometry.Fit(28, 0, 72, 128), IconGeometry.fit(90, 160, 128))
    }

    @Test
    fun `ohne Groessenangabe fuellt die Quelle die Flaeche`() {
        // Adaptive und vektorielle Symbole melden keine Größe.
        assertEquals(IconGeometry.Fit(0, 0, 64, 64), IconGeometry.fit(0, 0, 64))
        assertEquals(IconGeometry.Fit(0, 0, 64, 64), IconGeometry.fit(-1, 10, 64))
    }

    @Test
    fun `eine extrem flache Quelle behaelt mindestens ein Pixel Hoehe`() {
        // 1000×1 bei 8 px: ohne die Untergrenze wäre die Höhe 0 und draw()
        // würde nichts zeichnen.
        val fit = IconGeometry.fit(1000, 1, 8)
        assertEquals(8, fit.width)
        assertEquals(1, fit.height)
        assertEquals(0, fit.left)
        assertEquals(3, fit.top)
    }

    @Test
    fun `das Ziel bleibt immer innerhalb der Flaeche`() {
        for (width in listOf(1, 3, 7, 64, 101, 1000)) {
            for (size in listOf(1, 8, 52, 64, 128)) {
                val fit = IconGeometry.fit(width, width * 3, size)
                assertTrue("left $fit", fit.left >= 0)
                assertTrue("top $fit", fit.top >= 0)
                assertTrue("Breite $fit", fit.left + fit.width <= size)
                assertTrue("Hoehe $fit", fit.top + fit.height <= size)
            }
        }
    }

    @Test
    fun `die Ecken sind 22 Prozent der Kantenlaenge`() {
        assertEquals(28.16f, IconGeometry.cornerRadius(128), 0.01f)
        assertEquals(11.44f, IconGeometry.cornerRadius(52), 0.01f)
        assertEquals(IconGeometry.CORNER_RADIUS_RATIO, 0.22f, 0.0001f)
    }
}
