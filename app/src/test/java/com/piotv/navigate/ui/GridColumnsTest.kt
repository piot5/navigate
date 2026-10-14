package com.piotv.navigate.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Die Spaltenzahl des Rasters. Sie entscheidet, wie viele Apps gleichzeitig
 * sichtbar sind — auf dem Chromecast sind es 24 (6 × 4). Bis 0.3 war die
 * Rechnung eine private Methode der Activity und damit ungetestet.
 *
 * Die Breiten sind die realen Fälle: 960dp ist der Chromecast (1920 px bei
 * Dichte 320), 540dp ein Handy im Querformat, 1920dp ein 4K-Fernseher mit
 * Dichte 160.
 */
class GridColumnsTest {

    // --- Konfigurierte Spaltenzahl gewinnt --------------------------------

    @Test
    fun `konfigurierte Spaltenzahl gilt unveraendert`() {
        assertEquals(5, GridColumns.compute(configured = 5, widthDp = 960))
    }

    @Test
    fun `konfigurierte Spaltenzahl wird auch bei schmalem Bildschirm nicht beschnitten`() {
        // Bewusste Entscheidung: wer eine Zahl einträgt, will sie — die
        // Automatik-Grenzen 4–8 gelten nur für die Automatik.
        assertEquals(12, GridColumns.compute(configured = 12, widthDp = 540))
        assertEquals(1, GridColumns.compute(configured = 1, widthDp = 1920))
    }

    // --- Automatik (columns=0) --------------------------------------------

    @Test
    fun `null bedeutet Automatik`() {
        // 0 ist der Standardwert aus ConfigCodec, ein fehlender Schlüssel also
        // derselbe Fall.
        assertEquals(6, GridColumns.compute(configured = 0, widthDp = 960))
    }

    @Test
    fun `Automatik auf dem Chromecast ergibt sechs Spalten`() {
        // 960dp / 150dp = 6,4 → 6 Spalten à 160dp. Genau die Zahl aus README
        // und docs/CONFIG.md; weicht der Wert ab, ist eine der beiden Stellen
        // falsch.
        assertEquals(6, GridColumns.compute(configured = 0, widthDp = 960))
    }

    @Test
    fun `Automatik faellt nie unter vier Spalten`() {
        assertEquals(GridColumns.MIN_COLUMNS, GridColumns.compute(configured = 0, widthDp = 540))
        assertEquals(GridColumns.MIN_COLUMNS, GridColumns.compute(configured = 0, widthDp = 0))
    }

    @Test
    fun `Automatik geht nie ueber acht Spalten`() {
        assertEquals(GridColumns.MAX_COLUMNS, GridColumns.compute(configured = 0, widthDp = 1920))
        assertEquals(GridColumns.MAX_COLUMNS, GridColumns.compute(configured = 0, widthDp = 99_999))
    }

    @Test
    fun `Automatik rechnet mit 150dp je Zelle`() {
        assertEquals(4, GridColumns.compute(configured = 0, widthDp = 600))
        assertEquals(5, GridColumns.compute(configured = 0, widthDp = 750))
        assertEquals(7, GridColumns.compute(configured = 0, widthDp = 1_100))
    }
}
