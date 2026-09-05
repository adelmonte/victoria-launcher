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
import androidx.compose.ui.unit.dp
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.Folder
import dev.victorialauncher.ui.common.AppIcon
import dev.victorialauncher.R
import androidx.compose.ui.res.stringResource

/** Tick the apps that belong in a folder. Same shape as the favorites picker. */
@Composable
fun FolderAppsScreen(
    folder: Folder?,
    allApps: List<AppInfo>,
    nameOverrides: Map<String, String>,
    iconSizeDp: Int,
    onSetInFolder: (AppInfo, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    val members = folder?.apps?.toSet().orEmpty()

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

        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.padding(padding)) {
            item {
                Text(
                    stringResource(R.string.folder_member_count, members.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            items(allApps, key = { it.key }) { app ->
                val checked = members.contains(app.key)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSetInFolder(app, !checked) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app = app, sizeDp = minOf(iconSizeDp, 44))
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(nameOverrides[app.key] ?: app.label, style = MaterialTheme.typography.bodyLarge)
                    }
                    Checkbox(checked = checked, onCheckedChange = { onSetInFolder(app, it) })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            }
        }
    }
}