package com.piotv.navigate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Regel hinter den „Now Playing“-Zeilen: **eine je App**, und sie bleiben
 * stehen, bis sich für diese App etwas ändert.
 *
 * Warum das ohne Gerät geprüft wird: genau diese Regel ist in 0.12 auf dem
 * Zielgerät sichtbar geworden — die Zeile wurde bei jedem Durchlauf neu
 * geschrieben (Flackern, neue Cover-Anfragen). Ein Fehler in einer Regel ist in
 * Millisekunden prüfbar; derselbe Fehler auf dem Fernseher kostet einen Build und
 * einen Blick auf ein Gerät, das nicht am Schreibtisch steht.
 */
class NowPlayingBoardTest {

    private fun seen(
        packageName: String,
        title: String,
        artist: String? = null,
        playing: Boolean = true,
    ) = NowPlayingBoard.Observed(
        packageName,
        NowPlayingInfo(title = title, artist = artist, playing = playing),
    )

    private fun lines(board: NowPlayingBoard) = board.rows().map { "${it.packageName}=${it.line}" }

    @Test
    fun `ohne Beobachtung gibt es keine Zeile`() {
        val board = NowPlayingBoard()

        assertFalse(board.observe(emptyList()))
        assertEquals(emptyList<String>(), lines(board))
    }

    @Test
    fun `eine Zeile je App mit aktiver Session`() {
        val board = NowPlayingBoard()

        assertTrue(board.observe(listOf(seen("com.zdf", "Tagesschau"), seen("com.spotify", "Der Weg"))))
        assertEquals(listOf("com.zdf=Tagesschau", "com.spotify=Der Weg"), lines(board))
    }

    @Test
    fun `zwei Sessions derselben App ergeben eine Zeile, die erste gewinnt`() {
        val board = NowPlayingBoard()

        board.observe(listOf(seen("com.spotify", "Erster"), seen("com.spotify", "Zweiter")))
        assertEquals(listOf("com.spotify=Erster"), lines(board))
    }

    @Test
    fun `der App-Name steht vor dem Titel`() {
        val board = NowPlayingBoard()

        board.observe(
            listOf(NowPlayingBoard.Observed("com.zdf", NowPlayingInfo(title = "Tagesschau", app = "ZDF")))
        )
        assertEquals(listOf("com.zdf=ZDF · Tagesschau"), lines(board))
    }

    @Test
    fun `eine pausierte Session sagt es dazu`() {
        val board = NowPlayingBoard()

        board.observe(listOf(seen("com.zdf", "Tagesschau", playing = false)))
        assertEquals(listOf("com.zdf=Tagesschau · pausiert"), lines(board))
    }

    @Test
    fun `ohne Titel und ohne Interpret entsteht keine Zeile`() {
        val board = NowPlayingBoard()

        board.observe(
            listOf(
                NowPlayingBoard.Observed("com.zdf", NowPlayingInfo(playing = false)),
                NowPlayingBoard.Observed("com.spotify", NowPlayingInfo(title = "  ")),
                seen("com.netflix", "Eine Serie"),
            )
        )
        assertEquals(listOf("com.netflix=Eine Serie"), lines(board))
    }

    @Test
    fun `ein Paket ohne Namen wird uebersprungen`() {
        val board = NowPlayingBoard()

        board.observe(listOf(seen("", "Niemand"), seen("com.zdf", "Tagesschau")))
        assertEquals(listOf("com.zdf=Tagesschau"), lines(board))
    }

    @Test
    fun `unveraenderte Zeilen melden keine Aenderung`() {
        val board = NowPlayingBoard()

        assertTrue(board.observe(listOf(seen("com.zdf", "Tagesschau"))))
        // Der Takt läuft alle fünf Sekunden: ohne diese Zusage würde jedes Mal
        // dieselbe Zeile neu gesetzt und ein neues Cover geholt.
        assertFalse(board.observe(listOf(seen("com.zdf", "Tagesschau"))))
        assertEquals(listOf("com.zdf=Tagesschau"), lines(board))
    }

    @Test
    fun `eine fehlgeschlagene Abfrage laesst den Stand stehen`() {
        val board = NowPlayingBoard()
        board.observe(listOf(seen("com.zdf", "Tagesschau")))

        // `null` heißt: kein Benachrichtigungszugriff, Session-Dienst antwortet
        // nicht. Das ist etwas anderes als „erfolgreich nachgesehen, es läuft
        // nichts“ (leere Liste) — nur dort verschwindet eine Zeile.
        assertFalse(board.observe(null))
        assertEquals(listOf("com.zdf=Tagesschau"), lines(board))
    }

    @Test
    fun `nur die App die sich aendert wird neu geschrieben`() {
        val board = NowPlayingBoard()
        board.observe(listOf(seen("com.zdf", "Tagesschau"), seen("com.spotify", "Der Weg")))

        assertTrue(board.observe(listOf(seen("com.zdf", "Tagesschau"), seen("com.spotify", "Bochum"))))
        assertEquals(listOf("com.zdf=Tagesschau", "com.spotify=Bochum"), lines(board))
    }

    @Test
    fun `eine verschwundene App bleibt zwei Abfragen stehen`() {
        val board = NowPlayingBoard()
        board.observe(listOf(seen("com.zdf", "Tagesschau"), seen("com.spotify", "Der Weg")))

        // Erste erfolgreiche Abfrage ohne Spotify: die Schonfrist greift — eine
        // Session verschwindet beim Titelwechsel kurz.
        assertFalse(board.observe(listOf(seen("com.zdf", "Tagesschau"))))
        assertEquals(listOf("com.zdf=Tagesschau", "com.spotify=Der Weg"), lines(board))

        // Zweite Abfrage ohne Spotify: jetzt ist die Zeile weg.
        assertTrue(board.observe(listOf(seen("com.zdf", "Tagesschau"))))
        assertEquals(listOf("com.zdf=Tagesschau"), lines(board))
    }

    @Test
    fun `eine wieder aufgetauchte App behaelt ihre Position`() {
        val board = NowPlayingBoard()
        board.observe(listOf(seen("com.zdf", "Tagesschau"), seen("com.spotify", "Der Weg")))
        board.observe(listOf(seen("com.zdf", "Tagesschau")))

        // Zurück innerhalb der Schonfrist: die Zeilen springen nicht um.
        board.observe(listOf(seen("com.zdf", "Tagesschau"), seen("com.spotify", "Der Weg")))
        assertEquals(listOf("com.zdf=Tagesschau", "com.spotify=Der Weg"), lines(board))
    }

    @Test
    fun `eine neue App reiht sich hinten ein`() {
        val board = NowPlayingBoard()
        board.observe(listOf(seen("com.zdf", "Tagesschau")))

        assertTrue(board.observe(listOf(seen("com.netflix", "Eine Serie"), seen("com.zdf", "Tagesschau"))))
        // Die bekannte Zeile bleibt oben stehen, die neue kommt dazu.
        assertEquals(listOf("com.zdf=Tagesschau", "com.netflix=Eine Serie"), lines(board))
    }

    @Test
    fun `nach dem Verschwinden beginnt die Schonfrist von vorn`() {
        val board = NowPlayingBoard()
        board.observe(listOf(seen("com.spotify", "Der Weg")))
        board.observe(emptyList())
        board.observe(emptyList())
        assertEquals(emptyList<String>(), lines(board))

        board.observe(listOf(seen("com.spotify", "Der Weg")))
        board.observe(emptyList())
        assertEquals(listOf("com.spotify=Der Weg"), lines(board))
    }
}
