package com.piotv.navigate.ui

/**
 * Number of grid columns — pure computation without Android types, so it can
 * be tested without a device (GridColumnsTest).
 *
 * The number comes from the screen width: `width / [CELL_MIN_WIDTH_DP]`,
 * bounded by [MIN_COLUMNS] to [MAX_COLUMNS]. On the Chromecast (960dp) that
 * yields 6 columns — the same number the README and docs/CONFIG.md name.
 *
 * There is **no** setting for it: the only thing configurable in this UI is
 * the order of the apps. Whoever wants a different column count changes the
 * cell width here — one number, in one place, with a test.
 */
object GridColumns {

    /** Width a grid cell should get at minimum. */
    const val CELL_MIN_WIDTH_DP = 150

    /** Lower bound — below this the grid becomes unusable on TV. */
    const val MIN_COLUMNS = 4

    /** Upper bound — above this the cells become too narrow for text. */
    const val MAX_COLUMNS = 8

    fun compute(widthDp: Int): Int = (widthDp / CELL_MIN_WIDTH_DP).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
}

