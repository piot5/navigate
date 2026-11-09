package com.piotv.navigate.data

/**
 * What is playing right now — the details the UI needs.
 *
 * The class knows no Android: it is filled by [NowPlaying] (reading the
 * Media session), read by [NowPlayingText] (turning it into one line). The
 * split is the same seam as for the “last in foreground” question
 * ([LastApp.packageName]) and for the same reason — the rule, not the
 * Android quirk, was the mistake twice already in this project. That is
 * why it is testable here without a device.
 */
data class NowPlayingInfo(
    val title: String? = null,
    val artist: String? = null,
    val playing: Boolean = true,
    /**
     * Name of the app the session comes from (e.g. “Spotify”).
     *
     * The field is filled by the home side, not by [NowPlaying]: only the
     * app list (`AppRepository`) knows the names, and that list should not
     * grow into this class. If the name is missing, the line stays as it
     * was — the name is the seasoning, not the main dish.
     */
    val app: String? = null,
)

/**
 * Turns a session into **one** line for the field in the top-left corner.
 *
 * No icon, no two lines, no font sizes: a television seen from three meters
 * away and a 20-sp text tolerate exactly one line. The promise is “what is
 * playing and where” — not “what the song is officially called”. An empty
 * text means “nothing to show” and hides the field instead of putting up a
 * placeholder.
 *
 * **Two texts from one session — and why that is necessary:** [content] is the
 * content (app, artist, title), [line] is the content **plus** the suffix
 * “· paused” when the session is paused. The history hangs on the **content**,
 * not on the display line: otherwise every pause press would be a “new title”
 * and the same movie would appear twice in the list — once with and once
 * without “paused” (seen exactly like that on the target device). The
 * display, on the other hand, **must** carry the pause suffix, otherwise a
 * paused session would look like a running one.
 */
object NowPlayingText {

    /** Between artist and title. */
    const val SEPARATOR = " – "

    /** Between the app name and what is playing. */
    const val APP_SEPARATOR = " · "

    /**
     * Suffix for when the session is **paused**.
     *
     * It stays on screen because otherwise the row would look like
     * “playing”: after a pause press on the remote the title would simply
     * keep showing.
     */
    const val PAUSED = " · paused"

    /**
     * The **content** of the session without the pause suffix — the identity
     * used by the history (see [NowPlayingBoard.observe]). Empty means
     * “nothing to show”.
     */
    fun content(info: NowPlayingInfo?): String {
        if (info == null) return ""
        val title = info.title?.trim().orEmpty()
        val artist = info.artist?.trim().orEmpty()
        val text = when {
            title.isNotEmpty() && artist.isNotEmpty() -> "$artist$SEPARATOR$title"
            title.isNotEmpty() -> title
            artist.isNotEmpty() -> artist
            else -> return ""
        }
        val app = info.app?.trim().orEmpty()
        return buildString {
            if (app.isNotEmpty()) append(app).append(APP_SEPARATOR)
            append(text)
        }
    }

    /**
     * The **display line**: [content] plus “· paused” when the session is
     * paused. This is the text shown in the UI — and **not** what is written
     * into the history.
     */
    fun line(info: NowPlayingInfo?): String {
        val content = content(info)
        if (content.isEmpty()) return ""
        return if (info!!.playing) content else content + PAUSED
    }
}
