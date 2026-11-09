package com.piotv.navigate.ui

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.piotv.navigate.R
import com.piotv.navigate.data.AppEntry
import com.piotv.navigate.util.shortToast

/**
 * An app's context menu (long press of OK) and the actions behind it.
 *
 * It lived in [com.piotv.navigate.LauncherActivity] until 0.12 — not because it
 * belonged there, but because it was shortest there. It belongs here: the class
 * knows only the dialog, the three intents and the toast texts, and what it
 * does **not** know is the state of the grid (order, app list). Exactly that it
 * tells the caller via [Listener] — the same separation as between
 * [AppAdapter] and the home screen.
 *
 * The dialog is the framework's one: a custom menu would be a second UI for
 * five entries, with its own focus logic, on a device navigated with four keys.
 */
class AppMenu(private val activity: Activity, private val listener: Listener) {

    interface Listener {
        /** One position forward (writes the order immediately). */
        fun onMoveForward(entry: AppEntry)

        /** One position backward. */
        fun onMoveBackward(entry: AppEntry)

        /** Close all apps in the background — only the caller knows the list. */
        fun onCloseAll()
    }

    fun show(entry: AppEntry) {
        val items = arrayOf(
            activity.getString(R.string.menu_move_forward),
            activity.getString(R.string.menu_move_backward),
            activity.getString(R.string.menu_uninstall),
            activity.getString(R.string.menu_close),
            activity.getString(R.string.menu_close_all),
        )
        AlertDialog.Builder(activity)
            .setTitle(entry.label)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> listener.onMoveForward(entry)
                    1 -> listener.onMoveBackward(entry)
                    2 -> uninstall(entry)
                    3 -> close(entry)
                    else -> listener.onCloseAll()
                }
            }
            .show()
    }

    /** Only the system may uninstall: we show its dialog. */
    fun uninstall(entry: AppEntry) {
        try {
            activity.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:${entry.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            activity.shortToast(R.string.toast_uninstall_failed)
        }
    }

    /**
     * Closes an app in the background.
     *
     * `killBackgroundProcesses` is the only route a normal app has — and it
     * only works while the app is **not** in the foreground. On the home screen
     * that is exactly the case (our opaque window has stopped the app). An app
     * that is currently running cannot be closed by an app; there is no public
     * route for that.
     */
    fun close(entry: AppEntry) {
        val manager = activity.getSystemService(ActivityManager::class.java) ?: return
        manager.killBackgroundProcesses(entry.packageName)
    }
}
