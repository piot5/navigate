package com.piotv.navigate.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.piotv.navigate.data.AppEntry
import com.piotv.navigate.util.Background

/**
 * The focus banners: the TV artwork (`android:banner`) per app, loaded like the
 * icons — memory cache → the app's resources.
 *
 * The banner is the one background source of a launcher reachable through an
 * API: it sits in the manifest of every leanback app, is readable without a
 * special permission, and is exactly the image the system launcher shows
 * behind the focused app. The backgrounds Android TV otherwise has "built in"
 * (Ambient, recommendation images, wallpaper) are not — see
 * docs/LAST_SEEN_TITLE.md, section "Background".
 *
 * Why no disk cache like [IconLoader]: the icons are needed **all** at every
 * start — otherwise every restart would map every foreign APK again (the
 * measured ~25 MB "Private Clean"). A banner is only needed while the focus
 * rests on the app: per app and process **one** decode, a few milliseconds,
 * and after that the cache hits. A second cache would be effort without a
 * measured problem.
 *
 * The bitmap stays at the banner's **own** size (capped at [MAX_WIDTH] width,
 * see [BannerGeometry]) — the background layer scales it to fill. That way
 * twelve banners in the cache cost a few MB instead of a screenful of pixels.
 */
class BannerLoader private constructor(context: Context) {

    private val packageManager = context.applicationContext.packageManager

    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Is a decode currently running for this app? Main thread only. */
    private val pending = HashSet<String>()

    /**
     * Banner from the cache or `null`. If it is missing, it is decoded in the
     * background and [onLoaded] is called afterwards on the main thread — the
     * caller must then check whether the app is still under the focus (the
     * same flag as for the icons).
     *
     * `null` also means "this app has no banner" (`android:banner` missing) —
     * then it stays dark; nothing is guessed.
     */
    fun get(entry: AppEntry, onLoaded: (Bitmap) -> Unit): Bitmap? {
        val key = entry.packageName
        cache.get(key)?.let { return it }
        if (entry.appInfo.banner == 0) return null
        if (!pending.add(key)) return null
        Background.io.execute {
            val bitmap = decode(entry)
            Background.main.post {
                pending.remove(key)
                if (bitmap != null) cache.put(key, bitmap)
                if (bitmap != null) onLoaded(bitmap)
            }
        }
        return null
    }

    private fun decode(entry: AppEntry): Bitmap? {
        val info = entry.appInfo
        if (info.banner == 0) return null
        val drawable: Drawable = try {
            packageManager.getResourcesForApplication(info).getDrawable(info.banner, null)
                ?: return null
        } catch (e: Exception) {
            return null
        }
        val size = BannerGeometry.size(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            MAX_WIDTH,
        )
        if (size.width <= 0 || size.height <= 0) return null
        return try {
            val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size.width, size.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        /**
         * The memory cache. A 640×360 banner is ~0.9 MB in ARGB_8888; twelve
         * megabytes are a good dozen apps — rarely more sit under the focus
         * before the oldest entry is evicted.
         */
        private const val MAX_BYTES = 12 * 1024 * 1024

        /** Widest bitmap a banner produces — the TV cannot resolve more. */
        private const val MAX_WIDTH = 1280

        @Volatile
        private var instance: BannerLoader? = null

        fun get(context: Context): BannerLoader =
            instance ?: synchronized(this) {
                instance ?: BannerLoader(context.applicationContext).also { instance = it }
            }
    }
}
