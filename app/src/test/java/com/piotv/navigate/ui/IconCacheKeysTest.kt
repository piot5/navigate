package com.piotv.navigate.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The conversion package name → file name. Small, but the only place where
 * a file name is made from foreign data — and thus the only place where a
 * path component could sneak in.
 */
class IconCacheKeysTest {

    @Test
    fun `file name contains version size and package name`() {
        assertEquals(
            "v1-128px-com.netflix.ninja.webp",
            IconCacheKeys.fileName("com.netflix.ninja", 128),
        )
    }

    @Test
    fun `different sizes yield different files`() {
        // A changed grid size (different density) gets its own file —
        // otherwise one of the two would be scaled up.
        assertNotEquals(
            IconCacheKeys.fileName("com.netflix.ninja", 104),
            IconCacheKeys.fileName("com.netflix.ninja", 128),
        )
    }

    @Test
    fun `path components are replaced`() {
        // "../../etc/passwd" must not become a path: the separator falls
        // away. A single ".." is harmless afterwards — the name always starts
        // with "v<version>-<size>px-", so it can never be ".." itself.
        val name = IconCacheKeys.fileName("../../etc/passwd", 128)
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertTrue(name.startsWith("v1-128px-"))
        assertEquals("v1-128px-.._.._etc_passwd.webp", name)
    }

    @Test
    fun `unexpected characters are replaced`() {
        assertEquals("a_b_c", IconCacheKeys.sanitize("a b\tc"))
        assertEquals("a_b", IconCacheKeys.sanitize("a:b"))
        // Space, colon, backslash — none of it in the safe set.
        assertEquals("___", IconCacheKeys.sanitize(" :\\"))
    }

    @Test
    fun `common package names stay unchanged`() {
        val name = "de.swr.avp.ard.tv"
        assertEquals(name, IconCacheKeys.sanitize(name))
        assertTrue(IconCacheKeys.fileName(name, 104).endsWith(".webp"))
    }
}
