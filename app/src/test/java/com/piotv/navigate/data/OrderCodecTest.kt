package com.piotv.navigate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser of the order file. It holds the **order** of the apps (the
 * settings live next door in `settings.conf`) and is written by hand (or via
 * `adb push`) — this is where what happens when it is crooked is written down.
 * Principle as everywhere in this project: a hand-written file must never keep
 * the launcher from starting.
 */
class OrderCodecTest {

    @Test
    fun `a missing file gives an empty order`() {
        assertEquals(emptyList<String>(), OrderCodec.parse(null))
        assertEquals(emptyList<String>(), OrderCodec.parse(""))
    }

    @Test
    fun `one package-name line per entry`() {
        assertEquals(listOf("com.a", "com.b"), OrderCodec.parse("com.a\ncom.b"))
    }

    @Test
    fun `comments blank lines and whitespace do not interfere`() {
        val order = OrderCodec.parse(
            """
            # comment

               com.a
            com.b
            """.trimIndent()
        )
        assertEquals(listOf("com.a", "com.b"), order)
    }

    @Test
    fun `comma lists and the old key-equals-value form`() {
        // Earlier versions had "key=value" (favorites=…). Such a file must not
        // leave the home page empty.
        assertEquals(listOf("com.a", "com.b"), OrderCodec.parse("order=com.a, com.b"))
    }

    @Test
    fun `duplicate entries are dropped`() {
        // A duplicate name would put an app twice into the first row.
        assertEquals(listOf("com.a", "com.b"), OrderCodec.parse("com.a\ncom.b\ncom.a"))
    }

    @Test
    fun `writing and reading are lossless`() {
        val order = listOf("com.netflix.ninja", "de.swr.avp.ard.tv")
        assertEquals(order, OrderCodec.parse(OrderCodec.serialize(order)))
    }

    @Test
    fun `the written file explains itself`() {
        val text = OrderCodec.serialize(listOf("com.a"))
        assertTrue(text.contains("com.a"))
        // Comment lines are the reason the file can be edited by hand.
        assertTrue(text.lines().any { it.startsWith("#") })
    }
}
