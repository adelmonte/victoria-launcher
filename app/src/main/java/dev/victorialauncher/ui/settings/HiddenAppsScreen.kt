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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.victorialauncher.R
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.ui.common.AppIcon

/**
 * Hide apps from the A-Z list.
 *
 * Same shape as the favorites and folder screens: the ones already chosen are listed first, so
 * what is currently hidden can be read at a glance and put back without hunting for it among
 * everything installed. They are not reorderable — hidden is a set, and has no order to edit.
 */
@Composable
fun HiddenAppsScreen(
    allApps: List<AppInfo>,
    hiddenApps: Set<String>,
    nameOverrides: Map<String, String>,
    iconSizeDp: Int,
    onToggleHidden: (AppInfo, Boolean) -> Unit,
    onBack: () -> Unit,
    /**
     * Wording, so the one screen can serve the two lists that have this exact shape.
     *
     * Keeping apps out of the computed favorites is the same job as hiding them — a set of
     * apps, a switch each, the chosen ones first — and the only thing that differs is what the
     * list is called and what being on it means.
     */
    @StringRes titleRes: Int = R.string.settings_hidden_apps,
    @StringRes countRes: Int = R.string.hidden_count,
    @StringRes addRes: Int = R.string.hidden_add_apps,
) {
    val surface = MaterialTheme.colorScheme.surface
    val name = { app: AppInfo -> nameOverrides[app.key] ?: app.label }
    val hidden = remember(allApps, hiddenApps) { allApps.filter { it.key in hiddenApps } }
    val rest = remember(allApps, hiddenApps) { allApps.filterNot { it.key in hiddenApps } }

    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.padding(padding)) {
            item {
                ListSectionLabel(stringResource(countRes, hidden.size))
            }

            items(hidden, key = { "hidden:" + it.key }) { app ->
                HiddenAppRow(app, name(app), iconSizeDp, checked = true, onToggleHidden)
            }

            item {
                ListSectionLabel(stringResource(addRes))
            }

            items(rest, key = { it.key }) { app ->
                HiddenAppRow(app, name(app), iconSizeDp, checked = false, onToggleHidden)
            }
        }
    }
}

@Composable
private fun HiddenAppRow(
    app: AppInfo,
    label: String,
    iconSizeDp: Int,
    checked: Boolean,
    onToggleHidden: (AppInfo, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleHidden(app, !checked) }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app = app, sizeDp = minOf(iconSizeDp, 44))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggleHidden(app, it) })
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
}
