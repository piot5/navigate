package com.piotv.navigate.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The display order — the **only** setting this interface has.
 *
 * Why it matters so much: the grid shows only the first row, so the order
 * decides which apps you see without a single key press. A bug here is visible
 * immediately, not merely ugly.
 */
class OrderTest {

    private val apps = listOf("com.a", "com.b", "com.c", "com.d")

    @Test
    fun `without an order the base order stays`() {
        assertEquals(apps, Order.arrange(emptyList(), apps) { it })
    }

    @Test
    fun `configured packages come first, in their order`() {
        val order = listOf("com.c", "com.a")
        assertEquals(listOf("com.c", "com.a", "com.b", "com.d"), Order.arrange(order, apps) { it })
    }

    @Test
    fun `entries without an installed app are silently dropped`() {
        // An uninstalled app must not throw the order into disarray — the list
        // cleans up after itself.
        val order = listOf("com.gibtsnicht", "com.b")
        assertEquals(listOf("com.b", "com.a", "com.c", "com.d"), Order.arrange(order, apps) { it })
    }

    @Test
    fun `an order without installed apps changes nothing`() {
        assertEquals(apps, Order.arrange(listOf("x", "y"), apps) { it })
    }

    @Test
    fun `moveForward moves one position forward`() {
        val order = listOf("com.a", "com.b", "com.c")
        assertEquals(listOf("com.b", "com.a", "com.c"), Order.moveForward(order, "com.b"))
    }

    @Test
    fun `moveForward leaves the first entry alone`() {
        val order = listOf("com.a", "com.b")
        assertEquals(order, Order.moveForward(order, "com.a"))
    }

    @Test
    fun `moveForward appends an unknown app`() {
        // From the "rest" one position forward: before, the app sat behind all
        // configured ones; now directly behind them.
        assertEquals(listOf("com.a", "com.new"), Order.moveForward(listOf("com.a"), "com.new"))
    }

    @Test
    fun `moveForward twice brings an app two positions forward`() {
        var order = listOf("com.a", "com.b", "com.c")
        order = Order.moveForward(order, "com.c")
        order = Order.moveForward(order, "com.c")
        assertEquals(listOf("com.c", "com.a", "com.b"), order)
    }

    // --- "One position back" (context menu) -----------------------------------

    @Test
    fun `moveBackward moves one position back`() {
        val order = listOf("com.a", "com.b", "com.c")
        assertEquals(listOf("com.a", "com.c", "com.b"), Order.moveBackward(order, "com.b"))
    }

    @Test
    fun `moveBackward leaves the last entry alone`() {
        val order = listOf("com.a", "com.b")
        assertEquals(order, Order.moveBackward(order, "com.b"))
    }

    @Test
    fun `moveBackward leaves unknown packages alone`() {
        // They sit behind all configured ones anyway — "further back" could only
        // be expressed by promoting another app.
        val order = listOf("com.a")
        assertEquals(order, Order.moveBackward(order, "com.neu"))
    }
}
