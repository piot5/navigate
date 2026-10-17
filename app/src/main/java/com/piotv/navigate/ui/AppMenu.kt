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
 * Das Kontextmenü einer App (langes OK) und die Aktionen dahinter.
 *
 * Es stand bis 0.12 in [com.piotv.navigate.LauncherActivity] — nicht, weil es
 * dorthin gehört, sondern weil es dort am kürzesten war. Es gehört hierher: die
 * Klasse kennt nur den Dialog, die drei Intents und die Toast-Texte, und was sie
 * **nicht** weiß, ist der Zustand des Rasters (Reihenfolge, App-Liste). Genau
 * das sagt sie dem Aufrufer über [Listener] — dieselbe Trennung wie zwischen
 * [AppAdapter] und der Home-Seite.
 *
 * Der Dialog ist der des Frameworks: ein eigenes Menü wäre eine zweite
 * Bedienoberfläche für fünf Einträge, mit eigener Fokus-Logik auf einem Gerät,
 * auf dem man mit vier Tasten navigiert.
 */
class AppMenu(private val activity: Activity, private val listener: Listener) {

    interface Listener {
        /** Eine Position nach vorne (schreibt die Reihenfolge sofort). */
        fun onMoveForward(entry: AppEntry)

        /** Eine Position nach hinten. */
        fun onMoveBackward(entry: AppEntry)

        /** Alle Apps im Hintergrund schließen — die Liste kennt nur der Aufrufer. */
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

    /** Deinstallieren darf nur das System: wir zeigen seinen Dialog. */
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
     * Schließt eine App im Hintergrund.
     *
     * `killBackgroundProcesses` ist der einzige Weg, den eine normale App hat —
     * und er greift nur, solange die App **nicht** im Vordergrund ist. Auf der
     * Home-Seite ist genau das der Fall (unser opakes Fenster hat die App
     * gestoppt). Eine gerade laufende App kann eine App nicht beenden; dafür gibt
     * es keinen öffentlichen Weg.
     */
    fun close(entry: AppEntry) {
        val manager = activity.getSystemService(ActivityManager::class.java) ?: return
        manager.killBackgroundProcesses(entry.packageName)
    }
}
