// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.theme

import android.app.WallpaperManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.victorialauncher.data.TextColorMode

private val LightText = Color(0xFFFFFFFF)
private val DarkText = Color(0xFF10161C)

/**
 * Text colour for anything drawn over the wallpaper. AUTO asks the system for the
 * wallpaper's own colours and picks whichever reads against it, re-checking when the
 * wallpaper changes.
 */
@Composable
fun rememberContentColor(mode: TextColorMode): Color {
    val context = LocalContext.current
    var wallpaperIsLight by remember { mutableStateOf(false) }

    DisposableEffect(mode) {
        if (mode != TextColorMode.AUTO || Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            return@DisposableEffect onDispose { }
        }
        val manager = WallpaperManager.getInstance(context)

        fun refresh() {
            val colors = runCatching {
                manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            }.getOrNull()
            val primary = colors?.primaryColor
            wallpaperIsLight = primary != null && primary.luminance() > 0.5f
        }
        refresh()

        val listener = WallpaperManager.OnColorsChangedListener { _, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) refresh()
        }
        runCatching { manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper())) }
        onDispose { runCatching { manager.removeOnColorsChangedListener(listener) } }
    }

    return when (mode) {
        TextColorMode.LIGHT -> LightText
        TextColorMode.DARK -> DarkText
        TextColorMode.AUTO -> if (wallpaperIsLight) DarkText else LightText
    }
}