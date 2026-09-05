// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * A named group of apps that lives in the favorites list.
 *
 * Folders deliberately don't touch the A-Z list: that list is an index of everything
 * installed and stays flat, so a folder never hides an app from where you'd look for it.
 * Instead a folder occupies one row on the home screen and expands in place.
 */
data class Folder(
    val id: String,
    val name: String,
    val apps: List<String>,
    /** Same encoding as an app's icon override: `pack:<pkg>:<drawable>` or a content URI. */
    val icon: String? = null,
)

/** Favorites hold either an app's component key or a reference to a folder. */
const val FOLDER_TOKEN_PREFIX = "folder:"

fun folderToken(id: String) = "$FOLDER_TOKEN_PREFIX$id"

fun folderIdFromToken(token: String): String? =
    if (token.startsWith(FOLDER_TOKEN_PREFIX)) token.removePrefix(FOLDER_TOKEN_PREFIX) else null

internal fun foldersToJson(folders: List<Folder>): String {
    val array = JSONArray()
    folders.forEach { folder ->
        array.put(
            JSONObject().apply {
                put("id", folder.id)
                put("name", folder.name)
                put("apps", JSONArray().apply { folder.apps.forEach { put(it) } })
                folder.icon?.let { put("icon", it) }
            }
        )
    }
    return array.toString()
}

internal fun foldersFromJson(json: String?): List<Folder> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val appsArray = obj.optJSONArray("apps") ?: JSONArray()
            Folder(
                id = id,
                name = obj.optString("name", "Folder"),
                apps = (0 until appsArray.length()).mapNotNull { appsArray.optString(it).takeIf(String::isNotBlank) },
                icon = obj.optString("icon").takeIf { it.isNotBlank() },
            )
        }
    }.getOrDefault(emptyList())
}