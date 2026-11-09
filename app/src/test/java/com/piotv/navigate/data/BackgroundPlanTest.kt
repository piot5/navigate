package com.piotv.navigate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where the background file lives.
 *
 * Pure computation without Android types — therefore checkable without a
 * device, like [OrderCodec] and [SettingsCodec]. The three cases that occur in
 * practice are checked: no path (default name), an absolute path (`/sdcard/…`)
 * and a bare name. Plus the edge case "device without an external folder" —
 * then only an absolute path remains, and without one there is no background.
 */
class BackgroundPlanTest {

    private val external = "/storage/emulated/0/Android/data/com.piotv.navigate/files"

    @Test
    fun `without an image background no file is needed`() {
        assertNull(BackgroundPlan.file(BackgroundStyle.DARK, null, external))
        assertNull(BackgroundPlan.file(BackgroundStyle.DARK, "/sdcard/x.jpg", external))
        // The focus banner comes from the app manifest, not from a file.
        assertNull(BackgroundPlan.file(BackgroundStyle.FOCUS, null, external))
    }

    @Test
    fun `without a path the default name in the external folder applies`() {
        assertEquals(
            "$external/${BackgroundPlan.DEFAULT_IMAGE_NAME}",
            BackgroundPlan.file(BackgroundStyle.IMAGE, null, external)?.absolutePath,
        )
        // Whitespace is not a path.
        assertEquals(
            "$external/${BackgroundPlan.DEFAULT_IMAGE_NAME}",
            BackgroundPlan.file(BackgroundStyle.IMAGE, "   ", external)?.absolutePath,
        )
    }

    @Test
    fun `an absolute path is used as is`() {
        assertEquals(
            "/sdcard/Pictures/wall.jpg",
            BackgroundPlan.file(
                BackgroundStyle.IMAGE,
                "/sdcard/Pictures/wall.jpg",
                external,
            )?.absolutePath,
        )
    }

    @Test
    fun `a bare name lands in the external folder`() {
        assertEquals(
            "$external/vacation.jpg",
            BackgroundPlan.file(BackgroundStyle.IMAGE, "vacation.jpg", external)?.absolutePath,
        )
    }

    @Test
    fun `without an external folder only the absolute path remains`() {
        assertNull(BackgroundPlan.file(BackgroundStyle.IMAGE, null, null))
        assertNull(BackgroundPlan.file(BackgroundStyle.IMAGE, "vacation.jpg", null))
        assertEquals(
            "/sdcard/wall.jpg",
            BackgroundPlan.file(BackgroundStyle.IMAGE, "/sdcard/wall.jpg", null)?.absolutePath,
        )
    }
}
