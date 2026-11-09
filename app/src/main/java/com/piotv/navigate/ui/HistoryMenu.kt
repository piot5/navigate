package com.piotv.navigate.ui

import android.app.Activity
import android.app.AlertDialog
import com.piotv.navigate.R
import com.piotv.navigate.data.HistoryEntry

/**
 * The context menu of the "Last seen" row (long OK): the **drop-down list**
 * of older history entries.
 *
 * The block in the top left shows the history only as **one** row ([LastSeen]);
 * everything older sits behind it instead of stacked below. Unfolding happens
 * here — and the dialog is the framework's, the same one as in the grid
 * ([AppMenu]): a menu of our own would be a second user interface, with its
 * own focus logic, on a device you navigate with four keys.
 *
 * What this class does **not** know: which entries are older (that is decided
 * by [LastSeen]) and what OK on an entry does (that is up to the caller).
 */
class HistoryMenu(private val activity: Activity) {

    /**
     * Unfolds the older entries.
     *
     * @param entries the older entries, newest first ([LastSeen.older]).
     *   Empty means "nothing to unfold" — then no dialog appears; a menu
     *   with zero entries would be an empty gesture.
     * @param onOpen OK on an entry: bring back exactly this app — the same
     *   path as OK on the row itself.
     */
    fun show(entries: List<HistoryEntry>, onOpen: (String) -> Unit) {
        if (entries.isEmpty()) return
        AlertDialog.Builder(activity)
            .setTitle(R.string.menu_history_older)
            .setItems(entries.map { it.line }.toTypedArray()) { _, which ->
                onOpen(entries[which].packageName)
            }
            .show()
    }
}
