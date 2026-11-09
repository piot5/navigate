package com.piotv.navigate.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fitting of an icon into a square area.
 *
 * Visible, but untested until 0.4: a 16:9 TV banner (leanback-only apps
 * often have no square icon) must not be distorted, and an icon without a
 * size must fill the area.
 */
class IconGeometryTest {

    @Test
    fun `a square source fills the area exactly`() {
        assertEquals(IconGeometry.Fit(0, 0, 128, 128), IconGeometry.fit(128, 128, 128))
    }

    @Test
    fun `a smaller source is scaled up`() {
        // A 48 px icon is scaled to the target size, not left small in the
        // middle.
        assertEquals(IconGeometry.Fit(0, 0, 128, 128), IconGeometry.fit(48, 48, 128))
    }

    @Test
    fun `a 16 to 9 banner is fitted and centered`() {
        // 160×90 into 128: scale = 0.8 → 128×72, 28 px of air at the top and
        // bottom each.
        assertEquals(IconGeometry.Fit(0, 28, 128, 72), IconGeometry.fit(160, 90, 128))
    }

    @Test
    fun `a portrait icon is centered left and right`() {
        assertEquals(IconGeometry.Fit(28, 0, 72, 128), IconGeometry.fit(90, 160, 128))
    }

    @Test
    fun `without a size the source fills the area`() {
        // Adaptive and vector icons report no size.
        assertEquals(IconGeometry.Fit(0, 0, 64, 64), IconGeometry.fit(0, 0, 64))
        assertEquals(IconGeometry.Fit(0, 0, 64, 64), IconGeometry.fit(-1, 10, 64))
    }

    @Test
    fun `an extremely flat source keeps at least one pixel of height`() {
        // 1000×1 at 8 px: without the lower bound the height would be 0 and
        // draw() would paint nothing.
        val fit = IconGeometry.fit(1000, 1, 8)
        assertEquals(8, fit.width)
        assertEquals(1, fit.height)
        assertEquals(0, fit.left)
        assertEquals(3, fit.top)
    }

    @Test
    fun `the target always stays within the area`() {
        for (width in listOf(1, 3, 7, 64, 101, 1000)) {
            for (size in listOf(1, 8, 52, 64, 128)) {
                val fit = IconGeometry.fit(width, width * 3, size)
                assertTrue("left $fit", fit.left >= 0)
                assertTrue("top $fit", fit.top >= 0)
                assertTrue("width $fit", fit.left + fit.width <= size)
                assertTrue("height $fit", fit.top + fit.height <= size)
            }
        }
    }

    @Test
    fun `the corners are 22 percent of the edge length`() {
        assertEquals(28.16f, IconGeometry.cornerRadius(128), 0.01f)
        assertEquals(11.44f, IconGeometry.cornerRadius(52), 0.01f)
        assertEquals(IconGeometry.CORNER_RADIUS_RATIO, 0.22f, 0.0001f)
    }
}
