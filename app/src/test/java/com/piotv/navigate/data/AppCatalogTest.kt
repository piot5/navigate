package com.piotv.navigate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules of the app list: which candidates win, which drop out, how the
 * list is sorted.
 *
 * The most important case is the first: apps carrying **only**
 * `CATEGORY_LEANBACK_LAUNCHER` were missing from the grid — on the target
 * device those were Prime Video, WOW and ARD (docs/DEVICE_PERF.md). These rules
 * sat between PackageManager calls until 0.4 and were untested.
 */
class AppCatalogTest {

    /** Fixed comparator instead of Collator: the test must not depend on the
     *  device language. */
    private val order = String.CASE_INSENSITIVE_ORDER

    private fun candidate(
        packageName: String,
        label: String = packageName,
        enabled: Boolean = true,
        activityName: String = "$packageName.Main",
    ) = AppCatalog.Candidate(
        packageName = packageName,
        activityName = activityName,
        label = label,
        enabled = enabled,
    )

    private fun select(
        vararg candidates: AppCatalog.Candidate,
        own: String = "com.piotv.navigate",
    ): List<AppCatalog.Candidate> = AppCatalog.select(candidates.toList(), own, order)

    @Test
    fun `only apps declaring leanback make the list`() {
        val selected = select(
            candidate("com.netflix.ninja", "Netflix"),
            candidate("com.amazon.amazonvideo.livingroom", "Prime Video"),
            candidate("com.wbd.stream", "WOW"),
            candidate("de.swr.avp.ard.tv", "ARD"),
        )
        assertEquals(4, selected.size)
        assertTrue(selected.any { it.packageName == "com.amazon.amazonvideo.livingroom" })
        assertTrue(selected.any { it.packageName == "com.wbd.stream" })
        assertTrue(selected.any { it.packageName == "de.swr.avp.ard.tv" })
    }

    @Test
    fun `the first candidate per package wins`() {
        // The caller collects LAUNCHER before LEANBACK_LAUNCHER — the vendor's
        // entry thus wins against the TV entry.
        val selected = select(
            candidate("com.netflix.ninja", "Netflix", activityName = "com.netflix.ninja.MainActivity"),
            candidate("com.netflix.ninja", "Netflix (TV)", activityName = "com.netflix.ninja.TvActivity"),
        )
        assertEquals(1, selected.size)
        assertEquals("com.netflix.ninja.MainActivity", selected.single().activityName)
        assertEquals("Netflix", selected.single().label)
    }

    @Test
    fun `disabled apps are dropped`() {
        val selected = select(
            candidate("com.termux", "Termux"),
            candidate("com.disabled.app", "Disabled", enabled = false),
        )
        assertEquals(listOf("com.termux"), selected.map { it.packageName })
    }

    @Test
    fun `the own launcher is dropped — release and debug`() {
        val selected = select(
            candidate("com.piotv.navigate", "Navigate"),
            candidate("com.piotv.navigate.debug", "Navigate (Debug)"),
            candidate("com.termux", "Termux"),
        )
        assertEquals(listOf("com.termux"), selected.map { it.packageName })
    }

    @Test
    fun `the debug build recognizes itself too`() {
        // The debug build has the package name com.piotv.navigate.debug; both
        // variants must fall out of its grid.
        val selected = select(
            candidate("com.piotv.navigate", "Navigate"),
            candidate("com.piotv.navigate.debug", "Navigate (Debug)"),
            own = "com.piotv.navigate.debug",
        )
        assertTrue(selected.isEmpty())
    }

    @Test
    fun `an empty label is replaced by the package name`() {
        val selected = select(candidate("com.ohne.name", label = "   "))
        assertEquals("com.ohne.name", selected.single().label)
    }

    @Test
    fun `sorting uses the comparator passed in`() {
        val selected = select(
            candidate("com.z", "Zebra"),
            candidate("com.a", "apple"),
            candidate("com.m", "Mango"),
        )
        // CASE_INSENSITIVE_ORDER: apple, Mango, Zebra — not "Mango, Zebra,
        // apple" (code points).
        assertEquals(listOf("apple", "Mango", "Zebra"), selected.map { it.label })
    }

    @Test
    fun `empty input gives an empty list`() {
        assertTrue(select().isEmpty())
    }

    @Test
    fun `the output order is stable`() {
        // Same input twice → same output (the sort is total; with equal labels
        // the insertion order decides).
        val input = arrayOf(
            candidate("com.b", "Same"),
            candidate("com.a", "Same"),
            candidate("com.c", "Different"),
        )
        assertEquals(select(*input).map { it.packageName }, select(*input).map { it.packageName })
    }
}
