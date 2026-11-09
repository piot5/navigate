package com.piotv.navigate.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.piotv.navigate.data.AppEntry
import com.piotv.navigate.util.Background

/**
 * Supplies app icons: memory cache → disk cache → the app's resources.
 *
 * Why decode ourselves at all instead of `ImageView.setImageDrawable`: every
 * `getDrawable` costs a resource access into a foreign process's package
 * context and produces a Drawable that is redrawn on every bind. A 128-px
 * bitmap from the cache is practically free while scrolling — on the
 * Chromecast that is exactly the difference between smooth and not.
 *
 * ## Three stages, and why the middle one is new
 *
 * The memory cache (8 MB LRU) alone is not enough: it is empty after every
 * process restart, and on a 1.4 GB device the launcher regularly dies under
 * memory pressure. Then **all** icons were mapped again from foreign APKs —
 * exactly the process that produces the measured ~25 MB "Private Clean"
 * (docs/DEVICE_PERF.md). That is why every decoded icon is additionally stored
 * as a small WebP file in the app-internal storage ([IconDiskCache]) and read
 * from there next time.
 *
 * [warm] pre-fills both stages in the background so the **next** press of
 * HOME has nothing left to do.
 *
 * The memory cache is size-based (8 MB) instead of count-based: icon sizes are
 * not uniform, and an OOM on a 1.4 GB device would be the most expensive
 * mistake a launcher can make.
 */
class IconLoader private constructor(context: Context) {

    private val packageManager = context.applicationContext.packageManager
    private val disk = IconDiskCache.get(context)

    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Is a decode currently running for this key? Main thread only. */
    private val pending = HashSet<String>()

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val maskPath = Path()
    private val maskRect = RectF()

    init {
        // As the first task on the IO thread: a format change in the cache is
        // cleaned up before anyone reads or writes. The same single-thread
        // executor serializes this, so there is no race with load/store.
        Background.io.execute { disk.ensureFormatVersion() }
    }

    /**
     * Icon from the cache or `null`. If it is missing, it is decoded in the
     * background and [onLoaded] is called afterwards on the main thread — the
     * caller must then check whether its view still shows the same entry
     * (recycling!).
     */
    fun get(entry: AppEntry, sizePx: Int, onLoaded: (Bitmap) -> Unit): Bitmap? {
        val key = entry.iconKey(sizePx)
        cache.get(key)?.let { return it }
        if (!pending.add(key)) return null
        Background.io.execute {
            // Disk first: decoding an 8 KB WebP file is a file access in our own
            // storage, not a mapping of a foreign app's resources. Only the very
            // first time touches the resources — and the result lands on disk
            // right away.
            val bitmap = disk.load(entry, sizePx)
                ?: decode(entry, sizePx)?.also { disk.store(entry, sizePx, it) }
            Background.main.post {
                pending.remove(key)
                if (bitmap != null) {
                    cache.put(key, bitmap)
                    onLoaded(bitmap)
                }
            }
        }
        return null
    }

    /**
     * Pre-fills the memory and disk caches in the background.
     *
     * Meant for the moment the app list is ready: the icons are then in place
     * when the next press of HOME shows the grid — instead of the user watching
     * the decode. The call is cheap and non-blocking; the work runs on the IO
     * thread.
     *
     * `LruCache` is internally synchronized in Android, so writing from the IO
     * thread is allowed.
     */
    fun warm(entries: List<AppEntry>, sizePx: Int) {
        if (entries.isEmpty()) return
        Background.io.execute {
            for (entry in entries) {
                val key = entry.iconKey(sizePx)
                if (cache.get(key) != null) continue
                if (disk.load(entry, sizePx) != null) continue
                val bitmap = decode(entry, sizePx) ?: continue
                disk.store(entry, sizePx, bitmap)
                cache.put(key, bitmap)
            }
        }
    }

    /**
     * Under memory pressure, drop everything from memory: the icons come back
     * from the disk cache within milliseconds — that is the entire point of
     * [IconDiskCache].
     */
    fun evictAll() {
        cache.evictAll()
        pending.clear()
    }

    private fun decode(entry: AppEntry, sizePx: Int): Bitmap? {
        val source = loadSource(entry) ?: return null
        return try {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawFitted(canvas, source, sizePx)
            applyRoundMask(canvas, sizePx)
            bitmap
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Icon source, in order of importance: the app's own icon, else the TV
     * banner (otherwise leanback-only apps without an icon would get the
     * generic Android figure), else the system default icon.
     */
    private fun loadSource(entry: AppEntry): Drawable? {
        val info = entry.appInfo
        val resourceId = when {
            info.icon != 0 -> info.icon
            info.banner != 0 -> info.banner
            else -> 0
        }
        if (resourceId == 0) return packageManager.getDefaultActivityIcon()
        return try {
            packageManager.getResourcesForApplication(info).getDrawable(resourceId, null)
                ?: packageManager.getDefaultActivityIcon()
        } catch (e: Exception) {
            packageManager.getDefaultActivityIcon()
        }
    }

    /**
     * Fit while preserving the aspect ratio, and center. Square icons (the
     * normal case) fill the area exactly; a 16:9 banner is fitted instead of
     * distorted.
     *
     * The computation lives in [IconGeometry] — a pure function, tested without
     * a device (IconGeometryTest). Here it is only applied to the canvas.
     */
    private fun drawFitted(canvas: Canvas, drawable: Drawable, sizePx: Int) {
        val fit = IconGeometry.fit(drawable.intrinsicWidth, drawable.intrinsicHeight, sizePx)
        drawable.setBounds(fit.left, fit.top, fit.left + fit.width, fit.top + fit.height)
        drawable.draw(canvas)
    }

    /** Rounded corners like Android icons (22%, see [IconGeometry]) — a
     *  uniform look for old and new icons alike, one draw pass per icon. */
    private fun applyRoundMask(canvas: Canvas, sizePx: Int) {
        val radius = IconGeometry.cornerRadius(sizePx)
        maskRect.set(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
        maskPath.reset()
        maskPath.addRoundRect(maskRect, radius, radius, Path.Direction.CW)
        canvas.drawPath(maskPath, maskPaint)
    }

    companion object {
        private const val MAX_BYTES = 8 * 1024 * 1024

        @Volatile
        private var instance: IconLoader? = null

        /**
         * Process singleton: the panel, grid and settings show the same icons.
         * Two caches would be double the memory for the same images — the
         * wrong luxury on a 1.4 GB device.
         */
        fun get(context: Context): IconLoader =
            instance ?: synchronized(this) {
                instance ?: IconLoader(context.applicationContext).also { instance = it }
            }
    }
}
