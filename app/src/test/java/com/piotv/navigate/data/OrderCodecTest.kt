package com.piotv.navigate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Parser der Reihenfolge-Datei. Sie ist die einzige Einstellung dieser
 * Oberfläche und wird von Hand geschrieben (oder per `adb push`) — hier steht,
 * was passiert, wenn sie krumm ist. Grundsatz wie überall in diesem Projekt:
 * eine handgeschriebene Datei darf den Launcher nie daran hindern zu starten.
 */
class OrderCodecTest {

    @Test
    fun `fehlende Datei ergibt eine leere Reihenfolge`() {
        assertEquals(emptyList<String>(), OrderCodec.parse(null))
        assertEquals(emptyList<String>(), OrderCodec.parse(""))
    }

    @Test
    fun `eine Paketnamen-Zeile pro Eintrag`() {
        assertEquals(listOf("com.a", "com.b"), OrderCodec.parse("com.a\ncom.b"))
    }

    @Test
    fun `Kommentare Leerzeilen und Leerzeichen stoeren nicht`() {
        val order = OrderCodec.parse(
            """
            # Kommentar

               com.a
            com.b
            """.trimIndent()
        )
        assertEquals(listOf("com.a", "com.b"), order)
    }

    @Test
    fun `Komma-Listen und die alte schluessel-gleich-wert-Form`() {
        // Frühere Fassungen hatten "schlüssel=wert" (favorites=…). Eine solche
        // Datei darf nicht dazu führen, dass die Home-Seite leer bleibt.
        assertEquals(listOf("com.a", "com.b"), OrderCodec.parse("order=com.a, com.b"))
    }

    @Test
    fun `doppelte Eintraege fallen weg`() {
        // Ein doppelter Name würde eine App zweimal in die erste Zeile setzen.
        assertEquals(listOf("com.a", "com.b"), OrderCodec.parse("com.a\ncom.b\ncom.a"))
    }

    @Test
    fun `Schreiben und Lesen sind verlustfrei`() {
        val order = listOf("com.netflix.ninja", "de.swr.avp.ard.tv")
        assertEquals(order, OrderCodec.parse(OrderCodec.serialize(order)))
    }

    @Test
    fun `die geschriebene Datei erklaert sich selbst`() {
        val text = OrderCodec.serialize(listOf("com.a"))
        assertTrue(text.contains("com.a"))
        // Kommentarzeilen sind der Grund, warum die Datei von Hand editierbar ist.
        assertTrue(text.lines().any { it.startsWith("#") })
    }
}
