package com.piotv.navigate.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Die Einstellungsdatei als Datei: lesen, schreiben, Import über `adb push`.
 *
 * Die Mechanik selbst ist dieselbe wie bei der Reihenfolge — seit 0.12 steht sie
 * einmal in [ConfigFile], und OrderStoreTest prüft sie an der Reihenfolge
 * (Drossel, „eine gepushte Datei überschreibt keine Änderung auf dem Gerät“).
 * Hier wird geprüft, dass die **Einstellungen** sie richtig bedienen: Vorbelegung
 * ohne Datei, kaputte Datei, gepushte Datei, Schreiben, Änderung von außen.
 */
class SettingsStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var internalFile: File
    private lateinit var importSource: File
    private var clock = 1_000L

    @Before
    fun setUp() {
        internalFile = File(folder.root, "settings.conf")
        importSource = File(folder.root, "import/settings.conf")
        importSource.parentFile!!.mkdirs()
    }

    private fun store(importFile: File? = null) = SettingsStore(
        internalFile = internalFile,
        importFile = importFile,
        elapsedRealtime = { clock },
    )

    // --- Lesen ------------------------------------------------------------

    @Test
    fun `ohne Datei ist das Feld aus`() {
        assertEquals(NavigateSettings(nowPlaying = false), store().current())
    }

    @Test
    fun `eine vorhandene Datei wird beim Anlegen gelesen`() {
        internalFile.writeText("now_playing=on\n")
        assertTrue(store().current().nowPlaying)
    }

    @Test
    fun `eine kaputte Datei ergibt den Standard`() {
        internalFile.writeText("das ist keine Einstellung\n")
        assertEquals(NavigateSettings(), store().current())
    }

    @Test
    fun `die Pfade werden geliefert`() {
        assertEquals(internalFile.absolutePath, store().file.absolutePath)
        assertEquals(importSource.absolutePath, store(importSource).importPath)
        assertEquals(null, store().importPath)
    }

    // --- Schreiben --------------------------------------------------------

    @Test
    fun `speichern schreibt die Datei`() {
        val store = store()
        assertTrue(store.save(NavigateSettings(nowPlaying = true)))
        assertTrue(store.current().nowPlaying)
        assertTrue(internalFile.readText().contains("now_playing=on"))
    }

    @Test
    fun `ohne schreibbaren Pfad gilt die Aenderung nur fuer diese Sitzung`() {
        // internalFile liegt unterhalb einer *Datei* — mkdirs/writeText
        // scheitern. Der Stand muss trotzdem im Speicher stehen.
        val blocker = File(folder.root, "blocker")
        blocker.writeText("kein Verzeichnis")
        val broken = SettingsStore(File(blocker, "settings.conf"), null, { clock })
        broken.save(NavigateSettings(nowPlaying = true))
        assertTrue(broken.current().nowPlaying)
    }

    // --- Änderung von außen (`adb push`, Editor) --------------------------

    @Test
    fun `eine neuere Import-Datei wird uebernommen`() {
        importSource.writeText("now_playing=on\n")
        assertTrue(store(importSource).current().nowPlaying)
    }

    @Test
    fun `reloadIfChanged meldet eine Aenderung von aussen`() {
        internalFile.writeText("now_playing=off\n")
        val store = store()
        assertFalse(store.reloadIfChanged())

        internalFile.writeText("now_playing=on\n")
        internalFile.setLastModified(System.currentTimeMillis() + 5_000)
        assertTrue(store.reloadIfChanged())
        assertTrue(store.current().nowPlaying)
    }

    @Test
    fun `ohne Import-Pfad passiert nichts`() {
        val store = store(importFile = null)
        assertEquals(NavigateSettings(), store.current())
        assertFalse(store.reloadIfChanged())
    }
}
