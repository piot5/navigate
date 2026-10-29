package com.piotv.navigate.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Aus einer Media-Session **eine** Zeile für das Feld oben links machen.
 *
 * Die Regel ist klein — und genau deshalb der Ort, an dem sich ein Fehler lange
 * verstecken könnte: falsche Reihenfolge, ein Trennstrich ohne zweiten Teil, ein
 * „pausiert“ ohne Titel. Sie kennt kein Android und braucht deshalb kein Gerät.
 */
class NowPlayingTextTest {

    @Test
    fun `ohne Session gibt es keine Zeile`() {
        assertEquals("", NowPlayingText.line(null))
    }

    @Test
    fun `nur ein Titel bleibt ein Titel`() {
        assertEquals("Tagesschau", NowPlayingText.line(NowPlayingInfo(title = "Tagesschau")))
    }

    @Test
    fun `ohne Titel steht der Interpret allein`() {
        assertEquals("WDR 2", NowPlayingText.line(NowPlayingInfo(artist = "WDR 2")))
    }

    @Test
    fun `Interpret und Titel stehen zusammen, Interpret zuerst`() {
        assertEquals(
            "Herbert Grönemeyer – Der Weg",
            NowPlayingText.line(NowPlayingInfo(title = "Der Weg", artist = "Herbert Grönemeyer")),
        )
    }

    @Test
    fun `leere und nur-Leerzeichen-Angaben ergeben keine Zeile`() {
        assertEquals("", NowPlayingText.line(NowPlayingInfo()))
        assertEquals("", NowPlayingText.line(NowPlayingInfo(title = "  ", artist = "")))
    }

    @Test
    fun `umgebende Leerzeichen fallen weg`() {
        assertEquals("A – B", NowPlayingText.line(NowPlayingInfo(title = " B ", artist = " A ")))
    }

    @Test
    fun `eine pausierte Session sagt es dazu`() {
        assertEquals(
            "A – B · pausiert",
            NowPlayingText.line(NowPlayingInfo(title = "B", artist = "A", playing = false)),
        )
    }

    @Test
    fun `eine pausierte Session ohne Angaben bleibt leer`() {
        // Sonst stünde „· pausiert“ allein in der Ecke — Aussage ohne Inhalt.
        assertEquals("", NowPlayingText.line(NowPlayingInfo(playing = false)))
    }

    @Test
    fun `der App-Name steht vor dem, was laeuft`() {
        // Die Rückfrage auf dem Gerät war genau das: „… aber nicht, in welcher
        // App“. Der Name kommt aus der App-Liste, nicht aus der Session.
        assertEquals(
            "Spotify · Herbert Grönemeyer – Der Weg",
            NowPlayingText.line(
                NowPlayingInfo(title = "Der Weg", artist = "Herbert Grönemeyer", app = "Spotify"),
            ),
        )
    }

    @Test
    fun `der App-Name allein mit einem Titel bleibt lesbar`() {
        assertEquals("WDR 2 · Morgenecho", NowPlayingText.line(NowPlayingInfo(title = "Morgenecho", app = "WDR 2")))
    }

    @Test
    fun `pausiert steht auch mit App-Name am Ende`() {
        assertEquals(
            "Netflix · A – B · pausiert",
            NowPlayingText.line(NowPlayingInfo(title = "B", artist = "A", playing = false, app = "Netflix")),
        )
    }

    @Test
    fun `ohne App-Name aendert sich nichts`() {
        assertEquals("A – B", NowPlayingText.line(NowPlayingInfo(title = "B", artist = "A", app = "  ")))
    }

    // --- Inhalt (ohne Pausenzusatz) -----------------------------------------
    //    Die Kennung für den Verlauf: „was lief", nicht „wie es gerade steht".

    @Test
    fun `der Inhalt traegt keinen Pausenzusatz`() {
        val spielt = NowPlayingInfo(title = "B", artist = "A", app = "ZDF", playing = true)
        val pausiert = NowPlayingInfo(title = "B", artist = "A", app = "ZDF", playing = false)
        assertEquals("ZDF · A – B", NowPlayingText.content(spielt))
        assertEquals("ZDF · A – B", NowPlayingText.content(pausiert))
        // Und die Anzeigezeilen unterscheiden sich — genau darum geht es.
        assertEquals("ZDF · A – B", NowPlayingText.line(spielt))
        assertEquals("ZDF · A – B · pausiert", NowPlayingText.line(pausiert))
    }

    @Test
    fun `der Inhalt ist ohne Session leer`() {
        assertEquals("", NowPlayingText.content(null))
        assertEquals("", NowPlayingText.content(NowPlayingInfo(playing = false)))
    }

    @Test
    fun `der Inhalt ist nicht vom App-Namen abhaengig`() {
        // Dieselbe Regel für jede App — der Name ist die Zutat, nicht die Regel.
        for (app in listOf("Netflix", "Disney+", "ZDF", "WOW", "Prime Video", "ARD")) {
            assertEquals(
                "$app · A – B",
                NowPlayingText.content(NowPlayingInfo(title = "B", artist = "A", app = app)),
            )
        }
    }
}
