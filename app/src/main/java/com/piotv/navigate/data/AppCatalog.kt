package com.piotv.navigate.data

/**
 * Auswahl und Reihenfolge der App-Liste — reine Regeln, keine Android-Typen,
 * deshalb ohne Gerät prüfbar (AppCatalogTest).
 *
 * Warum das herausgezogen wurde: die Regeln hier sind die Ursache des Fehlers,
 * der auf dem Zielgerät am meisten gekostet hat — Prime Video, WOW und ARD
 * fehlten im Raster, weil sie **nur** `CATEGORY_LEANBACK_LAUNCHER` tragen.
 * Vorher standen sie zwischen PackageManager-Aufrufen in [AppRepository] und
 * waren damit ungetestet.
 *
 * Die Reihenfolge der Eingabe ist Teil der Regel: wer zuerst kommt, gewinnt.
 * [AppRepository] sammelt deshalb erst `LAUNCHER`, dann `LEANBACK_LAUNCHER` —
 * Apps mit beiden Kategorien landen so über ihren LAUNCHER-Eintrag, denselben,
 * den der Hersteller-Launcher benutzt.
 */
object AppCatalog {

    /** Der Debug-Build läuft neben dem Release-Build und ist für sich selbst
     *  keine startbare App — beide Varianten fallen aus dem eigenen Raster. */
    const val DEBUG_SUFFIX = ".debug"

    /** Ein Kandidat, wie ihn eine PackageManager-Abfrage liefert. */
    data class Candidate(
        val packageName: String,
        val activityName: String,
        val label: String,
        val enabled: Boolean,
    )

    /**
     * Wendet die Regeln an und sortiert:
     *
     *  1. deaktivierte Apps fallen weg (sie ließen sich nicht starten),
     *  2. der eigene Launcher fällt weg — Release **und** Debug-Variante,
     *  3. je Paket gewinnt der **erste** Kandidat (siehe Klassenkommentar),
     *  4. eine leere Beschriftung wird durch den Paketnamen ersetzt,
     *  5. sortiert wird über [labelOrder] (im Produktivpfad ein `Collator`,
     *     also sprachabhängig — im Test ein fester Vergleicher).
     */
    fun select(
        candidates: List<Candidate>,
        ownPackage: String,
        labelOrder: Comparator<String>,
    ): List<Candidate> {
        val ownBase = ownPackage.removeSuffix(DEBUG_SUFFIX)
        val byPackage = LinkedHashMap<String, Candidate>(candidates.size)
        for (candidate in candidates) {
            if (!candidate.enabled) continue
            if (candidate.packageName == ownBase || candidate.packageName == ownBase + DEBUG_SUFFIX) continue
            if (byPackage.containsKey(candidate.packageName)) continue
            byPackage[candidate.packageName] = candidate.copy(
                label = candidate.label.ifBlank { candidate.packageName },
            )
        }
        return byPackage.values.sortedWith { first, second -> labelOrder.compare(first.label, second.label) }
    }
}
