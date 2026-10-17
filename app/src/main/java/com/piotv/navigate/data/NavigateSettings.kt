package com.piotv.navigate.data

/**
 * Die Einstellungen, die Navigate annimmt — heute genau eine.
 *
 * Sie kommt aus der Datei ([SettingsStore]), nicht aus `SharedPreferences`: eine
 * Einstellung des Nutzers gehört dorthin, wo sie auch per `adb push` hinkommt und
 * wo man sie lesen kann (dieselbe Regel wie bei der Reihenfolge, siehe
 * [OrderStore]). Der Merker der zuletzt gestarteten App ist etwas anderes — ein
 * Laufzeitzustand, den nach einem Prozess-Neustart niemand mehr wissen muss.
 *
 * @property nowPlaying Das „Now Playing“-Feld oben links zeigen? Es erscheint nur
 *   im **zugeklappten** Zustand — dort ist der Platz leer; aufgeklappt gehört er
 *   den Apps.
 */
data class NavigateSettings(
    val nowPlaying: Boolean = false,
)
