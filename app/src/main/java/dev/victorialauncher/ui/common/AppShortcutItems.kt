// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

import android.content.pm.ShortcutInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.data.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Most an app is allowed to contribute, so a menu stays a menu rather than a list. */
private const val MAX_SHORTCUTS = 5

/**
 * The shortcuts an app publishes, as items at the top of its long-press menu.
 *
 * Read when the menu opens rather than with the app list: it is a call into the system per
 * app, and an app list is hundreds of apps of which one is ever asked about.
 *
 * Emits nothing at all when an app publishes none, so the menu is unchanged for most of them.
 */
@Composable
fun AppShortcutItems(app: AppInfo, expanded: Boolean, onStarted: () -> Unit) {
    val context = LocalContext.current
    val victoriaApp = context.applicationContext as VictoriaApp
    val density = LocalConfiguration.current.densityDpi
    var shortcuts by remember(app.key) { mutableStateOf<List<ShortcutInfo>>(emptyList()) }

    LaunchedEffect(app.key, expanded) {
        if (!expanded) return@LaunchedEffect
        shortcuts = withContext(Dispatchers.IO) {
            victoriaApp.appRepository.appShortcuts(app).take(MAX_SHORTCUTS)
        }
    }

    if (shortcuts.isEmpty()) return

    shortcuts.forEach { shortcut ->
        val label = (shortcut.shortLabel ?: shortcut.longLabel)?.toString().orEmpty()
        if (label.isBlank()) return@forEach
        val icon = remember(shortcut.id, density) {
            runCatching {
                victoriaApp.appRepository.shortcutIcon(shortcut, density)
                    ?.toBitmap(ICON_PX, ICON_PX)
                    ?.asImageBitmap()
            }.getOrNull()
        }
        DropdownMenuItem(
            text = { Text(label) },
            leadingIcon = icon?.let {
                { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(24.dp)) }
            },
            onClick = {
                victoriaApp.appRepository.startAppShortcut(shortcut)
                onStarted()
            },
        )
    }
}

/** Rasterised at menu-icon size; the drawable itself is whatever density the publisher had. */
private const val ICON_PX = 72

/**
 * Swipe a row from left to right to reach what the app publishes.
 *
 * A separate gesture from the long press on purpose: shortcuts are used daily and the menu
 * behind a long press is set-up-and-done, so they do not belong in the same place.
 *
 * Nothing happens on a row whose app publishes none. There is no way to know that before
 * asking, and asking every row up front is a call into the system per app.
 */
fun Modifier.shortcutSwipe(enabled: Boolean, onSwipe: (Offset) -> Unit): Modifier =
    if (!enabled) this else this.pointerInput(Unit) {
        var travelled = 0f
        var start = Offset.Zero
        var fired = false
        detectHorizontalDragGestures(
            onDragStart = { start = it; travelled = 0f; fired = false },
            onDragEnd = { },
            onDragCancel = { },
        ) { change, amount ->
            travelled += amount
            // Only rightward, and only once: leftward is left alone so it can mean something
            // else later, and a long drag should open one menu rather than a menu per pixel.
            if (!fired && travelled > SWIPE_THRESHOLD_PX) {
                fired = true
                change.consume()
                onSwipe(start)
            }
        }
    }

/** Far enough not to fire on a tap that wandered, short enough to feel like a flick. */
private const val SWIPE_THRESHOLD_PX = 90f

/**
 * The shortcuts on their own, for the swipe. Renders nothing when the app publishes none, so
 * a swipe on such a row opens an empty popup rather than a stray one.
 */
@Composable
fun AppShortcutMenu(app: AppInfo, expanded: Boolean, offset: DpOffset, onDismiss: () -> Unit) {
    TouchAnchoredMenu(expanded = expanded, offset = offset, onDismissRequest = onDismiss) {
        AppShortcutItems(app, expanded) { onDismiss() }
    }
}
