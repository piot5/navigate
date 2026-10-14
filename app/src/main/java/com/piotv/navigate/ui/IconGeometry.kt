package com.piotv.navigate.ui

import kotlin.math.min

/**
 * Einpassung eines Symbols in eine quadratische Fläche — reine Rechnung ohne
 * Android-Typen, deshalb ohne Gerät prüfbar (IconGeometryTest).
 *
 * Vorher stand das in [IconLoader] zwischen Canvas-Aufrufen und war damit
 * ungetestet. Die Regeln sind klein, aber sichtbar: seitenverhältnistreu
 * einpassen, zentrieren, mindestens 1 px je Kante.
 */
object IconGeometry {

    /**
     * Eckenradius der Symbole: 22 % der Kantenlänge — derselbe Wert, den
     * Android für adaptive Symbole verwendet. Ein Wert für alte wie neue
     * Symbole, damit das Raster einheitlich aussieht.
     */
    const val CORNER_RADIUS_RATIO = 0.22f

    /** Zielrechteck in Pixeln, relativ zur linken oberen Ecke der Fläche. */
    data class Fit(val left: Int, val top: Int, val width: Int, val height: Int)

    /**
     * Passt eine Quelle der Größe [intrinsicWidth]×[intrinsicHeight] in eine
     * [sizePx]×[sizePx]-Fläche ein.
     *
     * Eine nicht positive Kantenlänge heißt „keine Angabe" — adaptive und
     * vektorielle Symbole melden so etwas; die sollen die Fläche füllen.
     */
    fun fit(intrinsicWidth: Int, intrinsicHeight: Int, sizePx: Int): Fit {
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
            return Fit(left = 0, top = 0, width = sizePx, height = sizePx)
        }
        val scale = min(
            sizePx.toFloat() / intrinsicWidth,
            sizePx.toFloat() / intrinsicHeight,
        )
        // coerceAtLeast(1): ein extrem breites Symbol (1000×1) darf nicht auf
        // Höhe 0 zusammenfallen, sonst zeichnet draw() nichts.
        val width = (intrinsicWidth * scale).toInt().coerceAtLeast(1)
        val height = (intrinsicHeight * scale).toInt().coerceAtLeast(1)
        return Fit(left = (sizePx - width) / 2, top = (sizePx - height) / 2, width = width, height = height)
    }

    /** Radius der abgerundeten Ecken für eine Fläche der Kantenlänge [sizePx]. */
    fun cornerRadius(sizePx: Int): Float = sizePx * CORNER_RADIUS_RATIO
}
