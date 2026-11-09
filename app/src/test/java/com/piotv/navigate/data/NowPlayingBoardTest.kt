package com.piotv.navigate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The rule behind the "Now Playing" rows: **one per app**, and rows stay put
 * until something changes for that app.
 *
 * Why this is checked without a device: exactly this rule became visible on the
 * target device in 0.12 — the row was rewritten on every pass (flicker, new
 * cover requests). A bug in a rule is checkable in milliseconds; the same bug
 * on the TV costs a build and a look at a device that is not at the desk.
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

    /** A session that currently yields **nothing**: loading screen, ads, menu. */
    private fun silent(packageName: String) = NowPlayingBoard.Observed(packageName, null)

    /** An app that has **explicitly ended** (`STOPPED`/`ERROR`). */
    private fun ended(packageName: String) = NowPlayingBoard.Observed(packageName, null, ended = true)

    private fun lines(board: NowPlayingBoard) = board.rows().map { "${it.packageName}=${it.line}" }

    @Test
    fun `without observations there is no row`() {
        val board = NowPlayingBoard()

        assertFalse(board.observe(emptyList()))
        assertEquals(emptyList<String>(), lines(board))
    }

    @Test
    fun `one row per app with an active session`() {
        val board = NowPlayingBoard()

        assertTrue(board.observe(listOf(seen("com.zdf", "Newscast"), seen("com.spotify", "The Way"))))
        assertEquals(listOf("com.zdf=Newscast", "com.spotify=The Way"), lines(board))
    }

    @Test
    fun `two sessions of the same app make one row, the first wins`() {
        val board = NowPlayingBoard()

        board.observe(listOf(seen("com.spotify", "First"), seen("com.spotify", "Second")))
        assertEquals(listOf("com.spotify=First"), lines(board))
    }

    @Test
    fun `the app name comes before the title`() {
        val board = NowPlayingBoard()

        board.observe(
            listOf(NowPlayingBoard.Observed("com.zdf", NowPlayingInfo(title = "Newscast", app = "ZDF")))
        )
        assertEquals(listOf("com.zdf=ZDF · Newscast"), lines(board))
    }

    @Test
    fun `a paused session says so`() {
        val board = NowPlayingBoard()

        board.observe(listOf(seen("com.zdf", "Newscast", playing = false)))
        assertEquals(listOf("com.zdf=Newscast · paused"), lines(board))
    }

    // --- Paused is not a new title -------------------------------------------
    //    The bug on the target device: the same movie appeared twice in the
    //    list, once with and once without "· paused". The cause was that the
    //    display row (with the suffix) served as the change key.

    @Test
    fun `playing and pausing changes the row, but writes no history`() {
        val board = NowPlayingBoard()
        val history = memoryHistory()
        board.observe(listOf(seen("com.zdf", "Mummies")))

        // Pause press: the display gets the suffix …
        assertTrue(board.observe(listOf(seen("com.zdf", "Mummies", playing = false)), history, 2L))
        assertEquals(listOf("com.zdf=Mummies · paused"), lines(board))
        // … the history stays empty: same content, only a different state.
        assertEquals(emptyList<HistoryEntry>(), history.current())

        // Play press: the suffix goes away, the history stays empty.
        assertTrue(board.observe(listOf(seen("com.zdf", "Mummies")), history, 3L))
        assertEquals(listOf("com.zdf=Mummies"), lines(board))
        assertEquals(emptyList<HistoryEntry>(), history.current())
    }

    @Test
    fun `only the content goes into the history, not the pause suffix`() {
        val board = NowPlayingBoard()
        val history = memoryHistory()
        board.observe(listOf(seen("com.zdf", "Mummies", playing = false)))

        // Different content replaces the row — the old one moves into the
        // history, and **without** the "· paused" suffix.
        assertTrue(board.observe(listOf(seen("com.zdf", "Women in the Poverty Trap")), history, 5L))
        assertEquals(
            listOf(HistoryEntry("com.zdf", "Mummies", 5L)),
            history.current(),
        )
    }

    @Test
    fun `the paused state of a second app does not touch the history`() {
        // Not just ZDF: the same rule for every app, even when several sessions
        // pause at the same time.
        val board = NowPlayingBoard()
        val history = memoryHistory()
        board.observe(
            listOf(
                seen("com.netflix", "A Series"),
                seen("com.disney.disneyplus", "Vaiana"),
            )
        )

        assertTrue(
            board.observe(
                listOf(
                    seen("com.netflix", "A Series", playing = false),
                    seen("com.disney.disneyplus", "Vaiana", playing = false),
                ),
                history,
                9L,
            )
        )
        assertEquals(
            listOf("com.netflix=A Series · paused", "com.disney.disneyplus=Vaiana · paused"),
            lines(board),
        )
        assertEquals(emptyList<HistoryEntry>(), history.current())
    }

    @Test
    fun `no title and no artist makes no row`() {
        val board = NowPlayingBoard()

        board.observe(
            listOf(
                NowPlayingBoard.Observed("com.zdf", NowPlayingInfo(playing = false)),
                NowPlayingBoard.Observed("com.spotify", NowPlayingInfo(title = "  ")),
                seen("com.netflix", "A Series"),
            )
        )
        assertEquals(listOf("com.netflix=A Series"), lines(board))
    }

    @Test
    fun `a package without a name is skipped`() {
        val board = NowPlayingBoard()

        board.observe(listOf(seen("", "Nobody"), seen("com.zdf", "Newscast")))
        assertEquals(listOf("com.zdf=Newscast"), lines(board))
    }

    @Test
    fun `unchanged rows report no change`() {
        val board = NowPlayingBoard()

        assertTrue(board.observe(listOf(seen("com.zdf", "Newscast"))))
        // The tick runs every five seconds: without this promise the same row
        // would be set again and a new cover fetched every time.
        assertFalse(board.observe(listOf(seen("com.zdf", "Newscast"))))
        assertEquals(listOf("com.zdf=Newscast"), lines(board))
    }

    @Test
    fun `a failed poll leaves the state standing`() {
        val board = NowPlayingBoard()
        board.observe(listOf(seen("com.zdf", "Newscast")))

        // `null` means: no notification access, the session service does not
        // answer. That is different from "looked successfully, nothing is
        // playing" (empty list) — only there does a row disappear.
        assertFalse(board.observe(null))
        assertEquals(listOf("com.zdf=Newscast"), lines(board))
    }

    @Test
    fun `only the app that changes is rewritten`() {
        val board = NowPlayingBoard()
        board.observe(listOf(seen("com.zdf", "Newscast"), seen("com.spotify", "The Way")))

        assertTrue(board.observe(listOf(seen("com.zdf", "Newscast"), seen("com.spotify", "Bochum"))))
        assertEquals(listOf("com.zdf=Newscast", "com.spotify=Bochum"), lines(board))
    }

    @Test
    fun `a vanished session does not delete the row`() {
        val board = NowPlayingBoard()
        board.observe(listOf(seen("com.zdf", "Newscast"), seen("com.spotify", "The Way")))

        // On the target device the WOW session was **completely** gone after the
        // movie, without ever reporting "ended". The row stays anyway.
        assertFalse(board.observe(listOf(seen("com.zdf", "Newscast"))))
        assertEquals(listOf("com.zdf=Newscast", "com.spotify=The Way"), lines(board))

        // Even after half a minute without a sighting.
        repeat(6) { board.observe(listOf(seen("com.zdf", "Newscast"))) }
        assertEquals(listOf("com.zdf=Newscast", "com.spotify=The Way"), lines(board))
    }

    @Test
    fun `a session without a title does not delete the row`() {
        // That was the 0.13.2 bug: an app's loading screen delivers a session
        // **without** a title, and the entry vanished after a few seconds.
        val board = NowPlayingBoard()
        board.observe(listOf(seen("com.hbo", "A Movie", artist = "HBO Max")))

        assertFalse(board.observe(listOf(silent("com.hbo"))))
        assertFalse(board.observe(listOf(silent("com.hbo"))))
        // The text stays **word-identical** (artist – title), it is not
        // recomposed and not cleared.
        assertEquals(listOf("com.hbo=HBO Max – A Movie"), lines(board))

        // The next title then replaces it.
        assertTrue(board.observe(listOf(seen("com.hbo", "The Next Movie"))))
        assertEquals(listOf("com.hbo=The Next Movie"), lines(board))
    }

    @Test
    fun `an explicit end removes the row after two polls`() {
        val board = NowPlayingBoard()
        board.observe(listOf(seen("com.spotify", "The Way")))

        // Ended once: not yet — between two episodes the session briefly sits
        // on STOPPED.
        assertFalse(board.observe(listOf(ended("com.spotify"))))
        assertEquals(listOf("com.spotify=The Way"), lines(board))

        assertTrue(board.observe(listOf(ended("com.spotify"))))
        assertEquals(emptyList<String>(), lines(board))
    }

    @Test
    fun `an end between two episodes does not remove the row`() {
        val board = NowPlayingBoard()
        board.observe(listOf(seen("com.netflix", "Episode 1")))

        board.observe(listOf(ended("com.netflix")))
        // The next episode begins: the grace period starts over.
        assertTrue(board.observe(listOf(seen("com.netflix", "Episode 2"))))
        assertEquals(listOf("com.netflix=Episode 2"), lines(board))
        assertFalse(board.observe(listOf(ended("com.netflix"))))
        assertEquals(listOf("com.netflix=Episode 2"), lines(board))
    }

    @Test
    fun `a reappearing app keeps its position`() {
        val board = NowPlayingBoard()
        board.observe(listOf(seen("com.zdf", "Newscast"), seen("com.spotify", "The Way")))
        board.observe(listOf(seen("com.zdf", "Newscast")))

        // Back within the grace period: the rows do not jump around.
        board.observe(listOf(seen("com.zdf", "Newscast"), seen("com.spotify", "The Way")))
        assertEquals(listOf("com.zdf=Newscast", "com.spotify=The Way"), lines(board))
    }

    @Test
    fun `a new app joins at the back`() {
        val board = NowPlayingBoard()
        board.observe(listOf(seen("com.zdf", "Newscast")))

        assertTrue(board.observe(listOf(seen("com.netflix", "A Series"), seen("com.zdf", "Newscast"))))
        // The known row stays on top, the new one is added.
        assertEquals(listOf("com.zdf=Newscast", "com.netflix=A Series"), lines(board))
    }

    @Test
    fun `after a removed entry the grace period starts over`() {
        val board = NowPlayingBoard()
        board.observe(listOf(seen("com.spotify", "Der Weg")))
        board.observe(listOf(ended("com.spotify")))
        board.observe(listOf(ended("com.spotify")))
        assertEquals(emptyList<String>(), lines(board))

        board.observe(listOf(seen("com.spotify", "Ein neuer Titel")))
        board.observe(listOf(ended("com.spotify")))
        assertEquals(listOf("com.spotify=Ein neuer Titel"), lines(board))
    }

    // --- History --------------------------------------------------------------
    //    A new title replaces the row — the old one moves into the history
    //    instead of vanishing.

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
    fun `without history the row is only replaced`() {
        val board = NowPlayingBoard()
        board.observe(listOf(seen("com.zdf", "Newscast")))
        assertTrue(board.observe(listOf(seen("com.zdf", "Evening Report"))))
        assertEquals(listOf("com.zdf=Evening Report"), lines(board))
    }

    @Test
    fun `a new title pushes the old one into the history`() {
        val board = NowPlayingBoard()
        val history = memoryHistory()
        board.observe(listOf(seen("com.zdf", "Newscast")))

        assertTrue(board.observe(listOf(seen("com.zdf", "Evening Report")), history, 1730000000000L))
        assertEquals(listOf("com.zdf=Evening Report"), lines(board))
        assertEquals(
            listOf(HistoryEntry("com.zdf", "Newscast", 1730000000000L)),
            history.current(),
        )
    }

    @Test
    fun `the same title writes no history`() {
        // The tick runs every 5 seconds: without this promise the file would
        // grow by the same line on every pass.
        val board = NowPlayingBoard()
        val history = memoryHistory()
        board.observe(listOf(seen("com.zdf", "Newscast")))
        assertFalse(board.observe(listOf(seen("com.zdf", "Newscast")), history, 1730000000000L))
        assertEquals(emptyList<HistoryEntry>(), history.current())
    }

    @Test
    fun `a session without a title writes no history`() {
        val board = NowPlayingBoard()
        val history = memoryHistory()
        board.observe(listOf(seen("com.hbo", "A Movie", artist = "HBO Max")))

        assertFalse(board.observe(listOf(silent("com.hbo")), history, 1730000000000L))
        assertEquals(listOf("com.hbo=HBO Max – A Movie"), lines(board))
        assertEquals(emptyList<HistoryEntry>(), history.current())
    }

    @Test
    fun `three titles make two history entries`() {
        val board = NowPlayingBoard()
        val history = memoryHistory()
        board.observe(listOf(seen("com.zdf", "One")))
        board.observe(listOf(seen("com.zdf", "Two")), history, 2L)
        board.observe(listOf(seen("com.zdf", "Three")), history, 3L)

        assertEquals(listOf("com.zdf=Three"), lines(board))
        // Newest first: "Two" sits above "One".
        assertEquals(
            listOf(
                HistoryEntry("com.zdf", "Two", 3L),
                HistoryEntry("com.zdf", "One", 2L),
            ),
            history.latest(),
        )
    }
}
