// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.Folder
import dev.victorialauncher.data.PrivateSpace
import dev.victorialauncher.data.folderIdFromToken
import dev.victorialauncher.data.restoreConcealed
import dev.victorialauncher.ui.common.AppIcon
import dev.victorialauncher.R
import androidx.compose.ui.res.stringResource

/**
 * Pick favorites from the full app list with checkboxes, rather than relying on a
 * long-press in the A-Z list that nothing advertises.
 *
 * The ones already chosen are listed first, in the order they sit on the home screen, and can
 * be dragged into a different one — the same shape as a folder's members, and for the same
 * reason: that order is not alphabetical and cannot be edited in a list that is.
 */
@Composable
fun ManageFavoritesScreen(
    allApps: List<AppInfo>,
    favoriteKeys: List<String>,
    folders: List<Folder>,
    /** So a favorite that lives in a locked private space can be left out of this screen. */
    privateSpace: PrivateSpace,
    nameOverrides: Map<String, String>,
    iconSizeDp: Int,
    onSetFavorite: (AppInfo, Boolean) -> Unit,
    onReorder: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    val favorites = favoriteKeys.toSet()
    // A favorite inside a locked private space is left out entirely, rather than shown as the
    // row that says an app is missing: that row would still say how many are in there. The
    // stored keys are untouched, so those favorites come back when the space is opened.
    val shownKeys = remember(favoriteKeys, privateSpace) {
        favoriteKeys.filterNot(privateSpace::conceals)
    }
    val appsByKey = remember(allApps) { allApps.associateBy { it.key } }
    val foldersById = remember(folders) { folders.associateBy { it.id } }

    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.favorites_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.padding(padding),
        ) {
            item {
                ListSectionLabel(stringResource(R.string.favorites_count, shownKeys.size))
            }

            item {
                ReorderableRows(
                    keys = shownKeys,
                    // What was left out goes back where it was. The screen writes back the
                    // whole list it was given, so without this the first drag would delete
                    // every favorite the locked space is hiding.
                    onReorder = { order -> onReorder(restoreConcealed(favoriteKeys, order, privateSpace::conceals)) },
                ) { key ->
                    val folder = folderIdFromToken(key)?.let { foldersById[it] }
                    val app = appsByKey[key]
                    when {
                        folder != null -> {
                            // A folder is one favorite like any other and moves among them,
                            // but it is not an app: what it holds is edited in its own screen,
                            // so there is nothing here to untick.
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                folder.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        app != null -> {
                            AppIcon(app = app, sizeDp = minOf(iconSizeDp, 44))
                            Spacer(Modifier.width(16.dp))
                            Text(
                                nameOverrides[app.key] ?: app.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Checkbox(checked = true, onCheckedChange = { onSetFavorite(app, false) })
                        }

                        else -> Text(
                            stringResource(R.string.folder_member_missing),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                ListSectionLabel(stringResource(R.string.folder_add_apps))
            }

            items(allApps.filterNot { it.key in favorites }, key = { it.key }) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSetFavorite(app, true) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app = app, sizeDp = minOf(iconSizeDp, 44))
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(nameOverrides[app.key] ?: app.label, style = MaterialTheme.typography.bodyLarge)
                    }
                    Checkbox(checked = false, onCheckedChange = { onSetFavorite(app, true) })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            }
        }
    }
}