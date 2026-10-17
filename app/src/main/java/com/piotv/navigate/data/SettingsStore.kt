package com.piotv.navigate.data

import android.content.Context
import android.os.SystemClock
import java.io.File

/**
 * Die Einstellungsdatei (`files/settings.conf`) als Datei — dieselbe Mechanik wie
 * [OrderStore], nur mit einem anderen Inhalt: zwei Orte, ein Stand ([ConfigFile]).
 *
 * Warum eine eigene Datei und nicht `SharedPreferences`: eine Einstellung des
 * Nutzers gehört dorthin, wo sie lesbar ist und per `adb push` hinkommt. Und
 * warum nicht in `navigate.conf`: die Reihenfolge-Datei ist eine
 * Paketnamen-Liste — eine `schlüssel=wert`-Zeile darin würde von [OrderCodec] als
 * Paketname gelesen. Zwei Dateien, zwei Formate, keine Sonderfälle.
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

    /** Die Datei, die Navigate liest und schreibt (app-intern). */
    val file: File get() = config.file

    /** Für Diagnose und Doku: der Weg für `adb push`. */
    val importPath: String? get() = config.importPath

    /** Der aktuelle Stand — ohne Dateizugriff. */
    fun current(): NavigateSettings = config.current()

    /** Sieht nach, ob sich die Datei geändert hat (`adb push`), und übernimmt sie. */
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
         * Der Produktivpfad: interner Speicher plus Import-Pfad plus die Uhr des
         * Systems. Getrennt von [get], damit der Konstruktor für Tests frei
         * bleibt (SettingsStoreTest) — ohne Context, ohne Gerät.
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
