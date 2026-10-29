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
import com.piotv.navigate.data.BackgroundPlan
import com.piotv.navigate.data.BackgroundStyle
import com.piotv.navigate.data.NavigateSettings
import com.piotv.navigate.data.NowPlaying
import com.piotv.navigate.data.SettingsStore
import java.io.File

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
    private lateinit var backgroundPath: TextView

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
        backgroundPath = findViewById(R.id.settings_background_path)

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

    /**
     * Umschalten. Drei Zeilen, jede mit ihrer eigenen Regel:
     *
     *  - **Now Playing** und **Uhr** sind Schalter (an/aus),
     *  - der **Hintergrund** läuft weiter: Dunkel → Bild → Dunkel.
     *    Ein Auswahldialog wäre auf einer Fernbedienung der Umweg; OK ist die eine
     *    Taste, die man blind trifft, und zwei Werte lassen sich in einer Zeile
     *    zeigen („Dunkel“, „Bild“).
     */
    private fun toggle(position: Int) {
        val current = store.current()
        val next = when (position) {
            0 -> current.copy(nowPlaying = !current.nowPlaying)
            1 -> current.copy(showClock = !current.showClock)
            2 -> current.copy(background = nextBackground(current.background))
            else -> return
        }
        store.save(next)
        apply()
    }

    /** Der nächste Wert im Kreis — der letzte führt zurück auf [BackgroundStyle.DARK]. */
    private fun nextBackground(style: BackgroundStyle): BackgroundStyle = when (style) {
        BackgroundStyle.DARK -> BackgroundStyle.IMAGE
        BackgroundStyle.IMAGE -> BackgroundStyle.DARK
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
        applyBackgroundPath(settings)
    }

    /**
     * Wohin die Hintergrund-Datei gehört — und ob sie da ist.
     *
     * Dieselbe Ehrlichkeit wie bei [warning]: „Bild“ ohne Datei wäre eine
     * Einstellung, die nichts tut. Der Pfad steht hier, weil ihn nur `adb push`
     * füllen kann (der interne Speicher einer Release-App ist ohne Root nicht
     * beschreibbar).
     */
    private fun applyBackgroundPath(settings: NavigateSettings) {
        // Der externe App-Ordner ist der Ordner, in dem auch die importierbare
        // `settings.conf` liegt — dieselbe Stelle, dieselbe Mechanik.
        val externalDir = store.importPath?.let { File(it).parent }
        val file = BackgroundPlan.file(
            style = settings.background,
            configured = settings.backgroundFile,
            externalDir = externalDir,
        )
        if (file == null) {
            backgroundPath.visibility = View.GONE
            return
        }
        backgroundPath.visibility = View.VISIBLE
        backgroundPath.text = if (file.isFile) {
            getString(R.string.settings_background_path_label, file.absolutePath)
        } else {
            getString(R.string.settings_background_missing) + "\n" + file.absolutePath
        }
    }

    /**
     * Die Liste hat drei Einträge (zwei Schalter und die Hintergrund-Art), der
     * Adapter ist trotzdem einer: die vierte Einstellung ist damit eine Zeile,
     * kein Umbau.
     */
    private class SettingsAdapter : BaseAdapter() {

        private var settings = NavigateSettings()

        fun submit(value: NavigateSettings) {
            if (settings == value) return
            settings = value
            notifyDataSetChanged()
        }

        override fun getCount(): Int = COUNT

        override fun getItem(position: Int): Any = settings

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_setting, parent, false)
            val label = view.findViewById<TextView>(R.id.setting_label)
            val state = view.findViewById<TextView>(R.id.setting_state)
            when (position) {
                0 -> {
                    label.setText(R.string.settings_now_playing)
                    state.setText(if (settings.nowPlaying) R.string.settings_on else R.string.settings_off)
                }
                1 -> {
                    label.setText(R.string.settings_clock)
                    state.setText(if (settings.showClock) R.string.settings_on else R.string.settings_off)
                }
                else -> {
                    label.setText(R.string.settings_background)
                    state.setText(
                        when (settings.background) {
                            BackgroundStyle.DARK -> R.string.background_dark
                            BackgroundStyle.IMAGE -> R.string.background_image
                        }
                    )
                }
            }
            return view
        }

        private companion object {
            /** Now Playing, Uhr, Hintergrund — die Reihenfolge der Liste. */
            const val COUNT = 3
        }
    }
}
