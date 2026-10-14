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
 * Liefert App-Symbole: Speicher-Cache → Platten-Cache → Ressourcen der App.
 *
 * Warum überhaupt selbst dekodieren statt `ImageView.setImageDrawable`:
 * jedes `getDrawable` kostet einen Ressourcen-Zugriff in einen fremden
 * Prozess-Paketkontext und erzeugt ein Drawable, das bei jedem Bind neu
 * gezeichnet wird. Ein 128-px-Bitmap-Bild aus dem Cache ist beim Scrollen
 * praktisch kostenlos — auf dem Chromecast ist genau das der Unterschied
 * zwischen ruckelfrei und nicht.
 *
 * ## Drei Stufen, und warum die mittlere neu ist
 *
 * Der Speicher-Cache (8 MB LRU) allein reicht nicht: er ist nach jedem
 * Prozess-Neustart leer, und auf einem 1,4-GB-Gerät stirbt der Launcher unter
 * Speicherdruck regelmäßig. Dann wurden **alle** Symbole erneut aus fremden
 * APKs gemappt — genau der Vorgang, der die gemessenen ~25 MB „Private Clean"
 * erzeugt (docs/DEVICE_PERF.md). Deshalb liegt jedes dekodierte Symbol
 * zusätzlich als kleine WebP-Datei im app-internen Speicher
 * ([IconDiskCache]) und wird beim nächsten Mal von dort gelesen.
 *
 * [warm] füllt beide Stufen im Hintergrund vor, damit der **nächste**
 * HOME-Druck nichts mehr zu tun hat.
 *
 * Der Speicher-Cache ist größenbasiert (8 MB) statt anzahlbasiert: Symbolgrößen
 * sind nicht einheitlich, und ein OOM auf einem 1,4-GB-Gerät wäre der teuerste
 * Fehler, den ein Launcher machen kann.
 */
class IconLoader private constructor(context: Context) {

    private val packageManager = context.applicationContext.packageManager
    private val disk = IconDiskCache.get(context)

    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Läuft gerade eine Dekodierung für diesen Schlüssel? Nur Main-Thread. */
    private val pending = HashSet<String>()

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val maskPath = Path()
    private val maskRect = RectF()

    init {
        // Als erste Aufgabe auf dem IO-Thread: ein Formatwechsel im Cache wird
        // aufgeräumt, bevor irgendjemand liest oder schreibt. Derselbe
        // Single-Thread-Executor serialisiert das, also gibt es kein Rennen mit
        // load/store.
        Background.io.execute { disk.ensureFormatVersion() }
    }

    /**
     * Symbol aus dem Cache oder `null`. Fehlt es, wird es im Hintergrund
     * dekodiert und [onLoaded] danach auf dem Main-Thread aufgerufen — der
     * Aufrufer muss dann prüfen, ob sein View noch denselben Eintrag zeigt
     * (Recycling!).
     */
    fun get(entry: AppEntry, sizePx: Int, onLoaded: (Bitmap) -> Unit): Bitmap? {
        val key = entry.iconKey(sizePx)
        cache.get(key)?.let { return it }
        if (!pending.add(key)) return null
        Background.io.execute {
            // Platte zuerst: eine 8-KB-WebP-Datei zu dekodieren ist ein
            // Dateizugriff im eigenen Speicher, kein Mapping der Ressourcen
            // einer fremden App. Nur beim allerersten Mal geht es an die
            // Ressourcen — und das Ergebnis landet sofort auf der Platte.
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
     * Füllt Speicher- und Platten-Cache im Hintergrund vor.
     *
     * Gedacht für den Moment, in dem die App-Liste fertig ist: dann liegen die
     * Symbole bereit, wenn der nächste HOME-Druck das Raster zeigt — statt dass
     * der Nutzer auf das Dekodieren sieht. Der Aufruf ist billig und
     * nicht-blockierend; die Arbeit läuft auf dem IO-Thread.
     *
     * `LruCache` ist in Android intern synchronisiert, deshalb ist das
     * Schreiben aus dem IO-Thread zulässig.
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
     * Bei Speicherdruck alles aus dem Speicher wegwerfen: die Symbole kommen
     * aus dem Platten-Cache in Millisekunden wieder — das ist der ganze Sinn
     * von [IconDiskCache].
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
     * Symbol-Quelle. Reihenfolge nach Wichtigkeit: eigenes Symbol, sonst der
     * TV-Banner (leanback-only Apps ohne Symbol hätten sonst das generische
     * Android-Männchen), sonst das System-Standardsymbol.
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
     * Seitenverhältnistreu einpassen und zentrieren. Quadratische Symbole (der
     * Normalfall) füllen die Fläche exakt; ein 16:9-Banner wird eingepasst
     * statt verzerrt.
     *
     * Die Rechnung steht in [IconGeometry] — reine Funktion, ohne Gerät geprüft
     * (IconGeometryTest). Hier wird sie nur auf die Zeichenfläche angewendet.
     */
    private fun drawFitted(canvas: Canvas, drawable: Drawable, sizePx: Int) {
        val fit = IconGeometry.fit(drawable.intrinsicWidth, drawable.intrinsicHeight, sizePx)
        drawable.setBounds(fit.left, fit.top, fit.left + fit.width, fit.top + fit.height)
        drawable.draw(canvas)
    }

    /** Abgerundete Ecken wie Android-Symbole (22 %, siehe [IconGeometry]) —
     *  einheitlicher Look für alte wie neue Symbole, ein Zeichenvorgang pro
     *  Symbol. */
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
         * Prozess-Singleton: Leiste, Raster und Einstellungen zeigen dieselben
         * Symbole. Zwei Caches wären doppelter Speicher für dieselben Bilder —
         * auf einem 1,4-GB-Gerät der falsche Luxus.
         */
        fun get(context: Context): IconLoader =
            instance ?: synchronized(this) {
                instance ?: IconLoader(context.applicationContext).also { instance = it }
            }
    }
}
