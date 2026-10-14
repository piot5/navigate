package com.piotv.navigate

/**
 * Die zwei Zustände von Navigate. Es gibt genau einen sichtbaren Unterschied:
 *
 * - [GRID]: Home-Seite mit allen Apps, deckender Hintergrund, ganzes Fenster.
 * - [DOCK]: nur die untere Zeile, Fenster auf Dock-Höhe geschrumpft, die
 *   laufende App bleibt dahinter sichtbar.
 */
enum class LauncherMode {
    GRID,
    DOCK,
}
