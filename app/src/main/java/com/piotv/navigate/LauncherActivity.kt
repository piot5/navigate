package com.piotv.navigate

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.View
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.piotv.navigate.data.AppEntry
import com.piotv.navigate.data.AppRepository
import com.piotv.navigate.data.LastApp
import com.piotv.navigate.data.NowPlaying
import com.piotv.navigate.data.NowPlayingText
import com.piotv.navigate.data.NowPlayingTrack
import com.piotv.navigate.data.Order
import com.piotv.navigate.data.OrderStore
import com.piotv.navigate.data.SettingsStore
import com.piotv.navigate.ui.AppAdapter
import com.piotv.navigate.ui.GridColumns
import com.piotv.navigate.ui.IconLoader
import com.piotv.navigate.ui.SettingsActivity
import com.piotv.navigate.util.Background
import com.piotv.navigate.util.shortToast
import java.util.Date

/**
 * Die ganze Home-Seite — mehr hat dieser Launcher nicht.
 *
 * Ein HOME-Druck zeigt ein Raster mit allen startbaren Apps, die Uhr und — wenn
 * eingeschaltet — „Now Playing“ oben links. Zu sehen ist zunächst nur die
 * **erste Rasterzeile**; die Zelle ganz rechts darin ist der „…“-Knopf und
 * klappt den Rest auf. Einstellbar ist die Reihenfolge der Apps ([OrderStore],
 * per langem Drücken oder per `adb push`) und ob das Now-Playing-Feld erscheint
 * ([SettingsStore], langes OK auf „…“ öffnet die Einstellungen).
 *
 * Zwei Entscheidungen, die aus Fehlern gelernt sind:
 *
 *  - **Ein opakes Fenster.** Das Theme ist absichtlich das gewöhnlichste, das es
 *    gibt (siehe `res/values/styles.xml`). Die Fassungen bis 0.6 hatten ein
 *    translucentes Fenster und einen zweiten Zustand („untere Zeile“); auf dem
 *    Zielgerät war das Fenster dann gezeichnet, fokussiert und bedienbar — aber
 *    **nicht sichtbar**, mit einem kurzen Aufblitzen bei einem Klick. Ein
 *    translucentes Fenster hält die App darunter am Leben (nur pausiert); jede
 *    auf diesem Fernseher sichtbare App hat ein opakes Fenster.
 *  - **Eine Activity, ein Zustand.** Zwei HOME-Activities in einer App machen
 *    die Auflösung des Systems mehrdeutig (Auswahldialog statt Direktstart);
 *    einen zweiten Fenster-Zustand gibt es hier nicht mehr.
 */
class LauncherActivity : Activity() {

    private lateinit var grid: GridView
    private lateinit var clock: TextView

    /** Der „Now Playing“-Block oben links (nur im zugeklappten Zustand). */
    private lateinit var nowPlayingChip: View

    /** Die Zeile darin: App · Interpret – Titel [· pausiert]. */
    private lateinit var nowPlayingText: TextView

    /** Das Cover darin — unsichtbar, wenn die Session keines mitliefert. */
    private lateinit var nowPlayingArt: ImageView

    /** Der „…“-Knopf unter der App-Zeile. */
    private lateinit var more: TextView

    private lateinit var repository: AppRepository
    private lateinit var iconLoader: IconLoader
    private lateinit var order: OrderStore
    private lateinit var settings: SettingsStore

    /** Die Media-Sessions des Systems — die Quelle für das Feld oben links. */
    private lateinit var nowPlaying: NowPlaying

    /** Was gerade läuft, als **eine** Zeile (leer heißt: nichts zu zeigen). */
    private var nowPlayingLine = ""

    /** Die Session hinter der Zeile — für „OK holt die App zurück“. */
    private var nowPlayingTrack: NowPlayingTrack? = null

    /** Welche App war zuletzt im Vordergrund? (Grundlage für „zurück“.) */
    private lateinit var lastApp: LastApp

