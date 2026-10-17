package com.piotv.navigate.data

import java.io.File

/**
 * Eine Konfigurationsdatei an zwei Orten — mit einem Stand im Speicher.
 *
 * Der Code stand bis 0.11 in [OrderStore] und gilt dort unverändert weiter; seit
 * es eine zweite Datei gibt ([SettingsStore]), liegt er **einmal** hier. Der
 * Grund ist nicht „Zeilen sparen“: genau in dieser Mechanik saß der Fehler, den
 * `OrderStoreTest` in 0.4 gefunden hat — der Merker der Import-Drossel stand auf
 * `Long.MIN_VALUE`, `now - Long.MIN_VALUE` lief über und wurde negativ, die
 * Drossel griff damit **immer**, der Import fand nie statt. Zwei Kopien wären
 * zwei Chancen, ihn wieder einzubauen.
 *
 * ## Zwei Orte, ein Stand
 *
 * - **Gelesen und geschrieben wird app-intern** ([file]). Der externe Speicher
 *   wäre naheliegend, aber `/sdcard` läuft über den MediaProvider-FUSE-Daemon:
 *   `stat` und `open` gehen dort **immer** durch einen Userspace-Daemon
 *   (FUSE-Passthrough hilft nur für read/write nach dem Öffnen,
 *   source.android.com/docs/core/storage/fuse-passthrough). Diese Dateien werden
 *   bei jedem `onResume` angesehen — auf dem Zielgerät wäre das messbare Zeit.
 * - **Der Import-Pfad** ([importPath]) ist der Weg für `adb push`: in den
 *   internen Speicher einer Release-App kommt ADB ohne Root nicht hinein. Eine
 *   dort **neuere** Datei wird **einmal** übernommen und danach intern gehalten —
 *   sonst würde eine alte, einmal gepushte Datei jede Änderung auf dem Gerät
 *   wieder zurückdrehen.
 *
 * @param parse Aus dem Dateitext den Wert lesen; `null` heißt „keine Datei“.
 * @param serialize Den Wert als Dateitext schreiben.
 */
class ConfigFile<T> internal constructor(
    /** Quelle der Wahrheit: app-intern, kein FUSE. */
    private val internalFile: File,
    /** Import-Pfad für `adb push`; `null`, wenn das Gerät keinen hat. */
    private val importFile: File?,
    /**
     * Uhr für die Import-Drossel — injiziert, damit der Import ohne Warten
     * prüfbar ist. Im Betrieb ist das `SystemClock.elapsedRealtime`.
     */
    private val elapsedRealtime: () -> Long,
    private val parse: (String?) -> T,
    private val serialize: (T) -> String,
) {

    private var cached: T = parse(null)
    private var cachedModifiedAt = Long.MIN_VALUE
    private var lastImportAt = Long.MIN_VALUE

    /**
     * Laufzeituhr des letzten Nachsehens am Import-Pfad.
     *
     * Der Startwert ist **nicht** `Long.MIN_VALUE`: `now - Long.MIN_VALUE` läuft
     * über und wird negativ, die Bedingung „innerhalb des Intervalls“ wäre damit
     * immer wahr — der Import hätte nie stattgefunden. Genau das war bis 0.4 der
     * Fall (gefunden von OrderStoreTest, siehe CHANGELOG). Ein negativer Wert in
     * der Größe des Intervalls heißt „lange her“ und kann nicht überlaufen.
     */
    private var lastImportCheckAt = -IMPORT_CHECK_INTERVAL_MS

    /** Die Datei, die Navigate liest und schreibt (app-intern). */
    val file: File get() = internalFile

    /** Für Diagnose und Doku: der Weg für `adb push`. */
    val importPath: String? get() = importFile?.absolutePath

    init {
        loadInternal()
        // Beim Start einmal prüfen: so wird eine hinterlassene externe Datei
        // übernommen, ohne dass jemand etwas tun muss.
        importIfNewer()
    }

    /**
     * Aktueller Stand — **ohne Dateizugriff**.
     *
     * Der Launcher liest die Konfiguration bei jedem Aufbau des Rasters. Jeder
     * dieser Aufrufe machte vorher ein `stat` auf den externen Speicher — also
     * einen FUSE-Roundtrip. Der Stand liegt jetzt im Speicher;
     * [reloadIfChanged] ist die einzige Stelle, die nachsieht.
     */
    fun current(): T = cached

    /**
     * Sieht nach, ob sich die Datei geändert hat, und übernimmt sie dann.
     *
     * @return `true`, wenn ein neuer Stand gilt (dann muss der Aufrufer seine
     *   Anzeige neu aufbauen).
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
            // Kein schreibbarer Speicher: die Änderung gilt dann nur für diese
            // Sitzung, statt den Launcher abstürzen zu lassen.
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
     * Übernimmt eine per `adb push` neuere Datei aus dem externen Speicher.
     *
     * Übernommen wird nur, wenn der Zeitstempel **neuer** ist als beim letzten
     * Import — sonst würde eine alte, einmal gepushte Datei jede Änderung auf
     * dem Gerät wieder zurückdrehen.
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
         * Der Import-Pfad wird höchstens alle 5 Sekunden angesehen. Häufiger
         * wäre er ein FUSE-Zugriff auf dem HOME-Pfad — genau das, was der
         * interne Speicher vermeiden soll —, und seltener würde ein `adb push`
         * gefühlt nicht ankommen.
         */
        internal const val IMPORT_CHECK_INTERVAL_MS = 5_000L
    }
}
