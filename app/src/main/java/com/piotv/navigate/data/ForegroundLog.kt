package com.piotv.navigate.data

/**
 * Faltet Vordergrund-Ereignisse zu „welche App war zuletzt vorne?" — reine
 * Regeln ohne Android-Typen, deshalb ohne Gerät prüfbar (ForegroundLogTest).
 *
 * Die Ereignisse kommen aus der Nutzungsstatistik (`UsageEvents`); hier steht
 * nur, was daraus folgt. Die Frage ist die Grundlage für „HOME oder BACK auf der
 * Home-Seite → zurück in die letzte App".
 */
object ForegroundLog {

    /**
     * Ereignistyp für „diese Activity ist in den Vordergrund gekommen".
     *
     * Bewusst die **Zahl** und nicht `UsageEvents.Event.ACTIVITY_RESUMED`: die
     * Konstante heißt erst ab API 29 so (davor `MOVE_TO_FOREGROUND`), und auf
     * einem älteren Gerät (minSdk 23) wäre ein Zugriff darauf ein
     * `NoSuchFieldError`. Der Wert ist in beiden Namen derselbe.
     */
    const val RESUMED = 1

    /**
     * @param events Paare aus Paketname und Ereignistyp, in zeitlicher Reihenfolge.
     * @return das Paket des **letzten** Vordergrund-Ereignisses, das nicht das
     *   eigene ist — oder `null`, wenn es keines gibt.
     */
    fun lastResumed(ownPackage: String, events: List<Pair<String, Int>>): String? {
        var last: String? = null
        for ((packageName, type) in events) {
            if (type != RESUMED) continue
            if (packageName == ownPackage) continue
            last = packageName
        }
        return last
    }
}

