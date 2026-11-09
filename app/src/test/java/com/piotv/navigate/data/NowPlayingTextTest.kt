package com.piotv.navigate.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Turn a media session into **one** line for the field top left.
 *
 * The rule is small — and for exactly that reason the place where a bug could
 * hide for a long time: wrong order, a separator without a second part, a
 * "paused" without a title. It knows no Android and therefore needs no device.
 */
class NowPlayingTextTest {

    @Test
    fun `without a session there is no line`() {
        assertEquals("", NowPlayingText.line(null))
    }

    @Test
    fun `a title alone stays a title`() {
        assertEquals("Newscast", NowPlayingText.line(NowPlayingInfo(title = "Newscast")))
    }

    @Test
    fun `without a title the artist stands alone`() {
        assertEquals("WDR 2", NowPlayingText.line(NowPlayingInfo(artist = "WDR 2")))
    }

    @Test
    fun `artist and title go together, artist first`() {
        assertEquals(
            "Herbert Gronemeyer – The Way",
            NowPlayingText.line(NowPlayingInfo(title = "The Way", artist = "Herbert Gronemeyer")),
        )
    }

    @Test
    fun `empty and blank-only fields make no line`() {
        assertEquals("", NowPlayingText.line(NowPlayingInfo()))
        assertEquals("", NowPlayingText.line(NowPlayingInfo(title = "  ", artist = "")))
    }

    @Test
    fun `surrounding whitespace is dropped`() {
        assertEquals("A – B", NowPlayingText.line(NowPlayingInfo(title = " B ", artist = " A ")))
    }

    @Test
    fun `a paused session says so`() {
        assertEquals(
            "A – B · paused",
            NowPlayingText.line(NowPlayingInfo(title = "B", artist = "A", playing = false)),
        )
    }

    @Test
    fun `a paused session without fields stays empty`() {
        // Otherwise "· paused" would stand alone in the corner — a statement
        // without content.
        assertEquals("", NowPlayingText.line(NowPlayingInfo(playing = false)))
    }

    @Test
    fun `the app name comes before what is playing`() {
        // The follow-up question on the device was exactly that: "… but not in
        // which app". The name comes from the app list, not from the session.
        assertEquals(
            "Spotify · Herbert Gronemeyer – The Way",
            NowPlayingText.line(
                NowPlayingInfo(title = "The Way", artist = "Herbert Gronemeyer", app = "Spotify"),
            ),
        )
    }

    @Test
    fun `the app name alone with a title stays readable`() {
        assertEquals("WDR 2 · Morning Echo", NowPlayingText.line(NowPlayingInfo(title = "Morning Echo", app = "WDR 2")))
    }

    @Test
    fun `paused comes last even with an app name`() {
        assertEquals(
            "Netflix · A – B · paused",
            NowPlayingText.line(NowPlayingInfo(title = "B", artist = "A", playing = false, app = "Netflix")),
        )
    }

    @Test
    fun `without an app name nothing changes`() {
        assertEquals("A – B", NowPlayingText.line(NowPlayingInfo(title = "B", artist = "A", app = "  ")))
    }

    // --- Content (without the pause suffix) -----------------------------------
    //    The identifier for the history: "what played", not "how it stands
    //    right now".

    @Test
    fun `the content carries no pause suffix`() {
        val playing = NowPlayingInfo(title = "B", artist = "A", app = "ZDF", playing = true)
        val paused = NowPlayingInfo(title = "B", artist = "A", app = "ZDF", playing = false)
        assertEquals("ZDF · A – B", NowPlayingText.content(playing))
        assertEquals("ZDF · A – B", NowPlayingText.content(paused))
        // And the display lines differ — that is the whole point.
        assertEquals("ZDF · A – B", NowPlayingText.line(playing))
        assertEquals("ZDF · A – B · paused", NowPlayingText.line(paused))
    }

    @Test
    fun `the content is empty without a session`() {
        assertEquals("", NowPlayingText.content(null))
        assertEquals("", NowPlayingText.content(NowPlayingInfo(playing = false)))
    }

    @Test
    fun `the content does not depend on the app name`() {
        // The same rule for every app — the name is the ingredient, not the rule.
        for (app in listOf("Netflix", "Disney+", "ZDF", "WOW", "Prime Video", "ARD")) {
            assertEquals(
                "$app · A – B",
                NowPlayingText.content(NowPlayingInfo(title = "B", artist = "A", app = app)),
            )
        }
    }
}
