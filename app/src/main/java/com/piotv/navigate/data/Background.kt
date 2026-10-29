package com.piotv.navigate.data

import java.io.File

/**
 * Was hinter dem Raster liegt — **zwei** Möglichkeiten, mehr braucht ein
 * Fernseher nicht.
 *
 * | Wert | Was zu sehen ist | Woher das Bild kommt |
 * |---|---|---|
 * | [DARK] | der einfarbige, sehr dunkle Hintergrund (Standard) | `@color/bg_home` |
 * | [IMAGE] | ein Bild, formatfüllend | eine **Mediendatei** (`background_file=`) |
 *
 * Ein **Video**-Hintergrund gab es in 0.14 als dritte Möglichkeit; er ist
 * wieder entfernt. Ein Video belegt auf dem Chromecast dauerhaft einen
 * Hardware-Decoder — für eine Fläche, die hinter dem Raster liegt, ein Preis
 * ohne Nutzen. Zwei Möglichkeiten reichen, und [IMAGE] ist die einzige, die
 * eine Mediendatei braucht.
 *
 * Warum es **nicht** die Hintergründe sind, die Android TV „eingebaut“ hat: die
 * Bilder des Google-TV-Ambient-Modus („Backdrop“) liegen im privaten Speicher des
 * Google-TV-Launchers, die Empfehlungs-/Kanalbilder aus `TvContract` verlangen
 * eine TV-Input-Berechtigung, und `WallpaperManager` liefert auf dem Chromecast HD
 * kein Bild (kein Wallpaper-Dienst). Ein normaler Launcher kommt an keines davon
 * heran — siehe docs/LAST_SEEN_TITEL.md, Abschnitt „Hintergrund“.
 */
enum class BackgroundStyle {
    DARK,
    IMAGE,
}

/**
 * Wo die Hintergrund-Datei liegt — reine Rechnung, ohne Android-Typen, damit sie
 * ohne Gerät prüfbar ist ([BackgroundPlanTest]).
 *
 * Zwei Orte, wie bei den Konfigurationsdateien: Ein **absoluter** Pfad
 * (`background_file=/sdcard/…`) gilt, wie er dasteht. Steht dort nichts, gilt der
 * Standardname im **externen App-Ordner** — derselbe Ordner, in den `adb push`
 * auch die Konfigurationsdateien legt (docs/CONFIG.md), also der einzige Ort, den
 * man ohne Root beschreiben kann.
 */
object BackgroundPlan {

    /** Der Standardname für [BackgroundStyle.IMAGE] im externen App-Ordner. */
    const val DEFAULT_IMAGE_NAME = "background.jpg"

    /** Der Standardname einer Bildart — `null` für [BackgroundStyle.DARK]. */
    fun defaultName(style: BackgroundStyle): String? = when (style) {
        BackgroundStyle.DARK -> null
        BackgroundStyle.IMAGE -> DEFAULT_IMAGE_NAME
    }

    /**
     * Die Datei, die Navigate lesen soll — oder `null`, wenn keine gebraucht wird.
     *
     * @param configured Wert von `background_file` aus `settings.conf`.
     * @param externalDir Absoluter Pfad des externen App-Ordners; `null`, wenn das
     *   Gerät keinen hat (dann bleibt nur ein absoluter Pfad in [configured]).
     */
    fun file(style: BackgroundStyle, configured: String?, externalDir: String?): File? {
        val name = defaultName(style) ?: return null
        val path = configured?.trim().orEmpty()
        if (path.isEmpty()) {
            return externalDir?.let { File(it, name) }
        }
        // Ein absoluter Pfad gilt unverändert; alles andere wird als Name im
        // externen App-Ordner verstanden (`background_file=urlaub.jpg`).
        val direct = File(path)
        if (direct.isAbsolute) return direct
        return externalDir?.let { File(it, path) }
    }
}
