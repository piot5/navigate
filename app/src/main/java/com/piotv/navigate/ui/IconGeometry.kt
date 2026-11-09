package com.piotv.navigate.ui

import kotlin.math.min

/**
 * Fitting an icon into a square area — pure computation without Android types,
 * so it can be tested without a device (IconGeometryTest).
 *
 * Before, this lived in [IconLoader] between Canvas calls and was untested.
 * The rules are small but visible: fit while preserving the aspect ratio,
 * center it, at least 1 px per edge.
 */
object IconGeometry {

    /**
     * Corner radius of the icons: 22% of the edge length — the same value
     * Android uses for adaptive icons. One value for old and new icons alike,
     * so the grid looks uniform.
     */
    const val CORNER_RADIUS_RATIO = 0.22f

    /** Target rectangle in pixels, relative to the top-left corner of the area. */
    data class Fit(val left: Int, val top: Int, val width: Int, val height: Int)

    /**
     * Fits a source of size [intrinsicWidth]×[intrinsicHeight] into a
     * [sizePx]×[sizePx] area.
     *
     * A non-positive edge length means "no information" — adaptive and vector
     * icons report that; those are meant to fill the area.
     */
    fun fit(intrinsicWidth: Int, intrinsicHeight: Int, sizePx: Int): Fit {
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
            return Fit(left = 0, top = 0, width = sizePx, height = sizePx)
        }
        val scale = min(
            sizePx.toFloat() / intrinsicWidth,
            sizePx.toFloat() / intrinsicHeight,
        )
        // coerceAtLeast(1): an extremely wide icon (1000×1) must not collapse
        // to height 0, otherwise draw() paints nothing.
        val width = (intrinsicWidth * scale).toInt().coerceAtLeast(1)
        val height = (intrinsicHeight * scale).toInt().coerceAtLeast(1)
        return Fit(left = (sizePx - width) / 2, top = (sizePx - height) / 2, width = width, height = height)
    }

    /** Radius of the rounded corners for an area with edge length [sizePx]. */
    fun cornerRadius(sizePx: Int): Float = sizePx * CORNER_RADIUS_RATIO
}
