package com.piotv.navigate.ui

import com.piotv.navigate.data.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Die Aufteilung des Verlaufs: eine Zeile zu sehen, die älteren im Menü.
 *
 * Die Regel gegen Dopplung steht hier als Test: ein Eintrag, der gerade als
 * Live-Zeile zu sehen ist, steht weder noch einmal im Block noch im Menü —
 * derselbe Text nicht zweimal auf dem Schirm.
 */
class LastSeenTest {

    /** A muss bei leeren Live-Zeilen sichtbar sein: die Funktionsgrundlage. */
    @Test
    fun `der neueste Eintrag, der nicht läuft, ist die eine Zeile`() {
        val newest = LastSeen.visible(history, emptySet())
        assertEquals(entry("com.zdf", "ZDF · heute journal", 3_000L), newest)
        assertEquals(
            listOf(
                entry("com.spotify", "Spotify · Der Weg", 2_000L),
                entry("com.zdf", "ZDF · Tagesschau", 1_000L),
            ),
            LastSeen.older(history, emptySet()),
        )
    }

    /** Läuft der neueste Titel, rückt der nächste nach — er selbst steht nicht doppelt. */
    @Test
    fun `läuft der neueste Titel, rückt der nächste nach`() {
        val live = setOf("com.zdf" to "ZDF · heute journal")
        assertEquals(
            entry("com.spotify", "Spotify · Der Weg", 2_000L),
            LastSeen.visible(history, live),
        )
        assertEquals(
            listOf(entry("com.zdf", "ZDF · Tagesschau", 1_000L)),
            LastSeen.older(history, live),
        )
    }

    /** Jeder Verlaufseintrag läuft gerade: keine Zeile, kein Menü. */
    @Test
    fun `alles läuft — es gibt keine Zeile und kein Menü`() {
        val live = setOf(
            "com.zdf" to "ZDF · heute journal",
            "com.spotify" to "Spotify · Der Weg",
            "com.zdf" to "ZDF · Tagesschau",
        )
        assertNull(LastSeen.visible(history, live))
        assertEquals(emptyList<HistoryEntry>(), LastSeen.older(history, live))
    }

    @Test
    fun `leerer Verlauf — keine Zeile, kein Menü`() {
        assertNull(LastSeen.visible(emptyList(), emptySet()))
        assertEquals(emptyList<HistoryEntry>(), LastSeen.older(emptyList(), emptySet()))
    }

    /** Eine Zeile gibt es zu sehen, aber niemanden, den das Menü aufklappen könnte. */
    @Test
    fun `nur eine Zeile im Verlauf — das Menü bleibt leer`() {
        val single = listOf(entry("com.zdf", "ZDF · Tagesschau", 1_000L))
        assertEquals(
            entry("com.zdf", "ZDF · Tagesschau", 1_000L),
            LastSeen.visible(single, emptySet()),
        )
        assertEquals(emptyList<HistoryEntry>(), LastSeen.older(single, emptySet()))
    }

    /** Der Verlauf, wie `HistoryStore.latest()` ihn liefert: neueste zuerst. */
    private val history = listOf(
        entry("com.zdf", "ZDF · heute journal", 3_000L),
        entry("com.spotify", "Spotify · Der Weg", 2_000L),
        entry("com.zdf", "ZDF · Tagesschau", 1_000L),
    )

    private fun entry(packageName: String, line: String, time: Long) =
        HistoryEntry(packageName, line, time)
}
