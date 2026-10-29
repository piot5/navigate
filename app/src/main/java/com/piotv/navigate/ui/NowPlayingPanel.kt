package com.piotv.navigate.ui

import android.app.Activity
import android.app.PendingIntent
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.piotv.navigate.R
import com.piotv.navigate.data.AppEntry
import com.piotv.navigate.data.HistoryEntry
import com.piotv.navigate.data.HistoryStore
import com.piotv.navigate.data.NowPlaying
import com.piotv.navigate.data.NowPlayingBoard
import com.piotv.navigate.data.NowPlayingText
import com.piotv.navigate.data.NowPlayingTrack
import com.piotv.navigate.util.Background

/**
 * Die „Now Playing“-Zeilen oben links: **aktuelle je App** mit aktiver
 * Media-Session, darunter der **Verlauf** aus `history.conf` — neueste oben,
 * ältere werden beim Scrollen dynamisch nachgeladen.
 *
 * Bis 0.12 war das ein einzelner Block mit einer Zeile — der Session, die „den
 * Ton macht“. Läuft ein Hörbuch pausiert und daneben ein Radiosender, verschwand
 * damit eine der beiden Angaben; außerdem wurde der Block bei jedem Durchlauf neu
 * geschrieben. Seit 0.13 gilt:
 *
 *  - **Eine Zeile je App.** Welche Session einer App zählt (die spielende, sonst
 *    die erste mit Titel), entscheidet [NowPlaying.active].
 *  - **Eine Zeile ändert sich nur, wenn sich für diese App etwas ändert.** Der
 *    Vergleich steht in [bind] (Text gegen Text), die Regel dahinter in
 *    [NowPlayingBoard]. Ohne sie würde alle fünf Sekunden dieselbe Zeile neu
 *    gesetzt und ein neues Cover geholt.
 *  - **Ein fehlgeschlagener Blick löscht nichts**: [NowPlaying.active] gibt dann
 *    `null` statt einer leeren Liste.
 *
 * Seit der Verlauf-Fassung gilt zusätzlich:
 *
 *  - **Nichts geht verloren.** Ersetzt ein neuer Titel die Zeile einer App,
 *    wandert der alte in den Verlauf ([HistoryStore]) — statt zu verschwinden.
 *  - **Neueste oben.** Die Anzeige zeigt Live-Zeilen zuerst, darunter den Verlauf
 *    neueste-zuerst. Ältere Einträge werden beim Runterscrollen seitenweise
 *    ([HistoryStore.PAGE_SIZE]) nachgeladen — die Datei wird nie am Stück in
 *    Views verwandelt.
 *
 * Die Views werden je Zeile **einmal** gebaut und danach wiederverwendet
 * ([liveRows], [historyRows]) — dieselbe Idee wie `convertView` im Raster.
 * Eine neue App reiht sich hinten ein, eine verschwundene Zeile fällt aus dem
 * Baum.
 *
 * Was diese Klasse **nicht** weiß: welche App-Namen es gibt ([Listener.entryOf]),
 * was ein Klick auf eine Zeile tun soll ([Listener.openRow]) und wie viel Platz
 * das Raster darüber braucht ([Listener.onHeightChanged]).
 */
