package com.piotv.navigate.ui

import android.app.Activity
import android.app.PendingIntent
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.piotv.navigate.R
import com.piotv.navigate.data.AppEntry
import com.piotv.navigate.data.HistoryEntry
import com.piotv.navigate.data.HistoryStore
import com.piotv.navigate.data.NowPlaying
import com.piotv.navigate.data.NowPlayingBoard
import com.piotv.navigate.data.NowPlayingText
import com.piotv.navigate.data.NowPlayingTrack
import com.piotv.navigate.util.Background

/**
 * The "Now Playing" rows at the top left: **one per app** with an active media
 * session, below it **one** history row from `history.conf` — the newest entry
 * not currently playing ("Last seen"); everything older sits behind a long
 * press of OK on that row.
 *
 * Until 0.12 this was a single block with one row — the session that "makes
 * the sound". If an audiobook played paused next to a radio station, one of
 * the two pieces of information disappeared; moreover the block was rewritten
 * on every pass. Since 0.13 the rule is:
 *
 *  - **One row per app.** Which session of an app counts (the playing one,
 *    else the first with a title) is decided by [NowPlaying.active].
 *  - **A row changes only when something changes for that app.** The
 *    comparison lives in [bind] (text against text), the rule behind it in
 *    [NowPlayingBoard]. Without them the same row would be set again every
 *    five seconds and a new cover fetched.
 *  - **A failed look deletes nothing**: [NowPlaying.active] then returns
 *    `null` instead of an empty list.
 *
 * Since the history version, additionally:
 *
 *  - **Nothing is lost.** When a new title replaces an app's row, the old one
 *    moves into the history ([HistoryStore]) — instead of disappearing.
 *  - **Only the newest entry is visible.** The block shows live rows first,
 *    below them **one** history row: the newest entry not already standing as
 *    a live row ([LastSeen.visible]). Everything older sits behind a **long
 *    press of OK** on that row — the context menu ([HistoryMenu]) unfolds the
 *    older entries and brings the chosen app back. Until 0.15.1 the whole
 *    history stood there and was paged in; a list you first have to leaf
 *    through is not a list on a TV.
 *  - **A window, not a growing block.** The block is at most
 *    `now_playing_max_height` tall and then scrolls within itself; the
 *    scrollbar is permanent, and ▲/▼ walk the block row by row
 *    ([PanelScrollView]). Since 0.15.1 this is also **effective**: until then
 *    the limit stood as `android:maxHeight` on the ScrollView — an attribute
 *    that does not exist there. The history therefore grew until the grid was
 *    pushed off screen, and the arrow keys did nothing.
 *
 * The views are built **once** per row and reused afterwards ([liveRows],
 * [historyRows]) — the same idea as `convertView` in the grid. A new app joins
 * at the end, a disappeared row drops out of the tree.
 *
 * What this class does **not** know: which app names exist
 * ([Listener.entryOf]), what a click on a row should do ([Listener.openRow]),
 * and how much space the grid above needs ([Listener.onHeightChanged]).
 */
