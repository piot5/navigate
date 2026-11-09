package com.piotv.navigate.data

/**
 * A history entry: what played, where — and when.
 *
 * Pure Kotlin without Android types: written by the now-playing tick
 * ([NowPlayingBoard]), read by the panel — the rule, not the wiring, is what
 * is testable here (HistoryCodecTest).
 *
 * @property packageName package of the app the title comes from.
 * @property line the final line text (as it appeared on screen).
 * @property seenAtMillis when the title was seen (system time).
 */
data class HistoryEntry(
    val packageName: String,
    val line: String,
    val seenAtMillis: Long,
)