    /**
     * Steht die Home-Seite gerade sichtbar?
     *
     * Bewusst ein eigener Merker und **nicht** `isResumed`: das System pausiert
     * die Activity bei jedem HOME-Druck, *bevor* es den Intent zustellt
     * (gemessen: onPause → onNewIntent → onResume). `isResumed` ist dort immer
     * schon `false`.
     *
     * Gesetzt in [onResume], gelöscht in [onStop] — also genau dann, wenn die
     * Home-Seite den Bildschirm verlässt (andere App im Vordergrund) — und
     * zusätzlich beim Start einer App aus dieser Oberfläche heraus (für den
     * Fall, dass die gestartete App durchsichtig ist und uns nur pausiert).
     */
    private var homeOnScreen = false

    private var adapter: AppAdapter? = null

    /** Alle Apps in ihrer Grundordnung (sprachsortiert) — die Anzeige entsteht
     *  daraus plus der konfigurierten Reihenfolge. */
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

    private val clockHandler = Handler(Looper.getMainLooper())
    private val activeHandler = Handler(Looper.getMainLooper())
    private val nowPlayingHandler = Handler(Looper.getMainLooper())

    /**
     * Hält die grüne Schrift **laufend** aktuell (alle [ACTIVE_INTERVAL_MS]).
     *
     * Läuft nur, solange die Home-Seite sichtbar ist (onResume/onPause). Die
     * Abfrage ist ein Zeitstempel-Blick in die Nutzungsstatistik und läuft im
     * Hintergrund-Thread; der Grünton der zuletzt geöffneten App kommt aus dem
     * Merker und kostet keine Abfrage.
     */
    private val activeTick = object : Runnable {
        override fun run() {
            refreshActive()
            activeHandler.postDelayed(this, ACTIVE_INTERVAL_MS)
        }
    }

    private val clockTick = object : Runnable {
        override fun run() {
            clock.text = DateFormat.getTimeFormat(this@LauncherActivity).format(Date())
            clockHandler.postDelayed(this, CLOCK_INTERVAL_MS)
        }
    }

