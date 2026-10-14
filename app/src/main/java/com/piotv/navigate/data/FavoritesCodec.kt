package com.piotv.navigate.data

/**
 * Favoriten als kommaseparierte Paketnamen-Zeile — das Format in der
 * Konfigurationsdatei und in den SharedPreferences.
 *
 * Warum kein `StringSet`: ein Set verliert die Reihenfolge, und genau die ist
 * im Dock die Anordnung. Komma statt Zeilenumbruch, weil der Wert so in einer
 * `key=value`-Zeile der Konfigurationsdatei stehen kann. Paketnamen können kein
 * Komma enthalten.
 *
 * Reines Kotlin ohne Android-Typen → testbar (FavoritesCodecTest).
 */
object FavoritesCodec {

    /**
     * Mehr passt nicht in die Zeile. Gemessen am Layout: 9 Elemente à 96dp
     * (Favoriten + Home + Navigate-Einstellungen + Einstellungen) sind bei
     * 960dp Bildschirmbreite abzüglich der Dock-Ränder das Maximum.
     * Dieselbe Zahl steht als HARD_MAX_FAVORITES in [ConfigCodec].
     */
    const val MAX = 6

    private const val SEPARATOR = ","

    fun decode(raw: String?): List<String> =
        raw?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    fun encode(packages: List<String>): String = packages.joinToString(SEPARATOR)

    fun isFull(packages: List<String>, max: Int = MAX): Boolean = packages.size >= max

    /** Ist [packageName] Favorit, fliegt er raus — sonst kommt er hinten dran. */
    fun toggle(packages: List<String>, packageName: String): List<String> =
        if (packageName in packages) packages - packageName else packages + packageName

    /**
     * Verschiebt einen Favoriten um eine Position. Wird von den
     * Navigate-Einstellungen benutzt (◀ ▶ auf einer Favoritenzeile).
     */
    fun move(packages: List<String>, packageName: String, offset: Int): List<String> {
        val from = packages.indexOf(packageName)
        if (from < 0) return packages
        val to = (from + offset).coerceIn(0, packages.size - 1)
        if (to == from) return packages
        val result = packages.toMutableList()
        result.add(to, result.removeAt(from))
        return result
    }
}
