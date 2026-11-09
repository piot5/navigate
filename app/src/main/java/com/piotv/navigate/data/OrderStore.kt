package com.piotv.navigate.data

import android.content.Context
import android.os.SystemClock
import java.io.File

/**
 * The order file (`files/navigate.conf`) — the state the home screen reads on
 * every build of the grid.
 *
 * The file mechanics (read and write internally, `adb push` via the external
 * import path, throttled re-checks) live in [ConfigFile] — the same as for
 * the settings ([SettingsStore]). Only **what** is in the file lives here:
 * one package-name line per entry, read by [OrderCodec].
 */
class OrderStore internal constructor(
    internalFile: File,
    importFile: File?,
    elapsedRealtime: () -> Long,
) {

    /**
     * The file mechanics (two locations, throttling, cache) live in
     * [ConfigFile] since 0.12 — the same as for the settings
     * ([SettingsStore]); that is also where the bug lived that
     * `OrderStoreTest` found in 0.4.
     */
    private val config = ConfigFile<List<String>>(
        internalFile = internalFile,
        importFile = importFile,
        elapsedRealtime = elapsedRealtime,
        parse = OrderCodec::parse,
        serialize = OrderCodec::serialize,
    )

    /** The file Navigate reads and writes (app-internal). */
    val file: File get() = config.file

    /** For diagnostics and docs: the path for `adb push`. */
    val importPath: String? get() = config.importPath

    /**
     * Current state — **no file access**.
     *
     * The launcher reads the order on every build of the grid. Each of those
     * calls used to `stat` external storage — a FUSE round trip. The state
     * now lives in memory; [reloadIfChanged] is the only place that looks.
     */
    fun current(): List<String> = config.current()

    /**
     * Checks whether the file has changed, and adopts it if so.
     *
     * @return `true` when a new state applies (the caller must then rebuild
     *   the grid).
     */
    fun reloadIfChanged(): Boolean = config.reloadIfChanged()

    /**
     * Moves a package one position forward (see [Order.moveForward]) and
     * writes the result immediately.
     *
     * @return `true` when something changed. If the package is already at the
     *   very front, the file stays untouched.
     */
    fun moveForward(packageName: String): Boolean {
        val next = Order.moveForward(config.current(), packageName)
        if (next == config.current()) return false
        save(next)
        return true
    }

    /** Like [moveForward], but one position backward (context menu). */
    fun moveBackward(packageName: String): Boolean {
        val next = Order.moveBackward(config.current(), packageName)
        if (next == config.current()) return false
        save(next)
        return true
    }

    fun save(order: List<String>): Boolean = config.save(order)

    companion object {
        private const val FILE_NAME = "navigate.conf"

        /**
         * The import path is looked at at most every 5 seconds — the number
         * lives at [ConfigFile.IMPORT_CHECK_INTERVAL_MS], where the throttle
         * sits.
         */
        internal const val IMPORT_CHECK_INTERVAL_MS = ConfigFile.IMPORT_CHECK_INTERVAL_MS

        @Volatile
        private var instance: OrderStore? = null

        fun get(context: Context): OrderStore =
            instance ?: synchronized(this) {
                instance ?: create(context).also { instance = it }
            }

        /**
         * The production path: internal storage plus import path plus the
         * system clock. Kept separate from [get] so the constructor stays free
         * for tests (OrderStoreTest) — no Context, no device.
         */
        private fun create(context: Context): OrderStore {
            val appContext = context.applicationContext
            return OrderStore(
                internalFile = File(appContext.filesDir, FILE_NAME),
                importFile = appContext.getExternalFilesDir(null)?.let { File(it, FILE_NAME) },
                elapsedRealtime = SystemClock::elapsedRealtime,
            )
        }
    }
}
