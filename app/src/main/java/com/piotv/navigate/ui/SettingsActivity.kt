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
 * The Navigate settings — operated with the remote control, no dialogs.
 *
 * ```
 * ▲ ▼   select row
 * OK    toggle
 * ◀/BACK  back
 * ```
 *
 * Why a **separate Activity**: the settings are not a state of the home page,
 * and a dialog on top of it would be wiped away by the next press of HOME.
 * Why it is nevertheless not a second HOME Activity: the ambiguity that two
 * HOME filters create (a chooser dialog instead of a direct start) applies
 * only to the HOME intent — this Activity has no intent filter and is started
 * exclusively from the home page (long OK on "…").
 *
 * Every change goes **immediately** into the file (see docs/CONFIG.md) — a
 * "Save" button on a remote control would just be one more source of error.
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

        // The file is shown at the head of the page, not in the docs: it is
        // the answer to "why is the setting different on the other device".
        findViewById<TextView>(R.id.settings_path).text =
            getString(R.string.settings_path_label, store.file.absolutePath)
        val importPath = findViewById<TextView>(R.id.settings_import_path)
        val import = store.importPath
        if (import == null) {
            // Not every device has external storage — then there is no way
            // for `adb push` and nothing to announce either.
            importPath.visibility = View.GONE
        } else {
            importPath.text = getString(R.string.settings_import_path_label, import)
        }

        apply()
    }

    override fun onResume() {
        super.onResume()
        list.requestFocus()
        // May have been changed from outside (`adb push`).
        store.reloadIfChanged()
        apply()
    }

    /** ◀ acts like BACK — on a remote control that is the expected way. */
    private fun onListKey(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
            finish()
            return true
        }
        return false
    }

    /**
     * Toggle. Three rows, each with its own rule:
     *
     *  - **Now Playing** and **Clock** are switches (on/off),
     *  - the **background** keeps cycling: Dark → Image → Focus → Dark.
     *    A chooser dialog would be the detour on a remote control; OK is the
     *    one key you can hit blind, and three values fit in a single row
     *    ("Dark", "Image", "Focus").
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

    /** The next value in the cycle — the last one leads back to [BackgroundStyle.DARK]. */
    private fun nextBackground(style: BackgroundStyle): BackgroundStyle = when (style) {
        BackgroundStyle.DARK -> BackgroundStyle.IMAGE
        BackgroundStyle.IMAGE -> BackgroundStyle.FOCUS
        BackgroundStyle.FOCUS -> BackgroundStyle.DARK
    }

    /**
     * Shows the state — and, if necessary, **why** the field will stay empty.
     * A setting you can switch on and that then does nothing would be the
     * most unpleasant kind of setting.
     */
    private fun apply() {
        val settings = store.current()
        adapter.submit(settings)
        warning.visibility =
            if (settings.nowPlaying && !nowPlaying.hasAccess()) View.VISIBLE else View.GONE
        applyBackgroundPath(settings)
    }

    /**
     * Where the background file belongs — and whether it is there.
     *
     * The same honesty as with [warning]: "Image" without a file would be a
     * setting that does nothing. The path is shown here because only `adb push`
     * can fill it (the internal storage of a release app is not writable
     * without root).
     */
    private fun applyBackgroundPath(settings: NavigateSettings) {
        // The external app folder is the folder where the importable
        // `settings.conf` lives too — the same place, the same mechanism.
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
     * The list has three entries (two switches and the background type), yet
     * the adapter is still one: this makes the fourth setting a row, not a
     * rebuild.
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
                            BackgroundStyle.FOCUS -> R.string.background_focus
                        }
                    )
                }
            }
            return view
        }

        private companion object {
            /** Now Playing, Clock, Background — the order of the list. */
            const val COUNT = 3
        }
    }
}
