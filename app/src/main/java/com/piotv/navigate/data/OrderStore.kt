package com.piotv.navigate.data

import android.content.Context
import android.os.SystemClock
import java.io.File

/**
 * Die Reihenfolge-Datei (`files/navigate.conf`) — der Stand, den die Home-Seite
 * bei jedem Aufbau des Rasters liest.
 *
 * Die Datei-Mechanik (intern lesen und schreiben, `adb push` über den externen
 * Import-Pfad, gedrosseltes Nachsehen) steht in [ConfigFile] — dieselbe wie bei
 * den Einstellungen ([SettingsStore]). Hier steht nur, **was** in der Datei
 * steht: eine Paketnamen-Zeile pro Eintrag, gelesen von [OrderCodec].
 */
class OrderStore internal constructor(
    internalFile: File,
    importFile: File?,
    elapsedRealtime: () -> Long,
) {

    /**
     * Die Datei-Mechanik (zwei Orte, Drossel, Cache) liegt seit 0.12 in
     * [ConfigFile] — dieselbe wie bei den Einstellungen ([SettingsStore]), und in
     * ihr saß der Fehler, den `OrderStoreTest` in 0.4 gefunden hat.
     */
    private val config = ConfigFile<List<String>>(
        internalFile = internalFile,
        importFile = importFile,
        elapsedRealtime = elapsedRealtime,
        parse = OrderCodec::parse,
        serialize = OrderCodec::serialize,
    )

    /** Die Datei, die Navigate liest und schreibt (app-intern). */
    val file: File get() = config.file

    /** Für Diagnose und Doku: der Weg für `adb push`. */
    val importPath: String? get() = config.importPath

    /**
     * Aktueller Stand — **ohne Dateizugriff**.
     *
     * Der Launcher liest die Reihenfolge bei jedem Aufbau des Rasters. Jeder
     * dieser Aufrufe machte vorher ein `stat` auf den externen Speicher — also
     * einen FUSE-Roundtrip. Der Stand liegt jetzt im Speicher;
     * [reloadIfChanged] ist die einzige Stelle, die nachsieht.
     */
    fun current(): List<String> = config.current()

    /**
     * Sieht nach, ob sich die Datei geändert hat, und übernimmt sie dann.
     *
     * @return `true`, wenn ein neuer Stand gilt (dann muss der Aufrufer das
     *   Raster neu aufbauen).
     */
    fun reloadIfChanged(): Boolean = config.reloadIfChanged()

    /**
     * Schiebt ein Paket eine Position nach vorne (siehe [Order.moveForward]) und
     * schreibt das Ergebnis sofort.
     *
     * @return `true`, wenn sich etwas geändert hat. Steht das Paket schon ganz
     *   vorne, bleibt die Datei unangetastet.
     */
    fun moveForward(packageName: String): Boolean {
        val next = Order.moveForward(config.current(), packageName)
        if (next == config.current()) return false
        save(next)
        return true
    }

    /** Wie [moveForward], nur eine Position nach hinten (Kontextmenü). */
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
         * Der Import-Pfad wird höchstens alle 5 Sekunden angesehen — die Zahl
         * steht bei [ConfigFile.IMPORT_CHECK_INTERVAL_MS], wo die Drossel sitzt.
         */
        internal const val IMPORT_CHECK_INTERVAL_MS = ConfigFile.IMPORT_CHECK_INTERVAL_MS

        @Volatile
        private var instance: OrderStore? = null

        fun get(context: Context): OrderStore =
            instance ?: synchronized(this) {
                instance ?: create(context).also { instance = it }
            }

        /**
         * Der Produktivpfad: interner Speicher plus Import-Pfad plus die Uhr des
         * Systems. Getrennt von [get], damit der Konstruktor für Tests frei
         * bleibt (OrderStoreTest) — ohne Context, ohne Gerät.
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
