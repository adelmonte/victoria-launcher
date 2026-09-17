// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.victorialauncher.R
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.Folder
import dev.victorialauncher.data.PrivateSpace
import dev.victorialauncher.data.restoreConcealed
import dev.victorialauncher.ui.common.AppIcon

/**
 * Tick the apps that belong in a folder, and drag the ones already in it into the order they
 * should open in.
 *
 * The members are listed first, in their own order, rather than left to be found among every
 * installed app: their order is the thing being edited here, and it is not alphabetical.
 */
@Composable
fun FolderAppsScreen(
    folder: Folder?,
    allApps: List<AppInfo>,
    /** So a member that lives in a locked private space can be left out of this screen. */
    privateSpace: PrivateSpace,
    nameOverrides: Map<String, String>,
    iconSizeDp: Int,
    onSetInFolder: (AppInfo, Boolean) -> Unit,
    onReorder: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    val members = folder?.apps.orEmpty()
    val memberSet = members.toSet()
    // Left out rather than shown as a missing row, for the same reason as in the favorites
    // screen: a row saying an app is gone still says how many the locked space holds.
    val shownMembers = remember(members, privateSpace) { members.filterNot(privateSpace::conceals) }

    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
                title = { Text(folder?.name ?: stringResource(R.string.folder_title_fallback)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
            )
        },
    ) { padding ->
        if (folder == null) {
            Text(stringResource(R.string.folder_gone), modifier = Modifier.padding(padding).padding(20.dp))
            return@Scaffold
        }

        val appsByKey = remember(allApps) { allApps.associateBy { it.key } }

        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.padding(padding)) {
            item {
                ListSectionLabel(stringResource(R.string.folder_member_count, shownMembers.size))
            }

            item {
                ReorderableRows(
                    keys = shownMembers,
                    // Put back what was left out, or the first drag would drop it.
                    onReorder = { order -> onReorder(restoreConcealed(members, order, privateSpace::conceals)) },
                ) { key ->
                    val app = appsByKey[key]
                    if (app != null) {
                        AppIcon(app = app, sizeDp = minOf(iconSizeDp, 44))
                        Spacer(Modifier.width(16.dp))
                        Text(
                            nameOverrides[app.key] ?: app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Checkbox(checked = true, onCheckedChange = { onSetInFolder(app, false) })
                    } else {
                        // Installed when it was added, gone now. Shown rather than skipped so
                        // the count matches what is listed.
                        Text(
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

            items(allApps.filterNot { it.key in memberSet }, key = { it.key }) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSetInFolder(app, true) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app = app, sizeDp = minOf(iconSizeDp, 44))
                    Spacer(Modifier.width(16.dp))
                    Text(
                        nameOverrides[app.key] ?: app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Checkbox(checked = false, onCheckedChange = { onSetInFolder(app, true) })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            }
        }
    }
}

@Composable
internal fun ListSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}
