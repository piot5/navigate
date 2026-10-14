package com.piotv.navigate.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Der Cache-Schlüssel des Docks.
 *
 * Vier Zeilen Code mit zwei teuren Fehlermöglichkeiten: eine zu feine Signatur
 * kostet bei jedem HOME-Druck neun Layout-Inflationen, eine zu grobe zeigt eine
 * geänderte Zeile nicht an.
 */
class DockSignatureTest {

    @Test
    fun `gleiche Favoriten und gleiche Groesse ergeben dieselbe Signatur`() {
        assertEquals(
            DockSignature.of(listOf("com.a", "com.b"), 104),
            DockSignature.of(listOf("com.a", "com.b"), 104),
        )
    }

    @Test
    fun `die Reihenfolge ist Teil der Signatur`() {
        // Die Reihenfolge ist die Anordnung in der Zeile (FavoritesCodec.move).
        assertNotEquals(
            DockSignature.of(listOf("com.a", "com.b"), 104),
            DockSignature.of(listOf("com.b", "com.a"), 104),
        )
    }

    @Test
    fun `die Symbolgroesse ist Teil der Signatur`() {
        // Nach einem Dichte- oder Sprachwechsel wird die Zeile in einer anderen
        // Größe gebaut — ohne die Größe bliebe die alte stehen.
        assertNotEquals(
            DockSignature.of(listOf("com.a"), 104),
            DockSignature.of(listOf("com.a"), 128),
        )
    }

    @Test
    fun `ein zusaetzlicher Favorit aendert die Signatur`() {
        assertNotEquals(
            DockSignature.of(listOf("com.a"), 104),
            DockSignature.of(listOf("com.a", "com.b"), 104),
        )
    }

    @Test
    fun `die leere Zeile ist stabil und von jeder gefuellten verschieden`() {
        assertEquals(DockSignature.of(emptyList(), 104), DockSignature.of(emptyList(), 104))
        assertNotEquals(DockSignature.of(emptyList(), 104), DockSignature.of(listOf("com.a"), 104))
    }
}
