package com.piotv.navigate

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.piotv.navigate.data.AppEntry
import com.piotv.navigate.data.AppRepository
import com.piotv.navigate.data.FavoritesCodec
import com.piotv.navigate.data.FavoritesConfig
import com.piotv.navigate.data.Prefs
import com.piotv.navigate.fg.ForegroundProbe
import com.piotv.navigate.ui.AppGridAdapter
import com.piotv.navigate.ui.AppGridView
import com.piotv.navigate.ui.DockController
import com.piotv.navigate.ui.GridColumns
import com.piotv.navigate.ui.IconLoader
import com.piotv.navigate.ui.SettingsActivity
import com.piotv.navigate.util.shortToast
import java.util.Date

/**
 * Der ganze Launcher: eine Activity, zwei Fenster-Modi.
 *
 * Warum eine einzige Activity und nicht zwei (Home-Activity + Overlay-Activity):
 * eine App mit zwei HOME-Activities macht die Auflösung des Systems mehrdeutig —
 * der HOME-Druck zeigt dann einen Auswahldialog statt des Launchers. Ein
 * Fenster, zwei Zustände, keine Mehrdeutigkeit.
 *
 * Der Modus entsteht NICHT über ein zweites Theme (das ginge zur Laufzeit
 * nicht), sondern über Fenstergröße plus Hintergrund:
 *
 *  - GRID: Fenster = ganzer Bildschirm, Wurzel-Layout deckend → Home-Seite.
 *  - DOCK: Fenster = nur die untere Zeile (Gravity BOTTOM, Höhe WRAP_CONTENT),
 *    Wurzel-Layout transparent → die laufende App bleibt sichtbar. Sie wird
 *    dabei nur pausiert, nicht gestoppt: Video und Ton laufen weiter.
 */
class LauncherActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var grid: AppGridView
    private lateinit var dock: FrameLayout
    private lateinit var dockRow: LinearLayout
    private lateinit var clock: TextView

    private lateinit var prefs: Prefs
    private lateinit var probe: ForegroundProbe
    private lateinit var repository: AppRepository
    private lateinit var iconLoader: IconLoader
    private lateinit var config: FavoritesConfig
    private lateinit var dockController: DockController

    private var adapter: AppGridAdapter? = null
    private var mode = LauncherMode.GRID
    private var gridIconSizePx = 0
    private var dockIconSizePx = 0

    /** Zuletzt gesetzte Fenstergröße — ein zweiter Aufruf mit demselben Wert
     *  wäre ein Relayout über Binder für nichts (siehe [applyWindowLayout]). */
    private var appliedLayout: LauncherMode? = null

    /** Wurden die Raster-Symbole schon vorgewärmt? Einmal reicht. */
    private var iconsWarmed = false

    /** Ein HOME-Intent ist eingetroffen und wird in [onResume] konsumiert. */
    private var homeIntentPending = false

    /** Wir kommen gerade aus einer von uns selbst gestarteten fremden Activity
     *  (Einstellungen) zurück — das ist kein "App beendet". */
    private var launchedInternally = false

    /**
     * Steht die Zeile gerade sichtbar über einer App?
     *
     * Bewusst ein eigener Merker und NICHT `isResumed`/`hasWindowFocus`: das
     * System pausiert die Activity bei jedem HOME-Druck, *bevor* es den Intent
     * zustellt (auf dem Zielgerät gemessen: onPause → onNewIntent → onResume).
     * Ein Lebenszyklus-Flag ist an dieser Stelle also immer schon `false` —
     * genau daran ist die erste Fassung des zweiten HOME-Drucks gescheitert.
     */
    private var dockOnScreen = false

    private val clockHandler = Handler(Looper.getMainLooper())

    private val clockTick = object : Runnable {
        override fun run() {
            clock.text = DateFormat.getTimeFormat(this@LauncherActivity).format(Date())
            clockHandler.postDelayed(this, CLOCK_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fensterformat: bewusst NICHT angefasst (Theme-Format TRANSPARENT).
        //
        // Gemessen auf dem Zielgerät: mit TRANSPARENT stoppt die Plattform die
        // App darunter (ihr Fenster verschwindet, mHasSurface=false). Mit
        // window.setFormat(TRANSLUCENT) bleibt sie stattdessen nur pausiert —
        // dafür war der Inhalt dieses Fensters auf dem Gerät nicht mehr zu
        // sehen (Pixelprüfung per screencap: nur die App darunter). Sichtbares
        // Dock schlägt laufende App im Hintergrund, deshalb bleibt es beim
        // Theme-Format. Details: docs/ARCHITECTURE.md, docs/DEVICE_PERF.md.

        prefs = Prefs(this)
        probe = ForegroundProbe(this)
        repository = AppRepository.get(this)
        iconLoader = IconLoader.get(this)
        config = FavoritesConfig.get(this)

        setContentView(R.layout.activity_launcher)
        root = findViewById(R.id.root)
        grid = findViewById(R.id.grid)
        dock = findViewById(R.id.dock)
        dockRow = findViewById(R.id.dock_row)
        clock = findViewById(R.id.clock)

        gridIconSizePx = resources.getDimensionPixelSize(R.dimen.grid_icon)
        dockIconSizePx = resources.getDimensionPixelSize(R.dimen.dock_icon)
        dockController = DockController(this, dockRow, iconLoader, dockIconSizePx)

        setupGrid(force = false)

        // App-Liste sofort im Hintergrund bauen, nicht erst beim Öffnen des
        // Rasters: der erste HOME-Druck soll keine Abfrage abwarten.
        refreshApps(force = false)

        homeIntentPending = true
        applyMode(initialMode(intent))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        homeIntentPending = true

        // Die Zeile steht schon und hat den Tastaturfokus: der zweite
        // HOME-Druck schließt sie und kehrt zur App zurück. Ohne diese Regel
        // wäre der Launcher eine Sackgasse — er hält den Fokus, die App darunter
        // bekommt keine Taste mehr. Genau so auf dem Zielgerät aufgetreten:
        // nach einem HOME-Druck ließ sich Netflix nicht mehr bedienen.
        // (Ein finish() hilft hier nicht: das System startet die Home-Activity
        // sofort neu — gemessen, der Task wechselte dabei von t2667 auf t2668.)
        //
        // Die Regel selbst steht in [HomeKeyPolicy] und ist damit ohne Gerät
        // prüfbar (HomeKeyPolicyTest) — hier bleibt nur die Verdrahtung.
        if (
            HomeKeyPolicy.dismissesDock(
                intentIsHome = intent.hasCategory(Intent.CATEGORY_HOME),
                dockOnScreen = dockOnScreen,
            )
        ) {
            dismissDock()
            return
        }

        applyMode(initialMode(intent))
    }

    override fun onResume() {
        super.onResume()

        // Fenstergröße erneut setzen: in onCreate ist das Fenster noch nicht am
        // WindowManager angemeldet. Idempotent und ohne sichtbaren Sprung, weil
        // der erste Frame erst nach onResume gezeichnet wird.
        applyWindowLayout(mode)

        // Konfigurationsdatei kann sich geändert haben: in den
        // Navigate-Einstellungen, oder von Hand per adb. Ein `stat` kostet
        // nichts, ein Dateibeobachter wäre Aufwand ohne Nutzen.
        if (config.reloadIfChanged()) {
            setupGrid(force = true)
            refreshApps(force = false)
        }
        // Die Zeile immer neu abgleichen: buildDock() baut nur neu, wenn sich
        // die Favoriten wirklich geändert haben.
        buildDock()

        // Der HOME-Intent ist hier konsumiert: der Aufbau ist erledigt.
        //
        // Die Regel steht in [HomeKeyPolicy] (ohne Gerät prüfbar): ohne
        // HOME-Druck und ohne eigenen Ausflug in die Einstellungen wieder oben,
        // während die Zeile steht → die App davor ist beendet.
        val fallbackToGrid = HomeKeyPolicy.fallsBackToGrid(
            homeIntentPending = homeIntentPending,
            launchedInternally = launchedInternally,
            mode = mode,
        )
        homeIntentPending = false
        launchedInternally = false
        if (fallbackToGrid) {
            applyMode(LauncherMode.GRID)
        }

        if (mode == LauncherMode.GRID) {
            refreshApps(force = false)
            applyClockVisibility()
            startClock()
        }
    }

    override fun onPause() {
        super.onPause()
        // Kein Timer, während niemand hinsieht.
        stopClock()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Sprache oder Dichte geändert: App-Namen und Symbolgrößen stecken in
        // den zwischengespeicherten Daten und müssen neu entstehen. Bewusst
        // ohne Activity-Neustart (configChanges im Manifest) — ein Neustart
        // wäre auf dem Chromecast ein sichtbares Flackern.
        gridIconSizePx = resources.getDimensionPixelSize(R.dimen.grid_icon)
        dockIconSizePx = resources.getDimensionPixelSize(R.dimen.dock_icon)
        iconLoader.evictAll()
        dockController = DockController(this, dockRow, iconLoader, dockIconSizePx)
        setupGrid(force = true)
        refreshApps(force = true)
    }

    /**
     * Bei Speicherdruck die Symbole wegwerfen, bevor das System den Prozess
     * dafür tötet — sie sind in Millisekunden wieder da (Platten-Cache).
     *
     * `TRIM_MEMORY_RUNNING_CRITICAL` ist ab API 34 als veraltet markiert (das
     * System meldet es auf neueren Geräten nicht mehr), aber minSdk ist 23: auf
     * älteren Geräten ist es genau der Wert, auf den es ankommt. Die
     * Unterdrückung gilt dem Compiler-Hinweis, nicht dem Verhalten.
     */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            iconLoader.evictAll()
        }
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        when (mode) {
            // Im Dock: zurück zur laufenden App. Die Zeile ist eine
            // Einblendung, kein eigener Aufenthaltsort.
            LauncherMode.DOCK -> dismissDock()

            // Auf der Home-Seite gibt es nichts darüber — BACK bleibt ohne
            // Wirkung, statt den Launcher zu beenden.
            LauncherMode.GRID -> Unit
        }
    }

    /**
     * Schließt die Zeile und kehrt zur laufenden App zurück.
     *
     * Umgesetzt als Start der App (dieselbe explizite Komponente wie beim
     * Antippen eines Favoriten): damit kommt deren Task nach vorn, sie wird
     * wieder fortgesetzt und bekommt den Tastaturfokus. `finish()` wäre der
     * naheliegende Weg, funktioniert für eine Home-Activity aber nicht — das
     * System startet sie sofort neu (auf dem Gerät gemessen).
     */
    private fun dismissDock() {
        dockOnScreen = false
        if (returnToRunningApp()) return
        // Kein Ziel bekannt (App inzwischen deinstalliert?): dann ist die
        // Home-Seite die ehrliche Antwort — es gibt nichts, wohin man
        // zurückkehren könnte.
        applyMode(LauncherMode.GRID)
    }

    private fun returnToRunningApp(): Boolean {
        val packageName = probe.snapshot()?.foreignPackage
            ?: prefs.lastLaunchedPackage
            ?: return false
        repository.apps.firstOrNull { it.packageName == packageName }?.let { entry ->
            launch(entry)
            return true
        }
        // Zweiter Versuch über den Launcher-Intent des Pakets: greift auch,
        // wenn die App nicht im Raster steht (z. B. System-App).
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return try {
            prefs.appRunning = true
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    // ------------------------------------------------------------------
    // Modus
    // ------------------------------------------------------------------

    private fun initialMode(intent: Intent?): LauncherMode {
        // Aus der TV-Apps-Zeile gestartet (LEANBACK_LAUNCHER) will der Nutzer den
        // Launcher sehen — nicht die Zeile über der gerade laufenden App. Die
        // Entscheidung dazu steht in [HomeKeyPolicy]; die teure
        // Vordergrund-Abfrage ([decideMode]) entfällt auf diesem Weg.
        if (HomeKeyPolicy.mustShowGrid(intent?.hasCategory(Intent.CATEGORY_HOME))) {
            return LauncherMode.GRID
        }
        return decideMode()
    }

    private fun decideMode(): LauncherMode = ModeDecider.decide(
        foreground = probe.snapshot(),
        appRunning = prefs.appRunning,
    )

    private fun applyMode(next: LauncherMode) {
        mode = next
        applyWindowLayout(next)
        when (next) {
            LauncherMode.GRID -> showGrid()
            LauncherMode.DOCK -> showDock()
        }
    }

    /**
     * Fenstergröße = der ganze Modusunterschied.
     *
     * Im DOCK-Modus ergibt WRAP_CONTENT die Höhe des Wurzel-Layouts, und das ist
     * genau das Dock samt Rand — das Raster ist dann GONE und wird nicht
     * gemessen. Deshalb steht die Dock-Höhe an genau einer Stelle im Layout.
     */
    private fun applyWindowLayout(target: LauncherMode) {
        // Ein HOME-Druck kommt als onNewIntent: dort setzt applyMode() die
        // Größe, und onResume setzt sie gleich noch einmal. Das zweite Setzen
        // ist ein Relayout über Binder für nichts — es entfällt, wenn sich
        // nichts geändert hat. Ausnahme: solange das Fenster noch nicht am
        // WindowManager hängt (onCreate), muss es immer gesetzt werden.
        if (window.decorView.isAttachedToWindow && appliedLayout == target) return

        val params = window.attributes
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        if (target == LauncherMode.DOCK) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        } else {
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.TOP or Gravity.START
        }
        window.attributes = params
        appliedLayout = target
    }

    private fun showGrid() {
        // Das Fenster ist technisch translucide; im GRID-Modus soll davon nichts
        // zu sehen sein, also deckend malen.
        root.setBackgroundColor(getColor(R.color.bg_home))
        grid.visibility = View.VISIBLE
        dock.visibility = View.VISIBLE
        dockOnScreen = false
        applyClockVisibility()
        if (prefs.appRunning) prefs.appRunning = false
        buildDock()
        focusGrid()
        startClock()
    }

    private fun showDock() {
        dockOnScreen = true
        root.setBackgroundColor(Color.TRANSPARENT)
        grid.visibility = View.GONE
        clock.visibility = View.GONE
        dock.visibility = View.VISIBLE
        stopClock()
        buildDock()
        focusDock(force = false)
    }

    // ------------------------------------------------------------------
    // Uhr
    // ------------------------------------------------------------------

    private fun startClock() {
        stopClock()
        if (!config.current().showClock) return
        clockTick.run()
    }

    private fun stopClock() {
        clockHandler.removeCallbacks(clockTick)
    }

    private fun applyClockVisibility() {
        clock.visibility = if (config.current().showClock) View.VISIBLE else View.GONE
    }

    // ------------------------------------------------------------------
    // Raster
    // ------------------------------------------------------------------

    /**
     * Raster-Adapter und Zuhörer. Wird nur einmal gebaut (bzw. bei
     * Konfigurationswechsel neu), weil `setAdapter` einen kompletten Relayout
     * auslöst und die Zuhörer nichts kosten.
     */
    private fun setupGrid(force: Boolean) {
        if (adapter != null && !force) return
        val newAdapter = AppGridAdapter(iconLoader, gridIconSizePx)
        grid.numColumns = computeColumns()
        grid.adapter = newAdapter
        grid.setOnItemClickListener { _, _, position, _ -> launch(newAdapter.getItem(position)) }
        grid.setOnItemLongClickListener { _, _, position, _ ->
            toggleFavorite(newAdapter.getItem(position))
            true
        }
        adapter = newAdapter
        applyIconMode(newAdapter)
    }

    /**
     * Übernimmt den Konfigurationsschalter `show_icons` auf den Raster-Adapter.
     *
     * Beim Abschalten werden die Bitmaps auch aus dem Speicher-Cache geworfen:
     * ein Sparmodus, der die 8 MB LRU weiterhält, wäre keiner. Die neun Symbole
     * der Zeile kommen danach aus dem Platten-Cache zurück — das ist ein
     * Dateizugriff, kein Mapping fremder Ressourcen.
     */
    private fun applyIconMode(gridAdapter: AppGridAdapter) {
        val showIcons = config.current().showIcons
        if (gridAdapter.showIcons == showIcons) return
        gridAdapter.showIcons = showIcons
        if (showIcons) {
            iconsWarmed = false
        } else {
            iconLoader.evictAll()
        }
    }

    /**
     * Spaltenzahl: aus der Konfiguration, sonst aus der Breite. Auf dem
     * Chromecast (960dp) ergibt die Automatik 6 Spalten à 160dp — 24 Apps
     * gleichzeitig sichtbar, der Rest per Scroll.
     *
     * Die Rechnung selbst steht in [GridColumns] (reine Funktion, getestet);
     * hier wird nur die Bildschirmbreite geliefert.
     */
    private fun computeColumns(): Int = GridColumns.compute(
        configured = config.current().columns,
        widthDp = resources.configuration.screenWidthDp,
    )

    private fun refreshApps(force: Boolean) {
        repository.refresh(force) { apps ->
            // submit() vergleicht und meldet nur echte Änderungen — sonst würde
            // jeder HOME-Druck einen Relayout des ganzen Rasters auslösen.
            val changed = adapter?.let { gridAdapter ->
                applyIconMode(gridAdapter)
                gridAdapter.submit(apps)
            } ?: false

            // Symbole vorwärmen: dann findet der nächste HOME-Druck sie fertig
            // im Speicher bzw. auf der Platte vor, statt sie zu dekodieren.
            if (config.current().showIcons && (changed || !iconsWarmed)) {
                iconsWarmed = true
                iconLoader.warm(apps, gridIconSizePx)
            }

            if (mode == LauncherMode.DOCK) buildDock()
        }
    }

    // ------------------------------------------------------------------
    // Dock
    // ------------------------------------------------------------------

    private fun buildDock() {
        val favorites = resolveFavorites()
        val rebuilt = dockController.build(
            favorites = favorites,
            onOpenApp = ::launch,
            onRemoveFavorite = ::removeFavorite,
            onOpenHome = ::openHome,
            onOpenNavigateSettings = ::openNavigateSettings,
            onOpenSettings = ::openSettings,
            onSettingsLongPress = ::openUsageAccessSettings,
        )
        // Die Zeile ist genau das, was der nächste HOME-Druck zeigt: ihre
        // Symbole dürfen nicht erst in diesem Moment dekodiert werden. Nur beim
        // echten Neuaufbau vorwärmen, nicht bei jedem HOME-Druck.
        if (rebuilt) iconLoader.warm(favorites, dockIconSizePx)
    }

    /**
     * Favoriten kommen aus der Konfigurationsdatei und werden erst hier zu
     * Einträgen. Eine deinstallierte App verschwindet damit von selbst aus der
     * Zeile, ohne dass jemand aufräumen muss.
     *
     * Nachgeschlagen wird über den Index des [AppRepository] — vorher entstand
     * hier bei jedem HOME-Druck ein neues HashMap über alle Apps.
     */
    private fun resolveFavorites(): List<AppEntry> {
        val packages = config.current().favorites
        if (packages.isEmpty()) return emptyList()
        val byPackage = repository.byPackageName
        return packages.mapNotNull { byPackage[it] }
    }

    private fun focusGrid() {
        // Erst direkt versuchen: ein `post` kostet einen zusätzlichen Durchlauf
        // der Nachrichtenschleife, und nach einem HOME-Druck ist die Ansicht
        // längst vermessen. Nur beim allerersten Aufbau wird nachgereicht.
        if (!grid.requestFocus()) grid.post { grid.requestFocus() }
    }

    /**
     * Fokus in die Zeile — aber nur, wenn er nicht schon dort ist: ein zweiter
     * HOME-Druck soll den Fokus nicht auf das erste Element zurückwerfen.
     */
    private fun focusDock(force: Boolean) {
        if (!force && isInDock(currentFocus)) return
        // Wie bei [focusGrid]: direkt versuchen, nur bei Misserfolg nachreichen
        // (die Zeile ist unmittelbar nach dem Sichtbarwerden noch nicht
        // vermessen).
        if (!dockController.focusFirst()) dock.post { dockController.focusFirst() }
    }

    private fun isInDock(view: View?): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === dockRow) return true
            current = current.parent as? View
        }
        return false
    }

    // ------------------------------------------------------------------
    // Aktionen
    // ------------------------------------------------------------------

    private fun launch(entry: AppEntry) {
        dockOnScreen = false
        prefs.appRunning = true
        prefs.lastLaunchedPackage = entry.packageName

        // Explizite Komponente + MAIN/LAUNCHER: genau das, was der Systemdienst
        // LauncherApps intern tut. Die Kategorie ist bei expliziter Komponente
        // ohne Bedeutung, deshalb startet das auch leanback-only Apps.
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(entry.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Passiert, wenn eine App zwischen Rasteraufbau und Klick
            // deinstalliert wurde.
            prefs.appRunning = false
            repository.markDirty()
            shortToast(R.string.toast_launch_failed)
        }
    }

    /** Langes Drücken im Raster: Favorit setzen oder entfernen. */
    private fun toggleFavorite(entry: AppEntry) {
        val current = config.current()
        val isFavorite = entry.packageName in current.favorites
        if (!isFavorite && FavoritesCodec.isFull(current.favorites, current.maxFavorites)) {
            // Plural: bei max_favorites=1 hieß es vorher „Maximal 1 Favoriten".
            shortToast(
                resources.getQuantityString(
                    R.plurals.toast_favorites_full,
                    current.maxFavorites,
                    current.maxFavorites,
                )
            )
            return
        }
        config.update { it.copy(favorites = FavoritesCodec.toggle(it.favorites, entry.packageName)) }
        shortToast(if (isFavorite) R.string.toast_favorite_removed else R.string.toast_favorite_added)
        buildDock()
    }

    /** Langes Drücken im Dock: Favorit entfernen. */
    private fun removeFavorite(entry: AppEntry) {
        config.update { it.copy(favorites = it.favorites - entry.packageName) }
        shortToast(R.string.toast_favorite_removed)
        buildDock()
        focusDock(force = true)
    }

    /** Das Home-Symbol im Dock. */
    private fun openHome() {
        if (mode == LauncherMode.GRID) {
            // Schon auf der Home-Seite: Fokus dorthin, wo die Apps sind.
            focusGrid()
            return
        }
        // Aus der Zeile heraus die Home-Seite — und den Merker löschen, sonst
        // würde der nächste HOME-Druck wieder die Zeile zeigen.
        prefs.appRunning = false
        applyMode(LauncherMode.GRID)
    }

    /** Das Symbol "Navigate" in der Zeile: die eigenen Einstellungen. */
    private fun openNavigateSettings() {
        launchedInternally = true
        try {
            startActivity(
                Intent(this, SettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            shortToast(R.string.toast_settings_failed)
        }
    }

    /** Das Einstellungen-Symbol: die Systemeinstellungen, nicht eigene. */
    private fun openSettings() {
        launchedInternally = true
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            shortToast(R.string.toast_settings_failed)
        }
    }

    /**
     * Langes Drücken auf "Einstellungen": Nutzungszugriff.
     *
     * Ohne diesen Zugriff muss Navigate raten, ob eine App läuft (siehe README).
     * Ein eigener Einstellungsbildschirm dafür wäre ein ganzer Screen für einen
     * Schalter — der lange Druck ist die ehrlichere Lösung.
     */
    private fun openUsageAccessSettings() {
        launchedInternally = true
        try {
            startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            shortToast(R.string.toast_settings_failed)
        }
    }

    private companion object {
        /** Einmal pro Minute reicht: eine Uhr mit Sekunden wäre ein Timer pro
         *  Sekunde — auf dem Chromecast messbar, ohne Mehrwert. */
        const val CLOCK_INTERVAL_MS = 30_000L
    }
}
