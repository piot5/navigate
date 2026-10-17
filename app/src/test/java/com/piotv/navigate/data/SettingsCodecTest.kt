package com.piotv.navigate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Einstellungsdatei als Text.
 *
 * Ein Parser ist die Stelle, an der eine handgeschriebene Datei still kaputtgeht
 * — dieselbe Begründung wie bei OrderCodecTest. Geprüft wird deshalb nicht nur
 * der Normalfall, sondern die Toleranz: Kommentare, Groß- und Kleinschreibung,
 * Leerzeichen, unbekannte Schlüssel, ein unbekannter Wert. Die Zusage dahinter:
 * eine unbekannte Zeile darf Navigate nie am Starten hindern, und geraten wird
 * nicht.
 */
class SettingsCodecTest {

    @Test
    fun `ohne Inhalt gilt der Standard`() {
        assertEquals(NavigateSettings(), SettingsCodec.parse(null))
        assertEquals(NavigateSettings(nowPlaying = false), SettingsCodec.parse(""))
    }

    @Test
    fun `now_playing gleich on schaltet das Feld ein`() {
        assertTrue(SettingsCodec.parse("now_playing=on").nowPlaying)
    }

    @Test
    fun `now_playing gleich off schaltet das Feld aus`() {
        assertFalse(SettingsCodec.parse("now_playing=off").nowPlaying)
    }

    @Test
    fun `die Werte werden so tolerant gelesen wie der Rest der Datei`() {
        for (value in listOf("on", "ON", "true", "1", "an", "ja", "yes")) {
            assertTrue(
                "Wert '$value' gilt als ein",
                SettingsCodec.parse("now_playing=$value").nowPlaying,
            )
        }
        for (value in listOf("off", "OFF", "false", "0", "aus", "nein", "no")) {
            assertFalse(
                "Wert '$value' gilt als aus",
                SettingsCodec.parse("now_playing=$value").nowPlaying,
            )
        }
    }

    @Test
    fun `Schluessel, Leerzeichen und Kommentare stoeren nicht`() {
        val text = """
            # Navigate — Einstellungen
            # now_playing=off   (auskommentiert, zählt nicht)

              NOW-PLAYING = ON
        """.trimIndent()
        assertTrue(SettingsCodec.parse(text).nowPlaying)
    }

    @Test
    fun `unbekannte Schluessel und Zeilen ohne Wert werden ignoriert`() {
        val text = """
            irgendwas=an
            now_playing
            =on
            jetzt_playing=on
        """.trimIndent()
        assertFalse(SettingsCodec.parse(text).nowPlaying)
    }

    @Test
    fun `ein unbekannter Wert laesst den bisherigen Stand stehen`() {
        // „vielleicht“ ist kein Ja: geraten wird nicht, die vorige Zeile gilt.
        assertTrue(SettingsCodec.parse("now_playing=on\nnow_playing=vielleicht").nowPlaying)
        assertFalse(SettingsCodec.parse("now_playing=off\nnow_playing=vielleicht").nowPlaying)
    }

    @Test
    fun `die letzte gueltige Zeile gewinnt`() {
        assertTrue(SettingsCodec.parse("now_playing=off\nnow_playing=on").nowPlaying)
    }

    @Test
    fun `schreiben und lesen ergeben denselben Stand`() {
        for (settings in listOf(NavigateSettings(), NavigateSettings(nowPlaying = true))) {
            assertEquals(settings, SettingsCodec.parse(SettingsCodec.serialize(settings)))
        }
    }

    @Test
    fun `die geschriebene Datei nennt den Schluessel ausdruecklich`() {
        // Auch „aus“ steht in der Datei: wer sie liest, soll den Schlüssel sehen
        // und nicht rätseln müssen, ob er fehlt oder nur vergessen wurde.
        val off = SettingsCodec.serialize(NavigateSettings(nowPlaying = false))
        val on = SettingsCodec.serialize(NavigateSettings(nowPlaying = true))
        assertTrue(off.contains("now_playing=off"))
        assertTrue(on.contains("now_playing=on"))
    }
}
