package com.piotv.navigate.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.piotv.navigate.data.AppEntry
import java.io.File

/**
 * File names of the icon cache — the only place where a package name becomes
 * a file name. Pure Kotlin → testable (IconCacheKeysTest).
 *
 * Why a file name from foreign data at all: the package name comes from the
 * system and is therefore trusted — but a file whose name is derived from a
 * string must never be able to contain path parts. That is why everything
 * except `A–Z a–z 0–9 . _ -` is replaced; a path separator does not survive
 * that. The fixed prefix `v<version>-<size>px-` additionally ensures the name
 * can never be `.` or `..`.
 */
object IconCacheKeys {

    /**
     * Format version in the file name. If the storage format changes, the
     * launcher no longer finds the old files and creates them anew — instead
     * of reading a half-matching file.
     */
    const val FORMAT_VERSION = 1

    private const val SAFE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._-"

    /** The size belongs in the name: a changed grid size should get its own
     *  file instead of an upscaled one (see [AppEntry.iconKey]). */
    fun fileName(packageName: String, sizePx: Int): String =
        "v$FORMAT_VERSION-${sizePx}px-${sanitize(packageName)}.webp"

    fun sanitize(raw: String): String = buildString(raw.length) {
        for (character in raw) append(if (character in SAFE_CHARS) character else '_')
    }
}

/**
 * Icons as small WebP files in the app-**internal** storage.
 *
 * Why disk and not just the LRU cache: obtaining an icon means mapping the
 * resources of a foreign app (`getResourcesForApplication`). That is exactly
 * what produces the measured ~25 MB "Private Clean" on the target device
 * (docs/DEVICE_PERF.md) and costs time again at every process restart. A
 * 128-px WebP file is ~8 KB; reading and decoding it is a file access without
 * a foreign APK.
 *
 * Why internal and not external storage: `/sdcard` runs through the
 * MediaProvider FUSE daemon, and `stat`/`open` there go through the daemon
 * **always** — FUSE passthrough only helps for read/write after opening
 * (source.android.com/docs/core/storage/fuse-passthrough). For a cache read on
 * every press of HOME, that is the wrong place.
 *
 * The cache is disposable at any time: if a file is missing, it is rebuilt in
 * the background from the app's resources.
 */
class IconDiskCache private constructor(context: Context) {

    private val dir = File(context.filesDir, DIR_NAME)

    private fun fileFor(entry: AppEntry, sizePx: Int): File =
        File(dir, IconCacheKeys.fileName(entry.packageName, sizePx))

    /** Icon from disk or `null`. Runs in the background (see [IconLoader]). */
    fun load(entry: AppEntry, sizePx: Int): Bitmap? {
        val file = fileFor(entry, sizePx)
        if (!file.isFile) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /**
     * Writes the icon. First into a temporary file, then rename: an
     * interrupted write must not leave a half file that passes as "icon
     * present" on the next start.
     */
    fun store(entry: AppEntry, sizePx: Int, bitmap: Bitmap): Boolean {
        return try {
            if (!dir.isDirectory && !dir.mkdirs()) return false
            val temp = File(dir, IconCacheKeys.fileName(entry.packageName, sizePx) + TEMP_SUFFIX)
            temp.outputStream().use { out ->
                @Suppress("DEPRECATION")
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    Bitmap.CompressFormat.WEBP
                }
                bitmap.compress(format, QUALITY, out)
            }
            val target = fileFor(entry, sizePx)
            if (target.exists()) target.delete()
            temp.renameTo(target)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Throws everything away. Needed when the storage format changes
     * ([IconCacheKeys.FORMAT_VERSION]) — otherwise files pile up that nobody
     * reads anymore.
     */
    fun wipe() {
        try {
            dir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            // A cache that cannot be deleted is no reason to disturb the launcher.
        }
    }

    /**
     * Cleans up when the format version no longer matches. The marker is a file
     * inside the cache folder itself — that way the cache needs no entry in the
     * launcher settings and stays self-contained.
     */
    fun ensureFormatVersion() {
        val marker = File(dir, MARKER_NAME)
        val current = try {
            if (marker.isFile) marker.readText().trim() else null
        } catch (e: Exception) {
            null
        }
        if (current == IconCacheKeys.FORMAT_VERSION.toString()) return
        wipe()
        try {
            if (dir.isDirectory || dir.mkdirs()) {
                marker.writeText(IconCacheKeys.FORMAT_VERSION.toString())
            }
        } catch (e: Exception) {
            // Without the marker the cleanup runs again on the next start — harmless.
        }
    }

    companion object {
        private const val DIR_NAME = "icons"
        private const val MARKER_NAME = "version"
        private const val TEMP_SUFFIX = ".tmp"
        private const val QUALITY = 90

        @Volatile
        private var instance: IconDiskCache? = null

        fun get(context: Context): IconDiskCache =
            instance ?: synchronized(this) {
                instance ?: IconDiskCache(context.applicationContext).also { instance = it }
            }
    }
}
