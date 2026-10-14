package com.piotv.navigate

/**
 * Was die Nutzungsstatistik über den Vordergrund sagt.
 *
 * Reine Daten ohne Android-Typen, damit [ModeDecider] ohne Gerät testbar bleibt.
 *
 * @property foreignPackage letzte FREMD-App, die in den Vordergrund kam.
 *   `null` heißt: im Beobachtungsfenster war keine fremde App oben.
 * @property foreignAlive lebt diese App noch? `true`, wenn ihr neuestes
 *   Lebenszyklus-Ereignis PAUSED oder RESUMED ist (sie läuft, höchstens
 *   pausiert — z. B. weil das Dock darüber liegt), `false` bei STOPPED.
 *
 * Warum nicht einfach "neuestes Vordergrund-Ereignis": beim HOME-Druck steht
 * der Launcher selbst schon als RESUMED in der Statistik (gemessen auf dem
 * Zielgerät). Das eigene Ereignis ist damit wertlos, und die Zählung "wie viele
 * eigene Ereignisse danach kamen" wäre ein Wettlauf gegen die Protokollierung.
 * Der Lebenszyklus der fremden App dagegen ist eindeutig.
 */
data class ForegroundSnapshot(
    val foreignPackage: String?,
    val foreignAlive: Boolean,
)
