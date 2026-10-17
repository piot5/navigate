package com.piotv.navigate.ui

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import com.piotv.navigate.R
import com.piotv.navigate.data.NavigateSettings
import com.piotv.navigate.data.NowPlaying
import com.piotv.navigate.data.SettingsStore

/**
 * Die Navigate-Einstellungen — mit der Fernbedienung bedient, ohne Dialoge.
 *
 * ```
 * ▲ ▼   Zeile wählen
 * OK    umschalten
 * ◀/BACK  zurück
 * ```
 *
 * Warum eine **eigene Activity**: die Einstellungen sind kein Zustand der
 * Home-Seite, und ein Dialog über ihr würde beim nächsten HOME-Druck weggewischt.
 * Warum das trotzdem keine zweite HOME-Activity ist: die Mehrdeutigkeit, die zwei
 * HOME-Filter erzeugen (Auswahldialog statt Direktstart), gilt nur für den
 * HOME-Intent — diese Activity hat keinen Intent-Filter und wird ausschließlich
 * aus der Home-Seite gestartet (langes OK auf „…“).
 *
 * Jede Änderung geht **sofort** in die Datei (siehe docs/CONFIG.md) — ein
 * „Speichern“-Knopf wäre auf einer Fernbedienung nur eine zusätzliche
 * Fehlerquelle.
 */
class SettingsActivity : Activity() {

    private lateinit var list: ListView
    private lateinit var warning: TextView

    private lateinit var store: SettingsStore
    private lateinit var nowPlaying: NowPlaying
    private lateinit var adapter: SettingsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        store = SettingsStore.get(this)
        nowPlaying = NowPlaying(this)

        list = findViewById(R.id.settings_list)
        warning = findViewById(R.id.settings_warning)

        adapter = SettingsAdapter()
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> toggle(position) }
        list.setOnKeyListener { _, keyCode, event -> onListKey(keyCode, event) }

        // Die Datei steht im Kopf der Seite, nicht in der Doku: sie ist die
        // Antwort auf „warum ist die Einstellung auf dem anderen Gerät anders“.
        findViewById<TextView>(R.id.settings_path).text =
            getString(R.string.settings_path_label, store.file.absolutePath)
        val importPath = findViewById<TextView>(R.id.settings_import_path)
        val import = store.importPath
        if (import == null) {
            // Nicht jedes Gerät hat externen Speicher — dann gibt es keinen Weg
            // für `adb push` und auch nichts anzukündigen.
            importPath.visibility = View.GONE
        } else {
            importPath.text = getString(R.string.settings_import_path_label, import)
        }

        apply()
    }

    override fun onResume() {
        super.onResume()
        list.requestFocus()
        // Kann sich von außen geändert haben (`adb push`).
        store.reloadIfChanged()
        apply()
    }

    /** ◀ wirkt wie BACK — auf einer Fernbedienung ist das der erwartete Weg. */
    private fun onListKey(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
            finish()
            return true
        }
        return false
    }

    /** Umschalten. Heute gibt es genau eine Zeile — Position 0. */
    private fun toggle(position: Int) {
        if (position != 0) return
        val current = store.current()
        store.save(current.copy(nowPlaying = !current.nowPlaying))
        apply()
    }

    /**
     * Zeigt den Stand — und, falls nötig, **warum** das Feld leer bleiben wird.
     * Eine Einstellung, die man einschalten kann und die dann nichts tut, wäre
     * die unangenehmste Sorte Einstellung.
     */
    private fun apply() {
        val settings = store.current()
        adapter.submit(settings)
        warning.visibility =
            if (settings.nowPlaying && !nowPlaying.hasAccess()) View.VISIBLE else View.GONE
    }

    /**
     * Die Liste hat genau einen Eintrag (die Einstellung), der Adapter ist
     * trotzdem einer: die zweite Einstellung ist damit eine Zeile, kein Umbau.
     */
    private class SettingsAdapter : BaseAdapter() {

        private var settings = NavigateSettings()

        fun submit(value: NavigateSettings) {
            if (settings == value) return
            settings = value
            notifyDataSetChanged()
        }

        override fun getCount(): Int = 1

        override fun getItem(position: Int): Any = settings

        override fun getItemId(position: Int): Long = 0L

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_setting, parent, false)
            view.findViewById<TextView>(R.id.setting_label).setText(R.string.settings_now_playing)
            view.findViewById<TextView>(R.id.setting_state)
                .setText(if (settings.nowPlaying) R.string.settings_on else R.string.settings_off)
            return view
        }
    }
}
