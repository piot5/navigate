package com.piotv.navigate.data

/**
 * Die Einstellungen, die Navigate annimmt.
 *
 * Sie kommen aus der Datei ([SettingsStore]), nicht aus `SharedPreferences`: eine
 * Einstellung des Nutzers gehört dorthin, wo sie auch per `adb push` hinkommt und
 * wo man sie lesen kann (dieselbe Regel wie bei der Reihenfolge, siehe
 * [OrderStore]). Der Merker der zuletzt gestarteten App ist etwas anderes — ein
 * Laufzeitzustand, den nach einem Prozess-Neustart niemand mehr wissen muss.
 *
 * @property nowPlaying Das „Last seen“-Feld oben links zeigen? Es erscheint nur
 *   im **zugeklappten** Zustand — dort ist der Platz leer; aufgeklappt gehört er
 *   den Apps.
 * @property showClock Die Uhr oben rechts zeigen? Standard **an** (so war es bis
 *   0.13 immer). „Aus“ heißt nicht nur unsichtbar, sondern auch: kein Takt mehr
 *   ([com.piotv.navigate.ui.Ticker]) — ein Timer für ein Feld, das niemand sieht,
 *   wäre auf dem Chromecast messbar.
 * @property background Was hinter dem Raster liegt — [BackgroundStyle.DARK] ist
 *   der bisherige einfarbige Hintergrund und bleibt der Standard.
 * @property backgroundFile Der Pfad der Mediendatei für [BackgroundStyle.IMAGE];
 *   `null` heißt „der Standardname im externen App-Ordner“ (siehe
 *   [BackgroundPlan]). Ein absoluter Pfad gilt unverändert — damit funktioniert
 *   auch `/sdcard/…` (was auf Android 11+ nur für Medien-Ordner ohne Root lesbar
 *   ist, siehe docs/CONFIG.md).
 */
data class NavigateSettings(
    val nowPlaying: Boolean = false,
    val showClock: Boolean = true,
    val background: BackgroundStyle = BackgroundStyle.DARK,
    val backgroundFile: String? = null,
)
