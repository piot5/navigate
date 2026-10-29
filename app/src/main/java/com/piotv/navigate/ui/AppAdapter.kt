package com.piotv.navigate.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.piotv.navigate.R
import com.piotv.navigate.data.AppEntry

/**
 * Bindet die Apps ins Raster.
 *
 * `limit` ist der ganze Trick hinter „nur die erste Zeile“: der Adapter meldet
 * dann nur die ersten `limit` Einträge — eine Zeile —, und die GridView hat
 * nichts zu scrollen. Beim Aufklappen fällt das Limit weg (`0`) und alle Apps
 * kommen dazu: es wird nichts umgebaut, nur mehr gemeldet.
 *
 * BaseAdapter + convertView: GridView ruft `getView` nur für sichtbare Zellen
 * auf (bei 6 Spalten und 4 Zeilen also ~24 statt aller Einträge) und recycelt
 * die Views. Eine Bindung kostet damit einen Cache-Lookup und zwei Zuweisungen.
 */
class AppAdapter(
    private val iconLoader: IconLoader,
    private val iconSizePx: Int,
) : BaseAdapter() {

    private var apps: List<AppEntry> = emptyList()
    private var limit = 0

    /**
     * Übernimmt Apps und Limit.
     *
     * @param limit Anzahl der Einträge, die gemeldet werden; `0` heißt alle.
     * @return `true`, wenn sich wirklich etwas geändert hat — das spart den
     *   `notifyDataSetChanged()`-Relayout bei jedem HOME-Druck.
     */
    fun submit(apps: List<AppEntry>, limit: Int): Boolean {
        if (this.limit == limit && sameAs(apps)) return false
        this.apps = apps
        this.limit = limit
        notifyDataSetChanged()
        return true
    }

    override fun getCount(): Int = if (limit in 1 until apps.size) limit else apps.size

    override fun getItem(position: Int): AppEntry = apps[position]

    override fun getItemId(position: Int): Long = apps[position].packageName.hashCode().toLong()

    override fun hasStableIds(): Boolean = true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        val holder = view.tag as? Holder ?: Holder(
            view.findViewById(R.id.app_icon),
            view.findViewById(R.id.app_label),
        ).also { view.tag = it }

        val entry = apps[position]
        holder.label.text = entry.label
        holder.icon.contentDescription = entry.label
        // Keine Farbe hier: die Beschriftung ist `text_primary` (item_app.xml) und
        // für jede App gleich. Bis 0.13 stand an dieser Stelle eine Zustandsfarbe
        // (grün für „in den letzten 24 h aktiv", hellgrün für „zuletzt geöffnet")
        // — sie ist in 0.14 entfallen, samt dem Takt, der dafür alle 5 Sekunden
        // die Nutzungsstatistik befragte (siehe CHANGELOG 0.14.0).

        val key = entry.iconKey(iconSizePx)
        holder.boundKey = key
        val cached = iconLoader.get(entry, iconSizePx) { loaded ->
            // Die Zelle kann inzwischen eine andere App zeigen (Recycling).
            if (holder.boundKey == key) holder.icon.setImageBitmap(loaded)
        }
        if (cached != null) {
            holder.icon.setImageBitmap(cached)
        } else {
            holder.icon.setImageResource(R.drawable.bg_icon_placeholder)
        }
        return view
    }

    private fun sameAs(apps: List<AppEntry>): Boolean {
        if (apps.size != this.apps.size) return false
        for (index in apps.indices) {
            val next = apps[index]
            val current = this.apps[index]
            if (next.packageName != current.packageName ||
                next.activityName != current.activityName ||
                next.label != current.label
            ) {
                return false
            }
        }
        return true
    }

    private class Holder(val icon: ImageView, val label: TextView) {
        var boundKey: String? = null
    }
}
