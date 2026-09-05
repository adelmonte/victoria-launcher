// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
    var selectedPack by remember { mutableStateOf(packs.firstOrNull()?.packageName) }
    var query by remember { mutableStateOf("") }
    val surface = MaterialTheme.colorScheme.surface

    val allIcons = remember(selectedPack) {
        selectedPack?.let { app.iconPackRepository.getPackIcons(it) } ?: emptyList()
    }
    val icons = remember(allIcons, query) {
        if (query.isBlank()) allIcons
        else allIcons.filter { it.contains(query.trim(), ignoreCase = true) }
    }

    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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

            val pack = selectedPack
            if (pack == null || icons.isEmpty()) {
                Text(
                    if (allIcons.isEmpty()) stringResource(R.string.icon_picker_not_browsable)
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
                    items(icons) { drawableName ->
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