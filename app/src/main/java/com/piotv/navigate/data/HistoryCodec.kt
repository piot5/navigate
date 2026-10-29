package com.piotv.navigate.data

/**
 * Die Verlauf-Datei als Text: ein Eintrag pro Zeile.
 *
 * ```
 * # Navigate — Verlauf (Now Playing)
 * 1730000000000|com.zdf|ZDF · Tagesschau
 * ```
 *
 * Reines Kotlin ohne Android-Typen — wie [OrderCodec] an genau der Stelle, an
 * der eine handgeschriebene Datei still kaputtgeht. Toleranz ist Absicht:
 * `#`-Kommentare, Leerzeilen, umgebende Leerzeichen und Zeilen ohne die drei
 * Felder werden **ignoriert** statt als Fehler behandelt — eine unbekannte
 * Zeile darf Navigate nie am Starten hindern. Doppelte Einträge (gleiches
 * Paket, gleiche Zeile, gleiche Zeit) fallen weg.
 *
 * Bekannt, ehrlich gesagt: `#` beginnt einen Kommentar — auch mitten im Titel.
 * Ein Titel mit `#` wird an dieser Stelle gekürzt. Der Grund ist derselbe wie
 * bei der Reihenfolge-Datei ([OrderCodec]): handgeschriebene `#`-Kommentare
 * müssen möglich bleiben, und der Verlauf wird von Navigate selbst geschrieben,
 * nicht von Hand. Wer Titel mit `#` braucht, zieht per `adb pull` — dort steht
 * der volle Text nicht, sondern der gekürzte. Das ist die bewusste Grenze
 * dieses Formats.
 *
 * Die Datei wächst nur nach hinten: neue Einträge kommen dazu, gelesen wird
 * von hinten (neueste zuerst). Genau deshalb ist das Format eine Zeile pro
 * Eintrag und kein `schlüssel=wert` — dahinter steckt kein Parser für Blöcke,
 * sondern ein Zeilenleser, der bei Bedarf nur das Ende der Datei anfasst.
 */
object HistoryCodec {

    /** Trennt Zeit, Paket und Zeile — ein Zeichen, das in Titeln selten steht. */
    const val SEPARATOR = '|'

    /**
     * @param nowMillis Systemzeit für Einträge ohne Zeitstempel; `0` heißt
     *   „kein Fallback“ — dann fallen solche Zeilen weg.
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
            // Der Verlauf hält den **Inhalt**, nicht den Zustand: ein „· pausiert“
            // am Ende stammt aus einer älteren Fassung (bis 0.14.0) und wird hier
            // abgeschnitten. Sonst stünde derselbe Titel zweimal in der Liste —
            // einmal mit, einmal ohne Zusatz.
            val text_ = parts[2].trim().removeSuffix(NowPlayingText.PAUSED).trim()
            if (packageName.isEmpty() || text_.isEmpty()) continue
            if (seenAt <= 0L) continue
            val entry = HistoryEntry(packageName, text_, seenAt)
            if (result.contains(entry)) continue
            // Angrenzende Dopplungen zusammenziehen — genau das Paar, das der
            // Pausenzusatz hinterlassen hat. **Nicht** global: derselbe Titel, der
            // nach einem anderen wiederkehrt, bleibt zweimal im Verlauf (siehe
            // HistoryStore.append).
            val previous = result.lastOrNull()
            if (previous != null && previous.packageName == packageName && previous.line == text_) continue
            result.add(entry)
        }
        return result
    }

    fun serialize(entries: List<HistoryEntry>): String = buildString {
        appendLine("# Navigate — Verlauf (Now Playing)")
        appendLine("#")
        appendLine("# Eine Zeile pro Eintrag: zeit|paket|zeile, neueste zuletzt.")
        appendLine("# Diese Datei schreibt Navigate selbst; sie ist zusätzlich")
        appendLine("# per 'adb pull' lesbar (siehe docs/CONFIG.md).")
        appendLine()
        for (entry in entries) {
            append(entry.seenAtMillis).append(SEPARATOR)
            append(entry.packageName).append(SEPARATOR)
            appendLine(entry.line)
        }
    }
}
