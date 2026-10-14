package com.piotv.navigate.data

import android.content.ComponentName
import android.content.pm.ApplicationInfo

/**
 * Ein startbarer Eintrag — im Raster wie im Dock.
 *
 * Warum [ApplicationInfo] und keine [android.content.pm.LauncherActivityInfo]:
 * die App-Liste entsteht aus zwei PackageManager-Abfragen (siehe
 * [AppRepository]), weil LauncherApps auf Android TV leanback-only Apps
 * verschweigt. Die LauncherApps-Variante hätte den passenderen Symbolpfad
 * geliefert, aber ein unvollständiges Raster ist schlimmer als ein Symbol,
 * das eine Spur anders skaliert wird.
 */
class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val appInfo: ApplicationInfo,
) {

    /** Ziel für `startActivity` — der Komponentenname ist eindeutig. */
    val component: ComponentName
        get() = ComponentName(packageName, activityName)

    /** Schlüssel für den Symbol-Cache. Die Größe gehört dazu: Dock und Raster
     *  zeigen dasselbe Symbol in zwei Größen, und beide sollen einmal
     *  dekodiert werden, nicht bei jedem Bind neu. */
    fun iconKey(sizePx: Int): String = "$packageName@$sizePx"
}
