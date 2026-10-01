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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.victorialauncher.R
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.ui.common.AppIcon

/**
 * Pick one app, or none. Same shape as the favorites picker, with radio buttons because these
 * gestures bind to a single app.
 */
@Composable
fun AppPickerScreen(
    title: String,
    allApps: List<AppInfo>,
    selectedKey: String?,
    nameOverrides: Map<String, String>,
    iconSizeDp: Int,
    onPick: (AppInfo?) -> Unit,
    /**
     * An entry offered above the apps that is not one, with the key it stores.
     *
     * Null for the pickers that only ever bind an app. The corner button also takes "open the
     * search", which is a thing to do rather than a thing to launch and so has no app to pick.
     */
    extraChoice: Pair<String, String>? = null,
    onPickExtra: (String) -> Unit = {},
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(null) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.settings_quick_launch_none),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    RadioButton(selected = selectedKey == null, onClick = { onPick(null) })
                }
                HorizontalDivider()
            }

            if (extraChoice != null) {
                val (key, label) = extraChoice
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickExtra(key) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        RadioButton(selected = selectedKey == key, onClick = { onPickExtra(key) })
                    }
                    HorizontalDivider()
                }
            }

            items(allApps, key = { it.key }) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(app) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app = app, sizeDp = minOf(iconSizeDp, 44))
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            nameOverrides[app.key] ?: app.label,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    RadioButton(selected = selectedKey == app.key, onClick = { onPick(app) })
                }
            }
        }
    }
}
