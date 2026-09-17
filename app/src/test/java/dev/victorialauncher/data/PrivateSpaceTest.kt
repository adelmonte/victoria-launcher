// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of the private space that are decisions rather than system calls, which is where
 * every mistake with a privacy cost lives: what counts as locked, and which stored key belongs
 * to a space that is.
 */
class PrivateSpaceTest {

    private val appKey = "com.example.app/com.example.app.MainActivity"

    @Test
    fun `only a private profile in quiet mode is a locked space`() {
        assertEquals(
            PrivateSpaceKind.LOCKED,
            privateSpaceKind(USER_TYPE_PROFILE_PRIVATE, quietMode = true),
        )
        assertEquals(
            PrivateSpaceKind.UNLOCKED,
            privateSpaceKind(USER_TYPE_PROFILE_PRIVATE, quietMode = false),
        )
    }

    @Test
    fun `another kind of profile is never a private space however quiet it is`() {
        // A work profile is also switched off through quiet mode, and it is not this.
        for (quiet in listOf(true, false)) {
            assertEquals(
                PrivateSpaceKind.ABSENT,
                privateSpaceKind("android.os.usertype.profile.MANAGED", quiet),
            )
            assertEquals(PrivateSpaceKind.ABSENT, privateSpaceKind("android.os.usertype.full.SYSTEM", quiet))
            // Null is what the lookup returns without the permission, or below Android 15.
            assertEquals(PrivateSpaceKind.ABSENT, privateSpaceKind(null, quiet))
        }
    }

    @Test
    fun `the user type is matched exactly`() {
        assertEquals(PrivateSpaceKind.ABSENT, privateSpaceKind("android.os.usertype.profile.PRIVATE ", true))
        assertEquals(PrivateSpaceKind.ABSENT, privateSpaceKind("ANDROID.OS.USERTYPE.PROFILE.PRIVATE", true))
    }

    @Test
    fun `a key is concealed only while its own profile is the locked one`() {
        assertTrue(isConcealed(10L, "$appKey|u10"))
        assertTrue(isConcealed(10L, EntryKeys.shortcut("org.browser", "id", userSerial = 10L)))
        // A different profile: a work profile's apps stay listed while the space is locked.
        assertFalse(isConcealed(10L, "$appKey|u11"))
        // The main profile, whose keys have never carried a suffix.
        assertFalse(isConcealed(10L, appKey))
        assertFalse(isConcealed(10L, folderToken("abc123")))
    }

    @Test
    fun `nothing is concealed when no space is locked`() {
        // Zero is the main profile's serial, so it can never be the one being concealed.
        assertFalse(isConcealed(0L, "$appKey|u10"))
        assertFalse(isConcealed(0L, appKey))
        assertFalse(PrivateSpace.Absent.conceals("$appKey|u10"))
        assertEquals(0L, PrivateSpace.Absent.concealedSerial)
    }

    @Test
    fun `reordering what is shown puts back what was not, where it was`() {
        val stored = listOf("a", "b|u10", "c", "d|u10", "e")
        val conceals = { key: String -> isConcealed(10L, key) }
        val shown = stored.filterNot(conceals)
        assertEquals(listOf("a", "c", "e"), shown)

        // Dragged into a new order; the concealed keys keep their own places.
        assertEquals(
            listOf("e", "b|u10", "a", "d|u10", "c"),
            restoreConcealed(stored, listOf("e", "a", "c"), conceals),
        )
        // Untouched order comes back untouched.
        assertEquals(stored, restoreConcealed(stored, shown, conceals))
    }

    @Test
    fun `a key added while a space is locked lands after what was stored`() {
        val conceals = { key: String -> isConcealed(10L, key) }
        assertEquals(
            listOf("a", "b|u10", "new"),
            restoreConcealed(listOf("a", "b|u10"), listOf("a", "new"), conceals),
        )
    }

    @Test
    fun `a key removed while a space is locked does not take a concealed one with it`() {
        val conceals = { key: String -> isConcealed(10L, key) }
        assertEquals(
            listOf("c", "b|u10"),
            restoreConcealed(listOf("a", "b|u10", "c"), listOf("c"), conceals),
        )
    }

    @Test
    fun `with nothing concealed reordering is exactly what the screen said`() {
        val stored = listOf("a", "b", "c")
        assertEquals(listOf("c", "a", "b"), restoreConcealed(stored, listOf("c", "a", "b")) { false })
    }
}