class NowPlayingPanel(
    private val activity: Activity,
    private val container: LinearLayout,
    private val listener: Listener,
    private val source: NowPlaying,
    private val iconLoader: IconLoader,
    private val history: HistoryStore,
    intervalMs: Long,
) {

    /**
     * Was die Home-Seite beisteuern muss.
     *
     * Bewusst ein Zuhörer und keine Activity-Referenz: App-Liste, Starten und
     * Fokus liegen dort, und der Block soll nicht mit ihnen zusammenwachsen
     * (genau daran saß die 769-Zeilen-Activity von 0.12).
     */
    interface Listener {
        /** Ist das Feld eingeschaltet **und** das Raster zugeklappt? */
        fun isEnabled(): Boolean

        /** Der Eintrag einer App — für den Namen und das Symbol als Rückfall. */
        fun entryOf(packageName: String): AppEntry?

        /**
         * OK auf einer Zeile: die Session anspringen, sonst die App starten.
         *
         * @param sessionActivity der „Jetzt läuft“-Bildschirm der App oder `null`.
         */
        fun openRow(packageName: String, sessionActivity: PendingIntent?)

        /** Der Block verschwindet, während er den Fokus hat. */
        fun onFocusLost()

        /** So viel Höhe (px) braucht der Block; `0` heißt „er ist aus“. */
        fun onHeightChanged(heightPx: Int)
    }

    private val ticker = Ticker(intervalMs) { refresh() }

    /** Die Regel dahinter, welche Zeilen es gibt und wann sie sich ändern. */
    private val board = NowPlayingBoard()

    /** Die Live-Zeilen-Views, nach Paket — gebaut wird jede genau einmal. */
    private val liveRows = LinkedHashMap<String, View>()

    /** Die Verlauf-Zeilen-Views, nach Zeit+Paket+Zeile — gebaut wird jede genau einmal. */
    private val historyRows = LinkedHashMap<String, View>()

    /** Wie viele Verlaufseinträge gerade gezeigt werden (Seiten à PAGE_SIZE). */
    private var historyShown = HistoryStore.PAGE_SIZE

    /** Der Stand des Verlaufs, für den [historyShown] gilt — sonst zurücksetzen. */
    private var historyRevision = -1

    /** Die zuletzt gesehenen Angaben je App — für „OK“ und das Cover. */
    private val tracks = HashMap<String, NowPlayingTrack>()

    private var reportedHeight = -1

    init {
        // Die Höhe des Blocks bestimmt, wo das Raster anfangen darf (siehe
        // LauncherActivity.onHeightChanged): zwei Now-Playing-Zeilen dürfen nicht
        // hinter der ersten App-Zeile liegen.
        container.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) reportHeight(bottom - top)
        }
    }

    fun start() = ticker.start()

    fun stop() = ticker.stop()

    /** Zeichnet den gespeicherten Stand und regelt die Sichtbarkeit. */
    fun refreshVisibility() {
        render()
        updateVisibility()
    }

    /**
     * Fragt die Media-Sessions ab und stellt die Zeilen danach ein.
     *
     * Abgefragt wird nur, wenn der Block überhaupt erscheinen kann
     * ([Listener.isEnabled]) — ausgeschaltet oder aufgeklappt wäre jeder Aufruf
     * ein Binder-Aufruf für nichts. Die Abfrage läuft im Hintergrund-Thread, wie
     * die Nutzungsstatistik und aus demselben Grund.
     */
    private fun refresh() {
        if (!listener.isEnabled()) {
            refreshVisibility()
            return
        }
        val artSize = activity.resources.getDimensionPixelSize(R.dimen.now_playing_art_size)
        Background.io.execute {
            val found = source.active(artSize)
            Background.main.post {
                if (!listener.isEnabled()) {
                    refreshVisibility()
                    return@post
                }
                // Der App-Name kommt aus der App-Liste und wird deshalb auf dem
                // Main-Thread geholt — dort lebt die Liste. Ist die Zeile leer
                // (Ladescreen, Werbung), wird `info = null` übergeben: die Anzeige
                // behält dann, was sie hatte, statt die Zeile zu löschen.
                val observed = found?.map { track ->
                    val label = listener.entryOf(track.packageName)?.label ?: track.packageName
                    NowPlayingBoard.Observed(
                        packageName = track.packageName,
                        info = track.info.copy(app = label)
                            .takeIf { NowPlayingText.line(it).isNotEmpty() },
                        ended = track.ended,
                    )
                }
                // `null` heißt „Abfrage fehlgeschlagen“: die Zeilen bleiben, wie
                // sie sind, und auch die Cover werden nicht neu geholt.
                found?.forEach { tracks[it.packageName] = it }
                history.reloadIfChanged()
                board.observe(observed, history, System.currentTimeMillis())
                render()
                updateVisibility()
            }
        }
    }

    /**
     * Bringt die Views in die Reihenfolge des Boards — und nur den Text neu.
     *
     * Darunter der Verlauf: Live-Zeilen zuerst (aktuelle Titel), dann die
     * Verlaufseinträge neueste-zuerst. Gezeigt werden höchstens [historyShown];
     * der Rest kommt beim Runterscrollen dazu ([loadMoreIfNeeded]) — die Datei
     * wird nie am Stück in Views verwandelt.
     */
    private fun render() {
        val current = board.rows()
        val spacing = activity.resources.getDimensionPixelSize(R.dimen.now_playing_row_spacing)
        var index = 0
        for (row in current) {
            val view = liveRows[row.packageName] ?: inflateLive(row.packageName)
            if (container.getChildAt(index) !== view) {
                container.removeView(view)
                container.addView(view, index)
            }
            (view.layoutParams as? LinearLayout.LayoutParams)?.topMargin =
                if (index == 0) 0 else spacing
            bindLive(view, row)
            index++
        }
        // Verlaufseinträge, die gerade als Live-Zeile zu sehen sind, nicht
        // doppelt zeigen: derselbe Text stünde sonst zweimal da. Verglichen wird
        // der **Inhalt** (ohne „· pausiert“) — der Verlauf speichert ihn so, die
        // Live-Zeile trägt den Pausenzusatz nur in der Anzeige.
        val liveContent = current.map { it.packageName to it.content }.toSet()
        val allHistory = history.latest()
        if (allHistory.hashCode() != historyRevision) {
            historyRevision = allHistory.hashCode()
            historyShown = HistoryStore.PAGE_SIZE
        }
        val visible = allHistory.filterNot { liveContent.contains(it.packageName to it.line) }
            .take(historyShown)
        for (entry in visible) {
            val key = historyKey(entry)
            val view = historyRows[key] ?: inflateHistory(key, entry)
            if (container.getChildAt(index) !== view) {
                container.removeView(view)
                container.addView(view, index)
            }
            (view.layoutParams as? LinearLayout.LayoutParams)?.topMargin = spacing
            bindHistory(view, entry)
            index++
        }
        // Die letzte sichtbare Verlaufzeile lädt beim Fokus die nächste Seite
        // nach — auf der Fernbedienung gibt es kein Scroll-Ende, nur ▲ ▼.
        // Deshalb Fokus, nicht Scroll-Listener. Der Listener steht nur auf der
        // letzten Zeile; alle anderen tragen keinen (sonst würde jede
        // fokussierte Zeile nachladen).
        for ((key, view) in historyRows) {
            view.onFocusChangeListener = null
        }
        visible.lastOrNull()?.let { last ->
            historyRows[historyKey(last)]?.onFocusChangeListener =
                android.view.View.OnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) loadMoreIfNeeded(allHistory.size)
                }
        }
        // Zeilen, die es nicht mehr gibt, aus dem Baum und aus den Karten nehmen.
        while (container.childCount > index) {
            val stale = container.getChildAt(index)
            container.removeViewAt(index)
            liveRows.entries.removeAll { it.value === stale }
            historyRows.entries.removeAll { it.value === stale }
        }
        tracks.keys.retainAll(current.map { it.packageName }.toSet())
    }

    /**
     * Lädt die nächste Seite des Verlaufs nach — wenn es noch etwas gibt.
     *
     * @param total wie viele Verlaufseinträge es insgesamt gibt.
     */
    private fun loadMoreIfNeeded(total: Int) {
        if (historyShown >= total) return
        historyShown = minOf(historyShown + HistoryStore.PAGE_SIZE, total)
        render()
        updateVisibility()
    }

    private fun historyKey(entry: HistoryEntry): String =
        "${entry.seenAtMillis}|${entry.packageName}|${entry.line}"

    private fun inflateLive(packageName: String): View {
        val view = LayoutInflater.from(activity)
            .inflate(R.layout.item_now_playing, container, false)
        liveRows[packageName] = view
        return view
    }

    private fun inflateHistory(key: String, entry: HistoryEntry): View {
        val view = LayoutInflater.from(activity)
            .inflate(R.layout.item_now_playing, container, false)
        view.alpha = HISTORY_ALPHA
        historyRows[key] = view
        view.setOnClickListener { open(entry.packageName) }
        view.contentDescription = entry.line
        return view
    }

    /**
     * Schreibt die Live-Zeile — **nur wenn sich für diese App etwas geändert hat**.
     *
     * Der Text ist der Vergleichsschlüssel: er enthält App, Interpret, Titel und
     * den Pausenzustand. Ist er gleich, bleibt auch das Cover stehen (und wird
     * nicht neu geholt) — das ist die Zusage „die Zeile ändert sich nur, wenn Now
     * Playing sich für diese App ändert“.
     */
    private fun bindLive(view: View, row: NowPlayingBoard.Row) {
        val text = view.findViewById<TextView>(R.id.now_playing_row_text)
        view.setOnClickListener { open(row.packageName) }
        if (text.text?.toString() == row.line) return
        text.text = row.line
        // Für Screenreader ist die Zeile eine Zeile, kein Knopf.
        view.contentDescription = row.line
        applyArt(view.findViewById(R.id.now_playing_row_art), row.packageName)
    }

    /**
     * Schreibt die Verlauf-Zeile — einmal, danach bleibt sie.
     *
     * Kein Cover aus der Session (die ist längst weg), nur das App-Symbol als
     * Rückfall. Die Zeile ist gedimmt ([HISTORY_ALPHA]): sie ist Vergangenheit,
     * nicht Gegenwart.
     */
    private fun bindHistory(view: View, entry: HistoryEntry) {
        val text = view.findViewById<TextView>(R.id.now_playing_row_text)
        if (text.text?.toString() != entry.line) {
            text.text = entry.line
        }
        view.contentDescription = entry.line
        val art = view.findViewById<ImageView>(R.id.now_playing_row_art)
        val entry_ = listener.entryOf(entry.packageName)
        if (entry_ == null) {
            hideArt(art)
            return
        }
        val size = activity.resources.getDimensionPixelSize(R.dimen.now_playing_art_size)
        val key = entry_.iconKey(size)
        art.tag = key
        val cached = iconLoader.get(entry_, size) { loaded ->
            if (art.tag == key) art.setImageBitmap(loaded)
        }
        if (cached != null) {
            art.setImageBitmap(cached)
        } else {
            art.setImageResource(R.drawable.bg_icon_placeholder)
        }
        art.visibility = View.VISIBLE
    }

    /** Cover aus der Session, sonst das App-Symbol aus dem eigenen Cache. */
    private fun applyArt(art: ImageView, packageName: String) {
        val cover = tracks[packageName]?.art
        if (cover != null) {
            art.tag = null
            art.setImageBitmap(cover)
            art.visibility = View.VISIBLE
            return
        }
        val entry = listener.entryOf(packageName)
        if (entry == null) {
            hideArt(art)
            return
        }
        // Derselbe Merker (`tag`) wie im Raster: die Anfrage nach einem Symbol
        // kann später zurückkommen, während die Zeile längst etwas anderes zeigt.
        val size = activity.resources.getDimensionPixelSize(R.dimen.now_playing_art_size)
        val key = entry.iconKey(size)
        art.tag = key
        val cached = iconLoader.get(entry, size) { loaded ->
            if (art.tag == key) art.setImageBitmap(loaded)
        }
        if (cached != null) {
            art.setImageBitmap(cached)
        } else {
            art.setImageResource(R.drawable.bg_icon_placeholder)
        }
        art.visibility = View.VISIBLE
    }

    private fun hideArt(art: ImageView) {
        art.tag = null
        art.setImageDrawable(null)
        art.visibility = View.GONE
    }

    /**
     * Sichtbar sind die Zeilen nur, wenn das Feld eingeschaltet ist, das Raster
     * **zugeklappt** ist (aufgeklappt gehört der Platz den Apps) und es etwas zu
     * zeigen gibt. Der gespeicherte Stand bleibt dabei erhalten: beim Zuklappen
     * steht er sofort wieder da, statt auf die nächste Abfrage zu warten.
     */
    private fun updateVisibility() {
        val show = listener.isEnabled() && (board.rows().isNotEmpty() || history.current().isNotEmpty())
        // Verschwindet der Block, während er den Fokus hat, muss der Fokus
        // zurück ins Raster — sonst bedient die Fernbedienung ins Leere.
        if (!show && container.hasFocus()) listener.onFocusLost()
        // Sichtbar wird die äußere ScrollView (activity_home.xml): der innere
        // Container trägt die Zeilen, die ScrollView begrenzt die Höhe.
        val scroll = (container.parent as? View) ?: container
        scroll.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) reportHeight(0)
    }

    /** OK auf einer Zeile: erst der „Jetzt läuft“-Bildschirm, sonst die App. */
    private fun open(packageName: String) {
        listener.openRow(packageName, tracks[packageName]?.activity)
    }

    /**
     * Meldet die Höhe des Blocks, wenn sie sich ändert. Das Raster beginnt
     * darunter — sonst läge die erste App-Zeile hinter der zweiten Zeile.
     */
    private fun reportHeight(heightPx: Int) {
        if (heightPx == reportedHeight) return
        reportedHeight = heightPx
        listener.onHeightChanged(heightPx)
    }

    companion object {
        /**
         * Takt des Feldes. Fünf Sekunden sind der Kompromiss: ein Titelwechsel
         * soll zügig ankommen, und ein Binder-Aufruf in diesem Takt ist auf dem
         * Zielgerät nichts — die Nutzungsstatistik daneben ist die teurere von
         * beiden. Läuft nur, solange die Home-Seite sichtbar ist.
         */
        const val NOW_PLAYING_INTERVAL_MS = 5_000L

        /**
         * Verlauf ist Vergangenheit: gedimmt, aber lesbar. 60 % Deckkraft
         * trennen „läuft“ von „lief“, ohne dass die Zeile verschwindet.
         */
        const val HISTORY_ALPHA = 0.6f
    }
}
