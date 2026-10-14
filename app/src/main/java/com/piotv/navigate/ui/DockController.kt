package com.piotv.navigate.ui

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.piotv.navigate.R
import com.piotv.navigate.data.AppEntry
import kotlin.math.roundToInt

/**
 * Baut die untere Zeile: Favoriten, Trenner, Home, Einstellungen.
 *
 * Aufbau in Kotlin statt in XML, weil der Inhalt datenabhängig ist (Favoriten)
 * und die beiden Aktions-Elemente exakt wie ein Favorit aussehen und sich so
 * verhalten sollen — ein Layout, drei Verwendungen.
 *
 * Der Aufbau wird über eine Signatur zwischengespeichert: bei jedem HOME-Druck
 * dieselbe Zeile neu zu inflaten wäre 9 × Layout-Inflation für nichts.
 */
class DockController(
    private val context: Context,
    private val row: LinearLayout,
    private val iconLoader: IconLoader,
    private val dockIconSizePx: Int,
) {

    private val inflater = LayoutInflater.from(context)
    private var signature: String? = null

    /**
     * @return `true`, wenn die Zeile neu aufgebaut wurde (dann sollte der Fokus
     *   neu gesetzt werden).
     */
    fun build(
        favorites: List<AppEntry>,
        onOpenApp: (AppEntry) -> Unit,
        onRemoveFavorite: (AppEntry) -> Unit,
        onOpenHome: () -> Unit,
        onOpenNavigateSettings: () -> Unit,
        onOpenSettings: () -> Unit,
        onSettingsLongPress: () -> Unit,
    ): Boolean {
        val newSignature = favorites.joinToString(",") { it.packageName } + "@" + dockIconSizePx
        if (newSignature == signature) return false
        signature = newSignature

        row.removeAllViews()
        for (entry in favorites) {
            row.addView(appItem(entry, onOpenApp, onRemoveFavorite))
        }
        if (favorites.isNotEmpty()) {
            row.addView(divider())
        }
        row.addView(
            actionItem(
                iconRes = R.drawable.ic_home,
                label = context.getString(R.string.dock_home),
                onClick = onOpenHome,
                onLongClick = null,
            )
        )
        // Die eigenen Einstellungen (Favoritenzeile) sitzen neben Home; die
        // Systemeinstellungen bleiben ganz rechts.
        row.addView(
            actionItem(
                iconRes = R.drawable.ic_tune,
                label = context.getString(R.string.dock_navigate_settings),
                onClick = onOpenNavigateSettings,
                onLongClick = null,
            )
        )
        row.addView(
            actionItem(
                iconRes = R.drawable.ic_settings,
                label = context.getString(R.string.dock_settings),
                onClick = onOpenSettings,
                onLongClick = onSettingsLongPress,
            )
        )
        return true
    }

    /** Fokus auf das erste Element — bei einem HOME-Druck muss sofort klar sein,
     *  wo man ist. */
    fun focusFirst(): Boolean {
        for (index in 0 until row.childCount) {
            val child = row.getChildAt(index)
            if (child.isFocusable && child.visibility == View.VISIBLE) {
                return child.requestFocus()
            }
        }
        return false
    }

    private fun appItem(
        entry: AppEntry,
        onClick: (AppEntry) -> Unit,
        onLongClick: (AppEntry) -> Unit,
    ): View {
        val view = inflater.inflate(R.layout.item_dock, row, false)
        val icon = view.findViewById<ImageView>(R.id.dock_icon)
        val label = view.findViewById<TextView>(R.id.dock_label)

        label.text = entry.label
        icon.contentDescription = entry.label

        val key = entry.iconKey(dockIconSizePx)
        icon.tag = key
        val cached = iconLoader.get(entry, dockIconSizePx) { loaded ->
            if (icon.tag == key) icon.setImageBitmap(loaded)
        }
        if (cached != null) {
            icon.setImageBitmap(cached)
        } else {
            icon.setImageResource(R.drawable.bg_icon_placeholder)
        }

        view.setOnClickListener { onClick(entry) }
        // Lange drücken entfernt den Favoriten — mehr Bedienung braucht das
        // Dock nicht, und ein Kontextmenü wäre auf TV ein Fremdkörper.
        view.setOnLongClickListener {
            onLongClick(entry)
            true
        }
        view.nextFocusUpId = R.id.grid
        return view
    }

    private fun actionItem(
        iconRes: Int,
        label: String,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)?,
    ): View {
        val view = inflater.inflate(R.layout.item_dock, row, false)
        view.findViewById<ImageView>(R.id.dock_icon).setImageResource(iconRes)
        view.findViewById<TextView>(R.id.dock_label).text = label
        view.setOnClickListener { onClick() }
        if (onLongClick != null) {
            view.setOnLongClickListener {
                onLongClick()
                true
            }
        }
        view.nextFocusUpId = R.id.grid
        return view
    }

    private fun divider(): View {
        val view = View(context)
        val margin = dp(8)
        val params = LinearLayout.LayoutParams(dp(1), dp(48))
        params.marginStart = margin
        params.marginEnd = margin
        params.gravity = Gravity.CENTER_VERTICAL
        view.layoutParams = params
        view.setBackgroundColor(context.getColor(R.color.dock_divider))
        return view
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
}
