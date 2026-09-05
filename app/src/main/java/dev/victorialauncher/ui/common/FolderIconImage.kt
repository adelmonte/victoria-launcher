// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.victorialauncher.VictoriaApp

/**
 * Renders a folder's custom icon, using the same override encoding apps use so a folder can
 * take an icon-pack drawable or a picture from the gallery just like any app.
 *
 * Returns false when the override can't be decoded, so the caller can fall back to drawing
 * the folder's app previews instead.
 */
@Composable
fun FolderIconImage(override: String, sizeDp: Int, modifier: Modifier = Modifier): Boolean {
    val context = LocalContext.current
    val victoriaApp = context.applicationContext as VictoriaApp
    val px = with(LocalDensity.current) { sizeDp.dp.roundToPx() }.coerceAtLeast(1)

    val bitmap: ImageBitmap? = remember(override, px) {
        runCatching {
            decodeIconOverride(context, victoriaApp, override)?.toBitmap(px, px)?.asImageBitmap()
        }.getOrNull()
    }

    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(sizeDp.dp))
        return true
    }
    return false
}