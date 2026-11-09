package com.piotv.navigate.data

/**
 * The settings file as text: `key=value`, one line per entry.
 *
 * ```ini
 * # Navigate — settings
 * now_playing=off
 * show_clock=on
 * background=dark
 * background_file=
 * ```
 *
 * Pure Kotlin without Android types — like [OrderCodec], tested exactly where
 * a hand-written file silently breaks. Tolerance is intentional: `on`/`off`,
 * `true`/`false`, `1`/`0`, and the legacy German aliases `an`/`aus`,
 * `ja`/`nein` are recognized, as are upper and lower case, `_` and `-` in
 * keys, and surrounding whitespace. Lines without `=`, `#` comments, and
 * unknown keys are **ignored** rather than treated as errors (an unknown line
 * must never keep Navigate from starting). An unknown **value** leaves the
 * previous setting in place — no guessing.
 *
 * Since 0.14 there are four keys: two toggles, the background style, and the
 * path of the background file. `background_file=` **empty** means "the
 * default name in the external app folder" ([BackgroundPlan]) — this way a
 * path set once can be taken back again.
 *
 * Since 0.14.1 the background style knows only `dark` and `image`: `video`
 * from 0.14 has been removed and is now read like an unknown value — the
 * previous setting then stays in place, no guessing. Since 0.17 `focus` is
 * added: the TV banner of the app under the focus as background.
 */
object SettingsCodec {

    /** The key of the "Last seen" field — exactly as it appears in the file. */
    const val KEY_NOW_PLAYING = "now_playing"

    /** The clock in the top right corner. */
    const val KEY_SHOW_CLOCK = "show_clock"

    /** The background: `dark`, `image`, or `focus`. */
    const val KEY_BACKGROUND = "background"

    /** The path of the media file for `image`; empty = default name. */
    const val KEY_BACKGROUND_FILE = "background_file"

    // Legacy German aliases ("an", "ja", "aus", "nein", "dunkel", "kein",
    // "bild", "foto", "fokus") are kept for backward compatibility with
    // hand-written or older config files.
    private val TRUE_VALUES = setOf("on", "true", "1", "an", "ja", "yes")
    private val FALSE_VALUES = setOf("off", "false", "0", "aus", "nein", "no")

    private val DARK_VALUES = setOf("dark", "dunkel", "off", "aus", "kein", "none")
    private val IMAGE_VALUES = setOf("image", "bild", "foto", "picture")
    private val FOCUS_VALUES = setOf("focus", "fokus")

    fun parse(text: String?): NavigateSettings {
        if (text == null) return NavigateSettings()
        var settings = NavigateSettings()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val separator = line.indexOf('=')
            // Without "=value", the line says nothing about a setting.
            if (separator <= 0) continue
            val value = line.substring(separator + 1).trim()
            when (normalize(line.substring(0, separator))) {
                KEY_NOW_PLAYING ->
                    boolean(value)?.let { settings = settings.copy(nowPlaying = it) }
                KEY_SHOW_CLOCK ->
                    boolean(value)?.let { settings = settings.copy(showClock = it) }
                KEY_BACKGROUND ->
                    style(value)?.let { settings = settings.copy(background = it) }
                // Empty explicitly means "no custom path" — the default name
                // applies again.
                KEY_BACKGROUND_FILE ->
                    settings = settings.copy(backgroundFile = value.ifEmpty { null })
                else -> Unit // unknown key: the line says nothing
            }
        }
        return settings
    }

    fun serialize(settings: NavigateSettings): String = buildString {
        appendLine("# Navigate — settings")
        appendLine("#")
        appendLine("# One entry per line: key=value.")
        appendLine("# Lines starting with '#' are comments; unknown lines are ignored.")
        appendLine("# This file is written by the Navigate settings screen; it is also")
        appendLine("# reachable via 'adb push' (see docs/CONFIG.md).")
        appendLine()
        appendLine("$KEY_NOW_PLAYING=${if (settings.nowPlaying) "on" else "off"}")
        appendLine("$KEY_SHOW_CLOCK=${if (settings.showClock) "on" else "off"}")
        appendLine("$KEY_BACKGROUND=${name(settings.background)}")
        appendLine("$KEY_BACKGROUND_FILE=${settings.backgroundFile.orEmpty()}")
    }

    /** `Now_Playing`, `now-playing`, and `NOW PLAYING` are the same key. */
    private fun normalize(key: String): String =
        key.trim().lowercase().replace('-', '_').replace(' ', '_')

    /** `true`, `false`, or `null` — `null` means "no known value". */
    private fun boolean(value: String): Boolean? = when (value.lowercase()) {
        in TRUE_VALUES -> true
        in FALSE_VALUES -> false
        else -> null
    }

    /** The background style — `null` means "no known value". */
    private fun style(value: String): BackgroundStyle? = when (value.lowercase()) {
        in DARK_VALUES -> BackgroundStyle.DARK
        in IMAGE_VALUES -> BackgroundStyle.IMAGE
        in FOCUS_VALUES -> BackgroundStyle.FOCUS
        else -> null
    }

    /** The English form is written — it is the value the docs are keyed on. */
    private fun name(style: BackgroundStyle): String = when (style) {
        BackgroundStyle.DARK -> "dark"
        BackgroundStyle.IMAGE -> "image"
        BackgroundStyle.FOCUS -> "focus"
    }
}
