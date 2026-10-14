package com.piotv.navigate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesCodecTest {

    @Test
    fun `leerer oder fehlender Wert ergibt eine leere Liste`() {
        assertEquals(emptyList<String>(), FavoritesCodec.decode(null))
        assertEquals(emptyList<String>(), FavoritesCodec.decode(""))
        assertEquals(emptyList<String>(), FavoritesCodec.decode(",,  ,"))
    }

    @Test
    fun `Reihenfolge bleibt beim Kodieren und Dekodieren erhalten`() {
        // Die Reihenfolge IST die Anordnung im Dock — ein StringSet hätte sie
        // verloren, deshalb der Umweg über eine Zeile.
        val favorites = listOf("com.netflix.ninja", "com.google.android.youtube.tv", "com.termux")
        assertEquals(favorites, FavoritesCodec.decode(FavoritesCodec.encode(favorites)))
    }

    @Test
    fun `Leerzeichen und leere Eintraege werden verworfen`() {
        assertEquals(
            listOf("com.a", "com.b"),
            FavoritesCodec.decode(" com.a ,, com.b ,"),
        )
    }

    @Test
    fun `toggle haengt neue Favoriten hinten an`() {
        assertEquals(
            listOf("com.a", "com.b"),
            FavoritesCodec.toggle(listOf("com.a"), "com.b"),
        )
    }

    @Test
    fun `toggle entfernt vorhandene Favoriten`() {
        assertEquals(
            listOf("com.a", "com.c"),
            FavoritesCodec.toggle(listOf("com.a", "com.b", "com.c"), "com.b"),
        )
    }

    @Test
    fun `move verschiebt innerhalb der Liste und bleibt an den Raendern stehen`() {
        val list = listOf("com.a", "com.b", "com.c")
        assertEquals(listOf("com.b", "com.a", "com.c"), FavoritesCodec.move(list, "com.a", 1))
        assertEquals(listOf("com.a", "com.c", "com.b"), FavoritesCodec.move(list, "com.b", 1))
        // Ganz vorne weiter nach vorne: unverändert.
        assertEquals(list, FavoritesCodec.move(list, "com.a", -1))
        // Ganz hinten weiter nach hinten: unverändert.
        assertEquals(list, FavoritesCodec.move(list, "com.c", 1))
        // Unbekanntes Paket: unverändert.
        assertEquals(list, FavoritesCodec.move(list, "com.x", 1))
    }

    @Test
    fun `isFull greift genau an der Grenze`() {
        val five = (1..5).map { "com.app$it" }
        val six = (1..6).map { "com.app$it" }
        assertFalse(FavoritesCodec.isFull(five))
        assertTrue(FavoritesCodec.isFull(six))
        assertTrue(FavoritesCodec.isFull(five, max = 5))
        // 6 Favoriten + Home + Navigate-Einstellungen + Einstellungen = 9
        // Elemente à 96dp: das ist die Breite der Zeile auf dem Chromecast.
        assertEquals(6, FavoritesCodec.MAX)
        assertEquals(FavoritesCodec.MAX, ConfigCodec.HARD_MAX_FAVORITES)
    }
}
