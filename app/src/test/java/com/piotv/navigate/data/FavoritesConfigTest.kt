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
 * Die Konfigurationsdatei als Datei: lesen, schreiben, Import über `adb push`.
 *
 * Bis 0.4 war das nicht prüfbar — der Konstruktor verlangte einen `Context`.
 * Jetzt bekommt er zwei Dateien und eine Uhr, und damit lässt sich die
 * eigentliche Semantik testen: Was passiert bei kaputtem Inhalt, wann wird eine
 * gepushte Datei übernommen, wann **nicht**.
 *
 * Der Test `eine neuere Import-Datei wird uebernommen` hat in 0.4 einen echten
 * Fehler gefunden: der Merker der Drossel stand auf `Long.MIN_VALUE`, und
 * `now - Long.MIN_VALUE` läuft über und wird negativ — die Bedingung
 * „innerhalb des Intervalls" war damit **immer** wahr, der Import fand nie
 * statt. Siehe CHANGELOG 0.4.
 */
class FavoritesConfigTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var internalFile: File
    private lateinit var importSource: File
    private var clock = 1_000L

    @Before
    fun setUp() {
        internalFile = File(folder.root, "navigate.conf")
        importSource = File(folder.root, "import/navigate.conf")
        importSource.parentFile!!.mkdirs()
    }

    private fun config(importFile: File? = null) = FavoritesConfig(
        internalFile = internalFile,
        importFile = importFile,
        elapsedRealtime = { clock },
    )

    // --- Lesen ------------------------------------------------------------

    @Test
    fun `fehlende Datei ergibt die Standardwerte`() {
        assertEquals(ConfigCodec.default(), config().current())
    }

    @Test
    fun `vorhandene Datei wird beim Anlegen gelesen`() {
        internalFile.writeText("favorites=com.netflix.ninja,com.termux\nmax_favorites=4\n")
        val config = config()
        assertEquals(listOf("com.netflix.ninja", "com.termux"), config.current().favorites)
        assertEquals(4, config.current().maxFavorites)
    }

    @Test
    fun `kaputte Datei ergibt die Standardwerte`() {
        internalFile.writeText("kaputte Zeile ohne Gleichheitszeichen\nfavorites=\n")
        assertEquals(ConfigCodec.default(), config().current())
    }

    @Test
    fun `der Pfad wird fuer die Anzeige geliefert`() {
        assertEquals(internalFile.absolutePath, config().path)
        assertEquals(importSource.absolutePath, config(importSource).importPath)
        assertEquals(null, config().importPath)
    }

    // --- Änderungserkennung ----------------------------------------------

    @Test
    fun `reloadIfChanged meldet nur echte Aenderungen`() {
        internalFile.writeText("favorites=com.termux")
        val config = config()
        assertFalse(config.reloadIfChanged())

        // Von außen geändert (adb oder ein Editor): neuer Zeitstempel.
        internalFile.writeText("favorites=com.netflix.ninja")
        internalFile.setLastModified(System.currentTimeMillis() + 5_000)
        assertTrue(config.reloadIfChanged())
        assertEquals(listOf("com.netflix.ninja"), config.current().favorites)
    }

    // --- Schreiben --------------------------------------------------------

    @Test
    fun `update schreibt und liefert den neuen Stand`() {
        val config = config()
        val next = config.update {
            it.copy(favorites = listOf("com.termux"), showIcons = false)
        }
        assertEquals(listOf("com.termux"), next.favorites)
        assertFalse(next.showIcons)
        assertEquals(next, config.current())
        // Und die Datei trägt es auch — ein Öffnen und Schließen verliert nichts.
        val text = internalFile.readText()
        assertTrue(text.contains("favorites=com.termux"))
        assertTrue(text.contains("show_icons=false"))
    }

    @Test
    fun `ohne schreibbaren Pfad gilt die Aenderung nur fuer diese Sitzung`() {
        // internalFile liegt unterhalb einer *Datei* — mkdirs/writeText
        // scheitern. Der Stand muss trotzdem im Speicher stehen, sonst würde
        // ein Tippen in den Einstellungen wirkungslos bleiben.
        val blocker = File(folder.root, "blocker")
        blocker.writeText("kein Verzeichnis")
        val broken = FavoritesConfig(File(blocker, "navigate.conf"), null, { clock })
        broken.update { it.copy(showClock = false) }
        assertFalse(broken.current().showClock)
    }

    // --- Import (`adb push`) ---------------------------------------------

    @Test
    fun `eine neuere Import-Datei wird uebernommen`() {
        importSource.writeText("favorites=com.termux\nmax_favorites=3\n")
        val config = config(importSource)
        assertEquals(listOf("com.termux"), config.current().favorites)
        assertEquals(3, config.current().maxFavorites)
    }

    @Test
    fun `eine gleich alte Import-Datei wird nicht uebernommen`() {
        importSource.writeText("favorites=com.termux")
        val config = config(importSource)
        assertEquals(listOf("com.termux"), config.current().favorites)

        clock += FavoritesConfig.IMPORT_CHECK_INTERVAL_MS
        assertFalse(config.reloadIfChanged())
    }

    @Test
    fun `ohne Import-Pfad passiert nichts`() {
        val config = config(importFile = null)
        assertEquals(ConfigCodec.default(), config.current())
        assertFalse(config.reloadIfChanged())
    }

    @Test
    fun `die Drossel verhindert ein zweites Nachsehen innerhalb des Intervalls`() {
        // Der erste Blick (beim Anlegen) hat den Zeitstempel gesetzt.
        val config = config(importSource)
        importSource.writeText("favorites=com.termux")
        importSource.setLastModified(System.currentTimeMillis() + 10_000)

        assertFalse(config.reloadIfChanged())
        clock += FavoritesConfig.IMPORT_CHECK_INTERVAL_MS
        assertTrue(config.reloadIfChanged())
        assertEquals(listOf("com.termux"), config.current().favorites)
    }

    @Test
    fun `eine einmal importierte Datei ueberschreibt keine Aenderung aus den Einstellungen`() {
        // Die dokumentierte Zusage (docs/CONFIG.md): die gepushte Datei wird
        // EINMAL übernommen. Danach liegt der Stand intern, und eine alte Datei
        // darf ihn nicht zurückdrehen.
        importSource.writeText("favorites=com.termux")
        val config = config(importSource)
        assertEquals(listOf("com.termux"), config.current().favorites)

        config.update { it.copy(favorites = listOf("com.netflix.ninja")) }
        assertEquals(listOf("com.netflix.ninja"), config.current().favorites)

        clock += FavoritesConfig.IMPORT_CHECK_INTERVAL_MS
        config.reloadIfChanged()
        assertEquals(listOf("com.netflix.ninja"), config.current().favorites)
    }
}
