// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What an export leaves out of one preference so a backup never names a private app, whether or
 * not the space is locked at the moment someone exports — and whether or not the launcher can
 * say which profile is the private one, which is the state most in need of it. Every shape
 * [Prefs] stores an app key in gets a case here.
 */
class PrivateSpaceExportTest {

    /**
     * There is a private space, or there might be. Which profile it is never comes into it:
     * the rule is the suffix, so this is the same true in every state but Absent.
     */
    private val strip = true

    /** Positively no private space on this device, which is the only state that keeps them. */
    private val keep = false

    private val privateSerial = 11L
    private val a = "com.a/com.a.Main"
    private val b = "com.b/com.b.Main"
    private val c = "com.c/com.c.Main"
    private val bPrivate = EntryKeys.app("com.b/com.b.Main", userSerial = privateSerial)

    // --- favorites_order (newline-joined keys) ---------------------------------------------

    @Test
    fun `a private favorite is removed and its neighbours survive in order`() {
        val raw = listOf(a, bPrivate, c).joinToString("\n")
        assertEquals(
            listOf(a, c).joinToString("\n"),
            stripPrivateSpaceFromExport("favorites_order", raw, strip),
        )
    }

    @Test
    fun `favorites with no private key come back identical`() {
        val raw = listOf(a, c).joinToString("\n")
        assertSame(raw, stripPrivateSpaceFromExport("favorites_order", raw, strip))
    }

    // --- hidden_apps (a string set) ----------------------------------------------------------

    @Test
    fun `a private hidden app is removed, the rest of the set survives`() {
        val raw = setOf(a, bPrivate, c)
        assertEquals(setOf(a, c), stripPrivateSpaceFromExport("hidden_apps", raw, strip))
    }

    @Test
    fun `a hidden set with no private key comes back identical`() {
        val raw = setOf(a, c)
        assertSame(raw, stripPrivateSpaceFromExport("hidden_apps", raw, strip))
    }

    // --- name_overrides_json / icon_overrides_json / launch_counts_json (key -> value JSON) --

    @Test
    fun `a rename for a private app is removed, other renames survive`() {
        val raw = """{"$a":"Alpha","$bPrivate":"Secret","$c":"Charlie"}"""
        assertEquals(
            """{"$a":"Alpha","$c":"Charlie"}""",
            stripPrivateSpaceFromExport("name_overrides_json", raw, strip),
        )
    }

    @Test
    fun `an icon override for a private app is removed, other overrides survive`() {
        val raw = """{"$a":"pack:x:ic_a","$bPrivate":"content://secret","$c":"pack:x:ic_c"}"""
        assertEquals(
            """{"$a":"pack:x:ic_a","$c":"pack:x:ic_c"}""",
            stripPrivateSpaceFromExport("icon_overrides_json", raw, strip),
        )
    }

    @Test
    fun `a launch count for a private app is removed, other counts survive`() {
        val raw = """{"$a":"4","$bPrivate":"99","$c":"1"}"""
        assertEquals(
            """{"$a":"4","$c":"1"}""",
            stripPrivateSpaceFromExport("launch_counts_json", raw, strip),
        )
    }

    @Test
    fun `a map with no private key comes back identical`() {
        val raw = """{"$a":"Alpha","$c":"Charlie"}"""
        assertSame(raw, stripPrivateSpaceFromExport("name_overrides_json", raw, strip))
    }

    // --- folders_json (array of folders, each with an apps array) ---------------------------

    @Test
    fun `a private app inside a folder is removed, its folder-mates survive in order`() {
        val raw = foldersToJson(listOf(Folder(id = "1", name = "Mix", apps = listOf(a, bPrivate, c))))
        val stripped = stripPrivateSpaceFromExport("folders_json", raw, strip) as String
        assertEquals(listOf(Folder(id = "1", name = "Mix", apps = listOf(a, c))), foldersFromJson(stripped))
    }

    @Test
    fun `a folder whose every member is private becomes empty but keeps its name and icon`() {
        val raw = foldersToJson(
            listOf(Folder(id = "1", name = "Hush", apps = listOf(bPrivate), icon = "pack:x:ic_folder")),
        )
        val stripped = stripPrivateSpaceFromExport("folders_json", raw, strip) as String
        val restored = foldersFromJson(stripped).single()
        assertEquals("Hush", restored.name)
        assertEquals("pack:x:ic_folder", restored.icon)
        assertEquals(emptyList<String>(), restored.apps)
    }

    @Test
    fun `folders with no private member come back identical`() {
        val raw = foldersToJson(listOf(Folder(id = "1", name = "Plain", apps = listOf(a, c))))
        assertSame(raw, stripPrivateSpaceFromExport("folders_json", raw, strip))
    }

    // --- quick_launch_left_key / quick_launch_right_key (a single key, or absent) -----------

