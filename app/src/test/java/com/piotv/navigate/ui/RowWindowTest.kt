package com.piotv.navigate.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The visible window over the Now Playing lines.
 *
 * The numbers are those of the target device in dp: a row is 52 dp tall, 4 dp
 * sit between two rows, and the block is capped at 260 dp
 * (`now_playing_max_height`) — so exactly five rows fit into the window.
 * Exactly these five are the ones the focus seeker may offer; everything
 * below lies further down in the layout and thus geometrically where the
 * grid starts.
 *
 * The bug from 0.15.0 stands here as a test: the rows below the window were
 * focus candidates; a ▲ from the grid landed on a row nobody sees.
 */
class RowWindowTest {

    /** Eight rows of 52 dp with 4 dp of spacing — the case from the real block. */
    private val rows = (0 until 8).map { RowWindow.Row(top = it * 56, height = 52) }

    /** The content: eight rows with spacing, without the spacing after the last. */
    private val content = rows.size * 56 - 4

    private val viewport = 260

    @Test
    fun `a row fully in the window does not change the position`() {
        assertEquals(0, RowWindow.scrollFor(rows[3], viewport, content, scrollY = 0))
        // Bottom edge 220 dp, window to 260 dp: nothing to do.
        assertEquals(0, RowWindow.scrollFor(RowWindow.Row(168, 52), viewport, content, 0))
    }

    @Test
    fun `a row below the window is pulled to the bottom edge`() {
        // Row 4 sits at 224..276; the window ends at 260 → 16 dp further.
        assertEquals(16, RowWindow.scrollFor(rows[4], viewport, content, scrollY = 0))
    }

    @Test
    fun `a row above the window is pulled to the top edge`() {
        assertEquals(168, RowWindow.scrollFor(rows[3], viewport, content, scrollY = 184))
    }

    @Test
    fun `the position stays within the content`() {
        // The last row (392..444) with a 260 dp window: 184 dp is the end,
        // a ScrollView cannot scroll any further.
        assertEquals(184, RowWindow.scrollFor(rows[7], viewport, content, scrollY = 0))
        assertEquals(184, RowWindow.scrollFor(rows[7], viewport, content, scrollY = 999))
    }

    @Test
    fun `content shorter than the window never scrolls`() {
        // Three rows in a 260 dp window: nothing to scroll.
        val short = RowWindow.Row(top = 112, height = 52)
        assertEquals(0, RowWindow.scrollFor(short, viewport, contentHeight = 164, scrollY = 0))
        assertEquals(0, RowWindow.scrollFor(short, viewport, contentHeight = 164, scrollY = 40))
    }

    @Test
    fun `without a window nothing scrolls`() {
        // Before the first measurement the height is 0 — then there is no
        // alignment, and a guessed value would be worse than none.
        assertEquals(0, RowWindow.scrollFor(rows[4], viewportHeight = 0, content, scrollY = 100))
        assertFalse(RowWindow.isVisible(rows[0], viewportHeight = 0, scrollY = 0))
    }

    @Test
    fun `a pass down and back up hits the same positions`() {
        // The first four rows need no scroll; from row 5 the window moves
        // along, and coming back up the same position returns.
        var scrollY = 0
        val down = rows.indices.map { index ->
            scrollY = RowWindow.scrollFor(rows[index], viewport, content, scrollY)
            scrollY
        }
        assertEquals(listOf(0, 0, 0, 0, 16, 72, 128, 184), down)
        val up = intArrayOf(0, 56, 112, 168, 184, 184, 184, 184)
        scrollY = 184
        for (index in rows.indices.reversed()) {
            scrollY = RowWindow.scrollFor(rows[index], viewport, content, scrollY)
            assertEquals("row $index", up[index], scrollY)
        }
    }

    @Test
    fun `only a fully visible row is a focus candidate`() {
        // Window 0..260: row 3 (168..220) sits fully inside it, row 4
        // (224..276) sticks out.
        assertTrue(RowWindow.isVisible(rows[3], viewport, scrollY = 0))
        assertFalse(RowWindow.isVisible(rows[4], viewport, scrollY = 0))
        // Same at the top: at position 184 row 3 (168..220) sticks out at the top.
        assertTrue(RowWindow.isVisible(rows[4], viewport, scrollY = 184))
        assertFalse(RowWindow.isVisible(rows[3], viewport, scrollY = 184))
    }

    @Test
    fun `the window's edges count as visible`() {
        // Exactly on the edge is "fully visible" — otherwise the first row of
        // an unscrolled block would drop out of the selection.
        assertTrue(RowWindow.isVisible(RowWindow.Row(0, 52), viewport, scrollY = 0))
        assertTrue(RowWindow.isVisible(RowWindow.Row(208, 52), viewport, scrollY = 0))
        assertFalse(RowWindow.isVisible(RowWindow.Row(209, 52), viewport, scrollY = 0))
    }
}
