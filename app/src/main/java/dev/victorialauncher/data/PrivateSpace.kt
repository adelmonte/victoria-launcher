// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import android.os.UserHandle

/**
 * Android 15's private space: a second profile whose apps are meant to be invisible until it
 * is unlocked, and whose lock is a quiet mode the default launcher is allowed to turn on and
 * off on the owner's behalf.
 *
 * [Absent] covers every reason there is nothing to show — an older Android, a device with no
 * private space, or this launcher not being the default home, which is what the permission to
 * see the profile at all is conditional on. All three want identical behavior, so they are one
 * state rather than three.
 */
sealed interface PrivateSpace {

    /** The profile's serial. Zero when there is no private space; a profile's is never zero. */
    val serial: Long

    /** No private space this launcher can see. */
    data object Absent : PrivateSpace {
        override val serial: Long get() = 0L
    }

    data class Locked(val user: UserHandle, override val serial: Long) : PrivateSpace

    data class Unlocked(val user: UserHandle, override val serial: Long) : PrivateSpace

    /** The serial whose rows must be kept out of sight, or zero when none must be. */
    val concealedSerial: Long get() = if (this is Locked) serial else 0L

    /** Whether this stored key names something inside a space that is locked right now. */
    fun conceals(key: String): Boolean = isConcealed(concealedSerial, key)
}

/**
 * What Android calls a private space's profile. Written out rather than taken from
 * UserManager.USER_TYPE_PROFILE_PRIVATE so this file needs no Android types, and so nothing
 * here has to be compiled against an API level the app also runs below.
 */
const val USER_TYPE_PROFILE_PRIVATE = "android.os.usertype.profile.PRIVATE"

/** [PrivateSpace] without the profile it belongs to, so the decision can be made on its own. */
enum class PrivateSpaceKind { ABSENT, LOCKED, UNLOCKED }

/**
 * What a profile is, from the two things the platform will tell us about it.
 *
 * Quiet mode is how a private space is locked: the profile stays listed and keeps answering
 * questions about itself either way, and this flag is the only thing that says which it is.
 */
fun privateSpaceKind(userType: String?, quietMode: Boolean): PrivateSpaceKind = when {
    userType != USER_TYPE_PROFILE_PRIVATE -> PrivateSpaceKind.ABSENT
    quietMode -> PrivateSpaceKind.LOCKED
    else -> PrivateSpaceKind.UNLOCKED
}

/**
 * Whether a stored key belongs to the profile named by [concealedSerial], which is zero when
 * nothing is being concealed.
 *
 * Keys are matched by their profile suffix rather than by looking the row up, because the
 * point is to decide about keys whose rows are deliberately not there to be looked up.
 */
fun isConcealed(concealedSerial: Long, key: String): Boolean =
    concealedSerial != 0L && EntryKeys.userSerial(key) == concealedSerial

/**
 * Puts back the keys a screen left out, in the places they were, so reordering what is on
 * screen never drops what is not.
 *
 * Without this, hiding a locked space's favorites from the favorites screen would quietly
 * delete them the first time anything else on that screen was dragged: the screen writes back
 * the whole list it was given.
 */
fun restoreConcealed(
    stored: List<String>,
    reordered: List<String>,
    conceals: (String) -> Boolean,
): List<String> {
    val remaining = reordered.toMutableList()
    val out = ArrayList<String>(stored.size)
    for (key in stored) {
        if (conceals(key)) out += key else if (remaining.isNotEmpty()) out += remaining.removeAt(0)
    }
    // Anything the screen added rather than moved, which lands where the screen put it: last.
    out += remaining
    return out
}

/**
 * The private-space row names itself after this package, having no activity of its own, and
 * says which padlock it is in its class name. That is also what keeps the two apart in the
 * icon cache, whose key is built from the row and not from the state of the device.
 */
const val PRIVATE_SPACE_LOCKED_CLASS = "private-space-locked"
const val PRIVATE_SPACE_UNLOCKED_CLASS = "private-space-unlocked"