class NowPlayingPanel(
    private val activity: Activity,
    private val container: LinearLayout,
    /**
     * The ScrollView around [container] — the **visible** height of the block.
     *
     * It is declared here explicitly and not looked up via `container.parent`:
     * two easily-confused things hang on it. The grid starts below what is
     * **seen**, not below what the block contains in total (with 500 history
     * entries that would be the whole screen — exactly the bug from 0.15.0).
     * And it caps the height (`app:maxHeight`, see [PanelScrollView]).
     */
    private val scroll: ScrollView,
    private val listener: Listener,
    private val source: NowPlaying,
    private val iconLoader: IconLoader,
    private val history: HistoryStore,
    intervalMs: Long,
) {

    /**
     * What the home screen must contribute.
     *
     * Deliberately a listener and not an activity reference: the app list,
     * launching and focus live there, and the block should not grow together
     * with them (that is exactly what bogged down the 769-line activity of 0.12).
     */
    interface Listener {
        /** Is the field enabled **and** the grid collapsed? */
        fun isEnabled(): Boolean

        /** An app's entry — for the name and the icon as a fallback. */
        fun entryOf(packageName: String): AppEntry?

        /**
         * OK on a row: jump into the session, else start the app.
         *
         * @param sessionActivity the app's "Now playing" screen, or `null`.
         */
        fun openRow(packageName: String, sessionActivity: PendingIntent?)

        /** The block disappears while it has the focus. */
        fun onFocusLost()

        /**
         * How much height (px) the block **visibly** occupies; `0` means "it is
         * off".
         *
         * This means the window (`NowPlayingPanel.scroll`), not the content:
         * the history is longer than the window and is scrolled.
         */
        fun onHeightChanged(heightPx: Int)
    }

    private val ticker = Ticker(intervalMs) { refresh() }

    /** The rule behind which rows exist and when they change. */
    private val board = NowPlayingBoard()

    /** The live row views, by package — each is built exactly once. */
    private val liveRows = LinkedHashMap<String, View>()

    /** The history row views, by time+package+line — each is built exactly once. */
    private val historyRows = LinkedHashMap<String, View>()

    /** The context menu of the Last-seen row: the older entries (long press of OK). */
    private val historyMenu = HistoryMenu(activity)

    /** The last seen info per app — for OK and the cover art. */
    private val tracks = HashMap<String, NowPlayingTrack>()

    private var reportedHeight = -1

    init {
        // The height of the block decides where the grid may start (see
        // LauncherActivity.onHeightChanged): two Now Playing rows must not sit
        // behind the first app row.
        //
        // Measured is the **ScrollView**, not its content: it is the window.
        // The content can be longer than the window (one row per app with an
        // active session); if its height were reported to the grid, the first
        // app row would sit behind the block.
        scroll.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) reportHeight(bottom - top)
        }
    }

    fun start() = ticker.start()

    fun stop() = ticker.stop()

    /** Draws the stored state and regulates visibility. */
    fun refreshVisibility() {
        render()
        updateVisibility()
    }

    /**
     * Queries the media sessions and arranges the rows accordingly.
     *
     * Querying happens only when the block can appear at all
     * ([Listener.isEnabled]) — disabled or expanded, every call would be a
     * binder call for nothing. The query runs on the background thread, like
     * the usage stats and for the same reason.
     */
    private fun refresh() {
        if (!listener.isEnabled()) {
            refreshVisibility()
            return
        }
        val artSize = activity.resources.getDimensionPixelSize(R.dimen.now_playing_art_size)
        Background.io.execute {
            val found = source.active(artSize)
            Background.main.post {
                if (!listener.isEnabled()) {
                    refreshVisibility()
                    return@post
                }
                // The app name comes from the app list and is therefore fetched
                // on the main thread — the list lives there. If the line is
                // empty (loading screen, ads), `info = null` is passed: the
                // display keeps what it had instead of deleting the row.
                val observed = found?.map { track ->
                    val label = listener.entryOf(track.packageName)?.label ?: track.packageName
                    NowPlayingBoard.Observed(
                        packageName = track.packageName,
                        info = track.info.copy(app = label)
                            .takeIf { NowPlayingText.line(it).isNotEmpty() },
                        ended = track.ended,
                    )
                }
                // `null` means "the query failed": the rows stay as they are,
                // and the covers are not fetched again either.
                found?.forEach { tracks[it.packageName] = it }
                history.reloadIfChanged()
                board.observe(observed, history, System.currentTimeMillis())
                render()
                updateVisibility()
            }
        }
    }

    /**
     * Puts the views into the board's order — and only refreshes the text.
     *
     * Below it **one** history row: the newest entry not currently standing as
     * a live row ([LastSeen.visible]). Everything older sits behind a long
     * press of OK on that row ([LastSeen.older] → [HistoryMenu]) — the block
     * does not grow with the archive.
     */
    private fun render() {
        val current = board.rows()
        val spacing = activity.resources.getDimensionPixelSize(R.dimen.now_playing_row_spacing)
        var index = 0
        for (row in current) {
            val view = liveRows[row.packageName] ?: inflateLive(row.packageName)
            if (container.getChildAt(index) !== view) {
                container.removeView(view)
                container.addView(view, index)
            }
            (view.layoutParams as? LinearLayout.LayoutParams)?.topMargin =
                if (index == 0) 0 else spacing
            bindLive(view, row)
            index++
        }
        // One history row: the newest entry not currently playing ("Last
        // seen"). The comparison uses the **content** (without "· paused") —
        // the same text never appears twice on screen, neither among the live
        // rows nor in the menu. Everything older sits behind a long press of
        // OK on this row ([LastSeen.older] → [HistoryMenu]): the block does not
        // grow with the archive.
        val liveContent = current.map { it.packageName to it.content }.toSet()
        val allHistory = history.latest()
        val newest = LastSeen.visible(allHistory, liveContent)
        if (newest != null) {
            val key = historyKey(newest)
            val view = historyRows[key] ?: inflateHistory(key, newest)
            if (container.getChildAt(index) !== view) {
                container.removeView(view)
                container.addView(view, index)
            }
            (view.layoutParams as? LinearLayout.LayoutParams)?.topMargin = spacing
            bindHistory(view, newest, LastSeen.older(allHistory, liveContent))
            index++
        }
        // Remove rows that no longer exist from the tree and from the maps.
        while (container.childCount > index) {
            val stale = container.getChildAt(index)
            container.removeViewAt(index)
            liveRows.entries.removeAll { it.value === stale }
            historyRows.entries.removeAll { it.value === stale }
        }
        tracks.keys.retainAll(current.map { it.packageName }.toSet())
    }

    private fun historyKey(entry: HistoryEntry): String =
        "${entry.seenAtMillis}|${entry.packageName}|${entry.line}"

    private fun inflateLive(packageName: String): View {
        val view = LayoutInflater.from(activity)
            .inflate(R.layout.item_now_playing, container, false)
        liveRows[packageName] = view
        return view
    }

    private fun inflateHistory(key: String, entry: HistoryEntry): View {
        val view = LayoutInflater.from(activity)
            .inflate(R.layout.item_now_playing, container, false)
        view.alpha = HISTORY_ALPHA
        historyRows[key] = view
        view.contentDescription = entry.line
        return view
    }

    /**
     * Writes the live row — **only if something changed for this app**.
     *
     * The text is the comparison key: it contains app, artist, title and the
     * pause state. If it is equal, the cover stays in place too (and is not
     * fetched again) — that is the promise "the row changes only when Now
     * Playing changes for this app".
     */
    private fun bindLive(view: View, row: NowPlayingBoard.Row) {
        val text = view.findViewById<TextView>(R.id.now_playing_row_text)
        view.setOnClickListener { open(row.packageName) }
        if (text.text?.toString() == row.line) return
        text.text = row.line
        // For screen readers the row is a row, not a button.
        view.contentDescription = row.line
        applyArt(view.findViewById(R.id.now_playing_row_art), row.packageName)
    }

    /**
     * Writes the history row — once, and then it stays.
     *
     * No cover from the session (that is long gone), only the app icon as a
     * fallback. The row is dimmed ([HISTORY_ALPHA]): it is the past, not the
     * present. OK brings the app back; a **long press of OK** unfolds the older
     * entries ([HistoryMenu]) — that route exists only when there are older
     * ones.
     */
    private fun bindHistory(view: View, entry: HistoryEntry, older: List<HistoryEntry>) {
        val text = view.findViewById<TextView>(R.id.now_playing_row_text)
        view.setOnClickListener { open(entry.packageName) }
        if (older.isEmpty()) {
            view.setOnLongClickListener(null)
        } else {
            view.setOnLongClickListener {
                historyMenu.show(older) { packageName -> open(packageName) }
                true
            }
        }
        if (text.text?.toString() != entry.line) {
            text.text = entry.line
        }
        view.contentDescription = entry.line
        val art = view.findViewById<ImageView>(R.id.now_playing_row_art)
        val entry_ = listener.entryOf(entry.packageName)
        if (entry_ == null) {
            hideArt(art)
            return
        }
        val size = activity.resources.getDimensionPixelSize(R.dimen.now_playing_art_size)
        val key = entry_.iconKey(size)
        art.tag = key
        val cached = iconLoader.get(entry_, size) { loaded ->
            if (art.tag == key) art.setImageBitmap(loaded)
        }
        if (cached != null) {
            art.setImageBitmap(cached)
        } else {
            art.setImageResource(R.drawable.bg_icon_placeholder)
        }
        art.visibility = View.VISIBLE
    }

    /** Cover from the session, else the app icon from our own cache. */
    private fun applyArt(art: ImageView, packageName: String) {
        val cover = tracks[packageName]?.art
        if (cover != null) {
            art.tag = null
            art.setImageBitmap(cover)
            art.visibility = View.VISIBLE
            return
        }
        val entry = listener.entryOf(packageName)
        if (entry == null) {
            hideArt(art)
            return
        }
        // The same flag (`tag`) as in the grid: an icon request can come back
        // later while the row already shows something else.
        val size = activity.resources.getDimensionPixelSize(R.dimen.now_playing_art_size)
        val key = entry.iconKey(size)
        art.tag = key
        val cached = iconLoader.get(entry, size) { loaded ->
            if (art.tag == key) art.setImageBitmap(loaded)
        }
        if (cached != null) {
            art.setImageBitmap(cached)
        } else {
            art.setImageResource(R.drawable.bg_icon_placeholder)
        }
        art.visibility = View.VISIBLE
    }

    private fun hideArt(art: ImageView) {
        art.tag = null
        art.setImageDrawable(null)
        art.visibility = View.GONE
    }

    /**
     * The rows are visible only when the field is enabled, the grid is
     * **collapsed** (expanded, the space belongs to the apps) and there is
     * something to show. The stored state is preserved throughout: on
     * collapsing it is there again immediately instead of waiting for the next
     * query.
     */
    private fun updateVisibility() {
        val show = listener.isEnabled() && (board.rows().isNotEmpty() || history.current().isNotEmpty())
        // If the block disappears while it has the focus, the focus must go back
        // to the grid — otherwise the remote control fires into the void.
        if (!show && container.hasFocus()) listener.onFocusLost()
        // The ScrollView becomes visible (activity_home.xml): the inner
        // container holds the rows, the ScrollView caps the height.
        scroll.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) reportHeight(0)
    }

    /** OK on a row: first the "Now playing" screen, else the app. */
    private fun open(packageName: String) {
        listener.openRow(packageName, tracks[packageName]?.activity)
    }

    /**
     * Reports the height of the block when it changes. The grid starts below it
     * — otherwise the first app row would sit behind the second row.
     */
    private fun reportHeight(heightPx: Int) {
        if (heightPx == reportedHeight) return
        reportedHeight = heightPx
        listener.onHeightChanged(heightPx)
    }

    companion object {
        /**
         * Ticker interval of the field. Five seconds are the compromise: a
         * track change should arrive promptly, and a binder call at this rate
         * is nothing on the target device — the usage stats next to it are the
         * more expensive of the two. Runs only while the home screen is visible.
         */
        const val NOW_PLAYING_INTERVAL_MS = 5_000L

        /**
         * History is the past: dimmed, but readable. 60% opacity separates
         * "playing" from "played" without the row disappearing.
         */
        const val HISTORY_ALPHA = 0.6f
    }
}
