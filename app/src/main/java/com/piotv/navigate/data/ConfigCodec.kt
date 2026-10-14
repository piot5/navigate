package com.piotv.navigate.data

/**
 * Die Konfigurationsdatei von Navigate: eine Anweisung pro Zeile,
 * `#` leitet einen Kommentar ein.
 *
 * ```
 * favorites=com.netflix.ninja,com.google.android.youtube.tv
 * max_favorites=6
 * columns=0
 * show_clock=true
 * show_icons=true
 * ```
 *
 * Reines Kotlin ohne Android-Typen: Parsen und Schreiben sind die Stellen, an
 * denen eine Konfigurationsdatei still kaputtgeht (Tippfehler, halbe Zeilen,
 * Leerzeichen), deshalb sind sie getrennt testbar (ConfigCodecTest).
 */
object ConfigCodec {

    const val KEY_FAVORITES = "favorites"
    const val KEY_MAX_FAVORITES = "max_favorites"
    const val KEY_COLUMNS = "columns"
    const val KEY_SHOW_CLOCK = "show_clock"
    const val KEY_SHOW_ICONS = "show_icons"

    /**
     * Obergrenze für die Zeile. 9 Elemente à 96dp (6 Favoriten + Home +
     * Navigate-Einstellungen + Einstellungen) passen bei 960dp Breite abzüglich
     * der Dock-Ränder genau; 7 Favoriten wären 960dp und damit zu breit.
     */
    const val HARD_MAX_FAVORITES = 6

    private const val DEFAULT_MAX_FAVORITES = 6

    data class Config(
        val favorites: List<String> = emptyList(),
        val maxFavorites: Int = DEFAULT_MAX_FAVORITES,
        /** Spalten im Raster; 0 = automatisch aus der Bildschirmbreite. */
        val columns: Int = 0,
        val showClock: Boolean = true,
        /**
         * Symbole im Raster anzeigen. `false` = reine Textliste.
         *
         * Der Schalter existiert aus einem Messgrund: die Symbole sind der
         * größte Einzelposten im Speicher (auf dem Zielgerät ~25 MB „Private
         * Clean" aus den gemappten Ressourcen fremder Apps) und der teuerste
         * Teil des ersten Rasteraufbaus. Ohne sie läuft der Launcher deutlich
         * schlanker — die Uhr und das Dock behalten ihre Symbole, dort sind es
         * neun Elemente statt eines ganzen Rasters.
         */
        val showIcons: Boolean = true,
    )

    fun default(): Config = Config()

    /**
     * Liest die Datei. Unbekannte Schlüssel und kaputte Zeilen werden ignoriert,
     * nicht als Fehler behandelt: eine handgeschriebene Datei soll nie dazu
     * führen, dass der Launcher nicht startet.
     */
    fun parse(text: String?): Config {
        if (text == null) return default()
        var favorites = emptyList<String>()
        var maxFavorites = DEFAULT_MAX_FAVORITES
        var columns = 0
        var showClock = true
        var showIcons = true
        for (rawLine in text.lineSequence()) {
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val separator = line.indexOf('=')
            if (separator <= 0) continue
            val key = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            when (key) {
                KEY_FAVORITES -> favorites = FavoritesCodec.decode(value)
                KEY_MAX_FAVORITES -> value.toIntOrNull()?.let {
                    maxFavorites = it.coerceIn(0, HARD_MAX_FAVORITES)
                }
                KEY_COLUMNS -> value.toIntOrNull()?.let {
                    columns = it.coerceIn(0, 12)
                }
                KEY_SHOW_CLOCK -> showClock = value.toBooleanStrictOrNull() ?: showClock
                KEY_SHOW_ICONS -> showIcons = value.toBooleanStrictOrNull() ?: showIcons
            }
        }
        return Config(
            favorites = favorites.take(maxFavorites),
            maxFavorites = maxFavorites,
            columns = columns,
            showClock = showClock,
            showIcons = showIcons,
        )
    }

    /** Schreibt die Datei in der Form, die [parse] wieder einliest. */
    fun serialize(config: Config): String = buildString {
        appendLine("# Navigate — Konfiguration")
        appendLine("#")
        appendLine("# Normalerweise wird diese Datei über „Navigate-Einstellungen“")
        appendLine("# in der unteren Zeile bearbeitet. Von Hand gilt: eine Anweisung")
        appendLine("# pro Zeile, '#' beginnt einen Kommentar, unbekannte Zeilen werden")
        appendLine("# ignoriert.")
        appendLine()
        appendLine("# Favoriten in der unteren Zeile, in dieser Reihenfolge (Paketnamen).")
        appendLine("# Mehr als $HARD_MAX_FAVORITES passen nicht in die Zeile.")
        appendLine("$KEY_FAVORITES=${FavoritesCodec.encode(config.favorites)}")
        appendLine()
        appendLine("# Wie viele Favoriten die Zeile aufnehmen darf (0–$HARD_MAX_FAVORITES).")
        appendLine("$KEY_MAX_FAVORITES=${config.maxFavorites}")
        appendLine()
        appendLine("# Spalten im App-Raster. 0 = automatisch (Bildschirmbreite / 150dp).")
        appendLine("$KEY_COLUMNS=${config.columns}")
        appendLine()
        appendLine("# Uhr oben rechts auf der Home-Seite anzeigen.")
        appendLine("$KEY_SHOW_CLOCK=${config.showClock}")
        appendLine()
        appendLine("# App-Symbole im Raster anzeigen. false = reine Textliste (sparsamer).")
        appendLine("$KEY_SHOW_ICONS=${config.showIcons}")
    }
}
