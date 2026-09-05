// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONObject

enum class EdgeSide { LEFT, RIGHT, BOTH }
enum class AppFont { SYSTEM, SANS_SERIF, SERIF, MONOSPACE }

/** AUTO picks light or dark text from the wallpaper's own colours. */
enum class TextColorMode { AUTO, LIGHT, DARK }

private val Context.dataStore by preferencesDataStore(name = "victoria_prefs")

class Prefs(private val context: Context) {

    private object Keys {
        val HIDDEN_APPS = stringSetPreferencesKey("hidden_apps")
        val FAVORITES = stringPreferencesKey("favorites_order")
        val FOLDERS = stringPreferencesKey("folders_json")
        val NAME_OVERRIDES = stringPreferencesKey("name_overrides_json")
        val ICON_OVERRIDES = stringPreferencesKey("icon_overrides_json")
        val ICON_SIZE_DP = intPreferencesKey("icon_size_dp")
        val LABEL_SIZE_SP = intPreferencesKey("label_size_sp")
        val ITEM_SPACING_DP = intPreferencesKey("item_spacing_dp")
        val SIDE_PADDING_DP = intPreferencesKey("side_padding_dp")
        val NOW_PLAYING_HEIGHT_DP = intPreferencesKey("now_playing_height_dp")
        val NOW_PLAYING_PAD_TOP = intPreferencesKey("now_playing_pad_top")
        val NOW_PLAYING_PAD_BOTTOM = intPreferencesKey("now_playing_pad_bottom")
        val WIDGET_PAD_TOP = intPreferencesKey("widget_pad_top")
        val WIDGET_PAD_BOTTOM = intPreferencesKey("widget_pad_bottom")
        val FAVORITES_PAD_TOP = intPreferencesKey("favorites_pad_top")
        val FAVORITES_PAD_BOTTOM = intPreferencesKey("favorites_pad_bottom")
        val FONT = stringPreferencesKey("font")
        val HIDE_STATUS_BAR = booleanPreferencesKey("hide_status_bar")
        val DIM_WALLPAPER_ALPHA = floatPreferencesKey("dim_wallpaper_alpha")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val DIM_HOME_ALPHA = floatPreferencesKey("dim_home_alpha")
        val SHOW_FAVORITE_LABELS = booleanPreferencesKey("show_favorite_labels")
        val TEXT_COLOR_MODE = stringPreferencesKey("text_color_mode")
        val DOUBLE_TAP_TO_LOCK = booleanPreferencesKey("double_tap_to_lock")
        val EDGE_SIDE = stringPreferencesKey("edge_side")
        val ALWAYS_SHOW_AZ = booleanPreferencesKey("always_show_az")
        val SHOW_ALPHABET = booleanPreferencesKey("show_alphabet")
        val ALIGN_RIGHT = booleanPreferencesKey("align_right")
        val ICON_PACK_PACKAGE = stringPreferencesKey("icon_pack_package")
        val NOW_PLAYING_ENABLED = booleanPreferencesKey("now_playing_enabled")
        val WIDGET_ID = intPreferencesKey("widget_id")
        val WIDGET_POSITION = intPreferencesKey("widget_position")
        val WIDGET_HEIGHT_DP = intPreferencesKey("widget_height_dp")
    }

    private val data get() = context.dataStore.data

    /** Favorites are stored as one newline-joined string; these are the only two readers. */
    private fun readFavorites(pref: Preferences): List<String> =
        pref[Keys.FAVORITES]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    private fun MutablePreferences.writeFavorites(list: List<String>) {
        this[Keys.FAVORITES] = list.joinToString("\n")
    }

    val hiddenApps: Flow<Set<String>> =
        data.map { it[Keys.HIDDEN_APPS] ?: emptySet() }.distinctUntilChanged()

    val favorites: Flow<List<String>> =
        data.map { pref -> readFavorites(pref) }.distinctUntilChanged()

    val folders: Flow<List<Folder>> =
        data.map { pref -> foldersFromJson(pref[Keys.FOLDERS]) }.distinctUntilChanged()

    val nameOverrides: Flow<Map<String, String>> =
        data.map { pref -> jsonToMap(pref[Keys.NAME_OVERRIDES]) }.distinctUntilChanged()

    val iconOverrides: Flow<Map<String, String>> =
        data.map { pref -> jsonToMap(pref[Keys.ICON_OVERRIDES]) }.distinctUntilChanged()

