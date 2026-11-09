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
 * Binds the apps into the grid.
 *
 * `limit` is the entire trick behind "only the first row": the adapter then
 * reports only the first `limit` entries — one row — and the GridView has
 * nothing to scroll. When expanding, the limit goes away (`0`) and all the
 * apps join in: nothing is rebuilt, just more entries are reported.
 *
 * BaseAdapter + convertView: GridView calls `getView` only for visible cells
 * (~24 entries instead of all of them at 6 columns and 4 rows) and recycles
 * the views. A binding therefore costs one cache lookup and two assignments.
 */
class AppAdapter(
    private val iconLoader: IconLoader,
    private val iconSizePx: Int,
) : BaseAdapter() {

    private var apps: List<AppEntry> = emptyList()
    private var limit = 0

    /**
     * Takes over the apps and the limit.
     *
     * @param limit number of entries to report; `0` means all of them.
     * @return `true` if anything actually changed — this saves the
     *   `notifyDataSetChanged()` relayout on every press of HOME.
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
        // No color here: the label is `text_primary` (item_app.xml) and the same
        // for every app. Until 0.13 there was a state color here (green for
        // "active in the last 24 h", light green for "opened most recently") —
        // it went away in 0.14, along with the ticker that queried the usage
        // stats every 5 seconds for it (see CHANGELOG 0.14.0).

        val key = entry.iconKey(iconSizePx)
        holder.boundKey = key
        val cached = iconLoader.get(entry, iconSizePx) { loaded ->
            // The cell may meanwhile show a different app (recycling).
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
