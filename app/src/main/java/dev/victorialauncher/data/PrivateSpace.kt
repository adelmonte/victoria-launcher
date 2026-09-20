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
 * state rather than three. What [Absent] never covers is a read that failed: a failure means we
 * do not know, and not knowing is [Uncertain], which conceals.
 */
sealed interface PrivateSpace {

    /** The profile this pass positively found, or null when it found none it could name. */
    val user: UserHandle?

    /** The profile's serial. Zero when there is none to match stored keys against. */
    val serial: Long

    /** No private space this launcher can see, and none it has seen since it started. */
    data object Absent : PrivateSpace {
        override val user: UserHandle? get() = null
        override val serial: Long get() = 0L
    }

    data class Locked(override val user: UserHandle, override val serial: Long) : PrivateSpace

    data class Unlocked(override val user: UserHandle, override val serial: Long) : PrivateSpace

    /**
     * A private space the platform would not describe fully: a profile that says it is private
     * but will not give up its serial or its quiet mode, a profile that will not say what it is
     * at all, or a space that answered earlier in this session and is not in the list now.
     *
     * Treated as locked, because the alternative is that one failed binder call puts every
     * private app's name and icon on screen. [user] is null when this pass could not name the
     * profile, which is the only thing the cases differ in: a padlock can only be offered for a
     * profile there is something to ask about, and [serial] can only be the last one known.
     */
    data class Uncertain(override val user: UserHandle?, override val serial: Long) : PrivateSpace

    /** The serial whose rows must be kept out of sight, or zero when none must be. */
    val concealedSerial: Long get() = when (this) {
        Absent, is Unlocked -> 0L
        is Locked, is Uncertain -> serial
    }

    /** Whether this stored key names something inside a space that is locked right now. */
    fun conceals(key: String): Boolean = isConcealed(concealedSerial, key)

    /**
     * Whether there is a padlock to offer at all: a space this pass could name, or one that
     * named itself earlier in this session and will not now.
     *
     * The second half is what keeps a space reachable through a read that failed. [Uncertain]
     * with no profile to point at is treated as locked everywhere else, and a locked space
     * whose row has gone has no way back in — nothing here re-reads on its own, so it would
     * stay that way until something else happened to reload. The row discloses nothing it has
     * not already disclosed: it is offered only because a private space positively answered
     * earlier, and pressing it resolves the space again before it acts on anything.
     */
    val offersPadlockRow: Boolean get() = user != null || serial != 0L

    /**
     * Whether a stored key that names nothing on screen has to be left out of the screens that
     * list stored keys, rather than shown as a row saying the app is gone.
     *
     * Only [Uncertain] asks for that, and it has to: it is the one state where a private key
     * cannot be recognised as one, because the serial to match it against is missing or is only
     * the last one we happened to see. A row reading "app no longer installed" counts what is
     * inside the space as precisely as a name would.
     */
    val concealsUnresolved: Boolean get() = this is Uncertain
}

/**
 * What Android calls a private space's profile. Written out rather than taken from
 * UserManager.USER_TYPE_PROFILE_PRIVATE so this file needs no Android types, and so nothing
 * here has to be compiled against an API level the app also runs below.
 */
const val USER_TYPE_PROFILE_PRIVATE = "android.os.usertype.profile.PRIVATE"

/**
 * Android 15, which is both the first release to have a private space and the first to have
 * getLauncherUserInfo, the only call that says whether a profile is one.
 */
const val PRIVATE_SPACE_SDK = 35

/** [PrivateSpace] without the profile it belongs to, so the decision can be made on its own. */
enum class PrivateSpaceKind { ABSENT, LOCKED, UNLOCKED }

/**
 * What a profile is, from the two things the platform will tell us about it.
 *
 * Quiet mode is how a private space is locked: the profile stays listed and keeps answering
 * questions about itself either way, and this flag is the only thing that says which it is. A
 * null flag is the platform declining to say, which is read as locked — an unknown lock has to
 * be treated as a closed one, or the answer to a failed call is every app inside it.
 */
fun privateSpaceKind(userType: String?, quietMode: Boolean?): PrivateSpaceKind = when {
    userType != USER_TYPE_PROFILE_PRIVATE -> PrivateSpaceKind.ABSENT
    quietMode == false -> PrivateSpaceKind.UNLOCKED
    else -> PrivateSpaceKind.LOCKED
}

