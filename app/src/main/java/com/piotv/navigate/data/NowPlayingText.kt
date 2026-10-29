package com.piotv.navigate.data

/**
 * Was gerade läuft — die Angaben, die die Oberfläche braucht.
 *
 * Die Klasse kennt kein Android: gefüllt wird sie von [NowPlaying] (Media-Session
 * lesen), gelesen von [NowPlayingText] (daraus eine Zeile machen). Die Trennung
 * ist derselbe Schnitt wie bei der „zuletzt im Vordergrund"-Frage
 * ([LastApp.packageName]) und hat denselben Grund — die Regel, nicht die
 * Android-Eigenheit, war in diesem Projekt schon zweimal der Fehler. Deshalb ist
 * sie hier ohne Gerät prüfbar.
 */
data class NowPlayingInfo(
    val title: String? = null,
    val artist: String? = null,
    val playing: Boolean = true,
    /**
     * Name der App, aus der die Session kommt (z. B. „Spotify“).
     *
     * Gefüllt wird das Feld von der Home-Seite, nicht von [NowPlaying]: die Namen
     * kennt nur die App-Liste (`AppRepository`), und die soll hier nicht
     * hineinwachsen. Fehlt der Name, bleibt die Zeile wie vorher — er ist die
     * Zutat, nicht die Hauptsache.
     */
    val app: String? = null,
)

/**
 * Macht aus einer Session **eine** Zeile für das Feld oben links.
 *
 * Kein Symbol, keine zwei Zeilen, keine Schriftgrade: ein Fernseher aus drei
 * Metern Entfernung und ein 20-sp-Text vertragen genau eine Zeile. Die Zusage
 * lautet „was läuft und wo“ — nicht „wie heißt das Lied richtig“. Ein leerer Text
 * heißt „nichts zu zeigen“ und blendet das Feld aus, statt einen Platzhalter
 * hinzustellen.
 *
 * **Zwei Texte aus einer Session — und warum das nötig ist:** [content] ist der
 * Inhalt (App, Interpret, Titel), [line] ist der Inhalt **plus** dem Zusatz
 * „· pausiert“, wenn die Session pausiert. Der Verlauf hängt am **Inhalt**, nicht
 * an der Anzeigezeile: sonst wäre jeder Pause-Tipp ein „neuer Titel“ und derselbe
 * Film stünde zweimal in der Liste — einmal mit und einmal ohne „pausiert“ (auf
 * dem Zielgerät genau so gesehen). Die Anzeige dagegen **muss** den Pausenzusatz
 * tragen, sonst sähe eine pausierte Session aus wie eine laufende.
 */
object NowPlayingText {

    /** Zwischen Interpret und Titel. */
    const val SEPARATOR = " – "

    /** Zwischen App-Name und dem, was läuft. */
    const val APP_SEPARATOR = " · "

    /**
     * Zusatz, wenn die Session **pausiert** ist.
     *
     * Sie bleibt stehen, weil sie sonst wie „läuft“ aussähe: nach einem Pausen-Tipp
     * auf der Fernbedienung wäre der Titel sonst einfach weiter zu sehen.
     */
    const val PAUSED = " · pausiert"

    /**
     * Der **Inhalt** der Session ohne den Pausenzusatz — die Kennung für den
     * Verlauf (siehe [NowPlayingBoard.observe]). Leer heißt „nichts zu zeigen“.
     */
    fun content(info: NowPlayingInfo?): String {
        if (info == null) return ""
        val title = info.title?.trim().orEmpty()
        val artist = info.artist?.trim().orEmpty()
        val text = when {
            title.isNotEmpty() && artist.isNotEmpty() -> "$artist$SEPARATOR$title"
            title.isNotEmpty() -> title
            artist.isNotEmpty() -> artist
            else -> return ""
        }
        val app = info.app?.trim().orEmpty()
        return buildString {
            if (app.isNotEmpty()) append(app).append(APP_SEPARATOR)
            append(text)
        }
    }

    /**
     * Die **Anzeigezeile**: [content] plus „· pausiert“, wenn die Session pausiert
     * ist. Das ist der Text, der in der Oberfläche steht — und **nicht** das, was
     * in den Verlauf geschrieben wird.
     */
    fun line(info: NowPlayingInfo?): String {
        val content = content(info)
        if (content.isEmpty()) return ""
        return if (info!!.playing) content else content + PAUSED
    }
}
