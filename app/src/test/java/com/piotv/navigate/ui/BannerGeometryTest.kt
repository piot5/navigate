package com.piotv.navigate.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The target size of a focus banner.
 *
 * The cap is the whole point: a banner arrives in the density of its target
 * APK, and single resources are screen-sized. The layer scales to fill — a
 * cropped bitmap looks the same as the uncropped one after scaling, but
 * costs a fraction of the memory.
 */
class BannerGeometryTest {

    /** The normal case: 320 × 180 dp at the device's density — stays as it is. */
    @Test
    fun `a banner of normal size stays as it is`() {
        assertEquals(BannerGeometry.Size(640, 360), BannerGeometry.size(640, 360, 1280))
    }

    /** A 4K resource would be 33 MB — capped it is 3.7 MB. */
    @Test
    fun `a too-wide banner is capped at the upper bound`() {
        assertEquals(BannerGeometry.Size(1280, 720), BannerGeometry.size(3840, 2160, 1280))
        // The aspect ratio stays: 2000 × 500 → 1280 × 320.
        assertEquals(BannerGeometry.Size(1280, 320), BannerGeometry.size(2000, 500, 1280))
    }

    @Test
    fun `unmeasurable sizes yield nothing`() {
        assertEquals(BannerGeometry.Size(0, 0), BannerGeometry.size(0, 100, 1280))
        assertEquals(BannerGeometry.Size(0, 0), BannerGeometry.size(100, -1, 1280))
        assertEquals(BannerGeometry.Size(0, 0), BannerGeometry.size(100, 100, 0))
    }
}
