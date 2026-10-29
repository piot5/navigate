package com.piotv.navigate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Wo die Hintergrund-Datei liegt.
 *
 * Reine Rechnung ohne Android-Typen — deshalb ohne Gerät prüfbar, wie bei
 * [OrderCodec] und [SettingsCodec]. Geprüft werden die drei Fälle, die in der
 * Praxis vorkommen: kein Pfad (Standardname), ein absoluter Pfad (`/sdcard/…`)
 * und ein blosser Name. Dazu der Randfall „Gerät ohne externen Ordner“ — dann
 * bleibt nur ein absoluter Pfad, und ohne ihn gibt es keinen Hintergrund.
 */
class BackgroundPlanTest {

    private val external = "/storage/emulated/0/Android/data/com.piotv.navigate/files"

    @Test
    fun `ohne Bild-Hintergrund wird keine Datei gebraucht`() {
        assertNull(BackgroundPlan.file(BackgroundStyle.DARK, null, external))
        assertNull(BackgroundPlan.file(BackgroundStyle.DARK, "/sdcard/x.jpg", external))
    }

    @Test
    fun `ohne Pfad gilt der Standardname im externen Ordner`() {
        assertEquals(
            "$external/${BackgroundPlan.DEFAULT_IMAGE_NAME}",
            BackgroundPlan.file(BackgroundStyle.IMAGE, null, external)?.absolutePath,
        )
        // Leerzeichen sind kein Pfad.
        assertEquals(
            "$external/${BackgroundPlan.DEFAULT_IMAGE_NAME}",
            BackgroundPlan.file(BackgroundStyle.IMAGE, "   ", external)?.absolutePath,
        )
    }

    @Test
    fun `ein absoluter Pfad gilt unveraendert`() {
        assertEquals(
            "/sdcard/Pictures/wand.jpg",
            BackgroundPlan.file(
                BackgroundStyle.IMAGE,
                "/sdcard/Pictures/wand.jpg",
                external,
            )?.absolutePath,
        )
    }

    @Test
    fun `ein blosser Name landet im externen Ordner`() {
        assertEquals(
            "$external/urlaub.jpg",
            BackgroundPlan.file(BackgroundStyle.IMAGE, "urlaub.jpg", external)?.absolutePath,
        )
    }

    @Test
    fun `ohne externen Ordner bleibt nur der absolute Pfad`() {
        assertNull(BackgroundPlan.file(BackgroundStyle.IMAGE, null, null))
        assertNull(BackgroundPlan.file(BackgroundStyle.IMAGE, "urlaub.jpg", null))
        assertEquals(
            "/sdcard/wand.jpg",
            BackgroundPlan.file(BackgroundStyle.IMAGE, "/sdcard/wand.jpg", null)?.absolutePath,
        )
    }
}
