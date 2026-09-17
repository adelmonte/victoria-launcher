// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryKeysTest {

    private val appKey = "com.example.app/com.example.app.MainActivity"

    @Test
    fun `a main profile app key is the flattened component and nothing else`() {
        // Every favorite, rename and icon stored before this change is keyed this way.
        assertEquals(appKey, EntryKeys.app(appKey))
        assertEquals(appKey, EntryKeys.app(appKey, userSerial = 0L))
    }

    @Test
    fun `an app in another profile carries the profile serial`() {
        assertEquals("$appKey|u10", EntryKeys.app(appKey, userSerial = 10L))
    }

    @Test
    fun `a shortcut key round-trips`() {
        val key = EntryKeys.shortcut("org.browser", "bookmark-42")
        assertEquals("shortcut:org.browser/bookmark-42", key)
        assertEquals(EntryKeys.ShortcutRef("org.browser", "bookmark-42", 0L), EntryKeys.parseShortcut(key))
    }

    @Test
    fun `a shortcut in another profile round-trips`() {
        val key = EntryKeys.shortcut("org.browser", "bookmark-42", userSerial = 11L)
        assertEquals("shortcut:org.browser/bookmark-42|u11", key)
        assertEquals(EntryKeys.ShortcutRef("org.browser", "bookmark-42", 11L), EntryKeys.parseShortcut(key))
    }

    @Test
    fun `a shortcut id may contain slashes colons and pipes`() {
        val id = "https://example.org/a|b:c?d=e/f"
        val key = EntryKeys.shortcut("org.browser", id)
        assertEquals(EntryKeys.ShortcutRef("org.browser", id, 0L), EntryKeys.parseShortcut(key))
        val profiled = EntryKeys.shortcut("org.browser", id, userSerial = 3L)
        assertEquals(EntryKeys.ShortcutRef("org.browser", id, 3L), EntryKeys.parseShortcut(profiled))
    }

    @Test
    fun `malformed shortcut keys are refused rather than guessed at`() {
        listOf(
            "",
            "shortcut:",
            "shortcut:/id",
            "shortcut:org.browser",
            "shortcut:org.browser/",
            "shortcut:org.browser/|u5",
            "shortcut:|u5",
            "shortcut:org.browser/id|u99999999999999999999999",
            "Shortcut:org.browser/id",
            " shortcut:org.browser/id",
        ).forEach { key -> assertNull("expected null for \"$key\"", EntryKeys.parseShortcut(key)) }
    }

    @Test
    fun `an app key is not mistaken for a shortcut or the search entry`() {
        assertFalse(EntryKeys.isShortcut(appKey))
        assertFalse(EntryKeys.isSearch(appKey))
        assertNull(EntryKeys.parseShortcut(appKey))
        assertNull(EntryKeys.parseShortcut("$appKey|u10"))
    }

    @Test
    fun `folders shortcuts and the search entry cannot be mistaken for each other`() {
        val folder = folderToken("abc123")
        val shortcut = EntryKeys.shortcut("org.browser", "id")
        val search = EntryKeys.SEARCH

        assertNull(folderIdFromToken(shortcut))
        assertNull(folderIdFromToken(search))

        assertFalse(EntryKeys.isShortcut(folder))
        assertFalse(EntryKeys.isShortcut(search))
        assertNull(EntryKeys.parseShortcut(folder))
        assertNull(EntryKeys.parseShortcut(search))

        assertFalse(EntryKeys.isSearch(folder))
        assertFalse(EntryKeys.isSearch(shortcut))
        assertTrue(EntryKeys.isSearch(search))
        assertTrue(EntryKeys.isShortcut(shortcut))
    }

    @Test
    fun `a shortcut whose id is a folder token or the search key is still only a shortcut`() {
        val sneaky = EntryKeys.shortcut("org.evil", "folder:abc")
        assertNull(folderIdFromToken(sneaky))
        assertEquals("folder:abc", EntryKeys.parseShortcut(sneaky)?.shortcutId)
        assertFalse(EntryKeys.isSearch(EntryKeys.shortcut("org.evil", EntryKeys.SEARCH)))
    }

    @Test
    fun `a package named like a prefix cannot forge another kind of key`() {
        // A Java package segment cannot contain a colon, so no real app key starts with a prefix.
        // Even so, the search check is an exact match and never a prefix match.
        assertFalse(EntryKeys.isSearch("search:default/com.example.Main"))
        assertFalse(EntryKeys.isSearch("search:default|u10"))
    }
}