    val iconSizeDp: Flow<Int> = data.map { it[Keys.ICON_SIZE_DP] ?: 56 }.distinctUntilChanged()

    val labelSizeSp: Flow<Int> = data.map { it[Keys.LABEL_SIZE_SP] ?: 16 }.distinctUntilChanged()

    /** Vertical gap between favorite rows. */
    val itemSpacingDp: Flow<Int> = data.map { it[Keys.ITEM_SPACING_DP] ?: 10 }.distinctUntilChanged()

    /** Left/right inset applied to every element on the home screen, so they stay in line. */
    val sidePaddingDp: Flow<Int> = data.map { it[Keys.SIDE_PADDING_DP] ?: 20 }.distinctUntilChanged()

    val nowPlayingHeightDp: Flow<Int> = data.map { it[Keys.NOW_PLAYING_HEIGHT_DP] ?: 64 }.distinctUntilChanged()

    /** Draggable top/bottom padding for each home block, set in edit mode. */
    val homePaddings: Flow<HomePaddings> = data.map {
        HomePaddings(
            nowPlayingTop = it[Keys.NOW_PLAYING_PAD_TOP] ?: 8,
            nowPlayingBottom = it[Keys.NOW_PLAYING_PAD_BOTTOM] ?: 8,
            widgetTop = it[Keys.WIDGET_PAD_TOP] ?: 8,
            widgetBottom = it[Keys.WIDGET_PAD_BOTTOM] ?: 8,
            favoritesTop = it[Keys.FAVORITES_PAD_TOP] ?: 8,
            favoritesBottom = it[Keys.FAVORITES_PAD_BOTTOM] ?: 24,
        )
    }.distinctUntilChanged()

    val font: Flow<AppFont> = data.map {
        runCatching { AppFont.valueOf(it[Keys.FONT] ?: AppFont.SYSTEM.name) }.getOrDefault(AppFont.SYSTEM)
    }.distinctUntilChanged()

    val hideStatusBar: Flow<Boolean> = data.map { it[Keys.HIDE_STATUS_BAR] ?: false }.distinctUntilChanged()

    val dimWallpaperAlpha: Flow<Float> = data.map { it[Keys.DIM_WALLPAPER_ALPHA] ?: 0.35f }.distinctUntilChanged()

    val hapticsEnabled: Flow<Boolean> = data.map { it[Keys.HAPTICS_ENABLED] ?: true }.distinctUntilChanged()

    val dimHomeAlpha: Flow<Float> = data.map { it[Keys.DIM_HOME_ALPHA] ?: 0f }.distinctUntilChanged()

    val showFavoriteLabels: Flow<Boolean> =
        data.map { it[Keys.SHOW_FAVORITE_LABELS] ?: true }.distinctUntilChanged()

    val textColorMode: Flow<TextColorMode> = data.map {
        runCatching { TextColorMode.valueOf(it[Keys.TEXT_COLOR_MODE] ?: TextColorMode.AUTO.name) }
            .getOrDefault(TextColorMode.AUTO)
    }.distinctUntilChanged()

    val doubleTapToLock: Flow<Boolean> =
        data.map { it[Keys.DOUBLE_TAP_TO_LOCK] ?: false }.distinctUntilChanged()

    val edgeSide: Flow<EdgeSide> = data.map {
        runCatching { EdgeSide.valueOf(it[Keys.EDGE_SIDE] ?: EdgeSide.RIGHT.name) }.getOrDefault(EdgeSide.RIGHT)
    }.distinctUntilChanged()

    /** Keep the A-Z strip on screen even when the app list is closed. */
    val alwaysShowAz: Flow<Boolean> = data.map { it[Keys.ALWAYS_SHOW_AZ] ?: false }.distinctUntilChanged()

    /** The A-Z strip inside the app list; the edge gesture still works without it. */
    val showAlphabet: Flow<Boolean> = data.map { it[Keys.SHOW_ALPHABET] ?: true }.distinctUntilChanged()

    /** Lay icons and labels out from the right edge instead of the left. */
    val alignRight: Flow<Boolean> = data.map { it[Keys.ALIGN_RIGHT] ?: false }.distinctUntilChanged()

    val iconPackPackage: Flow<String?> = data.map { it[Keys.ICON_PACK_PACKAGE] }.distinctUntilChanged()

    val nowPlayingEnabled: Flow<Boolean> = data.map { it[Keys.NOW_PLAYING_ENABLED] ?: false }.distinctUntilChanged()

