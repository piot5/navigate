package com.piotv.navigate.data

/**
 * The history file as text: one entry per line.
 *
 * ```
 * # Navigate — history (Now Playing)
 * 1730000000000|com.zdf|ZDF · Tagesschau
 * ```
 *
 * Pure Kotlin without Android types — like [OrderCodec], at exactly the place
 * where a hand-written file silently breaks. Tolerance is intentional:
 * `#` comments, blank lines, surrounding whitespace and lines without the
 * three fields are **ignored** instead of treated as errors — an unknown line
 * must never stop Navigate from starting. Duplicate entries (same package,
 * same line, same time) are dropped.
 *
 * Known, honestly: `#` starts a comment — even in the middle of a title. A
 * title with `#` is cut off at that point. The reason is the same as for the
 * order file ([OrderCodec]): hand-written `#` comments must remain possible,
 * and the history is written by Navigate itself, not by hand. Anyone who
 * needs titles with `#` pulls via `adb pull` — what is stored there is not
 * the full text but the cut one. That is the deliberate limit of this
 * format.
 *
 * The file only grows at the end: new entries are appended, and reading goes
 * from the back (newest first). That is exactly why the format is one line
 * per entry and not `key=value` — behind it sits no block parser but a line
 * reader that, when needed, touches only the end of the file.
 */
object HistoryCodec {

    /** Separates time, package and line — a character rarely found in titles. */
    const val SEPARATOR = '|'

    /**
     * @param nowMillis system time for entries without a timestamp; `0` means
     *   "no fallback" — such lines are then dropped.
     */
    fun parse(text: String?, nowMillis: Long = 0L): List<HistoryEntry> {
        if (text == null) return emptyList()
        val result = ArrayList<HistoryEntry>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val parts = line.split(SEPARATOR, limit = 3)
            if (parts.size < 3) continue
            val rawTime = parts[0].trim()
            val seenAt = if (rawTime.isEmpty()) nowMillis else rawTime.toLongOrNull() ?: continue
            val packageName = parts[1].trim()
            // The history keeps the content, not the state: also strip the legacy German pause suffix written up to 0.14.0.
            val text_ = parts[2].trim().removeSuffix(NowPlayingText.PAUSED).removeSuffix(" · pausiert").trim()
            if (packageName.isEmpty() || text_.isEmpty()) continue
            if (seenAt <= 0L) continue
            val entry = HistoryEntry(packageName, text_, seenAt)
            if (result.contains(entry)) continue
            // Collapse adjacent duplicates — exactly the pair the pause suffix
            // left behind. **Not** global: the same title returning after another
            // one stays twice in the history (see HistoryStore.append).
            val previous = result.lastOrNull()
            if (previous != null && previous.packageName == packageName && previous.line == text_) continue
            result.add(entry)
        }
        return result
    }

    fun serialize(entries: List<HistoryEntry>): String = buildString {
        appendLine("# Navigate — history (Now Playing)")
        appendLine("#")
        appendLine("# One entry per line: time|package|line, newest last.")
        appendLine("# This file is written by Navigate itself; it is also")
        appendLine("# readable via 'adb pull' (see docs/CONFIG.md).")
        appendLine()
        for (entry in entries) {
            append(entry.seenAtMillis).append(SEPARATOR)
            append(entry.packageName).append(SEPARATOR)
            appendLine(entry.line)
        }
    }
}