    @Test
    fun `a quick-launch slot holding a private key is left out entirely`() {
        assertNull(stripPrivateSpaceFromExport("quick_launch_left_key", bPrivate, strip))
        assertNull(stripPrivateSpaceFromExport("quick_launch_right_key", bPrivate, strip))
    }

    @Test
    fun `a quick-launch slot holding an ordinary key survives`() {
        assertEquals(a, stripPrivateSpaceFromExport("quick_launch_left_key", a, strip))
    }

    // --- cross-cutting behavior ---------------------------------------------------------------

    @Test
    fun `a device with positively no private space exports exactly what it always did`() {
        val favorites = listOf(a, bPrivate, c).joinToString("\n")
        val hidden = setOf(a, bPrivate)
        val renames = """{"$a":"Alpha","$bPrivate":"Secret"}"""
        val folders = foldersToJson(listOf(Folder(id = "1", name = "Mix", apps = listOf(a, bPrivate))))

        assertSame(favorites, stripPrivateSpaceFromExport("favorites_order", favorites, keep))
        assertSame(hidden, stripPrivateSpaceFromExport("hidden_apps", hidden, keep))
        assertSame(renames, stripPrivateSpaceFromExport("name_overrides_json", renames, keep))
        assertSame(folders, stripPrivateSpaceFromExport("folders_json", folders, keep))
        assertEquals(bPrivate, stripPrivateSpaceFromExport("quick_launch_left_key", bPrivate, keep))
    }

    @Test
    fun `a key with no serial known to be the private one is still stripped`() {
        // The state this is for: a space the launcher can see is there, or might be, and
        // cannot describe. There is no serial to match, and a strip that needed one would
        // write every private key and launch count into the file and report it saved.
        val someProfileKey = EntryKeys.app("com.b/com.b.Main", userSerial = 4L)
        val raw = listOf(a, someProfileKey).joinToString("\n")
        assertEquals(a, stripPrivateSpaceFromExport("favorites_order", raw, strip))
    }

    @Test
    fun `a work profile's keys go too, because they could not be restored either`() {
        // Serials are per-device and do not survive a profile being recreated, so a work key
        // in a backup is an entry that will never match anything. Keeping it would mean
        // knowing which second profile is which before the strip could be safe, and the state
        // that needs the strip most is the one where that cannot be known.
        val workKey = EntryKeys.app("com.work/com.work.Main", userSerial = 10L)
        val raw = listOf(a, workKey).joinToString("\n")
        assertEquals(a, stripPrivateSpaceFromExport("favorites_order", raw, strip))
        // And on a device that positively has no private space, nothing is touched at all.
        assertSame(raw, stripPrivateSpaceFromExport("favorites_order", raw, keep))
    }

    @Test
    fun `only a positive answer of no private space keeps a second profile's keys`() {
        assertFalse(
            "Absent is the one state that positively rules a private space out",
            PrivateSpace.Absent.stripsOtherProfiles,
        )
        // The state a serial-keyed strip did nothing in: a space that might be there and has
        // never said which profile it is.
        assertTrue(PrivateSpace.Uncertain(user = null, serial = 0L).stripsOtherProfiles)
        // And one that answered earlier in the session and will not describe itself now.
        assertTrue(PrivateSpace.Uncertain(user = null, serial = privateSerial).stripsOtherProfiles)
        // Locked and Unlocked need a UserHandle no JVM test can build. Neither is Absent, so
        // both strip; they are also the two states where the serial was known all along.
    }

    @Test
    fun `malformed JSON is passed through rather than crashing the export`() {
        val brokenFolders = "{not an array"
        val brokenMap = "[not an object"
        assertSame(brokenFolders, stripPrivateSpaceFromExport("folders_json", brokenFolders, strip))
        assertSame(brokenMap, stripPrivateSpaceFromExport("name_overrides_json", brokenMap, strip))
    }

    @Test
    fun `an unrelated preference passes through untouched`() {
        assertEquals(56, stripPrivateSpaceFromExport("icon_size_dp", 56, strip))
        assertEquals(true, stripPrivateSpaceFromExport("haptics_enabled", true, strip))
    }

    @Test
    fun `a pinned-shortcut key for the private profile is stripped`() {
        val shortcutKey = EntryKeys.shortcut("com.x", "id", userSerial = privateSerial)
        val raw = listOf(a, shortcutKey).joinToString("\n")
        assertEquals(a, stripPrivateSpaceFromExport("favorites_order", raw, strip))
    }

    @Test
    fun `a shortcut id that merely contains the serial suffix mid-string is not stripped`() {
        // The publisher's own shortcut id happens to end in text that looks like a profile
        // suffix; it is not one, because it isn't at the end of the key.
        val lookalike = "shortcut:com.x/a|u${privateSerial}b"
        val raw = listOf(a, lookalike).joinToString("\n")
        assertSame(raw, stripPrivateSpaceFromExport("favorites_order", raw, strip))
    }
}
