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
 * Die Reihenfolge-Datei als Datei: lesen, schreiben, Import über `adb push`.
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
class OrderStoreTest {

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

    private fun store(importFile: File? = null) = OrderStore(
        internalFile = internalFile,
        importFile = importFile,
        elapsedRealtime = { clock },
    )

    // --- Lesen ------------------------------------------------------------

    @Test
    fun `fehlende Datei ergibt eine leere Reihenfolge`() {
        assertEquals(emptyList<String>(), store().current())
    }

    @Test
    fun `vorhandene Datei wird beim Anlegen gelesen`() {
        internalFile.writeText("com.b\ncom.a\n")
        assertEquals(listOf("com.b", "com.a"), store().current())
    }

    @Test
    fun `kaputte Datei ergibt eine leere Reihenfolge`() {
        internalFile.writeText("# nur ein Kommentar\n\n")
        assertEquals(emptyList<String>(), store().current())
    }

    @Test
    fun `der Import-Pfad wird geliefert`() {
        assertEquals(internalFile.absolutePath, store().file.absolutePath)
        assertEquals(importSource.absolutePath, store(importSource).importPath)
        assertEquals(null, store().importPath)
    }

    // --- Änderungserkennung ----------------------------------------------

    @Test
    fun `reloadIfChanged meldet nur echte Aenderungen`() {
        internalFile.writeText("com.a")
        val store = store()
        assertFalse(store.reloadIfChanged())

        // Von außen geändert (adb oder ein Editor): neuer Zeitstempel.
        internalFile.writeText("com.b")
        internalFile.setLastModified(System.currentTimeMillis() + 5_000)
        assertTrue(store.reloadIfChanged())
        assertEquals(listOf("com.b"), store.current())
    }

    // --- Schreiben --------------------------------------------------------

    @Test
    fun `moveForward schreibt und liefert den neuen Stand`() {
        internalFile.writeText("com.a\ncom.b\n")
        val store = store()
        assertTrue(store.moveForward("com.b"))
        assertEquals(listOf("com.b", "com.a"), store.current())
        // Und die Datei trägt es auch — ein Öffnen und Schließen verliert nichts.
        assertTrue(internalFile.readText().contains("com.b\ncom.a"))
    }

    @Test
    fun `moveForward auf dem ersten Eintrag aendert nichts`() {
        internalFile.writeText("com.a\ncom.b\n")
        val store = store()
        assertFalse(store.moveForward("com.a"))
        assertEquals(listOf("com.a", "com.b"), store.current())
    }

    @Test
    fun `ohne schreibbaren Pfad gilt die Aenderung nur fuer diese Sitzung`() {
        // internalFile liegt unterhalb einer *Datei* — mkdirs/writeText
        // scheitern. Der Stand muss trotzdem im Speicher stehen.
        val blocker = File(folder.root, "blocker")
        blocker.writeText("kein Verzeichnis")
        val broken = OrderStore(File(blocker, "navigate.conf"), null, { clock })
        broken.save(listOf("com.a"))
        assertEquals(listOf("com.a"), broken.current())
    }

    // --- Import (`adb push`) ---------------------------------------------

    @Test
    fun `eine neuere Import-Datei wird uebernommen`() {
        importSource.writeText("com.b\ncom.a\n")
        assertEquals(listOf("com.b", "com.a"), store(importSource).current())
    }

    @Test
    fun `eine gleich alte Import-Datei wird nicht uebernommen`() {
        importSource.writeText("com.b")
        val store = store(importSource)
        assertEquals(listOf("com.b"), store.current())

        clock += OrderStore.IMPORT_CHECK_INTERVAL_MS
        assertFalse(store.reloadIfChanged())
    }

    @Test
    fun `ohne Import-Pfad passiert nichts`() {
        val store = store(importFile = null)
        assertEquals(emptyList<String>(), store.current())
        assertFalse(store.reloadIfChanged())
    }

    @Test
    fun `die Drossel verhindert ein zweites Nachsehen innerhalb des Intervalls`() {
        // Der erste Blick (beim Anlegen) hat den Zeitstempel gesetzt.
        val store = store(importSource)
        importSource.writeText("com.b")
        importSource.setLastModified(System.currentTimeMillis() + 10_000)

        assertFalse(store.reloadIfChanged())
        clock += OrderStore.IMPORT_CHECK_INTERVAL_MS
        assertTrue(store.reloadIfChanged())
        assertEquals(listOf("com.b"), store.current())
    }

    @Test
    fun `eine einmal importierte Datei ueberschreibt keine Aenderung auf dem Geraet`() {
        // Die dokumentierte Zusage (docs/CONFIG.md): die gepushte Datei wird
        // EINMAL übernommen. Danach liegt der Stand intern, und eine alte Datei
        // darf ihn nicht zurückdrehen — sonst würde langes Drücken auf dem
        // Fernseher beim nächsten HOME-Druck wieder verschwinden.
        importSource.writeText("com.a")
        val store = store(importSource)
        assertEquals(listOf("com.a"), store.current())

        store.moveForward("com.new")
        // Unbekannte Pakete werden angehängt — aus dem „Rest“ ist das genau
        // eine Position nach vorne (siehe Order.moveForward).
        assertEquals(listOf("com.a", "com.new"), store.current())

        clock += OrderStore.IMPORT_CHECK_INTERVAL_MS
        store.reloadIfChanged()
        assertEquals(listOf("com.a", "com.new"), store.current())
    }
}
