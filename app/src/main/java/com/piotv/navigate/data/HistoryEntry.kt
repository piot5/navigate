package com.piotv.navigate.data

/**
 * Ein Verlaufseintrag: was lief, wo — und wann.
 *
 * Reines Kotlin ohne Android-Typen: geschrieben wird er vom Now-Playing-Takt
 * ([NowPlayingBoard]), gelesen vom Panel — die Regel, nicht die Verdrahtung,
 * ist hier prüfbar (HistoryCodecTest).
 *
 * @property packageName Paket der App, aus der der Titel kommt.
 * @property line Der fertige Zeilentext (wie er in der Anzeige stand).
 * @property seenAtMillis Wann der Titel gesehen wurde (Systemzeit).
 */
data class HistoryEntry(
    val packageName: String,
    val line: String,
    val seenAtMillis: Long,
)
