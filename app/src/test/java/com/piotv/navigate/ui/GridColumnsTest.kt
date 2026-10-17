package com.piotv.navigate.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Die Spaltenzahl des Rasters. Sie entscheidet, wie viele Apps in der ersten
 * Zeile stehen (und damit, wie viele man ohne Tastendruck sieht), und wie viele
 * Zeilen beim Aufklappen entstehen.
 *
 * Die Breiten sind die realen Fälle: 960dp ist der Chromecast (1920 px bei
 * Dichte 320), 540dp ein Handy im Querformat, 1920dp ein 4K-Fernseher mit
 * Dichte 160.
 */
class GridColumnsTest {

    @Test
    fun `Automatik auf dem Chromecast ergibt sechs Spalten`() {
        // 960dp / 150dp = 6,4 → 6 Spalten à 160dp. Genau die Zahl aus README und
        // docs/CONFIG.md; weicht der Wert ab, ist eine der beiden Stellen falsch.
        assertEquals(6, GridColumns.compute(960))
    }

    @Test
    fun `die Automatik faellt nie unter vier Spalten`() {
        assertEquals(GridColumns.MIN_COLUMNS, GridColumns.compute(540))
        assertEquals(GridColumns.MIN_COLUMNS, GridColumns.compute(0))
    }

    @Test
    fun `die Automatik geht nie ueber acht Spalten`() {
        assertEquals(GridColumns.MAX_COLUMNS, GridColumns.compute(1920))
        assertEquals(GridColumns.MAX_COLUMNS, GridColumns.compute(99_999))
    }

    @Test
    fun `die Automatik rechnet mit 150dp je Zelle`() {
        assertEquals(4, GridColumns.compute(600))
        assertEquals(5, GridColumns.compute(750))
        assertEquals(7, GridColumns.compute(1_100))
    }
}
