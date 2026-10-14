package com.piotv.navigate.ui

/**
 * Signatur des Docks — die Abkürzung, die entscheidet, ob die untere Zeile neu
 * gebaut werden muss.
 *
 * Warum eine eigene Funktion: die Signatur ist ein Cache-Schlüssel, und ein
 * falscher Schlüssel ist entweder eine unnötige Layout-Inflation bei jedem
 * HOME-Druck oder — schlimmer — eine Zeile, die eine Änderung nicht zeigt. Beide
 * Fälle sind hier in vier Zeilen prüfbar (DockSignatureTest).
 *
 * Die Symbolgröße gehört dazu: nach einem Dichte- oder Sprachwechsel wird die
 * Zeile mit einer anderen Größe neu gebaut, und ohne die Größe in der Signatur
 * bliebe die alte stehen.
 */
object DockSignature {

    fun of(packageNames: List<String>, iconSizePx: Int): String =
        packageNames.joinToString(",") + "@" + iconSizePx
}
