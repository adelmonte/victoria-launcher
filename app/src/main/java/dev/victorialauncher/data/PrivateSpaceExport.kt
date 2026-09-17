// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import org.json.JSONArray
import org.json.JSONObject

// What a backup leaves out so it never names a private app or says how often it was opened —
// whether or not the space happens to be unlocked at the moment someone exports, and whether
// or not the launcher can say which profile the private one is. A profile's serial is useless
// in a backup on its own terms: it will not match on another device, or even this one after
// the profile is recreated, so there is nothing to gain by keeping it and a privacy cost to
// keeping it anyway.
//
// Kept free of Android/DataStore types, like the rest of this file's neighbors, so the decision
// can be tested on the JVM; Prefs.exportJson is the only caller.

// The names live here rather than in Prefs, and Prefs builds its keys from them, because these
// are the settings whose contents are app keys — which is this file's whole subject, and only
// incidentally the store's. Written out in both places they could drift apart with every test
// still green, and the drift would silently put the private space back into the backups.
internal const val PREF_FAVORITES = "favorites_order"
internal const val PREF_FOLDERS = "folders_json"
internal const val PREF_HIDDEN_APPS = "hidden_apps"
internal const val PREF_NAME_OVERRIDES = "name_overrides_json"
internal const val PREF_ICON_OVERRIDES = "icon_overrides_json"
internal const val PREF_LAUNCH_COUNTS = "launch_counts_json"
internal const val PREF_QUICK_LAUNCH_LEFT = "quick_launch_left_key"
internal const val PREF_QUICK_LAUNCH_RIGHT = "quick_launch_right_key"

/**
 * Strips every second profile's keys out of one stored preference's value, given the DataStore
 * name that names its shape. [stripOtherProfiles] is false only when the launcher positively
 * established there is no private space, which strips nothing; see [stripsOtherProfiles].
 *
 * Which serial is the private one is deliberately not part of this. The state that most needs
 * stripping is the one where that serial is unknown — a space this launcher can see is there,
 * or might be, and cannot describe — and a strip keyed on the serial does nothing at all in
 * exactly that state, writing every `…|u<serial>` key and its launch count into a file the
 * user is then told was saved. Matching the suffix instead needs nothing to be known.
 *
 * A work profile's keys go with them. They could not be restored anywhere either — serials are
 * per-device and do not survive the profile being recreated — so an export that keeps them
 * gains an entry that will never match, at the cost of a rule that has to be sure which second
 * profile is which before it can be safe.
 *
 * Returns the value to export, or null when the whole entry should be dropped rather than kept
 * empty — a quick-launch slot pointed at a private app has nothing left to point at once that
 * app is gone from the backup. Every other shape stays present even when everything is stripped
 * out of it: an all-private favorites list exports as an empty one, and a folder every one of
 * whose members was private stays a folder, just an empty one, because the folder itself (its
 * name, its icon) is the user's, not a secret.
 *
 * A preference name this doesn't recognize, and a value with no second profile's key in it,
 * comes back as exactly the object it was handed — never a rebuilt copy — so an export from a
 * device with no private space is byte-for-byte what it always was.
 */
fun stripPrivateSpaceFromExport(name: String, value: Any, stripOtherProfiles: Boolean): Any? {
    if (!stripOtherProfiles) return value
    // The main profile's keys have never carried a suffix, so this is every key that belongs
    // to any other profile — which is what a backup has to leave behind, and all of it.
    val isPrivate = { key: String -> EntryKeys.userSerial(key) != 0L }

    return when (name) {
        PREF_FAVORITES -> (value as? String)?.let { stripNewlineList(it, isPrivate) } ?: value
        PREF_HIDDEN_APPS -> (value as? Set<*>)?.let { stripStringSet(it, isPrivate) } ?: value
        PREF_FOLDERS -> (value as? String)?.let { stripFoldersJson(it, isPrivate) } ?: value
        PREF_NAME_OVERRIDES, PREF_ICON_OVERRIDES, PREF_LAUNCH_COUNTS ->
            (value as? String)?.let { stripJsonMap(it, isPrivate) } ?: value
        PREF_QUICK_LAUNCH_LEFT, PREF_QUICK_LAUNCH_RIGHT -> {
            // Not `?.let { ... } ?: value`: that Elvis would mistake the deliberate null this
            // branch can return (drop the slot) for the cast having failed, and hand the
            // private key straight back out.
            val key = value as? String ?: return value
            if (isPrivate(key)) null else key
        }
        else -> value
    }
}

/** Favorites are newline-joined keys; a folder token never matches, so folders pass through. */
private fun stripNewlineList(raw: String, isPrivate: (String) -> Boolean): String {
    val lines = raw.split("\n")
    val kept = lines.filterNot(isPrivate)
    // Same list back out when nothing was removed, rather than a rejoin that could reformat it.
    return if (kept.size == lines.size) raw else kept.joinToString("\n")
}

private fun stripStringSet(raw: Set<*>, isPrivate: (String) -> Boolean): Set<*> {
    val kept = raw.filterNot { it is String && isPrivate(it) }
    return if (kept.size == raw.size) raw else kept.toSet()
}

/**
 * Each folder keeps its id, name and icon untouched; only its `apps` array loses private
 * members, and an array that loses every member is left in place as an empty one. Malformed
 * JSON, or a folder entry with no `apps` array, is left exactly as found rather than crashing —
 * [Prefs.importJson] is what validates a file on the way back in, not this.
 */
private fun stripFoldersJson(raw: String, isPrivate: (String) -> Boolean): String {
    val array = runCatching { JSONArray(raw) }.getOrNull() ?: return raw
    var changed = false
    for (i in 0 until array.length()) {
        val folder = array.optJSONObject(i) ?: continue
        val apps = folder.optJSONArray("apps") ?: continue
        val kept = JSONArray()
        var droppedAny = false
        for (j in 0 until apps.length()) {
            val key = apps.optString(j)
            if (isPrivate(key)) droppedAny = true else kept.put(key)
        }
        if (droppedAny) {
            folder.put("apps", kept)
            changed = true
        }
    }
    return if (changed) array.toString() else raw
}

/**
 * Name overrides, icon overrides and launch counts are all a JSON object keyed by app key; this
 * drops the entries whose key belongs to a second profile.
 */
private fun stripJsonMap(raw: String, isPrivate: (String) -> Boolean): String {
    val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return raw
    val toDrop = obj.keys().asSequence().filter(isPrivate).toList()
    if (toDrop.isEmpty()) return raw
    toDrop.forEach { obj.remove(it) }
    return obj.toString()
}

/**
 * Whether an export has to leave every second profile's keys out of itself.
 *
 * True in every state but [PrivateSpace.Absent], including the two that know exactly which
 * serial is the private one. Only [PrivateSpace.Absent] is a positive answer that there is no
 * private space to protect; [PrivateSpace.Uncertain] is the launcher saying it does not know,
 * and a backup written on a "do not know" has to assume there is something to leave out.
 */
val PrivateSpace.stripsOtherProfiles: Boolean get() = this != PrivateSpace.Absent
