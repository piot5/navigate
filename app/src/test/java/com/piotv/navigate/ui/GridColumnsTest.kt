package com.piotv.navigate.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The column count of the grid. It decides how many apps sit in the first
 * row (and thus how many are visible without a key press) and how many rows
 * appear when the grid unfolds.
 *
 * The widths are the real cases: 960 dp is the Chromecast (1920 px at
 * density 320), 540 dp a phone in landscape, 1920 dp a 4K TV at density 160.
 */
class GridColumnsTest {

    @Test
    fun `automatic on the Chromecast yields six columns`() {
        // 960 dp / 150 dp = 6.4 → 6 columns of 160 dp. Exactly the number
        // from README and docs/CONFIG.md; if the value drifts, one of the two
        // places is wrong.
        assertEquals(6, GridColumns.compute(960))
    }

    @Test
    fun `the automatic never drops below four columns`() {
        assertEquals(GridColumns.MIN_COLUMNS, GridColumns.compute(540))
        assertEquals(GridColumns.MIN_COLUMNS, GridColumns.compute(0))
    }

    @Test
    fun `the automatic never goes above eight columns`() {
        assertEquals(GridColumns.MAX_COLUMNS, GridColumns.compute(1920))
        assertEquals(GridColumns.MAX_COLUMNS, GridColumns.compute(99_999))
    }

    @Test
    fun `the automatic computes with 150 dp per cell`() {
        assertEquals(4, GridColumns.compute(600))
        assertEquals(5, GridColumns.compute(750))
        assertEquals(7, GridColumns.compute(1_100))
    }
}