    val widgetId: Flow<Int> = data.map { it[Keys.WIDGET_ID] ?: -1 }.distinctUntilChanged()
    /** Index into the merged (favorites + widget) home list where the widget sits. 0 = top. */
    val widgetPosition: Flow<Int> = data.map { it[Keys.WIDGET_POSITION] ?: 0 }.distinctUntilChanged()
    val widgetHeightDp: Flow<Int> = data.map { it[Keys.WIDGET_HEIGHT_DP] ?: 180 }.distinctUntilChanged()

    suspend fun setHidden(componentKey: String, hidden: Boolean) {
        context.dataStore.edit { pref ->
            val current = pref[Keys.HIDDEN_APPS] ?: emptySet()
            pref[Keys.HIDDEN_APPS] = if (hidden) current + componentKey else current - componentKey
        }
    }

    suspend fun setFavorites(list: List<String>) {
        context.dataStore.edit { it.writeFavorites(list) }
    }

    suspend fun addFavorite(componentKey: String) {
        context.dataStore.edit { pref ->
            val current = readFavorites(pref)
            if (componentKey !in current) pref.writeFavorites(current + componentKey)
        }
    }

    suspend fun removeFavorite(componentKey: String) {
        context.dataStore.edit { pref ->
            pref.writeFavorites(readFavorites(pref) - componentKey)
        }
    }

    /** Creates the folder if [id] is new, otherwise replaces it. */
    suspend fun upsertFolder(folder: Folder) {
        context.dataStore.edit { pref ->
            val existing = foldersFromJson(pref[Keys.FOLDERS])
            val updated = if (existing.any { it.id == folder.id }) {
                existing.map { if (it.id == folder.id) folder else it }
            } else {
                existing + folder
            }
            pref[Keys.FOLDERS] = foldersToJson(updated)
        }
    }

    /** Removes the folder and its row; the apps themselves are untouched. */
    suspend fun deleteFolder(id: String) {
        context.dataStore.edit { pref ->
            pref[Keys.FOLDERS] = foldersToJson(foldersFromJson(pref[Keys.FOLDERS]).filterNot { it.id == id })
            pref.writeFavorites(readFavorites(pref).filterNot { it == folderToken(id) })
        }
    }

    suspend fun setFolderIcon(id: String, icon: String?) {
        context.dataStore.edit { pref ->
            val updated = foldersFromJson(pref[Keys.FOLDERS])
                .map { if (it.id == id) it.copy(icon = icon) else it }
            pref[Keys.FOLDERS] = foldersToJson(updated)
        }
    }

    suspend fun setFolderApps(id: String, apps: List<String>) {
        context.dataStore.edit { pref ->
            val updated = foldersFromJson(pref[Keys.FOLDERS])
                .map { if (it.id == id) it.copy(apps = apps) else it }
            pref[Keys.FOLDERS] = foldersToJson(updated)
        }
    }

    /**
     * Puts [componentKey] in the folder. An app that was a top-level favorite moves into it
     * rather than being duplicated in both places.
     */
    suspend fun addAppToFolder(folderId: String, componentKey: String) {
        context.dataStore.edit { pref ->
            val updated = foldersFromJson(pref[Keys.FOLDERS]).map { folder ->
                if (folder.id == folderId && componentKey !in folder.apps) {
                    folder.copy(apps = folder.apps + componentKey)
                } else {
                    folder
                }
            }
            pref[Keys.FOLDERS] = foldersToJson(updated)
            pref.writeFavorites(readFavorites(pref).filterNot { it == componentKey })
        }
    }

    suspend fun removeAppFromFolder(folderId: String, componentKey: String) {
        context.dataStore.edit { pref ->
            val updated = foldersFromJson(pref[Keys.FOLDERS]).map { folder ->
                if (folder.id == folderId) folder.copy(apps = folder.apps - componentKey) else folder
            }
            pref[Keys.FOLDERS] = foldersToJson(updated)
        }
    }

    suspend fun setNameOverride(componentKey: String, name: String?) {
        context.dataStore.edit { pref ->
            val map = jsonToMap(pref[Keys.NAME_OVERRIDES]).toMutableMap()
            if (name.isNullOrBlank()) map.remove(componentKey) else map[componentKey] = name
            pref[Keys.NAME_OVERRIDES] = mapToJson(map)
        }
    }

    suspend fun setIconOverride(componentKey: String, uri: String?) {
        context.dataStore.edit { pref ->
            val map = jsonToMap(pref[Keys.ICON_OVERRIDES]).toMutableMap()
            if (uri.isNullOrBlank()) map.remove(componentKey) else map[componentKey] = uri
            pref[Keys.ICON_OVERRIDES] = mapToJson(map)
        }
    }

