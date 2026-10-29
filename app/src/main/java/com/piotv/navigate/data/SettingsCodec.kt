package com.piotv.navigate.data

/**
 * Die Einstellungsdatei als Text: `schlüssel=wert`, eine Zeile pro Eintrag.
 *
 * ```ini
 * # Navigate — Einstellungen
 * now_playing=off
 * show_clock=on
 * background=dark
 * background_file=
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
 *
 * Seit 0.14 gibt es vier Schlüssel: zwei Schalter, die Hintergrund-Art und den
 * Pfad der Hintergrund-Datei. `background_file=` **leer** heißt „der Standardname
 * im externen App-Ordner“ ([BackgroundPlan]) — so lässt sich ein einmal gesetzter
 * Pfad auch wieder zurücknehmen.
 *
 * Die Hintergrund-Art kennt seit 0.14.1 nur noch `dark` und `image`: `video` aus
 * 0.14 ist entfernt und wird jetzt wie ein unbekannter Wert gelesen — der
 * bisherige Stand bleibt dann stehen, geraten wird nicht.
 */
object SettingsCodec {

    /** Der Schlüssel des „Last seen“-Feldes — genau so steht er in der Datei. */
    const val KEY_NOW_PLAYING = "now_playing"

    /** Die Uhr oben rechts. */
    const val KEY_SHOW_CLOCK = "show_clock"

    /** Der Hintergrund: `dark` oder `image`. */
    const val KEY_BACKGROUND = "background"

    /** Der Pfad der Mediendatei für `image`; leer = Standardname. */
    const val KEY_BACKGROUND_FILE = "background_file"

    private val TRUE_VALUES = setOf("on", "true", "1", "an", "ja", "yes")
    private val FALSE_VALUES = setOf("off", "false", "0", "aus", "nein", "no")

    private val DARK_VALUES = setOf("dark", "dunkel", "off", "aus", "kein", "none")
    private val IMAGE_VALUES = setOf("image", "bild", "foto", "picture")

    fun parse(text: String?): NavigateSettings {
        if (text == null) return NavigateSettings()
        var settings = NavigateSettings()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val separator = line.indexOf('=')
            // Ohne "=wert" sagt die Zeile nichts über eine Einstellung.
            if (separator <= 0) continue
            val value = line.substring(separator + 1).trim()
            when (normalize(line.substring(0, separator))) {
                KEY_NOW_PLAYING ->
                    boolean(value)?.let { settings = settings.copy(nowPlaying = it) }
                KEY_SHOW_CLOCK ->
                    boolean(value)?.let { settings = settings.copy(showClock = it) }
                KEY_BACKGROUND ->
                    style(value)?.let { settings = settings.copy(background = it) }
                // Leer heißt ausdrücklich „kein eigener Pfad“ — der Standardname
                // gilt dann wieder.
                KEY_BACKGROUND_FILE ->
                    settings = settings.copy(backgroundFile = value.ifEmpty { null })
                else -> Unit // unbekannter Schlüssel: die Zeile sagt nichts
            }
        }
        return settings
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
        appendLine("$KEY_SHOW_CLOCK=${if (settings.showClock) "on" else "off"}")
        appendLine("$KEY_BACKGROUND=${name(settings.background)}")
        appendLine("$KEY_BACKGROUND_FILE=${settings.backgroundFile.orEmpty()}")
    }

    /** `Now_Playing`, `now-playing` und `NOW PLAYING` sind derselbe Schlüssel. */
    private fun normalize(key: String): String =
        key.trim().lowercase().replace('-', '_').replace(' ', '_')

    /** `true`, `false` oder `null` — `null` heißt „kein bekannter Wert“. */
    private fun boolean(value: String): Boolean? = when (value.lowercase()) {
        in TRUE_VALUES -> true
        in FALSE_VALUES -> false
        else -> null
    }

    /** Die Hintergrund-Art — `null` heißt „kein bekannter Wert“. */
    private fun style(value: String): BackgroundStyle? = when (value.lowercase()) {
        in DARK_VALUES -> BackgroundStyle.DARK
        in IMAGE_VALUES -> BackgroundStyle.IMAGE
        else -> null
    }

    /** Geschrieben wird die englische Form — sie ist der Schlüsselwert der Doku. */
    private fun name(style: BackgroundStyle): String = when (style) {
        BackgroundStyle.DARK -> "dark"
        BackgroundStyle.IMAGE -> "image"
    }
}
