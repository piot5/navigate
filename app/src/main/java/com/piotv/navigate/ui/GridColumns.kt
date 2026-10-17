package com.piotv.navigate.ui

/**
 * Spaltenzahl des Rasters — reine Rechnung ohne Android-Typen, deshalb ohne
 * Gerät prüfbar (GridColumnsTest).
 *
 * Die Zahl entsteht aus der Bildschirmbreite: `Breite / [CELL_MIN_WIDTH_DP]`,
 * begrenzt auf [MIN_COLUMNS] bis [MAX_COLUMNS]. Auf dem Chromecast (960dp)
 * ergibt das 6 Spalten — dieselbe Zahl, die README und docs/CONFIG.md nennen.
 *
 * Es gibt **keine** Einstellung dafür: einstellbar ist in dieser Oberfläche nur
 * die Reihenfolge der Apps. Wer die Spaltenzahl ändern will, ändert die
 * Zellbreite hier — eine Zahl, an einer Stelle, mit Test.
 */
object GridColumns {

    /** Breite, die eine Rasterzelle mindestens bekommen soll. */
    const val CELL_MIN_WIDTH_DP = 150

    /** Untergrenze — darunter wird das Raster auf TV unbedienbar. */
    const val MIN_COLUMNS = 4

    /** Obergrenze — darüber werden die Zellen zu schmal für Text. */
    const val MAX_COLUMNS = 8

    fun compute(widthDp: Int): Int = (widthDp / CELL_MIN_WIDTH_DP).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
}

