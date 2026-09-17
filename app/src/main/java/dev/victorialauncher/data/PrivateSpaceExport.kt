// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import org.json.JSONArray
import org.json.JSONObject

// What a backup leaves out so it never names a private app or says how often it was opened —
// whether or not the space happens to be unlocked at the moment someone exports. A private
// profile's serial is also useless in a backup on its own terms: it will not match on another
// device, or even this one after the space is recreated, so there is nothing to gain by keeping
// it and a privacy cost to keeping it anyway.
//
// Kept free of Android/DataStore types, like the rest of this file's neighbors, so the decision
// can be tested on the JVM; Prefs.exportJson is the only caller.

private const val PREF_FAVORITES = "favorites_order"
private const val PREF_FOLDERS = "folders_json"
private const val PREF_HIDDEN_APPS = "hidden_apps"
private const val PREF_NAME_OVERRIDES = "name_overrides_json"
private const val PREF_ICON_OVERRIDES = "icon_overrides_json"
private const val PREF_LAUNCH_COUNTS = "launch_counts_json"
private const val PREF_QUICK_LAUNCH_LEFT = "quick_launch_left_key"
private const val PREF_QUICK_LAUNCH_RIGHT = "quick_launch_right_key"

/**
 * Strips [privateSerial]'s keys out of one stored preference's value, given the DataStore name
 * that names its shape. [privateSerial] is null or zero when there is no private space, which
 * strips nothing.
 *
 * Returns the value to export, or null when the whole entry should be dropped rather than kept
 * empty — a quick-launch slot pointed at a private app has nothing left to point at once that
 * app is gone from the backup. Every other shape stays present even when everything is stripped
 * out of it: an all-private favorites list exports as an empty one, and a folder every one of
 * whose members was private stays a folder, just an empty one, because the folder itself (its
 * name, its icon) is the user's, not a secret.
 *
 * A preference name this doesn't recognize, and a value with nothing of [privateSerial]'s in it,
 * comes back as exactly the object it was handed — never a rebuilt copy — so an export with no
 * private space touched is byte-for-byte what it always was.
 */
fun stripPrivateSpaceFromExport(name: String, value: Any, privateSerial: Long?): Any? {
    val serial = privateSerial ?: 0L
    if (serial == 0L) return value
    val isPrivate = { key: String -> isConcealed(serial, key) }

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
 * drops the entries whose key is [privateSerial]'s.
 */
private fun stripJsonMap(raw: String, isPrivate: (String) -> Boolean): String {
    val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return raw
    val toDrop = obj.keys().asSequence().filter(isPrivate).toList()
    if (toDrop.isEmpty()) return raw
    toDrop.forEach { obj.remove(it) }
    return obj.toString()
}
