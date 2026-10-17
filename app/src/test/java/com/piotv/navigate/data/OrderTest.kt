package com.piotv.navigate.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Die Anzeige-Reihenfolge — die **einzige** Einstellung dieser Oberfläche.
 *
 * Warum sie so wichtig ist: das Raster zeigt nur die erste Zeile, also
 * entscheidet die Reihenfolge darüber, welche Apps man ohne einen Tastendruck
 * sieht. Ein Fehler hier ist sofort sichtbar, nicht nur unschön.
 */
class OrderTest {

    private val apps = listOf("com.a", "com.b", "com.c", "com.d")

    @Test
    fun `ohne Reihenfolge bleibt die Grundordnung`() {
        assertEquals(apps, Order.arrange(emptyList(), apps) { it })
    }

    @Test
    fun `konfigurierte Pakete stehen vorne, in ihrer Reihenfolge`() {
        val order = listOf("com.c", "com.a")
        assertEquals(listOf("com.c", "com.a", "com.b", "com.d"), Order.arrange(order, apps) { it })
    }

    @Test
    fun `nicht installierte Eintraege fallen still weg`() {
        // Eine deinstallierte App darf die Reihenfolge nicht durcheinanderbringen
        // — die Liste räumt sich selbst auf.
        val order = listOf("com.gibtsnicht", "com.b")
        assertEquals(listOf("com.b", "com.a", "com.c", "com.d"), Order.arrange(order, apps) { it })
    }

    @Test
    fun `eine Reihenfolge ohne installierte Apps aendert nichts`() {
        assertEquals(apps, Order.arrange(listOf("x", "y"), apps) { it })
    }

    @Test
    fun `moveForward schiebt eine Position nach vorne`() {
        val order = listOf("com.a", "com.b", "com.c")
        assertEquals(listOf("com.b", "com.a", "com.c"), Order.moveForward(order, "com.b"))
    }

    @Test
    fun `moveForward laesst den ersten Eintrag stehen`() {
        val order = listOf("com.a", "com.b")
        assertEquals(order, Order.moveForward(order, "com.a"))
    }

    @Test
    fun `moveForward haengt eine unbekannte App an`() {
        // Aus dem „Rest“ eine Position nach vorne: vorher stand die App hinter
        // allen konfigurierten, jetzt direkt dahinter.
        assertEquals(listOf("com.a", "com.new"), Order.moveForward(listOf("com.a"), "com.new"))
    }

    @Test
    fun `zweimal moveForward bringt eine App zwei Positionen nach vorne`() {
        var order = listOf("com.a", "com.b", "com.c")
        order = Order.moveForward(order, "com.c")
        order = Order.moveForward(order, "com.c")
        assertEquals(listOf("com.c", "com.a", "com.b"), order)
    }

    // --- „Position nach hinten“ (Kontextmenü) -----------------------------

    @Test
    fun `moveBackward schiebt eine Position nach hinten`() {
        val order = listOf("com.a", "com.b", "com.c")
        assertEquals(listOf("com.a", "com.c", "com.b"), Order.moveBackward(order, "com.b"))
    }

    @Test
    fun `moveBackward laesst den letzten Eintrag stehen`() {
        val order = listOf("com.a", "com.b")
        assertEquals(order, Order.moveBackward(order, "com.b"))
    }

    @Test
    fun `moveBackward laesst unbekannte Pakete stehen`() {
        // Sie stehen ohnehin hinter allen konfigurierten — „weiter hinten“
        // ließe sich nur ausdrücken, indem man eine andere App vorzieht.
        val order = listOf("com.a")
        assertEquals(order, Order.moveBackward(order, "com.neu"))
    }
}
