package com.piotv.navigate.ui

/**
 * The visible window over the Now Playing rows — pure calculation without
 * Android types, therefore testable without a device (RowWindowTest).
 *
 * The calculation is the same one `ScrollView` performs internally in
 * `computeScrollDeltaToGetChildRectOnScreen`: the window is
 * `[scrollY, scrollY + viewportHeight]`, no margins and no padding. It lives
 * here separately because [PanelScrollView] needs it **before** the focus
 * change — scroll first, then hand over the focus. The other way around the
 * framework's focus finder jumps to a row still outside the window: exactly
 * the bug of 0.15.0, where the history filled the screen.
 */
object RowWindow {

    /**
     * A row as it sits in the block: top edge and height in **content
     * coordinates** (not screen coordinates). The top edge stays put while
     * scrolling — it describes the content, not what is on screen.
     */
    data class Row(val top: Int, val height: Int)

    /**
     * Does the row lie **entirely** within the window?
     *
     * "Entirely" is the condition that matters: a half-clipped row is a
     * candidate for the focus finder that the user cannot see
     * (PanelScrollView.addFocusables asks here).
     */
    fun isVisible(row: Row, viewportHeight: Int, scrollY: Int): Boolean {
        if (viewportHeight <= 0) return false
        return row.top >= scrollY && row.top + row.height <= scrollY + viewportHeight
    }

    /**
     * Where must one scroll so that [row] lies entirely within the window?
     *
     * Three cases, in this order:
     *
     *  - **Above the window** (▲): the row's top edge becomes the top edge
     *    of the window.
     *  - **Below the window** (▼): the row's bottom edge becomes the bottom
     *    edge of the window.
     *  - **Already visible**: the position stays — a row on screen must not
     *    shift the whole window on a focus change.
     *
     * The value always lies between `0` and `contentHeight - viewportHeight`:
     * a ScrollView cannot scroll past its content, and a negative value would
     * be a jump into empty space.
     */
    fun scrollFor(row: Row, viewportHeight: Int, contentHeight: Int, scrollY: Int): Int {
        if (viewportHeight <= 0) return 0
        val limit = (contentHeight - viewportHeight).coerceAtLeast(0)
        val current = scrollY.coerceIn(0, limit)
        val bottom = row.top + row.height
        return when {
            row.top < current -> row.top.coerceIn(0, limit)
            bottom > current + viewportHeight -> (bottom - viewportHeight).coerceIn(0, limit)
            else -> current
        }
    }
}
