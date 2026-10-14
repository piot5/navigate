package com.piotv.navigate.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ListView
import android.widget.TextView
import com.piotv.navigate.R
import com.piotv.navigate.data.AppEntry
import com.piotv.navigate.data.AppRepository
import com.piotv.navigate.data.FavoritesCodec
import com.piotv.navigate.data.FavoritesConfig
import com.piotv.navigate.util.shortToast

/**
 * Navigate-Einstellungen — in dieser Ausbaustufe genau eine Aufgabe: die
 * Favoritenzeile.
 *
 * Bedienung mit der Fernbedienung, ohne Dialoge:
 *   ▲ ▼     Zeile wählen
 *   OK      Favorit an/aus
 *   ◀ ▶     Favorit in der Zeile verschieben
 *   ◀/BACK  zurück
 *
 * Jede Änderung geht sofort in die Konfigurationsdatei (siehe docs/CONFIG.md) —
 * ein "Speichern"-Knopf wäre auf einer Fernbedienung nur eine zusätzliche
 * Fehlerquelle.
 */
class SettingsActivity : Activity() {

    private lateinit var list: ListView
    private lateinit var counter: TextView
    private lateinit var pathView: TextView
    private lateinit var importPathView: TextView

    private lateinit var config: FavoritesConfig
    private lateinit var repository: AppRepository
    private lateinit var iconLoader: IconLoader

    private var adapter: SettingsAdapter? = null
    private var apps: List<AppEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        config = FavoritesConfig.get(this)
        repository = AppRepository.get(this)
        iconLoader = IconLoader.get(this)

        list = findViewById(R.id.settings_list)
        counter = findViewById(R.id.settings_counter)
        pathView = findViewById(R.id.settings_path)
        importPathView = findViewById(R.id.settings_import_path)

        pathView.text = getString(R.string.settings_path_label, config.path)
        // Der Import-Pfad steht nur da, wo es einen gibt (nicht jedes Gerät hat
        // externen Speicher). Er ist der Weg für `adb push`; die interne Datei
        // kann ADB ohne Root nicht schreiben.
        val importPath = config.importPath
        if (importPath == null) {
            importPathView.visibility = View.GONE
        } else {
            importPathView.text = getString(R.string.settings_import_path_label, importPath)
        }

        list.setOnItemClickListener { _, _, position, _ ->
            apps.getOrNull(position)?.let(::toggleFavorite)
        }
        list.setOnKeyListener { _, keyCode, event -> onListKey(keyCode, event) }

        loadApps()
    }

    override fun onResume() {
        super.onResume()
        list.requestFocus()
    }

    private fun loadApps() {
        repository.refresh(force = false) { loaded ->
            apps = loaded
            val newAdapter = SettingsAdapter(this, iconLoader, resources.getDimensionPixelSize(R.dimen.setting_icon))
            newAdapter.submit(loaded, config.current().favorites)
            list.adapter = newAdapter
            adapter = newAdapter
            updateCounter()
            list.requestFocus()
        }
    }

    private fun toggleFavorite(entry: AppEntry) {
        val current = config.current()
        val isFavorite = entry.packageName in current.favorites
        if (!isFavorite && FavoritesCodec.isFull(current.favorites, current.maxFavorites)) {
            shortToast(getString(R.string.toast_favorites_full, current.maxFavorites))
            return
        }
        config.update { it.copy(favorites = FavoritesCodec.toggle(it.favorites, entry.packageName)) }
        refreshMarkers()
        shortToast(
            if (isFavorite) R.string.toast_favorite_removed else R.string.toast_favorite_added
        )
    }

    private fun onListKey(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val offset = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -1
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1
            else -> return false
        }
        val entry = apps.getOrNull(list.selectedItemPosition) ?: return false
        val current = config.current()
        // Nur Favoriten haben eine Position in der Zeile.
        if (entry.packageName !in current.favorites) return false
        val moved = FavoritesCodec.move(current.favorites, entry.packageName, offset)
        if (moved != current.favorites) {
            config.update { it.copy(favorites = moved) }
            refreshMarkers()
        }
        // Tastendruck bleibt hier: die Liste soll den Fokus nicht weggeben.
        return true
    }

    private fun refreshMarkers() {
        adapter?.updateFavorites(config.current().favorites)
        updateCounter()
    }

    private fun updateCounter() {
        val current = config.current()
        counter.text = getString(
            R.string.settings_counter,
            current.favorites.size,
            current.maxFavorites,
        )
    }
}
