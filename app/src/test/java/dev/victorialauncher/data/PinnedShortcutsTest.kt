// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedShortcutsTest {

    private fun ref(pkg: String, id: String, serial: Long = 0L) =
        EntryKeys.ShortcutRef(pkg, id, serial)

    @Test
    fun `the other ids of the same package stay pinned`() {
        val pinned = listOf(ref("com.browser", "a"), ref("com.browser", "b"), ref("com.browser", "c"))
        assertEquals(listOf("a", "c"), PinnedShortcuts.remainingIds(pinned, ref("com.browser", "b")))
    }

    @Test
    fun `another package's shortcuts are left out rather than pinned onto this one`() {
        val pinned = listOf(ref("com.browser", "a"), ref("com.chat", "a"), ref("com.chat", "b"))
        assertEquals(listOf("a"), PinnedShortcuts.remainingIds(pinned, ref("com.browser", "z")))
    }

    @Test
    fun `the same id in another profile is a different shortcut`() {
        val pinned = listOf(ref("com.chat", "a"), ref("com.chat", "a", serial = 11L))
        // Removing the work profile's copy must leave the personal one alone, and the list
        // handed back is only ever the profile being written to.
        assertEquals(emptyList<String>(), PinnedShortcuts.remainingIds(pinned, ref("com.chat", "a", serial = 11L)))
        assertEquals(emptyList<String>(), PinnedShortcuts.remainingIds(pinned, ref("com.chat", "a")))
    }

    @Test
    fun `removing the only pinned shortcut leaves an empty list rather than everything`() {
        assertTrue(PinnedShortcuts.remainingIds(listOf(ref("com.browser", "a")), ref("com.browser", "a")).isEmpty())
    }

    @Test
    fun `removing one that is not pinned changes nothing`() {
        val pinned = listOf(ref("com.browser", "a"), ref("com.browser", "b"))
        assertEquals(listOf("a", "b"), PinnedShortcuts.remainingIds(pinned, ref("com.browser", "gone")))
    }

    @Test
    fun `a duplicate id is only listed once`() {
        // Two profiles' worth of the same query can hand back the same row twice; pinning an
        // id twice in one call is at best pointless.
        val pinned = listOf(ref("com.browser", "a"), ref("com.browser", "a"), ref("com.browser", "b"))
        assertEquals(listOf("a"), PinnedShortcuts.remainingIds(pinned, ref("com.browser", "b")))
    }
}
