package com.piotv.navigate.data

import android.content.Context
import android.os.SystemClock
import java.io.File

/**
 * Die Verlauf-Datei (`files/history.conf`) — was lief, in der Reihenfolge des
 * Auftretens (älteste zuerst, neueste zuletzt).
 *
 * Dieselbe Datei-Mechanik wie [OrderStore] und [SettingsStore]: intern lesen
 * und schreiben, `adb pull` über den externen Import-Pfad (der Verlauf wird
 * nie per `push` gesetzt — ein Import fände nur statt, wenn die externe Datei
 * **neuer** wäre; gelesen wird sie trotzdem, damit Diagnose möglich bleibt).
 * Hier steht nur, **was** in der Datei steht: ein Eintrag pro Zeile, gelesen
 * von [HistoryCodec].
 *
 * Zwei Grenzen, ehrlich gesagt:
 *
 *  - **Größe:** höchstens [MAX_ENTRIES] Einträge. Ältere fallen beim Schreiben
 *    weg — eine Datei, die ewig wächst, wäre auf dem Chromecast irgendwann ein
 *    Lesekosten-Problem beim Start.
 *  - **Lesen:** [latest] und [page] arbeiten auf dem Stand im Speicher
 *    ([current]) — kein Dateizugriff auf dem HOME-Pfad. Die Datei wird nur beim
 *    Anhängen und beim Start angefasst.
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

    /** Die Datei, die Navigate liest und schreibt (app-intern). */
    val file: File get() = config.file

    /** Für Diagnose und Doku: der Weg für `adb pull`. */
    val importPath: String? get() = config.importPath

    /** Aktueller Stand — **ohne Dateizugriff**, älteste zuerst. */
    fun current(): List<HistoryEntry> = config.current()

    /** Sieht nach, ob sich die Datei geändert hat, und übernimmt sie dann. */
    fun reloadIfChanged(): Boolean = config.reloadIfChanged()

    /**
     * Hängt einen Eintrag an — oder nichts, wenn die gleiche Zeile für dieses
     * Paket **schon die neueste** ist (ein Takt alle 5 Sekunden darf nicht bei
     * jedem Durchlauf dieselbe Zeile schreiben).
     *
     * Verglichen wird nur mit dem **letzten** Eintrag und **ohne** Zeitstempel:
     * der Takt erzeugt bei jedem Durchlauf eine neue Zeit, die Zeile bleibt aber
     * gleich — mit dem Zeitstempel im Vergleich wüchse die Datei deshalb bei
     * jedem Durchlauf. Gegen **alle** Einträge zu vergleichen wäre die andere
     * Extreme: derselbe Titel, der nach einem anderen wiederkehrt, käme nie
     * erneut in den Verlauf, obwohl er die Zeile erneut ersetzt hat (so steht es
     * in docs/CONFIG.md).
     *
     * @return `true`, wenn angehängt wurde.
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
     * Die neuesten Einträge zuerst — für die Anzeige.
     *
     * @param limit höchstens so viele; `0` heißt „alle“.
     */
    fun latest(limit: Int = 0): List<HistoryEntry> {
        val entries = config.current()
        if (entries.isEmpty()) return entries
        val reversed = entries.reversed()
        if (limit <= 0 || limit >= reversed.size) return reversed
        return reversed.subList(0, limit)
    }

    /**
     * Eine Seite des Verlaufs — für dynamisches Nachladen beim Scrollen.
     *
     * @param offset wie viele neueste Einträge übersprungen werden.
     * @param limit höchstens so viele; `0` heißt „alle ab offset“.
     */
    fun page(offset: Int, limit: Int = 0): List<HistoryEntry> {
        val reversed = latest()
        if (offset < 0 || offset >= reversed.size) return emptyList()
        if (limit <= 0) return reversed.subList(offset, reversed.size)
        return reversed.subList(offset, minOf(offset + limit, reversed.size))
    }

    fun save(entries: List<HistoryEntry>): Boolean = config.save(entries)

    companion object {
        private const val FILE_NAME = "history.conf"

        /**
         * Höchstens so viele Einträge in der Datei. 500 Zeilen à ~80 Zeichen
         * sind ~40 KB — beim Start einmal gelesen, danach nur im Speicher.
         * Wer mehr will, zieht per `adb pull` — die Datei ist lesbar.
         */
        const val MAX_ENTRIES = 500

        /** Wie viele Zeilen der Block zuerst zeigt, bevor gescrollt wird. */
        const val PAGE_SIZE = 8

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
