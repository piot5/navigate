package com.piotv.navigate.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Die Zielgröße eines Fokus-Banners.
 *
 * Die Obergrenze ist der ganze Sinn: ein Banner kommt in der Dichte seines
 * Ziel-APKs, und einzelne Ressourcen sind Bildschirm-größe. Die Ebene skaliert
 * formatfüllend — eine gekappte Bitmap sieht nach dem Skalieren genauso aus wie
 * die ungekappte, kostet aber einen Bruchteil an Speicher.
 */
class BannerGeometryTest {

    /** Der Normalfall: 320 × 180 dp in der Dichte des Geräts — bleibt, wie es ist. */
    @Test
    fun `ein Banner in normaler Groesse bleibt, wie es ist`() {
        assertEquals(BannerGeometry.Size(640, 360), BannerGeometry.size(640, 360, 1280))
    }

    /** Eine 4K-Ressource wäre 33 MB — gekappt sind es 3,7 MB. */
    @Test
    fun `ein zu breites Banner wird auf die Obergrenze gekappt`() {
        assertEquals(BannerGeometry.Size(1280, 720), BannerGeometry.size(3840, 2160, 1280))
        // Seitenverhältnis bleibt: 2000 × 500 → 1280 × 320.
        assertEquals(BannerGeometry.Size(1280, 320), BannerGeometry.size(2000, 500, 1280))
    }

    @Test
    fun `nicht messbare Angaben ergeben nichts`() {
        assertEquals(BannerGeometry.Size(0, 0), BannerGeometry.size(0, 100, 1280))
        assertEquals(BannerGeometry.Size(0, 0), BannerGeometry.size(100, -1, 1280))
        assertEquals(BannerGeometry.Size(0, 0), BannerGeometry.size(100, 100, 0))
    }
}
