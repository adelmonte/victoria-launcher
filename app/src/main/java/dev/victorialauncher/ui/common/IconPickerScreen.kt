// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.R
import androidx.compose.ui.res.stringResource

/** Most a single pack contributes to a search, so one huge pack cannot bury the others. */
private const val PER_PACK_MATCH_LIMIT = 60

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPickerScreen(
    appLabel: String,
    onPickPackIcon: (packPackage: String, drawableName: String) -> Unit,
    onPickFromGallery: () -> Unit,
    onResetIcon: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as VictoriaApp
    val packs = remember { app.iconPackRepository.getInstalledIconPacks() }
    // Null means All: browse or search every pack at once. Picking a pack narrows both.
    var selectedPack by remember { mutableStateOf<String?>(null) }
    // Seeded with the app's own name, because that is what you came here to find. It also
    // means the screen opens on a handful of matches rather than on every drawable a pack
    // ships, which is thousands and took a visible moment to lay out.
    var query by remember { mutableStateOf(appLabel) }
    val surface = MaterialTheme.colorScheme.surface

    // Whichever packs the chips have narrowed us to — all of them, or the one picked.
    val searched = remember(packs, selectedPack) {
        selectedPack?.let { chosen -> packs.filter { it.packageName == chosen } } ?: packs
    }

    val matches: List<Pair<String, String>> = remember(searched, query) {
        val term = query.trim()
        searched.flatMap { pack ->
            val names = app.iconPackRepository.getPackIcons(pack.packageName)
            val hits = if (term.isBlank()) names else names.filter { it.contains(term, ignoreCase = true) }
            // Capped per pack rather than overall, so one enormous pack cannot fill the grid
            // and leave the others with nothing to show for themselves.
            hits.take(PER_PACK_MATCH_LIMIT).map { pack.packageName to it }
        }
    }
    val anyIcons = remember(searched) {
        searched.any { app.iconPackRepository.getPackIcons(it.packageName).isNotEmpty() }
    }

    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
                windowInsets = topBarInsets,
                title = { Text(stringResource(R.string.icon_picker_title, appLabel)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip(stringResource(R.string.icon_picker_gallery), selected = false, onClick = onPickFromGallery)
                Chip(stringResource(R.string.icon_picker_reset), selected = false, onClick = onResetIcon)
            }

            if (packs.isEmpty()) {
                Text(
                    stringResource(R.string.icon_picker_no_packs),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
                return@Column
            }

            // Wraps: All plus a few packs is more chips than a line holds, and a Row answers
            // that by breaking the last label down the screen a letter at a time.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip(stringResource(R.string.icon_picker_all), selected = selectedPack == null) {
                    selectedPack = null
                }
                packs.forEach { pack ->
                    Chip(pack.label, selected = selectedPack == pack.packageName) {
                        selectedPack = pack.packageName
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(stringResource(R.string.icon_picker_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.icon_picker_clear_search))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (matches.isEmpty()) {
                Text(
                    if (!anyIcons) stringResource(R.string.icon_picker_not_browsable)
                    else stringResource(R.string.icon_picker_no_matches, query),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 64.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(matches, key = { it.first + "/" + it.second }) { (pack, drawableName) ->
                        PackIconCell(pack, drawableName) { onPickPackIcon(pack, drawableName) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackIconCell(packPackage: String, drawableName: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VictoriaApp
    val drawable: Drawable? = remember(packPackage, drawableName) {
        app.iconPackRepository.loadPackDrawable(packPackage, drawableName)
    }
    if (drawable == null) return

    AndroidView(
        modifier = Modifier
            .size(56.dp)
            .clickable(onClick = onClick),
        factory = { ctx -> ImageView(ctx) },
        update = { it.setImageDrawable(drawable) },
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(shape = RoundedCornerShape(50), color = bg, modifier = Modifier.clickable(onClick = onClick)) {
        Text(
            label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}