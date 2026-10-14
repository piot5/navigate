package com.piotv.navigate.data

import android.content.Context
import android.os.SystemClock

/**
 * Kurzlebiger Zustand des Launchers — zwei Werte.
 *
 * Die Favoriten stehen NICHT hier, sondern in der Konfigurationsdatei
 * ([FavoritesConfig]): sie sind eine Einstellung des Nutzers, kein Laufzeitzustand.
 */
class Prefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("navigate", Context.MODE_PRIVATE)

    init {
        // Nach einem Systemstart ist der Merker wertlos: die zuletzt gestartete
        // App läuft nicht mehr. Ohne diese Prüfung würde der erste HOME-Druck
        // nach dem Einschalten die Zeile statt der Home-Seite zeigen.
        val bootMarker = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        if (prefs.getLong(KEY_BOOT_MARKER, -1L) != bootMarker) {
            prefs.edit()
                .putLong(KEY_BOOT_MARKER, bootMarker)
                .putBoolean(KEY_APP_RUNNING, false)
                .apply()
        }
    }

    /**
     * Läuft gerade eine fremde App?
     *
     * Wird über HOME-Drucke hinweg gebraucht und deshalb persistiert: der
     * Launcher-Prozess stirbt auf einem 1,4-GB-Gerät unter Speicherdruck
     * regelmäßig, und nach dem Neustart muss der nächste HOME-Druck trotzdem
     * noch wissen, dass eine App lief.
     */
    var appRunning: Boolean
        get() = prefs.getBoolean(KEY_APP_RUNNING, false)
        set(value) {
            prefs.edit().putBoolean(KEY_APP_RUNNING, value).apply()
        }

    /** Zuletzt über Navigate gestartete App — nur für Diagnose und Erweiterbarkeit. */
    var lastLaunchedPackage: String?
        get() = prefs.getString(KEY_LAST_LAUNCHED, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_LAUNCHED, value).apply()
        }

    private companion object {
        const val KEY_APP_RUNNING = "app_running"
        const val KEY_LAST_LAUNCHED = "last_launched"
        const val KEY_BOOT_MARKER = "boot_marker"
    }
}
