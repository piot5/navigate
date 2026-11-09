package com.piotv.navigate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings file as text.
 *
 * A parser is the place where a hand-written file silently breaks — the same
 * reasoning as for OrderCodecTest. So not only the normal case is checked but
 * the tolerance: comments, casing, whitespace, unknown keys, an unknown value.
 * The promise behind it: an unknown line must never keep Navigate from
 * starting, and nothing is guessed.
 */
class SettingsCodecTest {

    @Test
    fun `without content the default applies`() {
        assertEquals(NavigateSettings(), SettingsCodec.parse(null))
        assertEquals(NavigateSettings(nowPlaying = false), SettingsCodec.parse(""))
    }

    @Test
    fun `now_playing on turns the field on`() {
        assertTrue(SettingsCodec.parse("now_playing=on").nowPlaying)
    }

    @Test
    fun `now_playing off turns the field off`() {
        assertFalse(SettingsCodec.parse("now_playing=off").nowPlaying)
    }

    @Test
    fun `values are read as tolerantly as the rest of the file`() {
        for (value in listOf("on", "ON", "true", "1", "an", "ja", "yes")) {
            assertTrue(
                "Value '$value' counts as on",
                SettingsCodec.parse("now_playing=$value").nowPlaying,
            )
        }
        for (value in listOf("off", "OFF", "false", "0", "aus", "nein", "no")) {
            assertFalse(
                "Value '$value' counts as off",
                SettingsCodec.parse("now_playing=$value").nowPlaying,
            )
        }
    }

    @Test
    fun `keys whitespace and comments do not interfere`() {
        val text = """
            # Navigate — settings
            # now_playing=off   (commented out, does not count)

              NOW-PLAYING = ON
        """.trimIndent()
        assertTrue(SettingsCodec.parse(text).nowPlaying)
    }

    @Test
    fun `unknown keys and lines without a value are ignored`() {
        val text = """
            whatever=an
            now_playing
            =on
            jetzt_playing=on
        """.trimIndent()
        assertFalse(SettingsCodec.parse(text).nowPlaying)
    }

    @Test
    fun `an unknown value leaves the previous state standing`() {
        // "maybe" is not a yes: nothing is guessed, the previous line holds.
        assertTrue(SettingsCodec.parse("now_playing=on\nnow_playing=maybe").nowPlaying)
        assertFalse(SettingsCodec.parse("now_playing=off\nnow_playing=maybe").nowPlaying)
    }

    @Test
    fun `the last valid line wins`() {
        assertTrue(SettingsCodec.parse("now_playing=off\nnow_playing=on").nowPlaying)
    }

    @Test
    fun `writing and reading give the same state`() {
        for (settings in listOf(
            NavigateSettings(),
            NavigateSettings(nowPlaying = true),
            NavigateSettings(background = BackgroundStyle.FOCUS),
        )) {
            assertEquals(settings, SettingsCodec.parse(SettingsCodec.serialize(settings)))
        }
    }

    @Test
    fun `the written file names the key explicitly`() {
        // "off" is written too: whoever reads the file should see the key and
        // not have to guess whether it is missing or was merely forgotten.
        val off = SettingsCodec.serialize(NavigateSettings(nowPlaying = false))
        val on = SettingsCodec.serialize(NavigateSettings(nowPlaying = true))
        assertTrue(off.contains("now_playing=off"))
        assertTrue(on.contains("now_playing=on"))
    }

    @Test
    fun `the clock is on by default and can be turned off`() {
        assertTrue(SettingsCodec.parse(null).showClock)
        assertFalse(SettingsCodec.parse("show_clock=off").showClock)
        assertTrue(SettingsCodec.parse("show_clock=an").showClock)
        // An unknown value leaves the default — nothing is guessed.
        assertTrue(SettingsCodec.parse("show_clock=maybe").showClock)
    }

    @Test
    fun `the background has three values and a default`() {
        assertEquals(BackgroundStyle.DARK, SettingsCodec.parse(null).background)
        assertEquals(BackgroundStyle.DARK, SettingsCodec.parse("background=dark").background)
        assertEquals(BackgroundStyle.DARK, SettingsCodec.parse("background=aus").background)
        assertEquals(BackgroundStyle.IMAGE, SettingsCodec.parse("background=image").background)
        assertEquals(BackgroundStyle.IMAGE, SettingsCodec.parse("background=Bild").background)
        assertEquals(BackgroundStyle.FOCUS, SettingsCodec.parse("background=focus").background)
        assertEquals(BackgroundStyle.FOCUS, SettingsCodec.parse("background=Fokus").background)
        // video from 0.14 is gone: an unknown value leaves the default standing.
        assertEquals(BackgroundStyle.DARK, SettingsCodec.parse("background=video").background)
        // An unknown value is not a fourth background.
        assertEquals(BackgroundStyle.DARK, SettingsCodec.parse("background=rainbow").background)
    }

    @Test
    fun `the background file path is read and can be cleared`() {
        assertEquals(
            "/sdcard/Pictures/wall.jpg",
            SettingsCodec.parse("background_file=/sdcard/Pictures/wall.jpg").backgroundFile,
        )
        // Empty (or without "=") explicitly means "no own path" — the default
        // name in the external app folder applies again.
        assertNull(SettingsCodec.parse("background_file=").backgroundFile)
        assertNull(SettingsCodec.parse("background_file").backgroundFile)
    }

    @Test
    fun `all four keys appear in the written file`() {
        val text = SettingsCodec.serialize(
            NavigateSettings(
                nowPlaying = true,
                showClock = false,
                background = BackgroundStyle.IMAGE,
                backgroundFile = "wall.jpg",
            )
        )
        assertTrue(text.contains("now_playing=on"))
        assertTrue(text.contains("show_clock=off"))
        assertTrue(text.contains("background=image"))
        assertTrue(text.contains("background_file=wall.jpg"))
    }
}
