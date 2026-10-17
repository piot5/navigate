package com.piotv.navigate.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.piotv.navigate.data.AppEntry
import java.io.File

/**
 * Dateinamen des Symbol-Caches — die einzige Stelle, an der aus einem
 * Paketnamen ein Dateiname wird. Reines Kotlin → testbar (IconCacheKeysTest).
 *
 * Warum überhaupt ein Dateiname aus fremden Daten: der Paketname kommt vom
 * System, ist also vertrauenswürdig — aber eine Datei, deren Name aus einer
 * Zeichenkette entsteht, darf niemals Pfadanteile enthalten können. Deshalb
 * wird alles außer `A–Z a–z 0–9 . _ -` ersetzt; ein Pfadtrenner überlebt das
 * nicht. Der feste Präfix `v<version>-<größe>px-` sorgt zusätzlich dafür, dass
 * der Name niemals `.` oder `..` lauten kann.
 */
object IconCacheKeys {

    /**
     * Format-Version im Dateinamen. Ändert sich das Speicherformat, findet der
     * Launcher die alten Dateien nicht mehr und legt sie neu an — statt eine
     * halb passende Datei zu lesen.
     */
    const val FORMAT_VERSION = 1

    private const val SAFE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._-"

    /** Die Größe gehört in den Namen: eine geänderte Rastergröße soll eine
     *  eigene Datei bekommen statt einer hochskalierten (siehe
     *  [AppEntry.iconKey]). */
    fun fileName(packageName: String, sizePx: Int): String =
        "v$FORMAT_VERSION-${sizePx}px-${sanitize(packageName)}.webp"

    fun sanitize(raw: String): String = buildString(raw.length) {
        for (character in raw) append(if (character in SAFE_CHARS) character else '_')
    }
}

/**
 * Symbole als kleine WebP-Dateien im app-**internen** Speicher.
 *
 * Warum Platte und nicht nur der LRU-Cache: ein Symbol zu gewinnen heißt, die
 * Ressourcen einer fremden App zu mappen (`getResourcesForApplication`). Genau
 * das erzeugt auf dem Zielgerät die gemessenen ~25 MB „Private Clean"
 * (docs/DEVICE_PERF.md) und kostet bei jedem Prozess-Neustart wieder Zeit. Eine
 * 128-px-WebP-Datei sind ~8 KB; sie zu lesen und zu dekodieren ist ein
 * Dateizugriff ohne Fremd-APK.
 *
 * Warum interner und nicht externer Speicher: `/sdcard` läuft über den
 * MediaProvider-FUSE-Daemon, und `stat`/`open` gehen dort **immer** durch den
 * Daemon — FUSE-Passthrough hilft nur für read/write nach dem Öffnen
 * (source.android.com/docs/core/storage/fuse-passthrough). Für einen Cache, der
 * bei jedem HOME-Druck gelesen wird, ist das der falsche Ort.
 *
 * Der Cache ist jederzeit wegwerfbar: fehlt eine Datei, wird sie im Hintergrund
 * aus den Ressourcen der App neu erzeugt.
 */
class IconDiskCache private constructor(context: Context) {

    private val dir = File(context.filesDir, DIR_NAME)

    private fun fileFor(entry: AppEntry, sizePx: Int): File =
        File(dir, IconCacheKeys.fileName(entry.packageName, sizePx))

    /** Symbol von Platte oder `null`. Läuft im Hintergrund (siehe [IconLoader]). */
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
     * Schreibt das Symbol. Erst in eine temporäre Datei, dann umbenennen: ein
     * abgebrochener Schreibvorgang darf keine halbe Datei hinterlassen, die
     * beim nächsten Start als „Symbol vorhanden" durchgeht.
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
     * Wirft alles weg. Wird gebraucht, wenn sich das Speicherformat ändert
     * ([IconCacheKeys.FORMAT_VERSION]) — sonst sammeln sich Dateien an, die
     * niemand mehr liest.
     */
    fun wipe() {
        try {
            dir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            // Ein nicht löschbarer Cache ist kein Grund, den Launcher zu stören.
        }
    }

    /**
     * Räumt auf, wenn die Format-Version nicht mehr stimmt. Der Merker ist eine
     * Datei im Cache-Ordner selbst — damit braucht der Cache keinen Eintrag in
     * den Launcher-Einstellungen und bleibt in sich geschlossen.
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
            // Ohne Merker wird beim nächsten Start erneut aufgeräumt — harmlos.
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
