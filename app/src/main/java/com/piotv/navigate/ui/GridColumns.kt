package com.piotv.navigate.ui

/**
 * Spaltenzahl des App-Rasters — reine Rechnung ohne Android-Typen, deshalb ohne
 * Gerät prüfbar (GridColumnsTest).
 *
 * Zwei Quellen, in dieser Reihenfolge:
 *  1. `columns` aus der Konfigurationsdatei. Der Parser begrenzt den Wert auf
 *     0–12; alles über 0 gilt **unverändert**, auch wenn die Zellen dadurch
 *     schmaler werden. Der Wert ist für andere Geräte gedacht (4K-Fernseher mit
 *     anderer Dichte), nicht für die Automatik.
 *  2. `0` (oder ein fehlender Schlüssel) heißt automatisch:
 *     Bildschirmbreite / [CELL_MIN_WIDTH_DP], begrenzt auf [MIN_COLUMNS] bis
 *     [MAX_COLUMNS].
 *
 * Auf dem Chromecast (960dp) ergibt die Automatik 6 Spalten — dieselbe Zahl,
 * die README und docs/CONFIG.md nennen.
 *
 * Warum das hier steht und nicht in der Activity: die Rechnung war vorher eine
 * private Methode von `LauncherActivity` und damit ungetestet. Verhalten
 * unverändert — nur der Ort ist ein anderer.
 */
object GridColumns {

    /** Breite, die eine Rasterzelle mindestens bekommen soll. */
    const val CELL_MIN_WIDTH_DP = 150

    /** Untergrenze der Automatik — darunter wird das Raster auf TV unbedienbar. */
    const val MIN_COLUMNS = 4

    /** Obergrenze der Automatik — darüber werden die Zellen zu schmal für Text. */
    const val MAX_COLUMNS = 8

    fun compute(configured: Int, widthDp: Int): Int {
        if (configured > 0) return configured
        return (widthDp / CELL_MIN_WIDTH_DP).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
    }
}