    suspend fun setIconSizeDp(v: Int) {
        context.dataStore.edit { it[Keys.ICON_SIZE_DP] = v }
    }

    suspend fun setLabelSizeSp(v: Int) {
        context.dataStore.edit { it[Keys.LABEL_SIZE_SP] = v }
    }

    suspend fun setSidePaddingDp(v: Int) {
        context.dataStore.edit { it[Keys.SIDE_PADDING_DP] = v }
    }

    suspend fun setItemSpacingDp(v: Int) {
        context.dataStore.edit { it[Keys.ITEM_SPACING_DP] = v }
    }

    suspend fun setNowPlayingHeightDp(v: Int) {
        context.dataStore.edit { it[Keys.NOW_PLAYING_HEIGHT_DP] = v }
    }

    suspend fun setHomePadding(slot: PaddingSlot, v: Int) {
        val key = when (slot) {
            PaddingSlot.NOW_PLAYING_TOP -> Keys.NOW_PLAYING_PAD_TOP
            PaddingSlot.NOW_PLAYING_BOTTOM -> Keys.NOW_PLAYING_PAD_BOTTOM
            PaddingSlot.WIDGET_TOP -> Keys.WIDGET_PAD_TOP
            PaddingSlot.WIDGET_BOTTOM -> Keys.WIDGET_PAD_BOTTOM
            PaddingSlot.FAVORITES_TOP -> Keys.FAVORITES_PAD_TOP
            PaddingSlot.FAVORITES_BOTTOM -> Keys.FAVORITES_PAD_BOTTOM
        }
        context.dataStore.edit { it[key] = v }
    }

    suspend fun setFont(f: AppFont) {
        context.dataStore.edit { it[Keys.FONT] = f.name }
    }

    suspend fun setHideStatusBar(v: Boolean) {
        context.dataStore.edit { it[Keys.HIDE_STATUS_BAR] = v }
    }

    suspend fun setDimWallpaperAlpha(v: Float) {
        context.dataStore.edit { it[Keys.DIM_WALLPAPER_ALPHA] = v }
    }

    suspend fun setDimHomeAlpha(v: Float) {
        context.dataStore.edit { it[Keys.DIM_HOME_ALPHA] = v }
    }

    suspend fun setShowFavoriteLabels(v: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_FAVORITE_LABELS] = v }
    }

    suspend fun setTextColorMode(v: TextColorMode) {
        context.dataStore.edit { it[Keys.TEXT_COLOR_MODE] = v.name }
    }

    suspend fun setDoubleTapToLock(v: Boolean) {
        context.dataStore.edit { it[Keys.DOUBLE_TAP_TO_LOCK] = v }
    }

    suspend fun setHapticsEnabled(v: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS_ENABLED] = v }
    }

    suspend fun setShowAlphabet(v: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_ALPHABET] = v }
    }

    suspend fun setAlignRight(v: Boolean) {
        context.dataStore.edit { it[Keys.ALIGN_RIGHT] = v }
    }

    suspend fun setAlwaysShowAz(v: Boolean) {
        context.dataStore.edit { it[Keys.ALWAYS_SHOW_AZ] = v }
    }

    suspend fun setEdgeSide(v: EdgeSide) {
        context.dataStore.edit { it[Keys.EDGE_SIDE] = v.name }
    }

    suspend fun setIconPackPackage(pkg: String?) {
        context.dataStore.edit { pref ->
            if (pkg.isNullOrBlank()) pref.remove(Keys.ICON_PACK_PACKAGE) else pref[Keys.ICON_PACK_PACKAGE] = pkg
        }
    }

    suspend fun setWidgetId(id: Int) {
        context.dataStore.edit { it[Keys.WIDGET_ID] = id }
    }

    suspend fun setNowPlayingEnabled(v: Boolean) {
        context.dataStore.edit { it[Keys.NOW_PLAYING_ENABLED] = v }
    }

    suspend fun setWidgetPosition(v: Int) {
        context.dataStore.edit { it[Keys.WIDGET_POSITION] = v }
    }

    suspend fun setWidgetHeightDp(v: Int) {
        context.dataStore.edit { it[Keys.WIDGET_HEIGHT_DP] = v }
    }

    private fun jsonToMap(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, String>()
            obj.keys().forEach { k -> map[k] = obj.getString(k) }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun mapToJson(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }
}