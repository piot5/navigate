package com.piotv.navigate.data

/**
 * Die Einstellungsdatei als Text: `schlüssel=wert`, eine Zeile pro Eintrag.
 *
 * ```ini
 * # Navigate — Einstellungen
 * now_playing=on
 * ```
 *
 * Reines Kotlin ohne Android-Typen — wie [OrderCodec] an genau der Stelle, an der
 * eine handgeschriebene Datei still kaputtgeht. Toleranz ist Absicht: erkannt
 * werden `on`/`off`, `true`/`false`, `1`/`0`, `an`/`aus`, `ja`/`nein`,
 * Groß- und Kleinschreibung, `_` und `-` im Schlüssel sowie umgebende
 * Leerzeichen. Zeilen ohne `=`, `#`-Kommentare und unbekannte Schlüssel werden
 * **ignoriert** statt als Fehler behandelt (eine unbekannte Zeile darf Navigate
 * nie am Starten hindern). Ein unbekannter **Wert** lässt den bisherigen Stand
 * stehen — geraten wird nicht.
 */
object SettingsCodec {

    /** Der Schlüssel des Now-Playing-Feldes — genau so steht er in der Datei. */
    const val KEY_NOW_PLAYING = "now_playing"

    private val TRUE_VALUES = setOf("on", "true", "1", "an", "ja", "yes")
    private val FALSE_VALUES = setOf("off", "false", "0", "aus", "nein", "no")

    fun parse(text: String?): NavigateSettings {
        if (text == null) return NavigateSettings()
        var nowPlaying = false
        for (rawLine in text.lineSequence()) {
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val separator = line.indexOf('=')
            // Ohne "=wert" sagt die Zeile nichts über eine Einstellung.
            if (separator <= 0) continue
            if (normalize(line.substring(0, separator)) != KEY_NOW_PLAYING) continue
            when (val value = line.substring(separator + 1).trim().lowercase()) {
                in TRUE_VALUES -> nowPlaying = true
                in FALSE_VALUES -> nowPlaying = false
                else -> Unit // unbekannter Wert: der bisherige Stand bleibt
            }
        }
        return NavigateSettings(nowPlaying)
    }

    fun serialize(settings: NavigateSettings): String = buildString {
        appendLine("# Navigate — Einstellungen")
        appendLine("#")
        appendLine("# Eine Zeile pro Eintrag: schlüssel=wert.")
        appendLine("# Zeilen mit '#' sind Kommentare, unbekannte Zeilen werden ignoriert.")
        appendLine("# Diese Datei schreiben die Navigate-Einstellungen; sie ist zusätzlich")
        appendLine("# per 'adb push' erreichbar (siehe docs/CONFIG.md).")
        appendLine()
        appendLine("$KEY_NOW_PLAYING=${if (settings.nowPlaying) "on" else "off"}")
    }

    /** `Now_Playing`, `now-playing` und `NOW PLAYING` sind derselbe Schlüssel. */
    private fun normalize(key: String): String =
        key.trim().lowercase().replace('-', '_').replace(' ', '_')
}
