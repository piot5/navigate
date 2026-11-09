package com.piotv.navigate.ui

import com.piotv.navigate.data.HistoryEntry

/**
 * The split of the history: **one** row visible, the older ones in the menu.
 *
 * Until 0.15.1 the history sat below the live rows — newest on top, older ones
 * paged in while scrolling down. On a TV that is a list you first have to hunt
 * through. Since 0.16 the rule is:
 *
 *  - **Visible is the newest entry that is not currently playing.** If a title
 *    already plays as a live row, its history entry carries no new
 *    information — the next row moves up.
 *  - **Everything older belongs in the context menu** (long press of OK on the
 *    row), in the same order (newest first) and **without** the entries
 *    currently visible as live rows. That is the same rule against duplication
 *    as in the display: the same text never appears twice on screen — neither
 *    in the block nor in the menu.
 *
 * Pure computation without Android types, so it can be tested without a device
 * (LastSeenTest) — the same separation as [RowWindow].
 */
object LastSeen {

    /**
     * The one row shown in the block: the newest entry that is not visible as
     * a live row; `null` if there is none.
     *
     * @param history the history, **newest first** (`HistoryStore.latest()`).
     * @param live the playing rows as (package, content) — the same key a
     *   `NowPlayingBoard.Row` carries: `NowPlayingText.content`, i.e.
     *   **without** the "· paused" suffix.
     */
    fun visible(history: List<HistoryEntry>, live: Set<Pair<String, String>>): HistoryEntry? =
        notPlaying(history, live).firstOrNull()

    /**
     * The older entries for the context menu — everything in the history that
     * is **not on screen**: neither as a live row nor as the one shown row.
     * Newest first.
     */
    fun older(history: List<HistoryEntry>, live: Set<Pair<String, String>>): List<HistoryEntry> =
        notPlaying(history, live).drop(1)

    /** The history without the entries currently visible as live rows. */
    private fun notPlaying(
        history: List<HistoryEntry>,
        live: Set<Pair<String, String>>,
    ): List<HistoryEntry> = history.filterNot { live.contains(it.packageName to it.line) }
}
