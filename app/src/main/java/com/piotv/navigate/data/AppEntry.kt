package com.piotv.navigate.data

import android.content.ComponentName
import android.content.pm.ApplicationInfo

/**
 * A launchable entry in the grid.
 *
 * Why [ApplicationInfo] and not [android.content.pm.LauncherActivityInfo]:
 * the app list comes from two PackageManager queries (see [AppRepository]),
 * because LauncherApps on Android TV omits leanback-only apps. The
 * LauncherApps variant would have provided the more fitting icon path, but
 * an incomplete grid is worse than an icon scaled a hair differently.
 */
class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val appInfo: ApplicationInfo,
) {

    /** Target for `startActivity` — the component name is unique. */
    val component: ComponentName
        get() = ComponentName(packageName, activityName)

    /** Key for the icon cache. The size is part of it: when the grid size
     *  changes (different density, `configChanges`), a second entry appears
     *  instead of an upscaled bitmap. */
    fun iconKey(sizePx: Int): String = "$packageName@$sizePx"
}
