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
 * The order file as a file: reading, writing, import via `adb push`.
 *
 * Until 0.4 this was untestable — the constructor demanded a `Context`. Now it
 * takes two files and a clock, which makes the actual semantics testable: what
 * happens with broken content, when a pushed file is adopted, when **not**.
 *
 * The test `a newer import file is adopted` found a real bug in 0.4: the
 * throttle marker started at `Long.MIN_VALUE`, and `now - Long.MIN_VALUE`
 * overflows and goes negative — the "within the interval" condition was thus
 * **always** true and the import never happened. See CHANGELOG 0.4.
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

    // --- Reading --------------------------------------------------------------

    @Test
    fun `a missing file gives an empty order`() {
        assertEquals(emptyList<String>(), store().current())
    }

    @Test
    fun `an existing file is read on creation`() {
        internalFile.writeText("com.b\ncom.a\n")
        assertEquals(listOf("com.b", "com.a"), store().current())
    }

    @Test
    fun `a broken file gives an empty order`() {
        internalFile.writeText("# just a comment\n\n")
        assertEquals(emptyList<String>(), store().current())
    }

    @Test
    fun `the import path is exposed`() {
        assertEquals(internalFile.absolutePath, store().file.absolutePath)
        assertEquals(importSource.absolutePath, store(importSource).importPath)
        assertEquals(null, store().importPath)
    }

    // --- Change detection ------------------------------------------------------

    @Test
    fun `reloadIfChanged reports only real changes`() {
        internalFile.writeText("com.a")
        val store = store()
        assertFalse(store.reloadIfChanged())

        // Changed from outside (adb or an editor): a new timestamp.
        internalFile.writeText("com.b")
        internalFile.setLastModified(System.currentTimeMillis() + 5_000)
        assertTrue(store.reloadIfChanged())
        assertEquals(listOf("com.b"), store.current())
    }

    // --- Writing ----------------------------------------------------------------

    @Test
    fun `moveForward writes and returns the new state`() {
        internalFile.writeText("com.a\ncom.b\n")
        val store = store()
        assertTrue(store.moveForward("com.b"))
        assertEquals(listOf("com.b", "com.a"), store.current())
        // And the file carries it too — opening and closing loses nothing.
        assertTrue(internalFile.readText().contains("com.b\ncom.a"))
    }

    @Test
    fun `moveForward on the first entry changes nothing`() {
        internalFile.writeText("com.a\ncom.b\n")
        val store = store()
        assertFalse(store.moveForward("com.a"))
        assertEquals(listOf("com.a", "com.b"), store.current())
    }

    @Test
    fun `without a writable path the change holds only for this session`() {
        // internalFile sits below a *file* — mkdirs/writeText fail. The state
        // must still be kept in memory.
        val blocker = File(folder.root, "blocker")
        blocker.writeText("not a directory")
        val broken = OrderStore(File(blocker, "navigate.conf"), null, { clock })
        broken.save(listOf("com.a"))
        assertEquals(listOf("com.a"), broken.current())
    }

    // --- Import (`adb push`) ----------------------------------------------------

    @Test
    fun `a newer import file is adopted`() {
        importSource.writeText("com.b\ncom.a\n")
        assertEquals(listOf("com.b", "com.a"), store(importSource).current())
    }

    @Test
    fun `an equally old import file is not adopted`() {
        importSource.writeText("com.b")
        val store = store(importSource)
        assertEquals(listOf("com.b"), store.current())

        clock += OrderStore.IMPORT_CHECK_INTERVAL_MS
        assertFalse(store.reloadIfChanged())
    }

    @Test
    fun `without an import path nothing happens`() {
        val store = store(importFile = null)
        assertEquals(emptyList<String>(), store.current())
        assertFalse(store.reloadIfChanged())
    }

    @Test
    fun `the throttle prevents a second look within the interval`() {
        // The first look (on creation) set the timestamp.
        val store = store(importSource)
        importSource.writeText("com.b")
        importSource.setLastModified(System.currentTimeMillis() + 10_000)

        assertFalse(store.reloadIfChanged())
        clock += OrderStore.IMPORT_CHECK_INTERVAL_MS
        assertTrue(store.reloadIfChanged())
        assertEquals(listOf("com.b"), store.current())
    }

    @Test
    fun `a once-imported file does not overwrite a change on the device`() {
        // The documented promise (docs/CONFIG.md): the pushed file is adopted
        // ONCE. Afterwards the state lives internally, and an old file must not
        // turn it back — otherwise a long press on the TV would vanish again at
        // the next HOME press.
        importSource.writeText("com.a")
        val store = store(importSource)
        assertEquals(listOf("com.a"), store.current())

        store.moveForward("com.new")
        // Unknown packages are appended — from the "rest" that is exactly one
        // position forward (see Order.moveForward).
        assertEquals(listOf("com.a", "com.new"), store.current())

        clock += OrderStore.IMPORT_CHECK_INTERVAL_MS
        store.reloadIfChanged()
        assertEquals(listOf("com.a", "com.new"), store.current())
    }
}
