package com.piotv.navigate.ui

/**
 * Das sichtbare Fenster über den Now-Playing-Zeilen — reine Rechnung ohne
 * Android-Typen, deshalb ohne Gerät prüfbar (RowWindowTest).
 *
 * Die Rechnung ist dieselbe, die `ScrollView` intern in
 * `computeScrollDeltaToGetChildRectOnScreen` anstellt: das Fenster ist
 * `[scrollY, scrollY + viewportHeight]`, ohne Ränder und ohne Polster. Sie steht
 * hier getrennt, weil [PanelScrollView] sie **vor** dem Fokuswechsel braucht —
 * erst scrollen, dann den Fokus geben. Andersherum springt der Fokus-Sucher des
 * Frameworks eine Zeile an, die noch außerhalb des Fensters liegt: genau der
 * Fehler aus 0.15.0, in dem der Verlauf den Bildschirm gefüllt hat.
 */
object RowWindow {

    /**
     * Eine Zeile, wie sie im Block liegt: Oberkante und Höhe im **Inhaltsmaß**
     * (nicht in Bildschirmkoordinaten). Die Oberkante bleibt beim Scrollen
     * stehen — sie beschreibt den Inhalt, nicht das, was man sieht.
     */
    data class Row(val top: Int, val height: Int)

    /**
     * Liegt die Zeile **ganz** im Fenster?
     *
     * „Ganz“ ist die Bedingung, auf die es ankommt: eine halb abgeschnittene
     * Zeile ist für den Fokus-Sucher ein Kandidat, den der Nutzer nicht sieht
     * (PanelScrollView.addFocusables fragt hier).
     */
    fun isVisible(row: Row, viewportHeight: Int, scrollY: Int): Boolean {
        if (viewportHeight <= 0) return false
        return row.top >= scrollY && row.top + row.height <= scrollY + viewportHeight
    }

    /**
     * Wohin muss gescrollt werden, damit [row] ganz im Fenster liegt?
     *
     * Drei Fälle, in dieser Reihenfolge:
     *
     *  - **Oberhalb des Fensters** (▲): die Oberkante der Zeile wird die
     *    Oberkante des Fensters.
     *  - **Unterhalb des Fensters** (▼): die Unterkante der Zeile wird die
     *    Unterkante des Fensters.
     *  - **Schon zu sehen**: der Stand bleibt — eine Zeile, die man sieht, darf
     *    beim Fokuswechsel nicht das ganze Fenster verschieben.
     *
     * Der Wert liegt immer zwischen `0` und `contentHeight - viewportHeight`:
     * ein ScrollView kann nicht über seinen Inhalt hinaus scrollen, und ein
     * negativer Wert wäre ein Sprung in eine leere Fläche.
     */
    fun scrollFor(row: Row, viewportHeight: Int, contentHeight: Int, scrollY: Int): Int {
        if (viewportHeight <= 0) return 0
        val limit = (contentHeight - viewportHeight).coerceAtLeast(0)
        val current = scrollY.coerceIn(0, limit)
        val bottom = row.top + row.height
        return when {
            row.top < current -> row.top.coerceIn(0, limit)
            bottom > current + viewportHeight -> (bottom - viewportHeight).coerceIn(0, limit)
            else -> current
        }
    }
}
