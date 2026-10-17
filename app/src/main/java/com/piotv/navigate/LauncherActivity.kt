package com.piotv.navigate

import android.app.Activity
import android.app.ActivityManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import com.piotv.navigate.data.AppEntry
import com.piotv.navigate.data.AppRepository
import com.piotv.navigate.data.LastApp
import com.piotv.navigate.data.NowPlaying
import com.piotv.navigate.data.Order
import com.piotv.navigate.data.OrderStore
import com.piotv.navigate.data.SettingsStore
import com.piotv.navigate.ui.AppAdapter
import com.piotv.navigate.ui.AppMenu
import com.piotv.navigate.ui.GridColumns
import com.piotv.navigate.ui.IconLoader
import com.piotv.navigate.ui.NowPlayingPanel
import com.piotv.navigate.ui.SettingsActivity
import com.piotv.navigate.ui.Ticker
import com.piotv.navigate.util.Background
import com.piotv.navigate.util.shortToast
import java.util.Date
import kotlin.math.max

/**
 * Die Home-Seite — mehr hat dieser Launcher nicht.
 *
 * Ein HOME-Druck zeigt ein Raster mit allen startbaren Apps, die Uhr und — wenn
 * eingeschaltet — oben links „Now Playing“ (seit 0.13 **eine Zeile je App** mit
 * aktiver Media-Session). Zu sehen ist zunächst nur die **erste Rasterzeile**;
 * die Zelle ganz rechts darin ist der „…“-Knopf und klappt den Rest auf.
 * Einstellbar ist die Reihenfolge der Apps ([OrderStore], per langem Drücken oder
 * per `adb push`) und ob das Now-Playing-Feld erscheint ([SettingsStore], langes
 * OK auf „…“ öffnet die Einstellungen).
 *
 * ## Was hier steht — und was seit 0.13 ausgelagert ist
 *
 * Diese Klasse hält den **einen** Zustand des Launchers (Raster, Reihenfolge,
 * Fokus) und die Lebenszyklus-Haken. Der Rest liegt in eigenen Klassen, weil die
 * Datei sonst wieder auf 769 Zeilen zuwächst:
 *
 *  - die drei Takte (Uhr, grüne Beschriftung, Now Playing) → [Ticker],
 *  - der Now-Playing-Block → [NowPlayingPanel] (mehrere Zeilen, Persistenz),
 *  - das Kontextmenü → [AppMenu],
 *  - die Regeln dahinter ohnehin: `Order`, `AppCatalog`, `NowPlayingBoard`.
 *
 * Zwei Entscheidungen, die aus Fehlern gelernt sind:
 *
 *  - **Ein opakes Fenster.** Das Theme ist absichtlich das gewöhnlichste, das es
 *    gibt (`res/values/styles.xml`). Die Fassungen bis 0.6 hatten ein
 *    translucentes Fenster; auf dem Zielgerät war das Fenster dann gezeichnet,
 *    fokussiert und bedienbar — aber **nicht sichtbar**.
 *  - **Eine Activity, ein Zustand.** Zwei HOME-Activities in einer App machen die
 *    Auflösung des Systems mehrdeutig (Auswahldialog statt Direktstart).
 */
class LauncherActivity : Activity(), NowPlayingPanel.Listener, AppMenu.Listener {

    private lateinit var grid: GridView
    private lateinit var clock: TextView

    /** Der „…“-Knopf unter der App-Zeile. */
    private lateinit var more: TextView

    private lateinit var repository: AppRepository
    private lateinit var iconLoader: IconLoader
    private lateinit var order: OrderStore
    private lateinit var settings: SettingsStore
    private lateinit var lastApp: LastApp

    /** Der Block oben links — er besitzt seine Zeilen, seinen Takt und seinen Stand. */
    private lateinit var nowPlayingPanel: NowPlayingPanel

    /** Das Kontextmenü (langes OK auf einer App). */
    private lateinit var menu: AppMenu

    private var adapter: AppAdapter? = null

    /** Alle Apps in Grundordnung (sprachsortiert) — die Anzeige entsteht daraus
     *  plus der konfigurierten Reihenfolge. */
    private var allApps: List<AppEntry> = emptyList()

    /** Die Apps in Anzeige-Reihenfolge. */
    private var display: List<AppEntry> = emptyList()

    /** Pakete, die aktiv sind — ihre Beschriftung ist grün. */
    private var activeApps: Set<String> = emptySet()

    /** Die zuletzt geöffnete App — sie bekommt den helleren Grünton. */
    private var lastPackage: String? = null

    /** Nur die erste Zeile oder alles? */
    private var expanded = false

