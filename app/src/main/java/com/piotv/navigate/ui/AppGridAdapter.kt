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
 * Bindet App-Einträge in Raster-Zellen.
 *
 * BaseAdapter + convertView: GridView ruft getView nur für sichtbare Zellen auf
 * (bei 6 Spalten × 3,5 Zeilen also ~21 statt aller Einträge) und recycelt die
 * Views. Eine Bindung kostet damit zwei Zuweisungen und einen Cache-Lookup.
 *
 * Mit `showIcons = false` (Config-Schlüssel `show_icons`) wird **kein** Symbol
 * geladen: die Zelle zeigt nur den Namen. Das ist der Sparmodus für das
 * 1,4-GB-Gerät — die Symbol-Bitmaps sind der größte Posten im Speicher
 * (~25 MB „Private Clean" aus den gemappten Fremd-Ressourcen, gemessen in
 * docs/DEVICE_PERF.md). Die bereits gehaltenen Bitmaps werden dabei aus der
 * ImageView gelöst, damit sie nicht über die View am Leben bleiben.
 */
class AppGridAdapter(
    private val iconLoader: IconLoader,
    private val iconSizePx: Int,
) : BaseAdapter() {

    private var items: List<AppEntry> = emptyList()

    /** Symbole anzeigen? Ein Wechsel wirkt sofort auf die gebundenen Zellen. */
    var showIcons: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    /**
     * Übernimmt eine neue Liste.
     *
     * @return `true`, wenn sich wirklich etwas geändert hat. Das spart den
     *   `notifyDataSetChanged()`-Relayout bei jedem HOME-Druck, denn in 99 %
     *   der Fälle ist die Liste identisch.
     */
    fun submit(apps: List<AppEntry>): Boolean {
        if (sameAs(apps)) return false
        items = apps
        notifyDataSetChanged()
        return true
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): AppEntry = items[position]

    override fun getItemId(position: Int): Long = items[position].packageName.hashCode().toLong()

    override fun hasStableIds(): Boolean = true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        val holder = view.tag as? Holder ?: Holder(
            view.findViewById(R.id.app_icon),
            view.findViewById(R.id.app_label),
        ).also { view.tag = it }

        val entry = items[position]
        holder.label.text = entry.label
        holder.icon.contentDescription = entry.label

        if (!showIcons) {
            // Textmodus: nichts laden, nichts halten. Der Schlüssel wird
            // gelöscht, damit ein noch laufender Ladevorgang die Zelle nicht
            // nachträglich befüllt.
            holder.boundKey = null
            holder.icon.setImageDrawable(null)
            holder.icon.visibility = View.GONE
            return view
        }
        holder.icon.visibility = View.VISIBLE

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
        if (apps.size != items.size) return false
        for (index in apps.indices) {
            val next = apps[index]
            val current = items[index]
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
