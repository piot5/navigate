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
 * The history file as a file: appending, capping.
 *
 * Like [OrderStoreTest]: the constructor takes two files and a clock —
 * no Context, no device.
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
    fun `a missing file gives an empty history`() {
        assertEquals(emptyList<HistoryEntry>(), store().current())
        assertEquals(emptyList<HistoryEntry>(), store().latest())
    }

    @Test
    fun `append adds and reports`() {
        val store = store()
        assertTrue(store.append("com.zdf", "ZDF · Newscast", 1730000000000L))
        assertEquals(
            listOf(HistoryEntry("com.zdf", "ZDF · Newscast", 1730000000000L)),
            store.current(),
        )
    }

    @Test
    fun `the same title twice writes only once`() {
        // The tick runs every 5 seconds: without this promise the file would
        // grow by the same line on every pass.
        val store = store()
        assertTrue(store.append("com.zdf", "ZDF · Newscast", 1730000000000L))
        assertFalse(store.append("com.zdf", "ZDF · Newscast", 1730000005000L))
        assertEquals(1, store.current().size)
    }

    @Test
    fun `empty values write nothing`() {
        val store = store()
        assertFalse(store.append("", "Title", 1730000000000L))
        assertFalse(store.append("com.zdf", "", 1730000000000L))
        assertFalse(store.append("com.zdf", "Title", 0L))
        assertEquals(emptyList<HistoryEntry>(), store.current())
    }

    @Test
    fun `a title returns to the history after another one`() {
        // Only the **last** entry is compared: if "Newscast" replaces the row
        // again after "Evening Report" sat in between, it belongs back in the
        // history (docs/CONFIG.md: the old title moves here).
        val store = store()
        assertTrue(store.append("com.zdf", "ZDF · Newscast", 1L))
        assertTrue(store.append("com.zdf", "ZDF · Evening Report", 2L))
        assertTrue(store.append("com.zdf", "ZDF · Newscast", 3L))
        assertEquals(
            listOf(
                HistoryEntry("com.zdf", "ZDF · Newscast", 3L),
                HistoryEntry("com.zdf", "ZDF · Evening Report", 2L),
                HistoryEntry("com.zdf", "ZDF · Newscast", 1L),
            ),
            store.latest(),
        )
    }

    @Test
    fun `the same title in a row writes only once`() {
        // The counter-check to the test above: **directly** one after the other
        // the same line is a duplicate — the 5-second tick must not write it.
        val store = store()
        assertTrue(store.append("com.zdf", "ZDF · Newscast", 1L))
        assertFalse(store.append("com.zdf", "ZDF · Newscast", 2L))
        assertFalse(store.append("com.zdf", "ZDF · Newscast", 3L))
        assertEquals(listOf(HistoryEntry("com.zdf", "ZDF · Newscast", 1L)), store.current())
    }

    @Test
    fun `latest returns newest first`() {
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
    fun `the file does not grow forever`() {
        val store = store()
        for (i in 1..(HistoryStore.MAX_ENTRIES + 10)) {
            store.append("com.a", "Title $i", i.toLong())
        }
        assertEquals(HistoryStore.MAX_ENTRIES, store.current().size)
        assertEquals("Title ${HistoryStore.MAX_ENTRIES + 10}", store.current().last().line)
    }

    @Test
    fun `an existing file is read on creation`() {
        internalFile.writeText("1730000000000|com.zdf|ZDF · Newscast\n")
        assertEquals(1, store().current().size)
    }
}
