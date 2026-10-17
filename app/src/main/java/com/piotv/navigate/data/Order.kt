package com.piotv.navigate.data

/**
 * Die Anzeige-Reihenfolge — reine Regeln ohne Android-Typen, deshalb ohne Gerät
 * prüfbar (OrderTest).
 *
 * Die Reihenfolge ist das **einzige**, was diese Oberfläche einstellt: die
 * Rasteransicht zeigt nur die erste Zeile, also entscheidet die Reihenfolge
 * darüber, welche Apps man ohne einen Tastendruck sieht.
 *
 * Zwei Regeln:
 *  - [arrange] legt die konfigurierten Pakete nach vorne (in ihrer Reihenfolge)
 *    und hängt den Rest in seiner bisherigen (sprachsortierten) Reihenfolge an.
 *    Ein konfiguriertes Paket, das nicht installiert ist, fällt still weg.
 *  - [moveForward] schiebt ein Paket **eine** Position nach vorne. Ein Paket,
 *    das nicht in der Liste steht, wird angehängt — das ist genau eine Position
 *    nach vorne, weil es vorher hinter allen konfigurierten stand.
 */
object Order {

    fun <T> arrange(order: List<String>, items: List<T>, key: (T) -> String): List<T> {
        if (order.isEmpty()) return items
        val byKey = items.associateBy(key)
        val head = order.mapNotNull { byKey[it] }
        if (head.isEmpty()) return items
        val configured = order.toHashSet()
        val tail = items.filter { key(it) !in configured }
        return head + tail
    }

    /**
     * Schiebt ein Paket **eine** Position nach hinten.
     *
     * Ein Paket, das nicht in der Liste steht, bleibt stehen: es steht bereits
     * hinter allen konfigurierten, und „weiter hinten“ ließe sich nur ausdrücken,
     * indem man eine andere App nach vorne schiebt.
     */
    fun moveBackward(order: List<String>, packageName: String): List<String> {
        val index = order.indexOf(packageName)
        return when {
            index < 0 -> order
            index == order.lastIndex -> order
            else -> order.toMutableList().apply { add(index + 1, removeAt(index)) }
        }
    }

    fun moveForward(order: List<String>, packageName: String): List<String> {
        val index = order.indexOf(packageName)
        return when {
            // Steht schon ganz vorne: nichts tun.
            index == 0 -> order
            // Aus dem „Rest“: anhängen heißt eine Position nach vorne.
            index < 0 -> order + packageName
            else -> order.toMutableList().apply { add(index - 1, removeAt(index)) }
        }
    }
}
