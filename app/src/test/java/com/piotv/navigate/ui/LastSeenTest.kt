package com.piotv.navigate.ui

import com.piotv.navigate.data.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The split of the history: one line to see, the older ones in the menu.
 *
 * The rule against duplication stands here as a test: an entry that is
 * currently visible as a live row appears neither again in the block nor in
 * the menu — the same text not twice on the screen.
 */
class LastSeenTest {

    /** A must be visible with empty live rows: the function's foundation. */
    @Test
    fun `the newest entry not playing is the one line`() {
        val newest = LastSeen.visible(history, emptySet())
        assertEquals(entry("com.zdf", "ZDF · Today Journal", 3_000L), newest)
        assertEquals(
            listOf(
                entry("com.spotify", "Spotify · The Way", 2_000L),
                entry("com.zdf", "ZDF · Daily News", 1_000L),
            ),
            LastSeen.older(history, emptySet()),
        )
    }

    /** When the newest title plays, the next moves up — the newest itself is not duplicated. */
    @Test
    fun `when the newest title plays, the next one moves up`() {
        val live = setOf("com.zdf" to "ZDF · Today Journal")
        assertEquals(
            entry("com.spotify", "Spotify · The Way", 2_000L),
            LastSeen.visible(history, live),
        )
        assertEquals(
            listOf(entry("com.zdf", "ZDF · Daily News", 1_000L)),
            LastSeen.older(history, live),
        )
    }

    /** Every history entry is playing: no line, no menu. */
    @Test
    fun `everything plays — no line and no menu`() {
        val live = setOf(
            "com.zdf" to "ZDF · Today Journal",
            "com.spotify" to "Spotify · The Way",
            "com.zdf" to "ZDF · Daily News",
        )
        assertNull(LastSeen.visible(history, live))
        assertEquals(emptyList<HistoryEntry>(), LastSeen.older(history, live))
    }

    @Test
    fun `empty history — no line, no menu`() {
        assertNull(LastSeen.visible(emptyList(), emptySet()))
        assertEquals(emptyList<HistoryEntry>(), LastSeen.older(emptyList(), emptySet()))
    }

    /** There is one line to see, but nobody for the menu to unfold. */
    @Test
    fun `only one line in the history — the menu stays empty`() {
        val single = listOf(entry("com.zdf", "ZDF · Daily News", 1_000L))
        assertEquals(
            entry("com.zdf", "ZDF · Daily News", 1_000L),
            LastSeen.visible(single, emptySet()),
        )
        assertEquals(emptyList<HistoryEntry>(), LastSeen.older(single, emptySet()))
    }

    /** The history as `HistoryStore.latest()` delivers it: newest first. */
    private val history = listOf(
        entry("com.zdf", "ZDF · Today Journal", 3_000L),
        entry("com.spotify", "Spotify · The Way", 2_000L),
        entry("com.zdf", "ZDF · Daily News", 1_000L),
    )

    private fun entry(packageName: String, line: String, time: Long) =
        HistoryEntry(packageName, line, time)
}
