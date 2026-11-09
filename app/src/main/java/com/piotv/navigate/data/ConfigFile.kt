package com.piotv.navigate.data

import java.io.File

/**
 * A configuration file in two locations — with one state in memory.
 *
 * This code lived in [OrderStore] until 0.11 and continues to apply there
 * unchanged; since there is a second file ([SettingsStore]), it lives **once**
 * here. The reason is not "to save lines": exactly this mechanics contained the
 * bug `OrderStoreTest` found in 0.4 — the import throttle marker sat at
 * `Long.MIN_VALUE`, `now - Long.MIN_VALUE` overflowed and became negative, so
 * the throttle applied **always** and the import never happened. Two copies
 * would be two chances to reintroduce it.
 *
 * ## Two locations, one state
 *
 * - **Read and written app-internally** ([file]). External storage would be the
 *   obvious choice, but `/sdcard` runs through the MediaProvider FUSE daemon:
 *   `stat` and `open` there **always** pass through a userspace daemon
 *   (FUSE passthrough only helps for read/write after opening,
 *   source.android.com/docs/core/storage/fuse-passthrough). These files are
 *   looked at on every `onResume` — on the target device that would be
 *   measurable time.
 * - **The import path** ([importPath]) is the way for `adb push`: without
 *   root, ADB cannot get into the internal storage of a release app. A file
 *   there that is **newer** is adopted **once** and afterwards kept internally
 *   — otherwise an old, once-pushed file would keep undoing every change made
 *   on the device.
 *
 * @param parse Reads the value from the file text; `null` means "no file".
 * @param serialize Writes the value as file text.
 */
class ConfigFile<T> internal constructor(
    /** Source of truth: app-internal, no FUSE. */
    private val internalFile: File,
    /** Import path for `adb push`; `null` when the device has none. */
    private val importFile: File?,
    /**
     * Clock for the import throttle — injected so the import can be tested
     * without waiting. In production this is `SystemClock.elapsedRealtime`.
     */
    private val elapsedRealtime: () -> Long,
    private val parse: (String?) -> T,
    private val serialize: (T) -> String,
) {

    private var cached: T = parse(null)
    private var cachedModifiedAt = Long.MIN_VALUE
    private var lastImportAt = Long.MIN_VALUE

    /**
     * Uptime clock of the last check of the import path.
     *
     * The initial value is **not** `Long.MIN_VALUE`: `now - Long.MIN_VALUE`
     * overflows and becomes negative, so the condition "within the interval"
     * would always be true — the import would never have happened. That was
     * exactly the case until 0.4 (found by OrderStoreTest, see CHANGELOG). A
     * negative value on the order of the interval means "a long time ago" and
     * cannot overflow.
     */
    private var lastImportCheckAt = -IMPORT_CHECK_INTERVAL_MS

    /** The file Navigate reads and writes (app-internal). */
    val file: File get() = internalFile

    /** For diagnostics and docs: the path for `adb push`. */
    val importPath: String? get() = importFile?.absolutePath

    init {
        loadInternal()
        // Check once at startup: this way a leftover external file is adopted
        // without anyone having to do anything.
        importIfNewer()
    }

    /**
     * Current state — **without file access**.
     *
     * The launcher reads the configuration on every build of the grid. Each of
     * those calls previously did a `stat` on external storage — i.e. a FUSE
     * round trip. The state now lives in memory; [reloadIfChanged] is the only
     * place that looks.
     */
    fun current(): T = cached

    /**
     * Checks whether the file has changed, and adopts it if so.
     *
     * @return `true` when a new state applies (the caller must then rebuild its
     *   display).
     */
    fun reloadIfChanged(): Boolean {
        if (importIfNewer()) return true
        val modifiedAt = if (internalFile.isFile) internalFile.lastModified() else 0L
        if (modifiedAt == cachedModifiedAt) return false
        loadInternal()
        return true
    }

    fun save(value: T): Boolean {
        return try {
            internalFile.parentFile?.mkdirs()
            internalFile.writeText(serialize(value))
            cached = value
            cachedModifiedAt = internalFile.lastModified()
            true
        } catch (e: Exception) {
            // No writable storage: the change then only applies to this
            // session, instead of crashing the launcher.
            cached = value
            false
        }
    }

    private fun loadInternal() {
        cached = if (internalFile.isFile) {
            try {
                parse(internalFile.readText())
            } catch (e: Exception) {
                parse(null)
            }
        } else {
            parse(null)
        }
        cachedModifiedAt = if (internalFile.isFile) internalFile.lastModified() else 0L
    }

    /**
     * Adopts a file from external storage pushed via `adb push` if it is newer.
     *
     * Adoption happens only when the timestamp is **newer** than at the last
     * import — otherwise an old, once-pushed file would keep undoing every
     * change made on the device.
     */
    private fun importIfNewer(): Boolean {
        val source = importFile ?: return false
        val now = elapsedRealtime()
        if (now - lastImportCheckAt < IMPORT_CHECK_INTERVAL_MS) return false
        lastImportCheckAt = now

        if (!source.isFile) return false
        val modifiedAt = source.lastModified()
        if (modifiedAt <= lastImportAt) return false
        lastImportAt = modifiedAt

        val parsed = try {
            parse(source.readText())
        } catch (e: Exception) {
            return false
        }
        if (parsed == cached) return false
        return save(parsed)
    }

    companion object {
        /**
         * The import path is looked at at most every 5 seconds. More often
         * would be a FUSE access on the HOME path — exactly what internal
         * storage is meant to avoid — and less often an `adb push` would feel
         * like it never arrives.
         */
        internal const val IMPORT_CHECK_INTERVAL_MS = 5_000L
    }
}