    private var iconsWarmed = false
    private var columns = 0

    /** Oben-Abstand des Rasters, wenn nichts darüber steht (Ressource). */
    private var baseGridTopPadding = 0
    private var topRowPaddingTop = 0
    private var nowPlayingGap = 0

    /**
     * Steht die Home-Seite gerade sichtbar auf dem Bildschirm?
     *
     * Kein Lebenszyklus-Flag: das System pausiert die Activity bei jedem
     * HOME-Druck, **bevor** es den Intent zustellt (`onPause → onNewIntent →
     * onResume`) — `isResumed` ist dort immer schon `false`. Genau daran ist die
     * erste Fassung dieses Verhaltens in 0.1 gescheitert.
     */
    private var homeOnScreen = false

    private val clockTicker = Ticker(CLOCK_INTERVAL_MS) {
        clock.text = DateFormat.getTimeFormat(this).format(Date())
    }

    /**
     * Hält die grüne Schrift **laufend** aktuell (alle [ACTIVE_INTERVAL_MS]).
     *
     * Läuft nur, solange die Home-Seite sichtbar ist (onResume/onPause). Die
     * Abfrage ist ein Zeitstempel-Blick in die Nutzungsstatistik und läuft im
     * Hintergrund-Thread; der Grünton der zuletzt geöffneten App kommt aus dem
     * Merker und kostet keine Abfrage.
     */
    private val activeTicker = Ticker(ACTIVE_INTERVAL_MS) { refreshActive() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = AppRepository.get(this)
        iconLoader = IconLoader.get(this)
        order = OrderStore.get(this)
        settings = SettingsStore.get(this)
        lastApp = LastApp(this)

        setContentView(R.layout.activity_home)
        grid = findViewById(R.id.grid)
        more = findViewById(R.id.more)
        clock = findViewById(R.id.clock)
        baseGridTopPadding = resources.getDimensionPixelSize(R.dimen.grid_padding_top)
        topRowPaddingTop = resources.getDimensionPixelSize(R.dimen.top_row_padding_top)
        nowPlayingGap = resources.getDimensionPixelSize(R.dimen.now_playing_gap_bottom)

        nowPlayingPanel = NowPlayingPanel(
            activity = this,
            container = findViewById(R.id.now_playing),
            listener = this,
            source = NowPlaying(this),
            iconLoader = iconLoader,
            intervalMs = NowPlayingPanel.NOW_PLAYING_INTERVAL_MS,
        )
        menu = AppMenu(this, this)

        more.setOnClickListener { setExpanded(!expanded) }
        // Langes OK auf „…“ öffnet die Einstellungen — der einzige Ort außerhalb
        // des Rasters, den diese Seite hat.
        more.setOnLongClickListener {
            openSettings()
            true
        }

        columns = GridColumns.compute(resources.configuration.screenWidthDp)
        grid.numColumns = columns
        setupAdapter()

        // App-Liste sofort im Hintergrund bauen, nicht erst beim Öffnen: der
        // erste HOME-Druck soll keine Abfrage abwarten.
        refreshApps(force = false)

        applyGridHeight()
        applyMoreButton()
        startClock()
        focusGrid()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Jeder HOME-Druck kommt hier an (launchMode=singleTask).
        //
        // Zwei Fälle, und sie sind der ganze Witz dieses Merkers:
        //  - Die Home-Seite stand schon sichtbar → der Nutzer will zurück in die
        //    App, in der er war (HOME als „Umschalter“).
        //  - Die Home-Seite kam gerade von einer App nach vorne → er will die
        //    Home-Seite sehen, und zwar zugeklappt (eine frische Home-Seite).
        if (homeOnScreen && returnToLastApp()) return

        setExpanded(false)
        refreshApps(force = false)
        startClock()
        focusGrid()
    }

    override fun onResume() {
        super.onResume()
        homeOnScreen = true
        activeTicker.start()
        // Die Reihenfolge kann sich von außen geändert haben (`adb push`). Ein
        // `stat` kostet nichts, ein Dateibeobachter wäre Aufwand ohne Nutzen.
        if (order.reloadIfChanged()) applyOrder(allApps, activeApps, lastPackage)
        // Die Einstellungen können sich geändert haben — in den Einstellungen
        // selbst oder per `adb push` (dieselbe Drossel wie bei der Reihenfolge).
        settings.reloadIfChanged()
        nowPlayingPanel.start()
        refreshApps(force = false)
        startClock()
        focusGrid()
    }

    /**
     * Die Home-Seite verlässt den Bildschirm (andere App im Vordergrund) → der
     * nächste HOME-Druck zeigt sie wieder, statt zurückzuspringen.
     */
    override fun onStop() {
        super.onStop()
        homeOnScreen = false
    }

