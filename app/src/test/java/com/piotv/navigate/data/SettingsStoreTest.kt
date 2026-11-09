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
 * The settings file as a file: reading, writing, import via `adb push`.
 *
 * The mechanics themselves are the same as for the order — since 0.12 they live
 * once in [ConfigFile], and OrderStoreTest checks them on the order (throttle,
 * "a pushed file does not overwrite a change on the device").
 * What is checked here is that the **settings** drive it correctly: defaults
 * without a file, a broken file, a pushed file, writing, external changes.
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

    // --- Reading ----------------------------------------------------------------

    @Test
    fun `without a file the field is off`() {
        assertEquals(NavigateSettings(nowPlaying = false), store().current())
    }

    @Test
    fun `an existing file is read on creation`() {
        internalFile.writeText("now_playing=on\n")
        assertTrue(store().current().nowPlaying)
    }

    @Test
    fun `a broken file gives the default`() {
        internalFile.writeText("this is not a setting\n")
        assertEquals(NavigateSettings(), store().current())
    }

    @Test
    fun `the paths are exposed`() {
        assertEquals(internalFile.absolutePath, store().file.absolutePath)
        assertEquals(importSource.absolutePath, store(importSource).importPath)
        assertEquals(null, store().importPath)
    }

    // --- Writing -----------------------------------------------------------------

    @Test
    fun `save writes the file`() {
        val store = store()
        assertTrue(store.save(NavigateSettings(nowPlaying = true)))
        assertTrue(store.current().nowPlaying)
        assertTrue(internalFile.readText().contains("now_playing=on"))
    }

    @Test
    fun `without a writable path the change holds only for this session`() {
        // internalFile sits below a *file* — mkdirs/writeText fail. The state
        // must still be kept in memory.
        val blocker = File(folder.root, "blocker")
        blocker.writeText("not a directory")
        val broken = SettingsStore(File(blocker, "settings.conf"), null, { clock })
        broken.save(NavigateSettings(nowPlaying = true))
        assertTrue(broken.current().nowPlaying)
    }

    // --- Change from outside (`adb push`, editor) -------------------------------

    @Test
    fun `a newer import file is adopted`() {
        importSource.writeText("now_playing=on\n")
        assertTrue(store(importSource).current().nowPlaying)
    }

    @Test
    fun `reloadIfChanged reports a change from outside`() {
        internalFile.writeText("now_playing=off\n")
        val store = store()
        assertFalse(store.reloadIfChanged())

        internalFile.writeText("now_playing=on\n")
        internalFile.setLastModified(System.currentTimeMillis() + 5_000)
        assertTrue(store.reloadIfChanged())
        assertTrue(store.current().nowPlaying)
    }

    @Test
    fun `without an import path nothing happens`() {
        val store = store(importFile = null)
        assertEquals(NavigateSettings(), store.current())
        assertFalse(store.reloadIfChanged())
    }
}
