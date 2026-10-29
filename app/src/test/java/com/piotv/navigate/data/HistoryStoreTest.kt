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
 * Die Verlauf-Datei als Datei: anhängen, blättern, begrenzen.
 *
 * Wie [OrderStoreTest]: der Konstruktor bekommt zwei Dateien und eine Uhr —
 * ohne Context, ohne Gerät.
 */
class HistoryStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var internalFile: File
    private lateinit var importSource: File
    private var clock = 1_000L

    @Before
    fun setUp() {
        internalFile = File(folder.root, "history-${System.nanoTime()}.conf")
        importSource = File(folder.root, "import/history.conf")
        importSource.parentFile!!.mkdirs()
    }

    private fun store(importFile: File? = null) = HistoryStore(
        internalFile = internalFile,
        importFile = importFile,
        elapsedRealtime = { clock },
    )

    @Test
    fun `fehlende Datei ergibt leeren Verlauf`() {
        assertEquals(emptyList<HistoryEntry>(), store().current())
        assertEquals(emptyList<HistoryEntry>(), store().latest())
        assertEquals(emptyList<HistoryEntry>(), store().page(0))
    }

    @Test
    fun `append haengt an und meldet`() {
        val store = store()
        assertTrue(store.append("com.zdf", "ZDF · Tagesschau", 1730000000000L))
        assertEquals(
            listOf(HistoryEntry("com.zdf", "ZDF · Tagesschau", 1730000000000L)),
            store.current(),
        )
    }

    @Test
    fun `derselbe Titel zweimal schreibt nur einmal`() {
        // Der Takt läuft alle 5 Sekunden: ohne diese Zusage wüchse die Datei
        // bei jedem Durchlauf um dieselbe Zeile.
        val store = store()
        assertTrue(store.append("com.zdf", "ZDF · Tagesschau", 1730000000000L))
        assertFalse(store.append("com.zdf", "ZDF · Tagesschau", 1730000005000L))
        assertEquals(1, store.current().size)
    }

    @Test
    fun `leere Werte schreiben nichts`() {
        val store = store()
        assertFalse(store.append("", "Titel", 1730000000000L))
        assertFalse(store.append("com.zdf", "", 1730000000000L))
        assertFalse(store.append("com.zdf", "Titel", 0L))
        assertEquals(emptyList<HistoryEntry>(), store.current())
    }

    @Test
    fun `ein Titel kehrt nach einem anderen erneut in den Verlauf`() {
        // Nur der **letzte** Eintrag wird verglichen: ersetzt „Tagesschau" die
        // Zeile erneut, nachdem „heute journal" dazwischen lag, gehört sie wieder
        // in den Verlauf (docs/CONFIG.md: der alte Titel wandert hierher).
        val store = store()
        assertTrue(store.append("com.zdf", "ZDF · Tagesschau", 1L))
        assertTrue(store.append("com.zdf", "ZDF · heute journal", 2L))
        assertTrue(store.append("com.zdf", "ZDF · Tagesschau", 3L))
        assertEquals(
            listOf(
                HistoryEntry("com.zdf", "ZDF · Tagesschau", 3L),
                HistoryEntry("com.zdf", "ZDF · heute journal", 2L),
                HistoryEntry("com.zdf", "ZDF · Tagesschau", 1L),
            ),
            store.latest(),
        )
    }

    @Test
    fun `derselbe Titel in Folge schreibt nur einmal`() {
        // Die Gegenprobe zum Test darüber: **direkt** hintereinander ist dieselbe
        // Zeile eine Dopplung — der 5-Sekunden-Takt darf sie nicht schreiben.
        val store = store()
        assertTrue(store.append("com.zdf", "ZDF · Tagesschau", 1L))
        assertFalse(store.append("com.zdf", "ZDF · Tagesschau", 2L))
        assertFalse(store.append("com.zdf", "ZDF · Tagesschau", 3L))
        assertEquals(listOf(HistoryEntry("com.zdf", "ZDF · Tagesschau", 1L)), store.current())
    }

    @Test
    fun `latest liefert neueste zuerst`() {
        val store = store()
        store.append("com.a", "A1", 1L)
        store.append("com.b", "B1", 2L)
        store.append("com.a", "A2", 3L)
        assertEquals(
            listOf(
                HistoryEntry("com.a", "A2", 3L),
                HistoryEntry("com.b", "B1", 2L),
                HistoryEntry("com.a", "A1", 1L),
            ),
            store.latest(),
        )
        assertEquals(
            listOf(HistoryEntry("com.a", "A2", 3L), HistoryEntry("com.b", "B1", 2L)),
            store.latest(2),
        )
    }

    @Test
    fun `page blaettert durch den Verlauf`() {
        val store = store()
        store.append("com.a", "A1", 1L)
        store.append("com.b", "B1", 2L)
        store.append("com.a", "A2", 3L)
        assertEquals(
            listOf(HistoryEntry("com.a", "A2", 3L), HistoryEntry("com.b", "B1", 2L)),
            store.page(0, 2),
        )
        assertEquals(
            listOf(HistoryEntry("com.a", "A1", 1L)),
            store.page(2, 2),
        )
        assertEquals(emptyList<HistoryEntry>(), store.page(3, 2))
        assertEquals(emptyList<HistoryEntry>(), store.page(-1, 2))
    }

    @Test
    fun `die Datei waechst nicht ewig`() {
        val store = store()
        for (i in 1..(HistoryStore.MAX_ENTRIES + 10)) {
            store.append("com.a", "Titel $i", i.toLong())
        }
        assertEquals(HistoryStore.MAX_ENTRIES, store.current().size)
        assertEquals("Titel ${HistoryStore.MAX_ENTRIES + 10}", store.current().last().line)
    }

    @Test
    fun `vorhandene Datei wird beim Anlegen gelesen`() {
        internalFile.writeText("1730000000000|com.zdf|ZDF · Tagesschau\n")
        assertEquals(1, store().current().size)
    }
}
