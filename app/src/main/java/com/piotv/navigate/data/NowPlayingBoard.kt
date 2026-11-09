package com.piotv.navigate.data

/**
 * The "Now Playing" lines: **one per app** — and they stay put.
 *
 * Until 0.12 there was exactly one line: the session "making the sound".
 * With an audiobook paused next to a radio station, one of the two entries
 * disappeared. Since 0.13, **every app with an active session** gets its
 * own line.
 *
 * ## When a line changes — and when it goes
 *
 * The rule sits here, and in 0.13.2 it was **wrong**: an app's entry
 * disappeared after a few seconds (seen on the target device). The cause
 * was that three different things were treated alike. They are not:
 *
 * | What the poll says | What happens |
 * |---|---|
 * | A title (playing or paused) | Create the line, or rewrite it **only when the text changed** |
 * | Session there but **without a title** (loading screen, ads, menu) | The line stays as it is |
 * | **No** session for this app | The line stays (see below) |
 * | Session reports **ended** (`STOPPED`/`ERROR`) | The line disappears after [MISSING_POLLS_BEFORE_REMOVE] polls |
 * | Poll **failed** (`null`) | nothing changes |
 *
 * Why it has to be this fine-grained: a TV app destroys and recreates its
 * session while navigating, on track changes and during ads — on the target
 * device the WOW session was **gone entirely** after the movie, without
 * ever reporting `STOPPED`. Whoever reads that as "the app is gone" deletes
 * a line the user is reading at that very moment and writes it back seconds
 * later. That was exactly the bug.
 *
 * **The price, stated openly:** an app that loses its session without
 * reporting `STOPPED` keeps its line — until the launcher process restarts
 * (on the 1.4 GB Chromecast the normal case after a few hours). That is the
 * deliberate decision: a line someone is reading must not vanish under
 * their eyes.
 *
 * The promise stands: **a line changes only when something changes for
 * that app.** The comparison is the finished text; when it is equal,
 * nothing is written and no new cover is fetched.
 *
 * No Android type in this file: the rules are testable without a device
 * (NowPlayingBoardTest), the Android quirks live in [NowPlaying].
 */
class NowPlayingBoard {

    /** What **one** poll says about an app. */
    data class Observed(
        val packageName: String,
        /**
         * The session's details — `null` means "the session is giving
         * nothing right now" (loading screen, ads, no title). The line then
         * stays put.
         */
        val info: NowPlayingInfo?,
        /** The app has ended: the session reports `STOPPED`/`ERROR`. */
        val ended: Boolean = false,
    )

    /**
     * One line of the display: the app, its display text and its **content**.
     *
     * [line] is the text shown in the UI (with "· paused"). [content] is the
     * same content **without** the pause suffix — it is the key for "has
     * something changed for this app?" and what moves into the history.
     * Without this split every pause press would be a new title and the same
     * movie would sit twice in the list (once with, once without "paused").
     */
    data class Row(val packageName: String, val line: String, val content: String)

    private var rows: List<Row> = emptyList()

    /** How often in a row an app has reported **ended** — the grace period. */
    private val endedPolls = HashMap<String, Int>()

    /** The lines in display order. */
    fun rows(): List<Row> = rows

    /**
     * Takes in what a poll has seen.
     *
     * @param observed `null` means "the poll failed" (no access, the session
     *   service isn't responding) — then everything stays as it is.
     * @param history where new titles are additionally written (`null` means
     *   "no history" — then the previous behavior applies: replace the line).
     * @param nowMillis system time for the history; `0` means "no history".
     * @return `true` when the display has to change.
     */
    fun observe(
        observed: List<Observed>?,
        history: HistoryStore? = null,
        nowMillis: Long = 0L,
    ): Boolean {
        if (observed == null) return false

        // 1. What this poll says about which app — **once** per app. An app's
        //    first session wins: NowPlaying sorts "playing" to the front.
        val fresh = LinkedHashMap<String, Observed>()
        for (item in observed) {
            if (item.packageName.isEmpty() || fresh.containsKey(item.packageName)) continue
            fresh[item.packageName] = item
        }

        // 2. Merge: existing lines keep their place (only their text is
        //    rewritten), new apps join at the end. That way the whole display
        //    doesn't jump around when a second title starts playing.
        val merged = ArrayList<Row>(rows.size + fresh.size)
        for (row in rows) {
            val seen = fresh.remove(row.packageName)
            if (seen == null) {
                // Not seen at all: the session is gone (app in the background,
                // track change, app ended). The line stays.
                merged.add(row)
                continue
            }
            if (seen.ended) {
                // Explicitly ended — but only after two polls: between two
                // episodes the session briefly sits at STOPPED.
                val missed = (endedPolls[row.packageName] ?: 0) + 1
                endedPolls[row.packageName] = missed
                if (missed < MISSING_POLLS_BEFORE_REMOVE) merged.add(row)
                continue
            }
            endedPolls.remove(row.packageName)
            val content = NowPlayingText.content(seen.info)
            val line = NowPlayingText.line(seen.info)
            // Nothing to read (loading screen, ads): the line stays as it is —
            // the app is still there and will bring the same title back.
            if (content.isEmpty()) {
                merged.add(row)
                continue
            }
            // Same content: only the **display line** can have changed (the
            // pause state — with or without "· paused"). The history stays
            // untouched, otherwise the same title would sit in it twice.
            if (content == row.content) {
                merged.add(if (line == row.line) row else row.copy(line = line))
                continue
            }
            // New content for a known app: the old one moves into the history
            // (when one is kept), the line shows the new one.
            if (history != null && nowMillis > 0L) {
                history.append(row.packageName, row.content, nowMillis)
            }
            merged.add(Row(row.packageName, line, content))
        }

        // 3. Apps that weren't there before: only with text is this worth a line.
        for (seen in fresh.values) {
            if (seen.ended) continue
            val content = NowPlayingText.content(seen.info)
            if (content.isEmpty()) continue
            merged.add(Row(seen.packageName, NowPlayingText.line(seen.info), content))
        }

        // Clean up the counters of apps that no longer have a line.
        endedPolls.keys.removeAll { key -> merged.none { it.packageName == key } }

        val changed = merged != rows
        rows = merged
        return changed
    }

    companion object {
        /**
         * How often in a row an app may report **ended** before its line
         * disappears. Two passes are ten seconds — long enough for the
         * moment between two episodes when the session briefly sits at
         * `STOPPED`.
         */
        const val MISSING_POLLS_BEFORE_REMOVE = 2
    }
}
