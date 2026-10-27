// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * A named group of apps that lives in the favorites list.
 *
 * A folder occupies one row on the home screen and expands in place. It can also be filed in
 * the A-Z list under its name (#126), but never instead of its apps: that list is an index of
 * everything installed, so a folder never hides an app from where you'd look for it.
 */
data class Folder(
    val id: String,
    val name: String,
    val apps: List<String>,
    /** Same encoding as an app's icon override: `pack:<pkg>:<drawable>` or a content URI. */
    val icon: String? = null,
    /** Shown above the apps while the folder is open (#115). */
    val widgets: List<FolderWidget> = emptyList(),
    /** Whether the Now Playing card opens with the folder too (#130). */
    val nowPlaying: Boolean = false,
)

/** A widget kept in a folder, and how tall it is drawn there. */
data class FolderWidget(val id: Int, val heightDp: Int)

const val FOLDER_WIDGET_DEFAULT_HEIGHT_DP = 180

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
                if (folder.widgets.isNotEmpty()) {
                    put("widgets", JSONArray().apply {
                        folder.widgets.forEach { put(JSONObject().put("id", it.id).put("h", it.heightDp)) }
                    })
                }
                if (folder.nowPlaying) put("nowPlaying", true)
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
                widgets = obj.optJSONArray("widgets")?.let { array ->
                    (0 until array.length()).mapNotNull { j ->
                        val w = array.optJSONObject(j) ?: return@mapNotNull null
                        val wid = w.optInt("id", -1).takeIf { it > 0 } ?: return@mapNotNull null
                        FolderWidget(wid, w.optInt("h", FOLDER_WIDGET_DEFAULT_HEIGHT_DP))
                    }
                }.orEmpty(),
                nowPlaying = obj.optBoolean("nowPlaying", false),
            )
        }
    }.getOrDefault(emptyList())
}