package com.piotv.navigate.data

import android.content.Context
import android.os.SystemClock
import java.io.File

/**
 * Die Konfigurationsdatei als Datei — Quelle der Wahrheit für die Favoriten.
 *
 * ## Zwei Orte, ein Stand
 *
 * Geschrieben und gelesen wird **app-intern** (`files/navigate.conf`). Der
 * externe Speicher war der naheliegende Ort — er ist per `adb push` erreichbar
 * und braucht keine Berechtigung —, aber `/sdcard` läuft über den
 * MediaProvider-FUSE-Daemon: `stat` und `open` gehen dort **immer** durch einen
 * Userspace-Daemon (FUSE-Passthrough hilft nur für read/write nach dem Öffnen,
 * source.android.com/docs/core/storage/fuse-passthrough). Die Konfiguration
 * wurde bei jedem HOME-Druck mehrfach geprüft — auf dem Zielgerät ist genau das
 * messbar.
 *
 * Der externe Ort bleibt trotzdem bestehen: [importPath] ist der Weg für
 * `adb push` (in den internen Speicher einer Release-App kommt ADB ohne Root
 * nicht hinein). Eine dort neuere Datei wird **einmal** übernommen und danach
 * intern gehalten — der Import wird gedrosselt geprüft
 * ([IMPORT_CHECK_INTERVAL_MS]), nicht bei jedem Zugriff.
 *
 * Der Prozess-Singleton sorgt dafür, dass die Einstellungen (gleicher Prozess)
 * und die Leiste denselben Stand sehen.
 */
class FavoritesConfig private constructor(context: Context) {

    private val appContext = context.applicationContext

    /** Quelle der Wahrheit: app-intern, kein FUSE. */
    private val internalFile = File(appContext.filesDir, FILE_NAME)

    /** Import-Pfad für `adb push`; `null`, wenn das Gerät keinen hat. */
    private val importFile: File? =
        appContext.getExternalFilesDir(null)?.let { File(it, FILE_NAME) }

    private var cached: ConfigCodec.Config = ConfigCodec.default()
    private var cachedModifiedAt = Long.MIN_VALUE
    private var lastImportAt = Long.MIN_VALUE
    private var lastImportCheckAt = Long.MIN_VALUE

    /** Die Datei, die Navigate liest und schreibt (app-intern). */
    val file: File get() = internalFile

    /** Für die Anzeige in den Einstellungen. */
    val path: String get() = internalFile.absolutePath

    /** Für die Anzeige in den Einstellungen: der Weg für `adb push`. */
    val importPath: String? get() = importFile?.absolutePath

    init {
        loadInternal()
        // Beim Start einmal prüfen: so wird eine in 0.1 hinterlassene externe
        // Datei übernommen, ohne dass jemand etwas tun muss.
        importIfNewer()
    }

    /**
     * Aktueller Stand — **ohne Dateizugriff**.
     *
     * Der Launcher liest die Konfiguration bei jedem HOME-Druck mehrfach (Uhr,
     * Spaltenzahl, Favoritenzeile). Jeder dieser Aufrufe machte vorher ein
     * `stat` auf den externen Speicher — also einen FUSE-Roundtrip. Der Stand
     * liegt jetzt im Speicher; [reloadIfChanged] ist die einzige Stelle, die
     * nachsieht.
     */
    fun current(): ConfigCodec.Config = cached

    /**
     * Prüft, ob sich der Stand geändert hat: erst die interne Datei (billiges
     * `stat`, kein FUSE), dann — gedrosselt — der Import-Pfad.
     *
     * Das ist der Weg, auf dem eine von Hand geänderte Datei ankommt, ohne dass
     * Navigate einen Dateibeobachter braucht.
     *
     * @return `true`, wenn der Stand neu eingelesen wurde. Nur dann muss das
     *   Raster bzw. die Zeile neu gebaut werden.
     */
    fun reloadIfChanged(): Boolean {
        val modifiedAt = if (internalFile.isFile) internalFile.lastModified() else 0L
        if (modifiedAt != cachedModifiedAt) {
            loadInternal()
            return true
        }
        return importIfNewer()
    }

    /** Liest die interne Datei. Fehlt oder klemmt sie, gelten die Standardwerte. */
    private fun loadInternal() {
        cached = if (internalFile.isFile) {
            try {
                ConfigCodec.parse(internalFile.readText())
            } catch (e: Exception) {
                ConfigCodec.default()
            }
        } else {
            ConfigCodec.default()
        }
        cachedModifiedAt = if (internalFile.isFile) internalFile.lastModified() else 0L
    }

    /**
     * Übernimmt eine per `adb push` neuere Datei aus dem externen Speicher.
     *
     * Übernommen wird nur, wenn der Zeitstempel **neuer** ist als beim letzten
     * Import — sonst würde eine alte, einmal gepushte Datei jede Änderung aus
     * den Navigate-Einstellungen wieder überschreiben.
     */
    private fun importIfNewer(): Boolean {
        val source = importFile ?: return false
        val now = SystemClock.elapsedRealtime()
        if (now - lastImportCheckAt < IMPORT_CHECK_INTERVAL_MS) return false
        lastImportCheckAt = now

        if (!source.isFile) return false
        val modifiedAt = source.lastModified()
        if (modifiedAt <= lastImportAt) return false
        lastImportAt = modifiedAt

        val parsed = try {
            ConfigCodec.parse(source.readText())
        } catch (e: Exception) {
            return false
        }
        if (parsed == cached) return false
        return save(parsed)
    }

    fun save(config: ConfigCodec.Config): Boolean {
        return try {
            internalFile.parentFile?.mkdirs()
            internalFile.writeText(ConfigCodec.serialize(config))
            cached = config
            cachedModifiedAt = internalFile.lastModified()
            true
        } catch (e: Exception) {
            // Kein schreibbarer Speicher: die Änderung gilt dann nur für diese
            // Sitzung, statt die Einstellungen abstürzen zu lassen.
            cached = config
            false
        }
    }

    /** Ändert den Stand und schreibt ihn sofort. */
    fun update(transform: (ConfigCodec.Config) -> ConfigCodec.Config): ConfigCodec.Config {
        val next = transform(current())
        save(next)
        return next
    }

    companion object {
        private const val FILE_NAME = "navigate.conf"

        /**
         * Der Import-Pfad wird höchstens alle 5 Sekunden angesehen. Häufiger
         * wäre er ein FUSE-Zugriff auf dem HOME-Pfad — genau das, was der
         * interne Speicher vermeiden soll —, und seltener würde ein `adb push`
         * gefühlt nicht ankommen.
         */
        private const val IMPORT_CHECK_INTERVAL_MS = 5_000L

        @Volatile
        private var instance: FavoritesConfig? = null

        fun get(context: Context): FavoritesConfig =
            instance ?: synchronized(this) {
                instance ?: FavoritesConfig(context.applicationContext).also { instance = it }
            }
    }
}
