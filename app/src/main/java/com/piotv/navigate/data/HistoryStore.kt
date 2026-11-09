package com.piotv.navigate.data

import android.content.Context
import android.os.SystemClock
import java.io.File

/**
 * The history file (`files/history.conf`) — what played, in order of
 * appearance (oldest first, newest last).
 *
 * The same file mechanics as [OrderStore] and [SettingsStore]: read and write
 * internally, `adb pull` via the external import path (the history is never
 * set via `push` — an import would only happen if the external file were
 * **newer**; it is still read so that diagnostics remain possible). Only
 * **what** is in the file lives here: one entry per line, read by
 * [HistoryCodec].
 *
 * Two limits, honestly:
 *
 *  - **Size:** at most [MAX_ENTRIES] entries. Older ones drop out on write —
 *    a file that grows forever would eventually make startup reads costly on
 *    the Chromecast.
 *  - **Reading:** [latest] works on the in-memory state ([current]) — no file
 *    access on the HOME path. The file is only touched when appending and at
 *    startup.
 */
class HistoryStore internal constructor(
    internalFile: File,
    importFile: File?,
    elapsedRealtime: () -> Long,
) {

    private val config = ConfigFile<List<HistoryEntry>>(
        internalFile = internalFile,
        importFile = importFile,
        elapsedRealtime = elapsedRealtime,
        parse = { HistoryCodec.parse(it) },
        serialize = HistoryCodec::serialize,
    )

    /** The file Navigate reads and writes (app-internal). */
    val file: File get() = config.file

    /** For diagnostics and docs: the path for `adb pull`. */
    val importPath: String? get() = config.importPath

    /** Current state — **no file access**, oldest first. */
    fun current(): List<HistoryEntry> = config.current()

    /** Checks whether the file has changed, and adopts it if so. */
    fun reloadIfChanged(): Boolean = config.reloadIfChanged()

    /**
     * Appends an entry — or nothing, when the same line for this package is
     * **already the newest** (a tick every 5 seconds must not write the same
     * line on every pass).
     *
     * The comparison uses only the **last** entry and **no** timestamp: the
     * tick produces a new time on every pass while the line stays the same —
     * with the timestamp in the comparison the file would grow on every pass.
     * Comparing against **all** entries would be the other extreme: the same
     * title returning after a different one would never enter the history
     * again, even though it replaced the line again (as documented in
     * docs/CONFIG.md).
     *
     * @return `true` when something was appended.
     */
    fun append(packageName: String, line: String, seenAtMillis: Long): Boolean {
        if (packageName.isEmpty() || line.isEmpty() || seenAtMillis <= 0L) return false
        val entries = config.current()
        val last = entries.lastOrNull()
        if (last != null && last.packageName == packageName && last.line == line) return false
        val next = if (entries.size >= MAX_ENTRIES) {
            entries.drop(entries.size - MAX_ENTRIES + 1) + HistoryEntry(packageName, line, seenAtMillis)
        } else {
            entries + HistoryEntry(packageName, line, seenAtMillis)
        }
        save(next)
        return true
    }

    /**
     * The newest entries first — for display.
     *
     * @param limit at most this many; `0` means "all".
     */
    fun latest(limit: Int = 0): List<HistoryEntry> {
        val entries = config.current()
        if (entries.isEmpty()) return entries
        val reversed = entries.reversed()
        if (limit <= 0 || limit >= reversed.size) return reversed
        return reversed.subList(0, limit)
    }

    fun save(entries: List<HistoryEntry>): Boolean = config.save(entries)

    companion object {
        private const val FILE_NAME = "history.conf"

        /**
         * At most this many entries in the file. 500 lines at ~80 characters
         * each are ~40 KB — read once at startup, then kept only in memory.
         * Anyone who wants more pulls via `adb pull` — the file is readable.
         */
        const val MAX_ENTRIES = 500

        @Volatile
        private var instance: HistoryStore? = null

        fun get(context: Context): HistoryStore =
            instance ?: synchronized(this) {
                instance ?: create(context).also { instance = it }
            }

        private fun create(context: Context): HistoryStore {
            val appContext = context.applicationContext
            return HistoryStore(
                internalFile = File(appContext.filesDir, FILE_NAME),
                importFile = appContext.getExternalFilesDir(null)?.let { File(it, FILE_NAME) },
                elapsedRealtime = SystemClock::elapsedRealtime,
            )
        }
    }
}
