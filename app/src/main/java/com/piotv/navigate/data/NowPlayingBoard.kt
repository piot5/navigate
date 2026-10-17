package com.piotv.navigate.data

/**
 * Die „Now Playing“-Zeilen: **eine je App** — und sie bleiben stehen.
 *
 * Bis 0.12 gab es genau eine Zeile: die Session, die „den Ton macht“. Läuft ein
 * Hörbuch pausiert und daneben ein Radiosender, verschwand damit eine der beiden
 * Angaben. Seit 0.13 bekommt **jede App mit einer aktiven Session** ihre eigene
 * Zeile.
 *
 * ## Wann sich eine Zeile ändert — und wann sie geht
 *
 * Hier sitzt die Regel, und sie war in 0.13.2 **falsch**: der Eintrag einer App
 * verschwand nach wenigen Sekunden (auf dem Zielgerät gesehen). Ursache war, dass
 * drei verschiedene Dinge gleich behandelt wurden. Sie sind es nicht:
 *
 * | Was die Abfrage sagt | Was passiert |
 * |---|---|
 * | Ein Titel (spielt oder pausiert) | Zeile anlegen bzw. **nur bei geändertem Text** neu schreiben |
 * | Session da, aber **ohne Titel** (Ladescreen, Werbung, Menü) | Zeile bleibt stehen, wie sie ist |
 * | **Keine** Session für diese App | Zeile bleibt stehen (siehe unten) |
 * | Session meldet **beendet** (`STOPPED`/`ERROR`) | Zeile verschwindet nach [MISSING_POLLS_BEFORE_REMOVE] Abfragen |
 * | Abfrage **fehlgeschlagen** (`null`) | nichts ändert sich |
 *
 * Warum das so fein sein muss: eine TV-App zerstört und erzeugt ihre Session beim
 * Navigieren, beim Titelwechsel und während der Werbung — auf dem Zielgerät war
 * die Session von WOW nach dem Film **ganz weg**, ohne je `STOPPED` zu melden. Wer
 * das als „die App ist weg“ liest, löscht eine Zeile, die der Nutzer gerade liest,
 * und schreibt sie Sekunden später wieder hin. Genau das war der Fehler.
 *
 * **Der Preis, offen gesagt:** eine App, die ihre Session verliert, ohne `STOPPED`
 * zu melden, behält ihre Zeile — bis der Launcher-Prozess neu startet (auf dem
 * Chromecast mit 1,4 GB der Normalfall nach ein paar Stunden). Das ist die
 * bewusste Entscheidung: eine Zeile, die man gerade liest, darf nicht unter dem
 * Lesen verschwinden.
 *
 * Die Zusage bleibt: **eine Zeile ändert sich nur, wenn sich für diese App etwas
 * ändert.** Der Vergleich ist der fertige Text; ist er gleich, wird nichts
 * geschrieben und kein neues Cover geholt.
 *
 * Kein Android-Typ in dieser Datei: die Regeln sind ohne Gerät prüfbar
 * (NowPlayingBoardTest), die Android-Eigenheiten stecken in [NowPlaying].
 */
class NowPlayingBoard {

    /** Was **eine** Abfrage über eine App sagt. */
    data class Observed(
        val packageName: String,
        /**
         * Die Angaben der Session — `null` heißt „die Session gibt gerade nichts
         * her“ (Ladescreen, Werbung, kein Titel). Die Zeile bleibt dann stehen.
         */
        val info: NowPlayingInfo?,
        /** Die App hat beendet: die Session meldet `STOPPED`/`ERROR`. */
        val ended: Boolean = false,
    )

    /** Eine Zeile der Anzeige: die App und ihr fertiger Text. */
    data class Row(val packageName: String, val line: String)

    private var rows: List<Row> = emptyList()

    /** Wie oft eine App in Folge **beendet** gemeldet hat — die Schonfrist. */
    private val endedPolls = HashMap<String, Int>()

    /** Die Zeilen in Anzeige-Reihenfolge. */
    fun rows(): List<Row> = rows

    /**
     * Übernimmt, was eine Abfrage gesehen hat.
     *
     * @param observed `null` heißt „die Abfrage ist fehlgeschlagen“ (kein Zugriff,
     *   Session-Dienst antwortet nicht) — dann bleibt alles, wie es ist.
     * @return `true`, wenn sich die Anzeige ändern muss.
     */
    fun observe(observed: List<Observed>?): Boolean {
        if (observed == null) return false

        // 1. Was diese Abfrage über welche App sagt — je App **einmal**. Die erste
        //    Session einer App gewinnt: NowPlaying sortiert „spielt“ nach vorne.
        val fresh = LinkedHashMap<String, Observed>()
        for (item in observed) {
            if (item.packageName.isEmpty() || fresh.containsKey(item.packageName)) continue
            fresh[item.packageName] = item
        }

        // 2. Zusammenführen: bestehende Zeilen behalten ihren Platz (nur ihr Text
        //    wird neu), neue Apps kommen hinten dazu. So springt beim zweiten
        //    laufenden Titel nicht die ganze Anzeige um.
        val merged = ArrayList<Row>(rows.size + fresh.size)
        for (row in rows) {
            val seen = fresh.remove(row.packageName)
            if (seen == null) {
                // Gar nicht gesehen: die Session ist weg (App im Hintergrund,
                // Titelwechsel, App beendet). Die Zeile bleibt stehen.
                merged.add(row)
                continue
            }
            if (seen.ended) {
                // Ausdrücklich beendet — aber erst nach zwei Abfragen: zwischen
                // zwei Folgen steht die Session kurz auf STOPPED.
                val missed = (endedPolls[row.packageName] ?: 0) + 1
                endedPolls[row.packageName] = missed
                if (missed < MISSING_POLLS_BEFORE_REMOVE) merged.add(row)
                continue
            }
            endedPolls.remove(row.packageName)
            val line = NowPlayingText.line(seen.info)
            // Nichts zu lesen (Ladescreen, Werbung): die Zeile bleibt, wie sie
            // ist — die App ist ja da und bringt denselben Titel zurück.
            merged.add(if (line.isEmpty()) row else Row(row.packageName, line))
        }

        // 3. Apps, die es vorher nicht gab: nur mit Text ist das eine Zeile wert.
        for (seen in fresh.values) {
            if (seen.ended) continue
            val line = NowPlayingText.line(seen.info)
            if (line.isEmpty()) continue
            merged.add(Row(seen.packageName, line))
        }

        // Zähler für Apps aufräumen, die keine Zeile mehr haben.
        endedPolls.keys.removeAll { key -> merged.none { it.packageName == key } }

        val changed = merged != rows
        rows = merged
        return changed
    }

    companion object {
        /**
         * So oft darf eine App in Folge **beendet** melden, bevor ihre Zeile
         * verschwindet. Zwei Durchläufe sind zehn Sekunden — lang genug für den
         * Moment zwischen zwei Folgen, in dem die Session kurz auf `STOPPED` steht.
         */
        const val MISSING_POLLS_BEFORE_REMOVE = 2
    }
}
