package com.piotv.navigate.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Umwandlung Paketname → Dateiname. Klein, aber die einzige Stelle, an der
 * aus fremden Daten ein Dateiname wird — und damit die einzige, an der ein
 * Pfadanteil hereinrutschen könnte.
 */
class IconCacheKeysTest {

    @Test
    fun `Dateiname enthaelt Version Grosse und Paketnamen`() {
        assertEquals(
            "v1-128px-com.netflix.ninja.webp",
            IconCacheKeys.fileName("com.netflix.ninja", 128),
        )
    }

    @Test
    fun `verschiedene Groessen ergeben verschiedene Dateien`() {
        // Dock (104 px) und Raster (128 px) zeigen dasselbe Symbol in zwei
        // Größen — zwei Dateien, sonst würde eine davon hochskaliert.
        assertNotEquals(
            IconCacheKeys.fileName("com.netflix.ninja", 104),
            IconCacheKeys.fileName("com.netflix.ninja", 128),
        )
    }

    @Test
    fun `Pfadanteile werden ersetzt`() {
        // Aus "../../etc/passwd" darf kein Pfad werden: der Trenner fällt weg.
        // Ein einzelnes ".." ist danach harmlos — der Name beginnt immer mit
        // "v<version>-<groesse>px-", kann also nie selbst ".." sein.
        val name = IconCacheKeys.fileName("../../etc/passwd", 128)
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertTrue(name.startsWith("v1-128px-"))
        assertEquals("v1-128px-.._.._etc_passwd.webp", name)
    }

    @Test
    fun `unerwartete Zeichen werden ersetzt`() {
        assertEquals("a_b_c", IconCacheKeys.sanitize("a b\tc"))
        assertEquals("a_b", IconCacheKeys.sanitize("a:b"))
        // Leerzeichen, Doppelpunkt, Backslash — alles nicht im sicheren Satz.
        assertEquals("___", IconCacheKeys.sanitize(" :\\"))
    }

    @Test
    fun `uebliche Paketnamen bleiben unveraendert`() {
        val name = "de.swr.avp.ard.tv"
        assertEquals(name, IconCacheKeys.sanitize(name))
        assertTrue(IconCacheKeys.fileName(name, 104).endsWith(".webp"))
    }
}
