package com.piotv.navigate.ui

/**
 * The target size of a focus banner — pure calculation without Android types,
 * therefore testable without a device (BannerGeometryTest).
 *
 * A banner arrives in the density of its source APK; most are 320 × 180 dp,
 * individual resources are considerably larger. Drawing happens at the
 * **native** size — the layer scales to fill (`centerCrop`), and an upper
 * limit keeps memory measurable: otherwise a 4K banner resource would mean
 * 33 MB for a surface behind the grid (the same calculation as for the
 * background file in [com.piotv.navigate.LauncherActivity]).
 */
object BannerGeometry {

    /** The target size in pixels. */
    data class Size(val width: Int, val height: Int)

    /**
     * Caps aspect-ratio fidelity at a width of at most [maxWidth]; anything
     * smaller stays as it is.
     *
     * Unmeasurable values (0 or negative — they exist, even in third-party
     * APKs) yield 0 × 0: the caller then leaves the layer out instead of
     * guessing.
     */
    fun size(width: Int, height: Int, maxWidth: Int): Size {
        if (width <= 0 || height <= 0 || maxWidth <= 0) return Size(0, 0)
        if (width <= maxWidth) return Size(width, height)
        val scaledHeight = (height.toLong() * maxWidth / width).toInt()
        return Size(maxWidth, scaledHeight.coerceAtLeast(1))
    }
}
