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
 * The list of all launchable apps — held exactly once per process.
 *
 * Why a process singleton: the launcher process lives long, and a HOME press
 * must get by without a query. The list is built in the background the first
 * time and afterwards only refreshed.
 *
 * The source is TWO PackageManager queries (LAUNCHER and LEANBACK_LAUNCHER).
 * This is not caution but measured necessity on the target device: Prime
 * Video, WOW and ARD declare only CATEGORY_LEANBACK_LAUNCHER and are missing
 * from every pure LAUNCHER query — including LauncherApps.getActivityList.
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

    /** Latest state. Empty before the first load — the grid then briefly shows nothing. */
    val apps: List<AppEntry> get() = cached

    init {
        registerLauncherCallback()
    }

    /** Something changed (app installed/uninstalled/renamed). */
    fun markDirty() {
        synchronized(lock) { dirty = true }
    }

    /**
     * Loads the list if needed and calls [onReady] on the main thread.
     *
     * When the state is fresh, [onReady] fires immediately and synchronously —
     * the HOME press must never wait on disk or Binder.
     *
     * @param force reload even when the state is fresh (after a configuration
     *   change).
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
            // Prefer keeping the old list over showing an empty grid.
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
        // The order is intentional: first come, first served — see
        // [AppCatalog.select]. LAUNCHER first, so apps with both categories
        // (Netflix, Disney+, YouTube, Stremio ...) appear via their LAUNCHER
        // entry, the same one the vendor launcher uses.
        val candidates = ArrayList<AppCatalog.Candidate>(64)
        val applicationInfos = HashMap<String, ApplicationInfo>(64)
        collect(Intent.CATEGORY_LAUNCHER, candidates, applicationInfos)
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, candidates, applicationInfos)

        // The rules (disabled, own launcher, first wins, empty label,
        // sorting) live in AppCatalog — pure Kotlin and thus tested without a
        // device (AppCatalogTest).
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
     * Sorting like the system launcher: language-aware via `Collator`, not by
     * character code (otherwise umlauts would sort after "Z").
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
            // First entry per package: matches the selection in AppCatalog.
            if (!infos.containsKey(packageName)) infos[packageName] = application
        }
    }

    /**
     * Live updates: when someone installs or uninstalls an app, the system
     * call marks the state as stale. The next grid build reloads —
     * deliberately NOT immediately, because nobody cares about the grid while
     * it is in the background and a rebuild would just be busywork.
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
            // Without the callback the staleness check in refresh() kicks in;
            // the launcher should not start any worse because of it.
        }
    }

    companion object {
        /**
         * Safety net against missed changes: older than 30 s → reload. Costs
         * one PackageManager query in the background and makes the launcher
         * independent of whether a callback arrived.
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
