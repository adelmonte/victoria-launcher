// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

/**
 * Which shortcut ids stay pinned when one of them is taken away.
 *
 * LauncherApps.pinShortcuts does not remove a shortcut: it replaces the whole pinned set for
 * one package in one profile, in one call. So unpinning means listing everything that stays,
 * and getting that list wrong unpins whatever was left out of it. An id belonging to another
 * package or another profile must not appear in it either — the same id can exist twice over,
 * and pinning one package's id against another is either refused or wrong.
 *
 * Kept free of Android types so all of it can be tested on the JVM.
 */
object PinnedShortcuts {

    fun remainingIds(
        pinned: List<EntryKeys.ShortcutRef>,
        removed: EntryKeys.ShortcutRef,
    ): List<String> =
        pinned
            .filter {
                it.packageName == removed.packageName &&
                    it.userSerial == removed.userSerial &&
                    it.shortcutId != removed.shortcutId
            }
            .map { it.shortcutId }
            .distinct()
}
