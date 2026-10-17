package com.piotv.navigate.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.SystemClock
import android.os.UserHandle
import com.piotv.navigate.util.Background
import java.text.Collator

/**
 * Die Liste aller startbaren Apps — pro Prozess genau einmal gehalten.
 *
 * Warum als Prozess-Singleton: der Launcher-Prozess lebt lange, und ein
 * HOME-Druck muss ohne Abfrage auskommen. Die Liste wird beim ersten Mal im
 * Hintergrund gebaut und danach nur noch aktualisiert.
 *
 * Quelle sind ZWEI PackageManager-Abfragen (LAUNCHER und LEANBACK_LAUNCHER).
 * Das ist keine Vorsicht, sondern auf dem Zielgerät gemessen notwendig: Prime
 * Video, WOW und ARD deklarieren ausschließlich CATEGORY_LEANBACK_LAUNCHER und
 * fehlen in jeder reinen LAUNCHER-Abfrage — auch in LauncherApps.getActivityList.
 */
class AppRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager
    private val launcherApps: LauncherApps? = appContext.getSystemService(LauncherApps::class.java)

    private val lock = Any()
    private val waiting = ArrayList<(List<AppEntry>) -> Unit>()

    @Volatile
    private var cached: List<AppEntry> = emptyList()

    @Volatile
    private var lastLoadAt = 0L

    private var loadedOnce = false
    private var dirty = true
    private var inFlight = false

    /** Letzter Stand. Vor dem ersten Laden leer — das Raster zeigt dann kurz nichts. */
    val apps: List<AppEntry> get() = cached

    init {
        registerLauncherCallback()
    }

    /** Etwas hat sich geändert (App installiert/deinstalliert/umbenannt). */
    fun markDirty() {
        synchronized(lock) { dirty = true }
    }

    /**
     * Lädt die Liste, falls nötig, und ruft [onReady] auf dem Main-Thread.
     *
     * Ist der Stand frisch, kommt [onReady] sofort und synchron — der
     * HOME-Druck soll auf keinen Fall auf Platte oder Binder warten.
     *
     * @param force auch bei frischem Stand neu laden (nach Konfigurationswechsel).
     */
    fun refresh(force: Boolean, onReady: (List<AppEntry>) -> Unit) {
        var readyNow: List<AppEntry>? = null
        var startLoad = false
        synchronized(lock) {
            val stale = SystemClock.elapsedRealtime() - lastLoadAt > STALE_AFTER_MS
            if (!force && loadedOnce && !dirty && !stale) {
                readyNow = cached
            } else {
                waiting.add(onReady)
                if (!inFlight) {
                    inFlight = true
                    startLoad = true
                }
            }
        }
        readyNow?.let { onReady(it) }
        if (startLoad) Background.io.execute(::loadBlocking)
    }

    private fun loadBlocking() {
        val fresh = try {
            queryApps()
        } catch (e: Exception) {
            // Lieber die alte Liste behalten als ein leeres Raster zeigen.
            cached
        }
        val callbacks: List<(List<AppEntry>) -> Unit>
        synchronized(lock) {
            cached = fresh
            loadedOnce = true
            dirty = false
            inFlight = false
            lastLoadAt = SystemClock.elapsedRealtime()
            callbacks = ArrayList(waiting)
            waiting.clear()
        }
        Background.main.post { for (callback in callbacks) callback(fresh) }
    }

    private fun queryApps(): List<AppEntry> {
        // Reihenfolge ist Absicht: wer zuerst kommt, gewinnt — siehe
        // [AppCatalog.select]. LAUNCHER zuerst, damit Apps mit beiden
        // Kategorien (Netflix, Disney+, YouTube, Stremio ...) über ihren
        // LAUNCHER-Eintrag erscheinen, denselben, den der Hersteller-Launcher
        // benutzt.
        val candidates = ArrayList<AppCatalog.Candidate>(64)
        val applicationInfos = HashMap<String, ApplicationInfo>(64)
        collect(Intent.CATEGORY_LAUNCHER, candidates, applicationInfos)
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, candidates, applicationInfos)

        // Die Regeln (deaktiviert, eigener Launcher, erster gewinnt, leere
        // Beschriftung, Sortierung) stehen in AppCatalog — reines Kotlin und
        // damit ohne Gerät geprüft (AppCatalogTest).
        return AppCatalog.select(
            candidates = candidates,
            ownPackage = appContext.packageName,
            labelOrder = labelOrder(),
        ).mapNotNull { candidate ->
            val application = applicationInfos[candidate.packageName] ?: return@mapNotNull null
            AppEntry(
                packageName = candidate.packageName,
                activityName = candidate.activityName,
                label = candidate.label,
                appInfo = application,
            )
        }
    }

    /**
     * Sortierung wie der System-Launcher: sprachabhängig über `Collator`, nicht
     * nach Zeichencode (sonst stünde „Ä" hinter „Z").
     */
    private fun labelOrder(): Comparator<String> {
        val collator = Collator.getInstance()
        return Comparator { first, second -> collator.compare(first, second) }
    }

    private fun collect(
        category: String,
        out: MutableList<AppCatalog.Candidate>,
        infos: MutableMap<String, ApplicationInfo>,
    ) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        val resolved: List<ResolveInfo> = try {
            packageManager.queryIntentActivities(intent, 0)
        } catch (e: Exception) {
            return
        }
        for (info in resolved) {
            val activity = info.activityInfo ?: continue
            val application = activity.applicationInfo ?: continue
            val packageName = application.packageName ?: continue
            out.add(
                AppCatalog.Candidate(
                    packageName = packageName,
                    activityName = activity.name,
                    label = info.loadLabel(packageManager).toString().trim(),
                    enabled = application.enabled,
                )
            )
            // Erster Eintrag je Paket: passt zur Auswahl in AppCatalog.
            if (!infos.containsKey(packageName)) infos[packageName] = application
        }
    }

    /**
     * Live-Aktualisierung: installiert oder deinstalliert jemand eine App,
     * markiert der Systemaufruf den Stand als veraltet. Der nächste Raster-Aufbau
     * lädt nach — bewusst NICHT sofort, weil das Raster im Hintergrund niemanden
     * interessiert und ein Neuaufbau nur Arbeit wäre.
     */
    private fun registerLauncherCallback() {
        val launcher = launcherApps ?: return
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = markDirty()

            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = markDirty()

            override fun onPackageChanged(packageName: String?, user: UserHandle?) = markDirty()

            override fun onPackagesAvailable(
                packageNames: Array<out String>?,
                user: UserHandle?,
                replacing: Boolean,
            ) = markDirty()

            override fun onPackagesUnavailable(
                packageNames: Array<out String>?,
                user: UserHandle?,
                replacing: Boolean,
            ) = markDirty()
        }
        try {
            launcher.registerCallback(callback, Background.main)
        } catch (e: Exception) {
            // Ohne Callback greift der Alters-Check in refresh(); der Launcher
            // soll deswegen nicht schlechter starten.
        }
    }

    companion object {
        /**
         * Sicherheitsnetz gegen verpasste Änderungen: älter als 30 s → neu laden.
         * Kostet eine PackageManager-Abfrage im Hintergrund und macht den
         * Launcher unabhängig davon, ob ein Callback angekommen ist.
         */
        private const val STALE_AFTER_MS = 30_000L

        @Volatile
        private var instance: AppRepository? = null

        fun get(context: Context): AppRepository =
            instance ?: synchronized(this) {
                instance ?: AppRepository(context.applicationContext).also { instance = it }
            }
    }
}
