package com.piotv.navigate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The folding of foreground events: "which app was in front last?"
 *
 * That is the basis for "HOME or BACK on the home screen → back into the last
 * app". A bug here is felt immediately: it would bring the wrong app (or the
 * home screen itself) to the front.
 */
class ForegroundLogTest {

    private val own = "com.piotv.navigate"

    @Test
    fun `without events there is no last app`() {
        assertNull(ForegroundLog.lastResumed(own, emptyList()))
    }

    @Test
    fun `the last foreground event wins`() {
        val events = listOf(
            "com.netflix.ninja" to ForegroundLog.RESUMED,
            "com.disney.disneyplus" to ForegroundLog.RESUMED,
        )
        assertEquals("com.disney.disneyplus", ForegroundLog.lastResumed(own, events))
    }

    @Test
    fun `the own launcher does not count`() {
        // On a HOME press the launcher itself stands in the statistics as
        // RESUMED. Without this filter one would never get back into the app.
        val events = listOf(
            "com.netflix.ninja" to ForegroundLog.RESUMED,
            own to ForegroundLog.RESUMED,
        )
        assertEquals("com.netflix.ninja", ForegroundLog.lastResumed(own, events))
    }

    @Test
    fun `other event types change nothing`() {
        // PAUSED/STOPPED (here as other numbers) say nothing about which app
        // was in front.
        val events = listOf(
            "com.netflix.ninja" to ForegroundLog.RESUMED,
            "com.disney.disneyplus" to 2,
            "com.disney.disneyplus" to 23,
        )
        assertEquals("com.netflix.ninja", ForegroundLog.lastResumed(own, events))
    }

    @Test
    fun `only the own launcher yields nothing`() {
        val events = listOf(own to ForegroundLog.RESUMED, own to ForegroundLog.RESUMED)
        assertNull(ForegroundLog.lastResumed(own, events))
    }

    // --- "active?" (green text in the grid) -----------------------------------
    //    The question itself is answered by the usage statistics via
    //    timestamps (LastApp.packageName) — here stands only the folding for
    //    "which app was in front last?".

    @Test
    fun `the last foreground event wins even with many packages`() {
        val events = listOf(
            "com.netflix.ninja" to ForegroundLog.RESUMED,
            "com.wbd.stream" to ForegroundLog.RESUMED,
            "com.disney.disneyplus" to ForegroundLog.RESUMED,
        )
        assertEquals("com.disney.disneyplus", ForegroundLog.lastResumed(own, events))
    }
}
