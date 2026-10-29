package com.piotv.navigate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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

    @get:Rule
    val folder = TemporaryFolder()


    private fun seen(
        packageName: String,
        title: String,
        artist: String? = null,
        playing: Boolean = true,
    ) = NowPlayingBoard.Observed(
        packageName,
        NowPlayingInfo(title = title, artist = artist, playing = playing),
    )

    /** Eine Session, die gerade **nichts** hergibt: Ladescreen, Werbung, Menü. */
    private fun silent(packageName: String) = NowPlayingBoard.Observed(packageName, null)

    /** Eine App, die **ausdrücklich beendet** hat (`STOPPED`/`ERROR`). */
    private fun ended(packageName: String) = NowPlayingBoard.Observed(packageName, null, ended = true)

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

    // --- Pausenzustand ist kein neuer Titel ---------------------------------
    //    Der Fehler auf dem Zielgerät: derselbe Film stand zweimal in der Liste,
    //    einmal mit und einmal ohne „· pausiert". Ursache war, dass die
    //    Anzeigezeile (mit Zusatz) als Änderungsschlüssel diente.

    @Test
    fun `spielen und pausieren aendert die Zeile, aber schreibt keinen Verlauf`() {
        val board = NowPlayingBoard()
        val history = memoryHistory()
        board.observe(listOf(seen("com.zdf", "Mumien")))

        // Pause-Tipp: die Anzeige bekommt den Zusatz …
        assertTrue(board.observe(listOf(seen("com.zdf", "Mumien", playing = false)), history, 2L))
        assertEquals(listOf("com.zdf=Mumien · pausiert"), lines(board))
        // … der Verlauf bleibt leer: derselbe Inhalt, nur anderer Zustand.
        assertEquals(emptyList<HistoryEntry>(), history.current())

        // Weiter-Tipp: der Zusatz fällt weg, der Verlauf bleibt leer.
        assertTrue(board.observe(listOf(seen("com.zdf", "Mumien")), history, 3L))
        assertEquals(listOf("com.zdf=Mumien"), lines(board))
        assertEquals(emptyList<HistoryEntry>(), history.current())
    }

    @Test
    fun `nur der Inhalt wandert in den Verlauf, nicht der Pausenzusatz`() {
        val board = NowPlayingBoard()
        val history = memoryHistory()
        board.observe(listOf(seen("com.zdf", "Mumien", playing = false)))

        // Ein anderer Inhalt ersetzt die Zeile — der alte wandert in den Verlauf,
        // und zwar **ohne** „· pausiert".
        assertTrue(board.observe(listOf(seen("com.zdf", "Frauen in der Armutsfalle")), history, 5L))
        assertEquals(
            listOf(HistoryEntry("com.zdf", "Mumien", 5L)),
            history.current(),
        )
    }

    @Test
    fun `der Pausenzustand einer zweiten App aendert nichts am Verlauf`() {
        // Nicht nur ZDF: dieselbe Regel für jede App, auch wenn mehrere Sessions
        // gleichzeitig pausieren.
        val board = NowPlayingBoard()
        val history = memoryHistory()
        board.observe(
            listOf(
                seen("com.netflix", "Eine Serie"),
                seen("com.disney.disneyplus", "Vaiana"),
            )
        )

        assertTrue(
            board.observe(
                listOf(
                    seen("com.netflix", "Eine Serie", playing = false),
                    seen("com.disney.disneyplus", "Vaiana", playing = false),
                ),
                history,
                9L,
            )
        )
        assertEquals(
            listOf("com.netflix=Eine Serie · pausiert", "com.disney.disneyplus=Vaiana · pausiert"),
            lines(board),
        )
        assertEquals(emptyList<HistoryEntry>(), history.current())
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
    fun `eine verschwundene Session loescht die Zeile nicht`() {
        val board = NowPlayingBoard()
        board.observe(listOf(seen("com.zdf", "Tagesschau"), seen("com.spotify", "Der Weg")))

        // Auf dem Zielgerät war die Session von WOW nach dem Film **ganz** weg,
        // ohne je „beendet" zu melden. Die Zeile bleibt trotzdem stehen.
        assertFalse(board.observe(listOf(seen("com.zdf", "Tagesschau"))))
        assertEquals(listOf("com.zdf=Tagesschau", "com.spotify=Der Weg"), lines(board))

        // Auch nach einer halben Minute ohne Sichtung.
        repeat(6) { board.observe(listOf(seen("com.zdf", "Tagesschau"))) }
        assertEquals(listOf("com.zdf=Tagesschau", "com.spotify=Der Weg"), lines(board))
    }

    @Test
    fun `eine Session ohne Titel loescht die Zeile nicht`() {
        // Das war der Fehler von 0.13.2: der Ladescreen einer App liefert eine
        // Session **ohne** Titel, und der Eintrag verschwand nach wenigen Sekunden.
        val board = NowPlayingBoard()
        board.observe(listOf(seen("com.hbo", "Ein Film", artist = "HBO Max")))

        assertFalse(board.observe(listOf(silent("com.hbo"))))
        assertFalse(board.observe(listOf(silent("com.hbo"))))
        // Der Text bleibt **wortgleich** stehen (Interpret – Titel), er wird nicht
        // neu zusammengesetzt und nicht geleert.
        assertEquals(listOf("com.hbo=HBO Max – Ein Film"), lines(board))

        // Der nächste Titel ersetzt ihn dann.
        assertTrue(board.observe(listOf(seen("com.hbo", "Der naechste Film"))))
        assertEquals(listOf("com.hbo=Der naechste Film"), lines(board))
    }

    @Test
    fun `ein ausdrueckliches Ende entfernt die Zeile nach zwei Abfragen`() {
        val board = NowPlayingBoard()
        board.observe(listOf(seen("com.spotify", "Der Weg")))

        // Einmal beendet: noch nicht — zwischen zwei Folgen steht die Session kurz
        // auf STOPPED.
        assertFalse(board.observe(listOf(ended("com.spotify"))))
        assertEquals(listOf("com.spotify=Der Weg"), lines(board))

        assertTrue(board.observe(listOf(ended("com.spotify"))))
        assertEquals(emptyList<String>(), lines(board))
    }

    @Test
    fun `ein Ende zwischen zwei Folgen nimmt die Zeile nicht weg`() {
        val board = NowPlayingBoard()
        board.observe(listOf(seen("com.netflix", "Folge 1")))

        board.observe(listOf(ended("com.netflix")))
        // Die nächste Folge beginnt: die Schonfrist beginnt von vorn.
        assertTrue(board.observe(listOf(seen("com.netflix", "Folge 2"))))
        assertEquals(listOf("com.netflix=Folge 2"), lines(board))
        assertFalse(board.observe(listOf(ended("com.netflix"))))
        assertEquals(listOf("com.netflix=Folge 2"), lines(board))
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
    fun `nach einem entfernten Eintrag beginnt die Schonfrist von vorn`() {
        val board = NowPlayingBoard()
        board.observe(listOf(seen("com.spotify", "Der Weg")))
        board.observe(listOf(ended("com.spotify")))
        board.observe(listOf(ended("com.spotify")))
        assertEquals(emptyList<String>(), lines(board))

        board.observe(listOf(seen("com.spotify", "Ein neuer Titel")))
        board.observe(listOf(ended("com.spotify")))
        assertEquals(listOf("com.spotify=Ein neuer Titel"), lines(board))
    }

    // --- Verlauf ------------------------------------------------------------
    //    Ein neuer Titel ersetzt die Zeile — der alte wandert in den Verlauf,
    //    statt zu verschwinden.

    private fun memoryHistory(): HistoryStore {
        val dir = folder.root.resolve("history-${System.nanoTime()}")
        dir.mkdirs()
        return HistoryStore(
            internalFile = java.io.File(dir, "history.conf"),
            importFile = null,
            elapsedRealtime = { 1_000L },
        )
    }

    @Test
    fun `ohne Verlauf wird die Zeile nur ersetzt`() {
        val board = NowPlayingBoard()
        board.observe(listOf(seen("com.zdf", "Tagesschau")))
        assertTrue(board.observe(listOf(seen("com.zdf", "heute journal"))))
        assertEquals(listOf("com.zdf=heute journal"), lines(board))
    }

    @Test
    fun `ein neuer Titel schiebt den alten in den Verlauf`() {
        val board = NowPlayingBoard()
        val history = memoryHistory()
        board.observe(listOf(seen("com.zdf", "Tagesschau")))

        assertTrue(board.observe(listOf(seen("com.zdf", "heute journal")), history, 1730000000000L))
        assertEquals(listOf("com.zdf=heute journal"), lines(board))
        assertEquals(
            listOf(HistoryEntry("com.zdf", "Tagesschau", 1730000000000L)),
            history.current(),
        )
    }

    @Test
    fun `derselbe Titel schreibt keinen Verlauf`() {
        // Der Takt läuft alle 5 Sekunden: ohne diese Zusage wüchse die Datei
        // bei jedem Durchlauf um dieselbe Zeile.
        val board = NowPlayingBoard()
        val history = memoryHistory()
        board.observe(listOf(seen("com.zdf", "Tagesschau")))
        assertFalse(board.observe(listOf(seen("com.zdf", "Tagesschau")), history, 1730000000000L))
        assertEquals(emptyList<HistoryEntry>(), history.current())
    }

    @Test
    fun `eine Session ohne Titel schreibt keinen Verlauf`() {
        val board = NowPlayingBoard()
        val history = memoryHistory()
        board.observe(listOf(seen("com.hbo", "Ein Film", artist = "HBO Max")))

        assertFalse(board.observe(listOf(silent("com.hbo")), history, 1730000000000L))
        assertEquals(listOf("com.hbo=HBO Max – Ein Film"), lines(board))
        assertEquals(emptyList<HistoryEntry>(), history.current())
    }

    @Test
    fun `drei Titel ergeben zwei Verlaufseintraege`() {
        val board = NowPlayingBoard()
        val history = memoryHistory()
        board.observe(listOf(seen("com.zdf", "Eins")))
        board.observe(listOf(seen("com.zdf", "Zwei")), history, 2L)
        board.observe(listOf(seen("com.zdf", "Drei")), history, 3L)

        assertEquals(listOf("com.zdf=Drei"), lines(board))
        // Neueste zuerst: „Zwei“ steht über „Eins“.
        assertEquals(
            listOf(
                HistoryEntry("com.zdf", "Zwei", 3L),
                HistoryEntry("com.zdf", "Eins", 2L),
            ),
            history.latest(),
        )
    }
}
