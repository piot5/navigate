package com.piotv.navigate.data

/**
 * The settings Navigate accepts.
 *
 * They come from the file ([SettingsStore]), not from `SharedPreferences`: a
 * user setting belongs where it can arrive via `adb push` and where it can be
 * read (the same rule as for the order, see [OrderStore]). The marker of the
 * last started app is a different thing — runtime state that nobody needs to
 * know anymore after a process restart.
 *
 * @property nowPlaying Show the "Last seen" field in the top left? It appears
 *   only in the **collapsed** state — that space is empty there; expanded, it
 *   belongs to the apps.
 * @property showClock Show the clock in the top right corner? Default **on**
 *   (that is how it always was until 0.13). "Off" means not only invisible but
 *   also: no more ticking ([com.piotv.navigate.ui.Ticker]) — a timer for a
 *   field nobody sees would be measurable on the Chromecast.
 * @property background What lies behind the grid — [BackgroundStyle.DARK] is
 *   the previous solid-color background and remains the default.
 *   [BackgroundStyle.FOCUS] puts the TV banner of the app under the focus
 *   behind the grid (0.17).
 * @property backgroundFile The path of the media file for
 *   [BackgroundStyle.IMAGE]; `null` means "the default name in the external
 *   app folder" (see [BackgroundPlan]). An absolute path applies as is — that
 *   way `/sdcard/…` works too (which on Android 11+ is only readable without
 *   root for media folders, see docs/CONFIG.md). In focus mode the file stays
 *   unused.
 */
data class NavigateSettings(
    val nowPlaying: Boolean = false,
    val showClock: Boolean = true,
    val background: BackgroundStyle = BackgroundStyle.DARK,
    val backgroundFile: String? = null,
)
