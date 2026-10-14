package com.piotv.navigate.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.piotv.navigate.R
import com.piotv.navigate.data.AppEntry

/**
 * App-Liste für die Einstellungen: jede Zeile eine App, Favoriten mit Stern und
 * ihrer Position in der Zeile.
 *
 * Gleiche Bauart wie [AppGridAdapter]: BaseAdapter, View-Recycling über
 * convertView, Symbole aus dem gemeinsamen [IconLoader].
 */
class SettingsAdapter(
    private val context: Context,
    private val iconLoader: IconLoader,
    private val iconSizePx: Int,
) : BaseAdapter() {

    private var items: List<AppEntry> = emptyList()
    private var favorites: List<String> = emptyList()

    fun submit(apps: List<AppEntry>, favoritePackages: List<String>) {
        items = apps
        favorites = favoritePackages
        notifyDataSetChanged()
    }

    fun updateFavorites(favoritePackages: List<String>) {
        favorites = favoritePackages
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): AppEntry = items[position]

    override fun getItemId(position: Int): Long = items[position].packageName.hashCode().toLong()

    override fun hasStableIds(): Boolean = true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_setting_app, parent, false)
        val holder = view.tag as? Holder ?: Holder(
            view.findViewById(R.id.setting_icon),
            view.findViewById(R.id.setting_label),
            view.findViewById(R.id.setting_marker),
        ).also { view.tag = it }

        val entry = items[position]
        holder.label.text = entry.label
        // Der Screenreader liest sonst nur „App-Symbol" (der XML-Rückfall) für
        // jede Zeile. Raster und Dock setzten das schon, die Einstellungen
        // fehlten — von lint als Missing contentDescription gemeldet.
        holder.icon.contentDescription = entry.label

        val order = favorites.indexOf(entry.packageName)
        holder.marker.text = if (order >= 0) {
            context.getString(R.string.settings_favorite_marker, order + 1)
        } else {
            ""
        }

        val key = entry.iconKey(iconSizePx)
        holder.icon.tag = key
        val cached = iconLoader.get(entry, iconSizePx) { loaded ->
            if (holder.icon.tag == key) holder.icon.setImageBitmap(loaded)
        }
        if (cached != null) {
            holder.icon.setImageBitmap(cached)
        } else {
            holder.icon.setImageResource(R.drawable.bg_icon_placeholder)
        }
        return view
    }

    private class Holder(val icon: ImageView, val label: TextView, val marker: TextView)
}
