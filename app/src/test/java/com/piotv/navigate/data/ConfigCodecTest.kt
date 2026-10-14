package com.piotv.navigate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Parser der Konfigurationsdatei. Eine handgeschriebene Datei darf Navigate
 * niemals aus dem Tritt bringen — hier steht, was passiert, wenn sie krumm ist.
 */
class ConfigCodecTest {

    @Test
    fun `fehlende Datei ergibt die Standardwerte`() {
        assertEquals(ConfigCodec.default(), ConfigCodec.parse(null))
        assertEquals(ConfigCodec.default(), ConfigCodec.parse(""))
    }

    @Test
    fun `vollstaendige Datei wird gelesen`() {
        val config = ConfigCodec.parse(
            """
            favorites=com.netflix.ninja,com.termux
            max_favorites=4
            columns=5
            show_clock=false
            """.trimIndent()
        )
        assertEquals(listOf("com.netflix.ninja", "com.termux"), config.favorites)
        assertEquals(4, config.maxFavorites)
        assertEquals(5, config.columns)
        assertFalse(config.showClock)
    }

    @Test
    fun `Kommentare Leerzeilen und unbekannte Schluessel stoeren nicht`() {
        val config = ConfigCodec.parse(
            """
            # Kommentar

            favorites=com.termux
            zukunft=irgendwas
            kaputte zeile ohne gleichheitszeichen
            =auch kaputt
            show_clock=true
            """.trimIndent()
        )
        assertEquals(listOf("com.termux"), config.favorites)
        assertTrue(config.showClock)
    }

    @Test
    fun `Grossschreibung und Leerzeichen sind erlaubt`() {
        val config = ConfigCodec.parse("  FAVORITES = com.a , com.b  ")
        assertEquals(listOf("com.a", "com.b"), config.favorites)
    }

    @Test
    fun `Zahlen ausserhalb des Bereichs werden begrenzt`() {
        // Mehr als 6 Favoriten passen nicht in die Zeile.
        val tooMany = (1..12).map { "com.app$it" }
        val config = ConfigCodec.parse(
            "favorites=${FavoritesCodec.encode(tooMany)}\nmax_favorites=99\ncolumns=999"
        )
        assertEquals(ConfigCodec.HARD_MAX_FAVORITES, config.favorites.size)
        assertEquals(ConfigCodec.HARD_MAX_FAVORITES, config.maxFavorites)
        assertEquals(12, config.columns)
    }

    @Test
    fun `Favoriten werden auf max_favorites gekuerzt`() {
        val config = ConfigCodec.parse("favorites=com.a,com.b,com.c\nmax_favorites=2")
        assertEquals(listOf("com.a", "com.b"), config.favorites)
    }

    @Test
    fun `unsinnige Wahrheitswerte aendern nichts`() {
        val config = ConfigCodec.parse("show_clock=ja")
        assertTrue(config.showClock)
    }

    @Test
    fun `show_icons laesst sich abschalten`() {
        val config = ConfigCodec.parse("show_icons=false")
        assertFalse(config.showIcons)
        // Standard bleibt an — ein fehlender Schlüssel darf den Sparmodus
        // nicht versehentlich einschalten.
        assertTrue(ConfigCodec.default().showIcons)
        assertTrue(ConfigCodec.parse("show_icons=irgendwas").showIcons)
    }

    @Test
    fun `der Textmodus ueberlebt Schreiben und Lesen`() {
        val config = ConfigCodec.Config(showIcons = false, showClock = false)
        val wieder = ConfigCodec.parse(ConfigCodec.serialize(config))
        assertFalse(wieder.showIcons)
        assertFalse(wieder.showClock)
    }

    @Test
    fun `die geschriebene Datei nennt den Symbolschalter`() {
        val text = ConfigCodec.serialize(ConfigCodec.default())
        assertTrue(text.contains("show_icons="))
    }

    @Test
    fun `Schreiben und Lesen sind verlustfrei`() {
        // Die Datei, die die Einstellungen schreiben, muss der Parser exakt
        // wieder einlesen — sonst verliert ein Öffnen-und-Schließen Favoriten.
        val config = ConfigCodec.Config(
            favorites = listOf("com.netflix.ninja", "de.swr.avp.ard.tv"),
            maxFavorites = 4,
            columns = 5,
            showClock = false,
        )
        assertEquals(config, ConfigCodec.parse(ConfigCodec.serialize(config)))
    }

    @Test
    fun `die geschriebene Datei erklaert sich selbst`() {
        val text = ConfigCodec.serialize(ConfigCodec.default())
        assertTrue(text.contains("favorites="))
        assertTrue(text.contains("max_favorites="))
        assertTrue(text.contains("columns="))
        assertTrue(text.contains("show_clock="))
        // Kommentarzeilen sind der Grund, warum die Datei von Hand editierbar ist.
        assertTrue(text.lines().any { it.startsWith("#") })
    }
}