/**
 * Whether one profile's apps may be listed, decided from everything the platform was willing to
 * say about that profile and before any key is built from it.
 *
 * Everything the platform would not say counts against listing. A profile that will not say
 * what it is might be the private one; a private one that will not say whether it is locked
 * might be locked; and either mistake puts every name and icon inside a locked private space on
 * screen for whoever is holding the phone. The launcher is the only thing doing the hiding —
 * Android keeps answering for a locked space exactly as it does for an open one — so a failure
 * here is not a degraded list, it is the whole disclosure.
 *
 * [serial] is separate from the private space and applies on every Android: a second profile
 * whose serial cannot be read would have its apps keyed as main-profile apps, indistinguishable
 * from real ones and out of reach of a concealment that works by serial, and those keys get
 * written into favorites and launch counts.
 *
 * Pure, and told [sdk] rather than reading it, so the whole table can be tested on the JVM.
 */
fun shouldListProfile(
    isMainUser: Boolean,
    sdk: Int,
    userType: String?,
    quietMode: Boolean?,
    serial: Long?,
): Boolean = when {
    // The profile the launcher itself runs in. Its serial is zero by definition, and its keys
    // have never carried a suffix, so none of the rest applies to it.
    isMainUser -> true
    // Zero is the launcher's own profile and -1 is what the platform answers for a user it
    // cannot find: a row keyed by either is indistinguishable from a main-profile row, so
    // neither can be concealed by serial later.
    serial == null || serial <= 0L -> false
    // A private space cannot exist below Android 15, so nothing found there can be one.
    sdk < PRIVATE_SPACE_SDK -> true
    userType == null -> false
    userType != USER_TYPE_PROFILE_PRIVATE -> true
    // Positively the private space: listed only while it is positively open.
    else -> quietMode == false
}

/**
 * Whether a stored key belongs to the profile named by [concealedSerial], which is zero when
 * nothing is being concealed.
 *
 * Keys are matched by their profile suffix rather than by looking the row up, because the
 * point is to decide about keys whose rows are deliberately not there to be looked up.
 */
fun isConcealed(concealedSerial: Long, key: String): Boolean =
    concealedSerial > 0L && EntryKeys.userSerial(key) == concealedSerial

/**
 * Whether a stored key has to be left out of a screen that lists stored keys, where [resolves]
 * says whether the key still names something that screen could draw.
 *
 * Three reasons to leave one out, and the last two are the quiet ones.
 *
 * A key belonging to the space while it is locked, which is the obvious one.
 *
 * Any key at all that names nothing while the space cannot be named either, because there is
 * then no serial to recognise a private key by. A row that says an app is no longer installed
 * is still a row, and a handful of them says how many apps are in there.
 *
 * And a key from any profile but the main one that names nothing — in every state, [Absent]
 * included. [Absent] is what a launcher that is not the default home reports on a fresh
 * process: the private profile is invisible to it, nothing is left unclassified, and no serial
 * has been seen yet, so a stored private favorite resolves to nothing and would be drawn as an
 * "app no longer installed" row. That row is countable and it can be ticked away, which is
 * both halves of the threat at once — how many are in there, and the owner's favorites gone
 * for good at the hands of whoever is holding the phone.
 *
 * The cost is that favorites left behind by a private space that was deleted, or a work
 * profile that was removed, sit in storage where nothing will ever offer to remove them. Only
 * a main-profile key that names nothing gets the row that says so.
 */
fun PrivateSpace.concealsStored(key: String, resolves: Boolean): Boolean =
    concealsStoredKey(concealedSerial, concealsUnresolved, key, resolves)

/**
 * [PrivateSpace.concealsStored] told the two things it decides from rather than reading them
 * off a state, like [shouldListProfile] above, so the whole table can be tested on the JVM: a
 * UserHandle cannot be built there, and nothing in this decision has ever looked at one.
 *
 * The four states collapse to three rows here. [PrivateSpace.Absent] and
 * [PrivateSpace.Unlocked] are the same pair of values — nothing concealed, missing rows shown
 * — which is why they behave identically, and the last reason below is what keeps them safe
 * anyway.
 */
fun concealsStoredKey(
    concealedSerial: Long,
    concealsUnresolved: Boolean,
    key: String,
    resolves: Boolean,
): Boolean = isConcealed(concealedSerial, key) ||
    (!resolves && (concealsUnresolved || EntryKeys.userSerial(key) != 0L))

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
