package com.piotv.navigate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Verlauf-Datei als Text: `zeit|paket|zeile`, eine Zeile pro Eintrag.
 *
 * Wie [OrderCodecTest]: Toleranz ist Absicht — eine handgeschriebene Datei
 * darf Navigate nie am Starten hindern.
 */
class HistoryCodecTest {

    @Test
    fun `leere Datei ergibt leeren Verlauf`() {
        assertEquals(emptyList<HistoryEntry>(), HistoryCodec.parse(null))
        assertEquals(emptyList<HistoryEntry>(), HistoryCodec.parse(""))
        assertEquals(emptyList<HistoryEntry>(), HistoryCodec.parse("# nur ein Kommentar\n\n"))
    }

    @Test
    fun `ein Eintrag pro Zeile`() {
        val entries = HistoryCodec.parse(
            "1730000000000|com.zdf|ZDF · Tagesschau\n" +
                "1730000005000|com.spotify|Spotify · Der Weg\n"
        )
        assertEquals(
            listOf(
                HistoryEntry("com.zdf", "ZDF · Tagesschau", 1730000000000L),
                HistoryEntry("com.spotify", "Spotify · Der Weg", 1730000005000L),
            ),
            entries,
        )
    }

    @Test
    fun `kaputte Zeilen fallen weg`() {
        val entries = HistoryCodec.parse(
            "keinTrenner\n" +
                "123|nurZwei\n" +
                "|com.zdf|\n" +
                "abc|com.zdf|Ohne Zeit\n" +
                "|com.zdf|Leere Zeit nimmt Fallback\n" +
                "1730000000000|com.zdf|Gut\n",
            nowMillis = 1740000000000L,
        )
        assertEquals(
            listOf(
                HistoryEntry("com.zdf", "Leere Zeit nimmt Fallback", 1740000000000L),
                HistoryEntry("com.zdf", "Gut", 1730000000000L),
            ),
            entries,
        )
    }

    @Test
    fun `doppelte Eintraege fallen weg`() {
        val entries = HistoryCodec.parse(
            "1730000000000|com.zdf|ZDF · Tagesschau\n" +
                "1730000000000|com.zdf|ZDF · Tagesschau\n"
        )
        assertEquals(listOf(HistoryEntry("com.zdf", "ZDF · Tagesschau", 1730000000000L)), entries)
    }

    @Test
    fun `ein Pausenzusatz aus einer aelteren Fassung faellt weg`() {
        // Bis 0.14.0 wanderte die **Anzeigezeile** in den Verlauf — also mit
        // „· pausiert". Beim Lesen wird der Zusatz abgeschnitten und das
        // angrenzende Paar zu einem Eintrag zusammengezogen.
        val entries = HistoryCodec.parse(
            "1730000000000|com.zdf|ZDF · Mumien\n" +
                "1730000005000|com.zdf|ZDF · Mumien · pausiert\n" +
                "1730000010000|com.zdf|ZDF · Frauen in der Armutsfalle\n"
        )
        assertEquals(
            listOf(
                HistoryEntry("com.zdf", "ZDF · Mumien", 1730000000000L),
                HistoryEntry("com.zdf", "ZDF · Frauen in der Armutsfalle", 1730000010000L),
            ),
            entries,
        )
    }

    @Test
    fun `derselbe Titel nach einem anderen bleibt zweimal`() {
        // Nur **angrenzende** Dopplungen fallen weg: wer denselben Titel nach
        // einem anderen erneut sieht, hat ihn zweimal gesehen.
        val entries = HistoryCodec.parse("1|com.zdf|A\n" + "2|com.zdf|B\n" + "3|com.zdf|A\n")
        assertEquals(
            listOf(
                HistoryEntry("com.zdf", "A", 1L),
                HistoryEntry("com.zdf", "B", 2L),
                HistoryEntry("com.zdf", "A", 3L),
            ),
            entries,
        )
    }

    @Test
    fun `serialize schreibt parse zurueck`() {
        val entries = listOf(
            HistoryEntry("com.zdf", "ZDF · Tagesschau", 1730000000000L),
            HistoryEntry("com.spotify", "Spotify · Der Weg", 1730000005000L),
        )
        val text = HistoryCodec.serialize(entries)
        assertTrue(text.contains("1730000000000|com.zdf|ZDF · Tagesschau"))
        assertEquals(entries, HistoryCodec.parse(text))
    }
}
