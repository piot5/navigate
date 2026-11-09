package com.piotv.navigate.data

import android.content.Context
import android.os.SystemClock
import java.io.File

/**
 * The settings file (`files/settings.conf`) as a file — the same mechanics as
 * [OrderStore], only with different content: two locations, one state
 * ([ConfigFile]).
 *
 * Why a separate file and not `SharedPreferences`: a user setting belongs
 * where it is readable and where it can arrive via `adb push`. And why not in
 * `navigate.conf`: the order file is a list of package names — a `key=value`
 * line in it would be read as a package name by [OrderCodec]. Two files, two
 * formats, no special cases.
 */
class SettingsStore internal constructor(
    internalFile: File,
    importFile: File?,
    elapsedRealtime: () -> Long,
) {

    private val config = ConfigFile<NavigateSettings>(
        internalFile = internalFile,
        importFile = importFile,
        elapsedRealtime = elapsedRealtime,
        parse = SettingsCodec::parse,
        serialize = SettingsCodec::serialize,
    )

    /** The file Navigate reads and writes (app-internal). */
    val file: File get() = config.file

    /** For diagnostics and docs: the path for `adb push`. */
    val importPath: String? get() = config.importPath

    /** The current state — without file access. */
    fun current(): NavigateSettings = config.current()

    /** Checks whether the file has changed (`adb push`) and adopts it if so. */
    fun reloadIfChanged(): Boolean = config.reloadIfChanged()

    fun save(settings: NavigateSettings): Boolean = config.save(settings)

    companion object {
        private const val FILE_NAME = "settings.conf"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: create(context).also { instance = it }
            }

        /**
         * The production paths: internal storage plus import path plus the
         * system clock. Kept separate from [get] so the constructor stays free
         * for tests (SettingsStoreTest) — without Context, without a device.
         */
        private fun create(context: Context): SettingsStore {
            val appContext = context.applicationContext
            return SettingsStore(
                internalFile = File(appContext.filesDir, FILE_NAME),
                importFile = appContext.getExternalFilesDir(null)?.let { File(it, FILE_NAME) },
                elapsedRealtime = SystemClock::elapsedRealtime,
            )
        }
    }
}
