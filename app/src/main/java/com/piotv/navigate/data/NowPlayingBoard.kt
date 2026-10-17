package com.piotv.navigate.data

/**
 * Die „Now Playing“-Zeilen: **eine je App** — und sie bleiben stehen.
 *
 * Bis 0.12 gab es genau eine Zeile: die Session, die „den Ton macht“. Das war
 * eine Auswahl, die niemand getroffen hat — läuft ein Hörbuch pausiert und
 * daneben ein Radiosender, verschwand eine der beiden Angaben. Seit 0.13
 * bekommt **jede App mit einer aktiven Session** ihre eigene Zeile.
 *
 * ## Zwei Regeln, die hier stehen und nicht in der Oberfläche
 *
 *  1. **Eine Zeile ändert sich nur, wenn sich für diese App etwas ändert.** Der
 *     Takt ist fünf Sekunden; ohne diese Regel würde jeder Durchlauf die Anzeige
 *     neu schreiben (Flackern, neue Cover-Anfragen).
 *  2. **Ein fehlgeschlagener Blick löscht nichts.** Ohne
 *     Benachrichtigungszugriff, bei einem sterbenden Session-Dienst oder
 *     während einer DRM-Umschaltung antwortet die Abfrage mit „ich weiß es
 *     nicht“. [observe] unterscheidet das (`null`) von „erfolgreich nachgesehen,
 *     es läuft nichts“ (leere Liste) — im ersten Fall bleibt der Stand stehen.
 *
 * Dazu eine Schonfrist: eine Session, die in **einem** Durchlauf fehlt,
 * verschwindet nicht sofort, sondern erst nach
 * [MISSING_POLLS_BEFORE_REMOVE] erfolgreichen Abfragen ohne sie. Auf dem
 * Zielgerät verschwindet eine Session beim Titelwechsel kurz; ohne die Frist
 * flackerte die Zeile.
 *
 * Kein Android-Typ in dieser Datei: die Regeln sind ohne Gerät prüfbar
 * (NowPlayingBoardTest), die Android-Eigenheiten stecken in [NowPlaying].
 */
class NowPlayingBoard {

    /** Was **eine** Abfrage gesehen hat: eine App und ihre Angaben. */
    data class Observed(val packageName: String, val info: NowPlayingInfo)

    /** Eine Zeile der Anzeige: die App und ihr fertiger Text. */
    data class Row(val packageName: String, val line: String)

    private var rows: List<Row> = emptyList()

    /** Wie oft eine App in Folge gefehlt hat — die Schonfrist. */
    private val missingPolls = HashMap<String, Int>()

    /** Die Zeilen in Anzeige-Reihenfolge. */
    fun rows(): List<Row> = rows

    /**
     * Übernimmt, was eine Abfrage gesehen hat.
     *
     * @param observed `null` heißt „die Abfrage ist fehlgeschlagen“ — dann
     *   bleibt der Stand stehen. Eine leere Liste heißt „erfolgreich
     *   nachgesehen, es läuft nichts“.
     * @return `true`, wenn sich die Anzeige ändern muss.
     */
    fun observe(observed: List<Observed>?): Boolean {
        if (observed == null) return false

        // 1. Was diese Abfrage gesehen hat — je App **einmal**. Die erste
        //    Session einer App gewinnt: NowPlaying sortiert „spielt“ nach vorne,
        //    die Auswahl ist damit die richtige.
        val fresh = LinkedHashMap<String, Row>()
        for (item in observed) {
            val packageName = item.packageName
            if (packageName.isEmpty() || fresh.containsKey(packageName)) continue
            val line = NowPlayingText.line(item.info)
            // Ohne Titel und ohne Interpret gibt es nichts zu zeigen — dann auch
            // keine leere Zeile.
            if (line.isEmpty()) continue
            fresh[packageName] = Row(packageName, line)
        }

        // 2. Zusammenführen: bestehende Zeilen behalten ihren Platz (nur ihr
        //    Text wird neu), neue Apps kommen hinten dazu. So springt beim
        //    zweiten laufenden Titel nicht die ganze Anzeige um.
        val merged = ArrayList<Row>(fresh.size + rows.size)
        for (row in rows) {
            val updated = fresh.remove(row.packageName)
            if (updated != null) {
                missingPolls.remove(row.packageName)
                merged.add(updated)
                continue
            }
            val missed = (missingPolls[row.packageName] ?: 0) + 1
            missingPolls[row.packageName] = missed
            if (missed < MISSING_POLLS_BEFORE_REMOVE) merged.add(row)
        }
        merged.addAll(fresh.values)

        // Zähler für Apps aufräumen, die keine Zeile mehr haben.
        missingPolls.keys.removeAll { key -> merged.none { it.packageName == key } }

        val changed = merged != rows
        rows = merged
        return changed
    }

    companion object {
        /**
         * So oft darf eine App in Folge fehlen, bevor ihre Zeile verschwindet.
         * Zwei Durchläufe sind zehn Sekunden — lang genug für einen
         * Titelwechsel, kurz genug, dass eine beendete App nicht liegen bleibt.
         */
        const val MISSING_POLLS_BEFORE_REMOVE = 2
    }
}
