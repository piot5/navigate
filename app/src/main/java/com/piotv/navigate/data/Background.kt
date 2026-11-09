package com.piotv.navigate.data

import java.io.File

/**
 * What lies behind the grid — **three** options.
 *
 * | Value | What is shown | Where the image comes from |
 * |---|---|---|
 * | [DARK] | the solid, very dark background (default) | `@color/bg_home` |
 * | [IMAGE] | an image, filling the area | a **media file** (`background_file=`) |
 * | [FOCUS] | the app's **TV banner** under the focus, otherwise dark | the app's `android:banner` (0.17) |
 *
 * A **video** background existed in 0.14 as a fourth option; it has since been
 * removed again. A video permanently occupies a hardware decoder on the
 * Chromecast — for a surface behind the grid, a price without benefit.
 *
 * Why these are **not** the backgrounds Android TV has "built in": the images
 * of Google TV's ambient mode ("Backdrop") live in the private storage of the
 * Google TV launcher, the recommendation/channel images from `TvContract`
 * require a TV input permission, and `WallpaperManager` returns no image on
 * the Chromecast HD (no wallpaper service). A normal launcher cannot reach any
 * of them — see docs/LAST_SEEN_TITEL.md, section "Background". The **banner**
 * is the one exception: it is in the manifest of every Leanback app and
 * readable without special rights (`PackageManager`) — exactly the artwork the
 * system launcher also shows behind the focused app.
 */
enum class BackgroundStyle {
    DARK,
    IMAGE,
    FOCUS,
}

/**
 * Where the background file lives — pure calculation, no Android types, so it
 * can be tested without a device ([BackgroundPlanTest]).
 *
 * Two locations, as with the configuration files: an **absolute** path
 * (`background_file=/sdcard/…`) applies as it stands. If nothing is set there,
 * the default name in the **external app folder** applies — the same folder
 * `adb push` also puts the configuration files in (docs/CONFIG.md), i.e. the
 * only place one can write to without root.
 */
object BackgroundPlan {

    /** The default name for [BackgroundStyle.IMAGE] in the external app folder. */
    const val DEFAULT_IMAGE_NAME = "background.jpg"

    /** The default name of an image style — `null` for [BackgroundStyle.DARK] and [BackgroundStyle.FOCUS]. */
    fun defaultName(style: BackgroundStyle): String? = when (style) {
        BackgroundStyle.DARK -> null
        BackgroundStyle.IMAGE -> DEFAULT_IMAGE_NAME
        BackgroundStyle.FOCUS -> null
    }

    /**
     * The file Navigate should read — or `null` when none is needed.
     *
     * @param configured Value of `background_file` from `settings.conf`.
     * @param externalDir Absolute path of the external app folder; `null` when
     *   the device has none (then only an absolute path in [configured] works).
     */
    fun file(style: BackgroundStyle, configured: String?, externalDir: String?): File? {
        val name = defaultName(style) ?: return null
        val path = configured?.trim().orEmpty()
        if (path.isEmpty()) {
            return externalDir?.let { File(it, name) }
        }
        // An absolute path applies as is; anything else is understood as a
        // name in the external app folder (`background_file=vacation.jpg`).
        val direct = File(path)
        if (direct.isAbsolute) return direct
        return externalDir?.let { File(it, path) }
    }
}