    /**
     * Hält das „Now Playing“-Feld aktuell (alle [NOW_PLAYING_INTERVAL_MS]).
     *
     * Läuft nur, solange die Home-Seite sichtbar ist (onResume/onPause) — wie die
     * Uhr und die grüne Schrift. Ist das Feld ausgeschaltet oder das Raster
     * aufgeklappt, steigt ein Durchlauf sofort wieder aus ([refreshNowPlaying]),
     * ein Timer ohne Arbeit ist also der einzige Preis.
     */
    private val nowPlayingTick = object : Runnable {
        override fun run() {
            refreshNowPlaying()
            nowPlayingHandler.postDelayed(this, NOW_PLAYING_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = AppRepository.get(this)
        iconLoader = IconLoader.get(this)
        order = OrderStore.get(this)
        settings = SettingsStore.get(this)
        lastApp = LastApp(this)
        nowPlaying = NowPlaying(this)

        setContentView(R.layout.activity_home)
        grid = findViewById(R.id.grid)
        more = findViewById(R.id.more)
        clock = findViewById(R.id.clock)
        nowPlayingChip = findViewById(R.id.now_playing)
        nowPlayingText = findViewById(R.id.now_playing_text)
        nowPlayingArt = findViewById(R.id.now_playing_art)

        more.setOnClickListener { setExpanded(!expanded) }
        // Langes OK auf „…“ öffnet die Einstellungen — der einzige Ort außerhalb
        // des Rasters, den diese Seite hat.
        more.setOnLongClickListener {
            openSettings()
            true
        }
        // OK auf „Now Playing“ holt die App zurück, die den Ton macht.
        nowPlayingChip.setOnClickListener { openPlayingApp() }

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
        startActiveUpdates()
        // Die Reihenfolge kann sich von außen geändert haben (`adb push`). Ein
        // `stat` kostet nichts, ein Dateibeobachter wäre Aufwand ohne Nutzen.
        if (order.reloadIfChanged()) applyOrder(allApps, activeApps, lastPackage)
        // Die Einstellungen können sich geändert haben — in den Einstellungen
        // selbst oder per `adb push` (dieselbe Drossel wie bei der Reihenfolge).
        settings.reloadIfChanged()
        startNowPlaying()
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
        stopActiveUpdates()
        stopNowPlaying()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Sprache oder Dichte geändert: App-Namen und Symbolgrößen stecken in
        // den zwischengespeicherten Daten und müssen neu entstehen. Bewusst
        // ohne Activity-Neustart (configChanges im Manifest) — ein Neustart wäre
        // auf dem Chromecast ein sichtbares Flackern.
        iconLoader.evictAll()
        iconsWarmed = false
        columns = GridColumns.compute(resources.configuration.screenWidthDp)
        grid.numColumns = columns
        setupAdapter()
        refreshApps(force = true)
    }

    /**
     * Bei Speicherdruck die Symbole wegwerfen, bevor das System den Prozess
     * dafür tötet — sie sind in Millisekunden wieder da (Platten-Cache).
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
     * BACK: erst das aufgeklappte Raster zuklappen, dann zurück in die letzte
     * App. Auf der zugeklappten Home-Seite ohne bekannte App bleibt es ohne
     * Wirkung.
     *
     * `finish()` wäre der naheliegende Weg — das System startet eine
     * Home-Activity danach aber sofort neu (auf dem Zielgerät gemessen: der Task
     * wechselte von t2667 auf t2668), also ein sichtbares Flackern für nichts.
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
     * Der Weg ist derselbe wie beim Antippen einer Zelle: der Launcher-Intent
     * der App. Er holt den **bestehenden** Task nach vorne (die App wird
     * fortgesetzt, nicht neu gestartet) und gibt ihr den Fokus zurück.
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
            showAppMenu(newAdapter.getItem(position))
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
     * unteren Rand; aufgeklappt bekommt es den ganzen Platz über dem „…“-Knopf
     * und scrollt. `layout_weight` statt `match_parent`, weil ein
     * `match_parent`-Kind in einer senkrechten LinearLayout den Knopf darunter
     * aus dem Bild schieben würde.
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
        updateNowPlayingVisibility()
    }

    private fun refreshApps(force: Boolean) {
        // Beides gehört in den Hintergrund-Thread: die App-Liste **und** die
        // Frage „welche Apps laufen noch?" (6-Stunden-Fenster der
        // Nutzungsstatistik). Auf dem Main-Thread wäre das genau der Posten, der
        // den HOME-Druck früher langsam gemacht hat.
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
     * @param active Pakete, die aktiv sind — sie bekommen die grüne
     *   Beschriftung (siehe [com.piotv.navigate.data.LastApp.recentlyUsed]).
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
     * speichern. Das ist der Weg, die Reihenfolge **auf dem Fernseher** zu
     * ändern (der andere ist die Datei, siehe docs/CONFIG.md).
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
     * Kontextmenü einer App (langes OK).
     *
     * Der Dialog des Frameworks — ein eigenes Menü wäre eine zweite
     * Bedienoberfläche für fünf Einträge.
     */
    private fun showAppMenu(entry: AppEntry) {
        val items = arrayOf(
            getString(R.string.menu_move_forward),
            getString(R.string.menu_move_backward),
            getString(R.string.menu_uninstall),
            getString(R.string.menu_close),
            getString(R.string.menu_close_all),
        )
        AlertDialog.Builder(this)
            .setTitle(entry.label)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> moveForward(entry)
                    1 -> moveBackward(entry)
                    2 -> uninstall(entry)
                    3 -> closeApp(entry)
                    else -> closeAllApps()
                }
            }
            .show()
    }

