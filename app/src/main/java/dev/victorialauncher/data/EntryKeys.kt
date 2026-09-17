// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

/**
 * The strings that identify a row everywhere it is stored: favorites, folders, renames, icon
 * overrides, the hidden set and launch counts. Nothing that stores a key ever looks inside it,
 * so a new kind of row only needs a key that cannot be mistaken for one of the others.
 *
 * An app's key is its flattened ComponentName, `package/class`, and a folder's token starts
 * with `folder:`. Java package names cannot contain a colon, so a prefix ending in one can
 * never be the start of an app key.
 *
 * Kept free of Android types so all of it can be tested on the JVM.
 */
object EntryKeys {
    const val SHORTCUT_PREFIX = "shortcut:"

    /** The one search entry. The suffix leaves room for more than one later. */
    const val SEARCH = "search:default"

    private const val USER_SUFFIX = "|u"
    private val userSuffixPattern = Regex("""\|u(\d+)$""")

    /** The longest shortcut id this store will keep; a publisher's id has no length limit of its own. */
    private const val MAX_SHORTCUT_ID_LENGTH = 1024

    /**
     * Whether a publisher's shortcut id is safe to keep at all.
     *
     * Favorites are stored as one newline-joined string ([Prefs.readFavorites]), so an id
     * carrying `\n` — or any other ISO control character, which is never meaningful in an id —
     * would corrupt that list for every favorite after it, not just itself. A shortcut whose id
     * fails this is refused rather than stored: [dev.victorialauncher.data.AppRepository]
     * filters it out of the pinned list, and the pin confirmation refuses to accept it.
     */
    fun isStorableShortcutId(id: String): Boolean =
        id.isNotEmpty() && id.length <= MAX_SHORTCUT_ID_LENGTH && id.none { Character.isISOControl(it) }

    /**
     * The main profile's key is byte-for-byte what it was before profiles existed, so every
     * stored favorite, rename, icon and hidden entry still matches. Only apps from a second
     * profile carry the suffix, and those could not have been stored before anyway.
     */
    fun app(flattenedComponent: String, userSerial: Long = 0L): String =
        if (userSerial == 0L) flattenedComponent else flattenedComponent + USER_SUFFIX + userSerial

    fun shortcut(packageName: String, shortcutId: String, userSerial: Long = 0L): String =
        app("$SHORTCUT_PREFIX$packageName/$shortcutId", userSerial)

    fun isShortcut(key: String): Boolean = key.startsWith(SHORTCUT_PREFIX)

    fun isSearch(key: String): Boolean = key == SEARCH

    data class ShortcutRef(val packageName: String, val shortcutId: String, val userSerial: Long)

    /**
     * Null for anything that is not a well-formed shortcut key. A package name cannot contain
     * a slash, so the first one ends it; the publisher chooses the id, which may contain
     * anything, including slashes. A trailing `|u<digits>` is always read as the profile, so an
     * id that itself ends that way in the main profile comes back as a different id in another
     * profile. The key still identifies one row and never matches a real shortcut by mistake,
     * because the lookup is by the parsed pair and that pair does not exist.
     */
    fun parseShortcut(key: String): ShortcutRef? {
        if (!isShortcut(key)) return null
        var body = key.removePrefix(SHORTCUT_PREFIX)
        var serial = 0L
        userSuffixPattern.find(body)?.let { match ->
            serial = match.groupValues[1].toLongOrNull() ?: return null
            body = body.substring(0, match.range.first)
        }
        val slash = body.indexOf('/')
        if (slash <= 0 || slash == body.length - 1) return null
        return ShortcutRef(body.substring(0, slash), body.substring(slash + 1), serial)
    }
}
