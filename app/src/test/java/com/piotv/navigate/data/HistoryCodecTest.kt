package com.piotv.navigate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The history file as text: `time|package|line`, one entry per line.
 *
 * Like [OrderCodecTest]: tolerance is intentional — a hand-written file must
 * never keep Navigate from starting.
 */
class HistoryCodecTest {

    @Test
    fun `an empty file gives an empty history`() {
        assertEquals(emptyList<HistoryEntry>(), HistoryCodec.parse(null))
        assertEquals(emptyList<HistoryEntry>(), HistoryCodec.parse(""))
        assertEquals(emptyList<HistoryEntry>(), HistoryCodec.parse("# just a comment\n\n"))
    }

    @Test
    fun `one entry per line`() {
        val entries = HistoryCodec.parse(
            "1730000000000|com.zdf|ZDF · Newscast\n" +
                "1730000005000|com.spotify|Spotify · The Way\n"
        )
        assertEquals(
            listOf(
                HistoryEntry("com.zdf", "ZDF · Newscast", 1730000000000L),
                HistoryEntry("com.spotify", "Spotify · The Way", 1730000005000L),
            ),
            entries,
        )
    }

    @Test
    fun `broken lines are dropped`() {
        val entries = HistoryCodec.parse(
            "noSeparator\n" +
                "123|onlyTwo\n" +
                "|com.zdf|\n" +
                "abc|com.zdf|Without time\n" +
                "|com.zdf|Empty time takes the fallback\n" +
                "1730000000000|com.zdf|Good\n",
            nowMillis = 1740000000000L,
        )
        assertEquals(
            listOf(
                HistoryEntry("com.zdf", "Empty time takes the fallback", 1740000000000L),
                HistoryEntry("com.zdf", "Good", 1730000000000L),
            ),
            entries,
        )
    }

    @Test
    fun `duplicate entries are dropped`() {
        val entries = HistoryCodec.parse(
            "1730000000000|com.zdf|ZDF · Newscast\n" +
                "1730000000000|com.zdf|ZDF · Newscast\n"
        )
        assertEquals(listOf(HistoryEntry("com.zdf", "ZDF · Newscast", 1730000000000L)), entries)
    }

    @Test
    fun `a pause suffix from an older version is dropped`() {
        // Until 0.14.0 the **display line** went into the history — pause
        // suffix and all. When reading, the suffix is cut off and the adjacent
        // pair collapsed into one entry. The suffix itself changed with the
        // language switch — “· pausiert” back then, “· paused” since — so
        // HistoryCodec strips both; files already on the device still carry
        // the German one.
        val entries = HistoryCodec.parse(
            "1730000000000|com.zdf|ZDF · Mummies\n" +
                "1730000005000|com.zdf|ZDF · Mummies · paused\n" +
                "1730000080000|com.zdf|ZDF · Mummies · pausiert\n" +
                "1730000010000|com.zdf|ZDF · Women in the Poverty Trap\n"
        )
        assertEquals(
            listOf(
                HistoryEntry("com.zdf", "ZDF · Mummies", 1730000000000L),
                HistoryEntry("com.zdf", "ZDF · Women in the Poverty Trap", 1730000010000L),
            ),
            entries,
        )
    }

    @Test
    fun `the same title after another one stays twice`() {
        // Only **adjacent** duplicates are dropped: whoever sees the same title
        // again after another one has seen it twice.
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
    fun `serialize writes what parse reads back`() {
        val entries = listOf(
            HistoryEntry("com.zdf", "ZDF · Newscast", 1730000000000L),
            HistoryEntry("com.spotify", "Spotify · The Way", 1730000005000L),
        )
        val text = HistoryCodec.serialize(entries)
        assertTrue(text.contains("1730000000000|com.zdf|ZDF · Newscast"))
        assertEquals(entries, HistoryCodec.parse(text))
    }
}