    /** Deinstallieren darf nur das System: wir zeigen seinen Dialog. */
    private fun uninstall(entry: AppEntry) {
        try {
            startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:${entry.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            shortToast(R.string.toast_uninstall_failed)
        }
    }

    /**
     * Schließt eine App im Hintergrund.
     *
     * `killBackgroundProcesses` ist der einzige Weg, den eine normale App hat —
     * und er greift nur, solange die App **nicht** im Vordergrund ist. Auf der
     * Home-Seite ist genau das der Fall (unser opakes Fenster hat die App
     * gestoppt). Eine gerade laufende App kann eine App nicht beenden; dafür
     * gibt es keinen öffentlichen Weg.
     */
    private fun closeApp(entry: AppEntry) {
        val activityManager = getSystemService(ActivityManager::class.java) ?: return
        activityManager.killBackgroundProcesses(entry.packageName)
    }

    /** Alle Hintergrund-Apps schließen — dieselbe Grenze wie [closeApp]. */
    private fun closeAllApps() {
        val activityManager = getSystemService(ActivityManager::class.java) ?: return
        for (entry in allApps) {
            activityManager.killBackgroundProcesses(entry.packageName)
        }
        shortToast(R.string.toast_closed_all)
    }

    // ------------------------------------------------------------------
    // Grüne Schrift: laufende Aktualisierung
    // ------------------------------------------------------------------

    private fun startActiveUpdates() {
        stopActiveUpdates()
        activeTick.run()
    }

    private fun stopActiveUpdates() {
        activeHandler.removeCallbacks(activeTick)
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
        // Erst direkt versuchen: ein `post` kostet einen zusätzlichen Durchlauf
        // der Nachrichtenschleife, und nach einem HOME-Druck ist die Ansicht
        // längst vermessen. Nur beim allerersten Aufbau wird nachgereicht.
        if (!grid.requestFocus()) grid.post { grid.requestFocus() }
    }

    // ------------------------------------------------------------------
    // Uhr
    // ------------------------------------------------------------------

    private fun startClock() {
        stopClock()
        clockTick.run()
    }

    private fun stopClock() {
        clockHandler.removeCallbacks(clockTick)
    }

    // ------------------------------------------------------------------
    // Now Playing
    // ------------------------------------------------------------------

    private fun startNowPlaying() {
        stopNowPlaying()
        nowPlayingTick.run()
    }

    private fun stopNowPlaying() {
        nowPlayingHandler.removeCallbacks(nowPlayingTick)
    }

    /**
     * Fragt die laufende Media-Session ab und stellt den Block danach ein.
     *
     * Abgefragt wird nur, wenn der Block überhaupt erscheinen kann
     * ([nowPlayingEnabled]) — ausgeschaltet oder aufgeklappt wäre jeder Aufruf
     * ein Binder-Aufruf für nichts. Die Abfrage läuft im Hintergrund-Thread, wie
     * die Nutzungsstatistik und aus demselben Grund.
     *
     * Ohne Benachrichtigungszugriff antwortet [NowPlaying] mit „nichts“; dann
     * bleibt der Block leer und unsichtbar — lieber nichts als falsch.
     */
    private fun refreshNowPlaying() {
        if (!nowPlayingEnabled()) {
            applyNowPlaying(null, "")
            return
        }
        val artSize = resources.getDimensionPixelSize(R.dimen.now_playing_art_size)
        Background.io.execute {
            val track = nowPlaying.current(artSize)
            Background.main.post {
                // Der App-Name kommt aus der App-Liste und wird deshalb auf dem
                // Main-Thread geholt — dort lebt die Liste.
                val label = track?.let { labelOf(it.packageName) }
                applyNowPlaying(track, NowPlayingText.line(track?.info?.copy(app = label)))
            }
        }
    }

    /** Der Anzeigename einer App aus dem Raster — oder das Paket selbst. */
    private fun labelOf(packageName: String): String? =
        findEntry(packageName)?.label ?: packageName.ifEmpty { null }

    private fun findEntry(packageName: String): AppEntry? =
        display.firstOrNull { it.packageName == packageName }
            ?: allApps.firstOrNull { it.packageName == packageName }

    private fun applyNowPlaying(track: NowPlayingTrack?, line: String) {
        nowPlayingTrack = track
        nowPlayingLine = line
        updateNowPlayingVisibility()
    }

    /**
     * Sichtbar ist der Block nur, wenn er eingeschaltet ist, das Raster
     * **zugeklappt** ist (aufgeklappt gehört der Platz den Apps) und es etwas zu
     * zeigen gibt.
     */
    private fun updateNowPlayingVisibility() {
        val show = nowPlayingEnabled() && nowPlayingLine.isNotEmpty()
        nowPlayingText.text = nowPlayingLine
        nowPlayingChip.contentDescription = nowPlayingLine
        // Verschwindet der Block, während er den Fokus hat, muss der Fokus
        // zurück ins Raster — sonst bedient die Fernbedienung ins Leere.
        if (!show && nowPlayingChip.hasFocus()) focusGrid()
        nowPlayingChip.visibility = if (show) View.VISIBLE else View.GONE
        if (show) applyNowPlayingArt(nowPlayingTrack) else hideArt()
    }

    /**
     * Cover aus der Session, sonst das App-Symbol aus dem eigenen Cache.
     *
     * Beides läuft über **einen** Merker (`tag`): die Anfrage nach einem Symbol
     * kann später zurückkommen, während längst ein anderes Lied läuft — dieselbe
     * Falle wie im Raster (siehe `AppAdapter`).
     */
    private fun applyNowPlayingArt(track: NowPlayingTrack?) {
        val art = track?.art
        if (art != null) {
            nowPlayingArt.tag = null
            nowPlayingArt.setImageBitmap(art)
            nowPlayingArt.visibility = View.VISIBLE
            return
        }
        val entry = track?.let { findEntry(it.packageName) }
        if (entry == null) {
            hideArt()
            return
        }
        val size = resources.getDimensionPixelSize(R.dimen.now_playing_art_size)
        val key = entry.iconKey(size)
        nowPlayingArt.tag = key
        val cached = iconLoader.get(entry, size) { loaded ->
            if (nowPlayingArt.tag == key) nowPlayingArt.setImageBitmap(loaded)
        }
        if (cached != null) {
            nowPlayingArt.setImageBitmap(cached)
        } else {
            nowPlayingArt.setImageResource(R.drawable.bg_icon_placeholder)
        }
        nowPlayingArt.visibility = View.VISIBLE
    }

    private fun hideArt() {
        nowPlayingArt.tag = null
        nowPlayingArt.setImageDrawable(null)
        nowPlayingArt.visibility = View.GONE
    }

    private fun nowPlayingEnabled(): Boolean = settings.current().nowPlaying && !expanded

    /**
     * OK auf „Now Playing“: zurück in die App, die den Ton macht.
     *
     * Zwei Wege, in dieser Reihenfolge:
     *
     *  1. `sessionActivity` der Session — der „Jetzt läuft“-Bildschirm, den die
     *     App **selbst** anbietet (`PendingIntent`). Das ist der vorgesehene Weg:
     *     er landet dort, wo der Nutzer weiterhört, nicht im Hauptmenü der App.
     *  2. Sonst der Launcher-Intent, genau wie aus dem Raster ([launch]).
     *
     * Ohne Session tut der Block nichts, statt irgendetwas zu starten.
     */
    private fun openPlayingApp() {
        val track = nowPlayingTrack ?: return
        val activity = track.activity
        if (activity != null) {
            try {
                homeOnScreen = false
                activity.send()
                return
            } catch (e: Exception) {
                // Abgelaufen, schon benutzt oder vom Ersteller gesperrt: dann der
                // normale Weg.
            }
        }
        val entry = findEntry(track.packageName)
        if (entry != null) {
            launch(entry)
        } else {
            shortToast(R.string.toast_launch_failed)
        }
    }

    /** Langes OK auf „…“: die Einstellungen (eigene Activity, siehe dort). */
    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
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
        // LauncherApps intern tut. Die Kategorie ist bei expliziter Komponente
        // ohne Bedeutung, deshalb startet das auch leanback-only Apps.
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

        /**
         * Takt des „Now Playing“-Feldes. Fünf Sekunden sind der Kompromiss: ein
         * Titelwechsel soll zügig ankommen, und ein Binder-Aufruf in diesem Takt
         * ist auf dem Zielgerät nichts — die Nutzungsstatistik daneben ist die
         * teurere von beiden. Läuft nur, solange die Home-Seite sichtbar ist.
         */
        const val NOW_PLAYING_INTERVAL_MS = 5_000L
    }
}
