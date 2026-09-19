// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.runtime.Immutable

/**
 * What a row is. Everything that lists, sorts, searches, renames or files rows away treats them
 * alike; only starting one, finding its icon and the menu it offers depend on this.
 */
enum class EntryKind {
    /** An installed app's launchable activity. */
    APP,

    /** A shortcut another app asked to have pinned, such as a browser's bookmark. */
    SHORTCUT,

    /** The entry that asks for a query and hands it to a search engine. */
    SEARCH,

    /** The row that locks and unlocks Android's private space. */
    PRIVATE_SPACE,
}

/**
 * A ComponentName is immutable, but it comes from the platform with no stability information,
 * so Compose infers this whole class as unstable and stops every row that takes one from ever
 * skipping recomposition. The annotation states what is already true.
 */
@Immutable
data class AppInfo(
    val componentName: ComponentName,
    val label: String,
    /** Which profile owns this activity: work, private space, or the main one. */
    val user: UserHandle? = null,
    /** The profile's serial. Zero is the main profile, which is what every old key assumed. */
    val userSerial: Long = 0L,
    val kind: EntryKind = EntryKind.APP,
    /** The publisher's id for a pinned shortcut. Null for every other kind. */
    val shortcutId: String? = null,
    /** A shortcut its publisher has switched off: still listed, shown dimmed, never started. */
    val disabled: Boolean = false,
) {
    // Held rather than derived: this is the map key for overrides, favorites and list item
    // keys, so it is asked for several times per visible row per frame while scrubbing, and
    // flattenToString() builds a new string every time.
    //
    // The main profile's key is byte-for-byte what it was before profiles existed, so every
    // stored favorite, rename, icon and hidden entry still matches. Only apps from a second
    // profile carry the suffix, and those could not have been stored before anyway.
    //
    // A shortcut or the search entry has no activity of its own to name it, so its key carries
    // a prefix no component can start with. See EntryKeys.
    val key: String = when (kind) {
        EntryKind.APP -> EntryKeys.app(componentName.flattenToString(), userSerial)
        EntryKind.SHORTCUT ->
            EntryKeys.shortcut(componentName.packageName, shortcutId.orEmpty(), userSerial)
        EntryKind.SEARCH -> EntryKeys.SEARCH
        EntryKind.PRIVATE_SPACE -> EntryKeys.PRIVATE_SPACE
    }

    val packageName: String get() = componentName.packageName
}