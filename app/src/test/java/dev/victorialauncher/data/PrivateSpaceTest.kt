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

    private companion object {
        const val MANAGED = "android.os.usertype.profile.MANAGED"
        const val CLONE = "android.os.usertype.profile.CLONE"

        /** Any second profile's serial; a real one is never zero. */
        const val SERIAL = 7L

        /** Nothing concealed, missing rows shown: both Absent and Unlocked amount to this. */
        val ABSENT_OR_UNLOCKED = 0L to false

        /** A space positively read and positively shut: its serial, missing rows shown. */
        val LOCKED = SERIAL to false

        /** A space that answered earlier and will not describe itself now. */
        val UNCERTAIN_KNOWN = SERIAL to true

        /** A space that might be there and has never given up a serial. */
        val UNCERTAIN_UNNAMED = 0L to true

        val EVERY_STATE = listOf(ABSENT_OR_UNLOCKED, LOCKED, UNCERTAIN_KNOWN, UNCERTAIN_UNNAMED)
    }

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
    fun `a lock nobody would report is read as locked`() {
        assertEquals(
            PrivateSpaceKind.LOCKED,
            privateSpaceKind(USER_TYPE_PROFILE_PRIVATE, quietMode = null),
        )
        assertEquals(PrivateSpaceKind.ABSENT, privateSpaceKind("android.os.usertype.profile.MANAGED", null))
        assertEquals(PrivateSpaceKind.ABSENT, privateSpaceKind(null, null))
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

    // The listing decision, profile by profile, in full. Every row of this table where the
    // platform did not answer has to come out "not listed": the launcher is the only thing
    // hiding a locked private space, so an unknown that resolves the other way is the whole
    // disclosure, not a smaller list.

    @Test
    fun `the profile we run in is always listed, whatever else is unknown`() {
        for (sdk in listOf(26, 34, PRIVATE_SPACE_SDK, 36)) {
            for (type in listOf(null, USER_TYPE_PROFILE_PRIVATE, MANAGED)) {
                for (quiet in listOf(null, true, false)) {
                    for (serial in listOf(null, 0L, 7L)) {
                        assertTrue(
                            "main user, sdk $sdk, type $type, quiet $quiet, serial $serial",
                            shouldListProfile(true, sdk, type, quiet, serial),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `a second profile with no readable serial is never listed, on any Android`() {
        // Its rows would be keyed as the main profile's: indistinguishable from real ones,
        // stored into favorites and launch counts, and out of reach of concealment by serial.
        for (sdk in listOf(26, 34, PRIVATE_SPACE_SDK, 36)) {
            for (type in listOf(null, USER_TYPE_PROFILE_PRIVATE, MANAGED)) {
                for (quiet in listOf(null, true, false)) {
                    for (serial in listOf(null, 0L)) {
                        assertFalse(
                            "sdk $sdk, type $type, quiet $quiet, serial $serial",
                            shouldListProfile(false, sdk, type, quiet, serial),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `below Android 15 a second profile with a serial is listed as it always was`() {
        for (sdk in listOf(26, 34)) {
            for (type in listOf(null, MANAGED, CLONE, USER_TYPE_PROFILE_PRIVATE)) {
                for (quiet in listOf(null, true, false)) {
                    assertTrue(
                        "sdk $sdk, type $type, quiet $quiet",
                        shouldListProfile(false, sdk, type, quiet, SERIAL),
                    )
                }
            }
        }
    }

    @Test
    fun `a private profile is listed only while it positively says it is open`() {
        for (sdk in listOf(PRIVATE_SPACE_SDK, 36)) {
            assertTrue(shouldListProfile(false, sdk, USER_TYPE_PROFILE_PRIVATE, false, SERIAL))
            assertFalse(shouldListProfile(false, sdk, USER_TYPE_PROFILE_PRIVATE, true, SERIAL))
            // The lock the platform would not report: unknown is locked.
            assertFalse(shouldListProfile(false, sdk, USER_TYPE_PROFILE_PRIVATE, null, SERIAL))
        }
    }

    @Test
    fun `a profile that will not say what it is is never listed`() {
        // It might be the private one, and there is nothing else to tell from.
        for (sdk in listOf(PRIVATE_SPACE_SDK, 36)) {
            for (quiet in listOf(null, true, false)) {
                assertFalse("sdk $sdk, quiet $quiet", shouldListProfile(false, sdk, null, quiet, SERIAL))
            }
        }
    }

    @Test
    fun `work and clone profiles are listed as they always were`() {
        for (type in listOf(MANAGED, CLONE)) {
            for (quiet in listOf(null, true, false)) {
                assertTrue(
                    "type $type, quiet $quiet",
                    shouldListProfile(false, PRIVATE_SPACE_SDK, type, quiet, SERIAL),
                )
            }
        }
    }

    @Test
    fun `a space that could not be read conceals by the serial it was last known by`() {
        val uncertain = PrivateSpace.Uncertain(user = null, serial = 10L)
        assertEquals(10L, uncertain.concealedSerial)
        assertTrue(uncertain.conceals("$appKey|u10"))
        assertFalse(uncertain.conceals("$appKey|u11"))
        assertFalse(uncertain.conceals(appKey))
    }

    @Test
    fun `a space that was never named conceals every stored key that names nothing`() {
        // No serial to match against, so the only thing left to go by is that the key resolves
        // to nothing — which is exactly what a private key does while the space is locked.
        val unnamed = PrivateSpace.Uncertain(user = null, serial = 0L)
        assertTrue(unnamed.concealsStored("$appKey|u10", resolves = false))
        assertTrue(unnamed.concealsStored(appKey, resolves = false))
        // Something still on screen is still shown; this hides rows, not the whole screen.
        assertFalse(unnamed.concealsStored("$appKey|u10", resolves = true))
        assertFalse(unnamed.concealsStored(appKey, resolves = true))
    }

    @Test
    fun `with the space read properly an app that really is gone still says so`() {
        assertFalse(PrivateSpace.Absent.concealsUnresolved)
        assertFalse(PrivateSpace.Absent.concealsStored(appKey, resolves = false))
        assertTrue(PrivateSpace.Uncertain(user = null, serial = 0L).concealsUnresolved)
    }

    // What a screen listing stored keys leaves out, over every state it can be handed. The
    // states are given as the pair of values they amount to, because Locked and Unlocked need
    // a UserHandle no JVM test can build — and nothing in this decision reads one.

    private fun Pair<Long, Boolean>.hides(key: String, resolves: Boolean) =
        concealsStoredKey(first, second, key, resolves)

    @Test
    fun `a second profile's key that names nothing is carried in every state, never shown`() {
        // Absent is what a launcher that is not the default home reports on a fresh process:
        // the private profile is invisible to it, nothing is left unclassified, and no serial
        // has been seen — so a private favorite stored earlier names nothing, and an "app no
        // longer installed" row for it would be both a count of what is in the space and a
        // checkbox that throws the owner's favorite away.
        val privateKey = "$appKey|u$SERIAL"
        val otherProfileKey = "$appKey|u11"
        for (state in EVERY_STATE) {
            assertTrue("$state", state.hides(privateKey, resolves = false))
            assertTrue("$state", state.hides(otherProfileKey, resolves = false))
        }
    }

    @Test
    fun `a main-profile key that names nothing still gets its removable row`() {
        // The one case that row is for, and it says nothing about a private space: an ordinary
        // app that was uninstalled. Only the state that cannot tell the two apart hides it.
        assertFalse(ABSENT_OR_UNLOCKED.hides(appKey, resolves = false))
        assertFalse(LOCKED.hides(appKey, resolves = false))
        assertTrue(UNCERTAIN_KNOWN.hides(appKey, resolves = false))
        assertTrue(UNCERTAIN_UNNAMED.hides(appKey, resolves = false))
    }

    @Test
    fun `a key that does name a row is shown unless its own space is the locked one`() {
        // Carrying is for keys with no row. A work app, or a private app while the space is
        // open, has one, and leaving it out would hide an app visible everywhere else.
        val otherProfileKey = "$appKey|u11"
        for (state in EVERY_STATE) {
            assertFalse("$state", state.hides(appKey, resolves = true))
            assertFalse("$state", state.hides(otherProfileKey, resolves = true))
        }
        // The locked space's own key goes whether or not it still names a row: the row only
        // outlives the lock by a moment, and the key is what says which profile it is in.
        assertTrue(LOCKED.hides("$appKey|u$SERIAL", resolves = true))
        assertTrue(UNCERTAIN_KNOWN.hides("$appKey|u$SERIAL", resolves = true))
    }

    @Test
    fun `a space that answered earlier keeps its padlock when a later read cannot name it`() {
        // The row is the only way into a locked space, and nothing here re-reads on its own,
        // so a row dropped on one failed read stays dropped.
        assertTrue(PrivateSpace.Uncertain(user = null, serial = SERIAL).offersPadlockRow)
        // Nothing has ever said there is a space, so there is nothing to offer a padlock for.
        assertFalse(PrivateSpace.Uncertain(user = null, serial = 0L).offersPadlockRow)
        assertFalse(PrivateSpace.Absent.offersPadlockRow)
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

    // The screens hide with concealsStored and restore with the same predicate. These build
    // `shown` with a predicate that is WIDER than conceals — which is what an uncertain space
    // gives them — and then check that nothing at all leaves the store across a reorder, an
    // add and a remove. Restoring with the narrower `conceals` instead silently drops every
    // key between the two rules the first time anything is dragged, and the store is what a
    // screen writes back, so the drop is permanent.

    /** An uncertain space with no serial: everything that names nothing is hidden. */
    private val unnamedSpace = PrivateSpace.Uncertain(user = null, serial = 0L)

    /** Two keys have rows to draw; the other two do not, for two different reasons. */
    private fun hidesUnresolved(key: String): Boolean =
        unnamedSpace.concealsStored(key, resolves = key == "a" || key == "c")

    @Test
    fun `a key hidden for naming nothing survives a reorder of what is shown`() {
        val stored = listOf("a", "gone", "c", "b|u10")
        val shown = stored.filterNot(::hidesUnresolved)
        assertEquals(listOf("a", "c"), shown)

        assertEquals(
            listOf("c", "gone", "a", "b|u10"),
            restoreConcealed(stored, listOf("c", "a"), ::hidesUnresolved),
        )
        // And an untouched screen writes back exactly what it was given.
        assertEquals(stored, restoreConcealed(stored, shown, ::hidesUnresolved))
    }

    @Test
    fun `a key hidden for naming nothing survives one being added`() {
        val stored = listOf("a", "gone", "c", "b|u10")
        assertEquals(
            listOf("a", "gone", "c", "b|u10", "new"),
            restoreConcealed(stored, listOf("a", "c", "new"), ::hidesUnresolved),
        )
    }

    @Test
    fun `a key hidden for naming nothing survives another being removed`() {
        val stored = listOf("a", "gone", "c", "b|u10")
        assertEquals(
            listOf("c", "gone", "b|u10"),
            restoreConcealed(stored, listOf("c"), ::hidesUnresolved),
        )
    }

    @Test
    fun `restoring by the narrower rule is what loses them`() {
        // The bug this pins down, stated as the difference between the two predicates: with
        // `conceals` there is nothing to match "gone" and the reorder reads one key short.
        val stored = listOf("a", "gone", "c", "b|u10")
        val shown = stored.filterNot(::hidesUnresolved)
        val byTheNarrowRule = restoreConcealed(stored, shown, unnamedSpace::conceals)
        assertFalse("this is the deletion the screens must not perform", "gone" in byTheNarrowRule)
        assertTrue("and the same rule kept everything", "gone" in restoreConcealed(stored, shown, ::hidesUnresolved))
    }

    @Test
    fun `with nothing concealed reordering is exactly what the screen said`() {
        val stored = listOf("a", "b", "c")
        assertEquals(listOf("c", "a", "b"), restoreConcealed(stored, listOf("c", "a", "b")) { false })
    }

    @Test
    fun `a drag on the home screen puts back every favorite that drew no row`() {
        // The home screen hands back only what it drew. A locked space's favorites draw nothing,
        // and so does a favorite whose app is gone; neither is the drag's to delete.
        val stored = listOf("signal/Main", "private/Main|u11", "photos/Main", "gone/Main")
        val drawnInNewOrder = listOf("photos/Main", "signal/Main")
        val drawn = drawnInNewOrder.toSet()
        assertEquals(
            listOf("photos/Main", "private/Main|u11", "signal/Main", "gone/Main"),
            restoreConcealed(stored, drawnInNewOrder) { it !in drawn },
        )
    }
}