    override fun onPause() {
        super.onPause()
        // Keine Timer, während niemand hinsieht.
        stopClock()
        activeTicker.stop()
        nowPlayingPanel.stop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Sprache oder Dichte geändert: App-Namen und Symbolgrößen stecken in den
        // zwischengespeicherten Daten und müssen neu entstehen. Bewusst ohne
        // Activity-Neustart (configChanges im Manifest) — ein Neustart wäre auf
        // dem Chromecast ein sichtbares Flackern.
        iconLoader.evictAll()
        iconsWarmed = false
        columns = GridColumns.compute(resources.configuration.screenWidthDp)
        grid.numColumns = columns
        setupAdapter()
        refreshApps(force = true)
    }

    /**
     * Bei Speicherdruck die Symbole wegwerfen, bevor das System den Prozess dafür
     * tötet — sie sind in Millisekunden wieder da (Platten-Cache).
     *
     * `TRIM_MEMORY_RUNNING_CRITICAL` ist ab API 34 als veraltet markiert (das
     * System meldet es auf neueren Geräten nicht mehr), aber minSdk ist 23: auf
     * älteren Geräten ist es genau der Wert, auf den es ankommt.
     */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            iconLoader.evictAll()
        }
    }

    /**
     * BACK: erst das aufgeklappte Raster zuklappen, dann zurück in die letzte App.
     * Auf der zugeklappten Home-Seite ohne bekannte App bleibt es ohne Wirkung.
     *
     * `finish()` wäre der naheliegende Weg — das System startet eine Home-Activity
     * danach aber sofort neu (auf dem Zielgerät gemessen: der Task wechselte von
     * t2667 auf t2668), also ein sichtbares Flackern für nichts.
     */
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        when {
            expanded -> setExpanded(false)
            homeOnScreen -> returnToLastApp()
        }
    }

    /**
     * Holt die zuletzt im Vordergrund stehende App nach vorne.
     *
     * Der Weg ist derselbe wie beim Antippen einer Zelle: der Launcher-Intent der
     * App. Er holt den **bestehenden** Task nach vorne (die App wird fortgesetzt,
     * nicht neu gestartet) und gibt ihr den Fokus zurück.
     *
     * @return `true`, wenn eine App gestartet werden konnte.
     */
    private fun returnToLastApp(): Boolean {
        val packageName = lastApp.packageName() ?: return false
        val entry = display.firstOrNull { it.packageName == packageName }
        if (entry != null) {
            launch(entry)
            return true
        }
        // Zweiter Versuch über den Launcher-Intent: greift auch, wenn die App
        // nicht im Raster steht (z. B. eine System-App).
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return try {
            homeOnScreen = false
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    // ------------------------------------------------------------------
    // Raster
    // ------------------------------------------------------------------

    /**
     * Adapter und Zuhörer. Wird nur einmal gebaut (bzw. bei Konfigurationswechsel
     * neu), weil `setAdapter` einen kompletten Relayout auslöst.
     */
    private fun setupAdapter() {
        val newAdapter = AppAdapter(
            iconLoader = iconLoader,
            iconSizePx = resources.getDimensionPixelSize(R.dimen.grid_icon),
            colorIdle = getColor(R.color.text_primary),
            colorActive = getColor(R.color.text_active),
            colorLast = getColor(R.color.text_last),
        )
        grid.adapter = newAdapter
        grid.setOnItemClickListener { _, _, position, _ -> launch(newAdapter.getItem(position)) }
        grid.setOnItemLongClickListener { _, _, position, _ ->
            menu.show(newAdapter.getItem(position))
            true
        }
        adapter = newAdapter
    }

    /**
     * Wie viele Einträge der Adapter meldet: eine Zeile oder alle.
     *
     * Das ist der ganze Unterschied zwischen „zugeklappt“ und „aufgeklappt“ — es
     * gibt kein zweites Layout und keine zweite Ansicht.
     */
    private fun rowLimit(): Int = if (expanded) 0 else columns

    /**
     * Zugeklappt ist das Raster nur eine Zeile hoch und hängt mit der Spalte am
     * unteren Rand; aufgeklappt bekommt es den ganzen Platz über dem „…“-Knopf und
     * scrollt. `layout_weight` statt `match_parent`, weil ein `match_parent`-Kind
     * in einer senkrechten LinearLayout den Knopf darunter aus dem Bild schieben
     * würde.
     */
    private fun applyGridHeight() {
        val params = grid.layoutParams as LinearLayout.LayoutParams
        if (expanded) {
            params.height = 0
            params.weight = 1f
        } else {
            params.height = LinearLayout.LayoutParams.WRAP_CONTENT
            params.weight = 0f
        }
        grid.layoutParams = params
    }

    /** Beschriftung für Screenreader — die Glyphe allein sagt nichts. */
    private fun applyMoreButton() {
        more.contentDescription =
            getString(if (expanded) R.string.less_label else R.string.more_label)
    }

    /** Klappt das Raster auf (alles) oder zu (nur die erste Zeile). */
    private fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        adapter?.submit(display, rowLimit(), activeApps, lastPackage)
        applyGridHeight()
        applyMoreButton()
        // Aufgeklappt gehört der Platz den Apps: der Now-Playing-Block verschwindet
        // (seinen Stand behält er — siehe NowPlayingPanel).
        nowPlayingPanel.refreshVisibility()
    }

    private fun refreshApps(force: Boolean) {
        // Beides gehört in den Hintergrund-Thread: die App-Liste **und** die
        // Frage „welche Apps laufen noch?“ (Fenster der Nutzungsstatistik). Auf
        // dem Main-Thread wäre das genau der Posten, der den HOME-Druck früher
        // langsam gemacht hat.
        Background.io.execute {
            val active = lastApp.recentlyUsed()
            val last = lastApp.packageName()
            repository.refresh(force) { apps ->
                allApps = apps
                applyOrder(apps, active, last)
            }
        }
    }

    /**
     * Bringt die Apps in die konfigurierte Reihenfolge und übergibt sie an den
     * Adapter. Die Rechnung selbst steht in [Order.arrange] (ohne Android-Typen,
     * damit ohne Gerät prüfbar).
     *
     * @param active Pakete, die aktiv sind — sie bekommen die grüne Beschriftung
     *   (siehe [com.piotv.navigate.data.LastApp.recentlyUsed]).
     */
    private fun applyOrder(apps: List<AppEntry>, active: Set<String>, last: String?) {
        display = Order.arrange(order.current(), apps) { it.packageName }
        activeApps = active
        lastPackage = last
        val changed = adapter?.submit(display, rowLimit(), active, last) ?: false
        // Symbole vorwärmen: dann findet der nächste HOME-Druck sie fertig im
        // Speicher bzw. auf der Platte vor, statt sie zu dekodieren.
        if (changed || !iconsWarmed) {
            iconsWarmed = true
            iconLoader.warm(display, resources.getDimensionPixelSize(R.dimen.grid_icon))
        }
    }

    /**
     * Langes Drücken: die App eine Position nach vorne schieben und sofort
     * speichern. Das ist der Weg, die Reihenfolge **auf dem Fernseher** zu ändern
     * (der andere ist die Datei, siehe docs/CONFIG.md).
     */
    private fun moveForward(entry: AppEntry) {
        if (order.moveForward(entry.packageName)) {
            shortToast(R.string.toast_order_moved)
            applyOrder(allApps, activeApps, lastPackage)
        } else {
            shortToast(R.string.toast_order_first)
        }
    }

    /** Wie [moveForward], nur eine Position nach hinten (Kontextmenü). */
    private fun moveBackward(entry: AppEntry) {
        if (order.moveBackward(entry.packageName)) {
            shortToast(R.string.toast_order_back)
            applyOrder(allApps, activeApps, lastPackage)
        } else {
            shortToast(R.string.toast_order_last)
        }
    }

    /**
     * Fragt im Hintergrund, welche Apps aktiv sind, und übergibt sie an den
     * Adapter. Der Adapter vergleicht selbst — ohne echte Änderung wird nicht neu
     * gebunden.
     */
    private fun refreshActive() {
        Background.io.execute {
            val active = lastApp.recentlyUsed()
            Background.main.post {
                activeApps = active
                adapter?.submit(display, rowLimit(), active, lastPackage)
            }
        }
    }

    private fun focusGrid() {
        // Erst direkt versuchen: ein `post` kostet einen zusätzlichen Durchlauf der
        // Nachrichtenschleife, und nach einem HOME-Druck ist die Ansicht längst
        // vermessen. Nur beim allerersten Aufbau wird nachgereicht.
        if (!grid.requestFocus()) grid.post { grid.requestFocus() }
    }

    // ------------------------------------------------------------------
    // Uhr
    // ------------------------------------------------------------------

    private fun startClock() = clockTicker.start()

    private fun stopClock() = clockTicker.stop()

    /** Langes OK auf „…“: die Einstellungen (eigene Activity, siehe dort). */
    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    // ------------------------------------------------------------------
    // NowPlayingPanel.Listener — was der Block von der Home-Seite braucht
    // ------------------------------------------------------------------

    /**
     * Sichtbar ist der Block nur, wenn er eingeschaltet ist **und** das Raster
     * zugeklappt ist. Aufgeklappt gehört der Platz den Apps.
     */
    override fun isEnabled(): Boolean = settings.current().nowPlaying && !expanded

    /** Der Eintrag einer App: erst aus der Anzeige, dann aus der ganzen Liste. */
    override fun entryOf(packageName: String): AppEntry? =
        display.firstOrNull { it.packageName == packageName }
            ?: allApps.firstOrNull { it.packageName == packageName }

    /**
     * OK auf einer Now-Playing-Zeile: zurück in die App, die den Ton macht.
     *
     * Zwei Wege, in dieser Reihenfolge:
     *
     *  1. `sessionActivity` der Session — der „Jetzt läuft“-Bildschirm, den die App
     *     **selbst** anbietet (`PendingIntent`). Das ist der vorgesehene Weg: er
     *     landet dort, wo der Nutzer weiterhört, nicht im Hauptmenü der App.
     *  2. Sonst der Launcher-Intent, genau wie aus dem Raster ([launch]).
     *
     * Ohne Session tut die Zeile nichts, statt irgendetwas zu starten.
     */
    override fun openRow(packageName: String, sessionActivity: PendingIntent?) {
        if (sessionActivity != null) {
            try {
                homeOnScreen = false
                sessionActivity.send()
                return
            } catch (e: Exception) {
                // Abgelaufen, schon benutzt oder vom Ersteller gesperrt: dann der
                // normale Weg.
            }
        }
        val entry = entryOf(packageName)
        if (entry != null) {
            launch(entry)
        } else {
            shortToast(R.string.toast_launch_failed)
        }
    }

    /** Der Block verschwindet (aufgeklappt): der Fokus gehört zurück ins Raster. */
    override fun onFocusLost() {
        focusGrid()
    }

    /**
     * Der Block meldet seine Höhe; das Raster beginnt darunter.
     *
     * Seit 0.13 kann er **mehrere** Zeilen hoch sein (eine je App mit aktiver
     * Media-Session). Ohne diese Rückmeldung läge die erste App-Zeile hinter der
     * zweiten Now-Playing-Zeile: der Block liegt im `FrameLayout` über dem Raster,
     * und das kennt nur seinen festen oberen Rand.
     */
    override fun onHeightChanged(heightPx: Int) {
        val needed = if (heightPx <= 0) {
            baseGridTopPadding
        } else {
            max(baseGridTopPadding, heightPx + topRowPaddingTop + nowPlayingGap)
        }
        if (needed == grid.paddingTop) return
        grid.setPadding(grid.paddingLeft, needed, grid.paddingRight, grid.paddingBottom)
    }

    // ------------------------------------------------------------------
    // AppMenu.Listener — das Kontextmenü
    // ------------------------------------------------------------------

    override fun onMoveForward(entry: AppEntry) {
        moveForward(entry)
    }

    override fun onMoveBackward(entry: AppEntry) {
        moveBackward(entry)
    }

    /** Alle Hintergrund-Apps schließen — dieselbe Grenze wie [AppMenu.close]. */
    override fun onCloseAll() {
        val manager = getSystemService(ActivityManager::class.java) ?: return
        for (entry in allApps) {
            manager.killBackgroundProcesses(entry.packageName)
        }
        shortToast(R.string.toast_closed_all)
    }

    // ------------------------------------------------------------------
    // Aktionen
    // ------------------------------------------------------------------

    private fun launch(entry: AppEntry) {
        // Wir verlassen die Home-Seite — auch dann, wenn die gestartete App
        // durchsichtig ist und uns nur pausiert (onStop käme dann nicht).
        homeOnScreen = false
        lastApp.remember(entry.packageName)

        // Explizite Komponente + MAIN/LAUNCHER: genau das, was der Systemdienst
        // LauncherApps intern tut. Die Kategorie ist bei expliziter Komponente ohne
        // Bedeutung, deshalb startet das auch leanback-only Apps.
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(entry.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            shortToast(R.string.toast_launch_failed)
        }
    }

    private companion object {
        /** Einmal pro Minute reicht: eine Uhr mit Sekunden wäre ein Timer pro
         *  Sekunde — auf dem Chromecast messbar, ohne Mehrwert. */
        const val CLOCK_INTERVAL_MS = 30_000L

        /** Takt der grünen Markierung, solange die Home-Seite sichtbar ist. */
        const val ACTIVE_INTERVAL_MS = 5_000L
    }
}
