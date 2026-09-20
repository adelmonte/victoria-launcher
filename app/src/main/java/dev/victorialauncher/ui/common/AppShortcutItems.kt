// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

import android.content.pm.ShortcutInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.ShortcutSwipe
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
fun AppShortcutItems(
    app: AppInfo,
    expanded: Boolean,
    labelSizeSp: Int,
    onStarted: () -> Unit,
) {
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
        val iconDp = iconSizeFor(labelSizeSp)
        val icon = remember(shortcut.id, density) {
            runCatching {
                victoriaApp.appRepository.shortcutIcon(shortcut, density)
                    ?.toBitmap(ICON_PX, ICON_PX)
                    ?.asImageBitmap()
            }.getOrNull()
        }
        DropdownMenuItem(
            text = { Text(label, fontSize = labelSizeSp.sp) },
            leadingIcon = icon?.let {
                { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(iconDp)) }
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
 * The icon beside a shortcut, sized from the label it sits next to.
 *
 * Tied to the launcher's own label size rather than given a setting of its own: this is the
 * same list of app names as everywhere else, and a panel that ignored the size set for those
 * was the thing worth fixing. Bounded so it stays an icon in a menu at either extreme.
 */
private fun iconSizeFor(labelSizeSp: Int): Dp =
    (labelSizeSp * 1.5f).dp.coerceIn(20.dp, 44.dp)

/**
 * Swipe a row sideways to reach what the app publishes.
 *
 * A separate gesture from the long press on purpose: shortcuts are used daily and the menu
 * behind a long press is set-up-and-done, so they do not belong in the same place.
 *
 * One direction, chosen in settings, so the other stays free to mean something later. It fires
 * once per drag rather than per pixel, and only past a distance no tap wanders.
 *
 * Nothing happens on a row whose app publishes none. There is no way to know that before
 * asking, and asking every row up front is a call into the system per app.
 */
fun Modifier.swipeForShortcuts(
    mode: ShortcutSwipe,
    enabled: Boolean,
    onSwipe: () -> Unit,
): Modifier =
    if (mode == ShortcutSwipe.OFF || !enabled) this else this.pointerInput(mode) {
        var travelled = 0f
        var fired = false
        detectHorizontalDragGestures(
            onDragStart = { travelled = 0f; fired = false },
            onDragEnd = { },
            onDragCancel = { },
        ) { change, amount ->
            travelled += amount
            val far = when (mode) {
                ShortcutSwipe.RIGHT -> travelled > SWIPE_THRESHOLD_PX
                ShortcutSwipe.LEFT -> travelled < -SWIPE_THRESHOLD_PX
                ShortcutSwipe.OFF -> false
            }
            if (!fired && far) {
                fired = true
                change.consume()
                onSwipe()
            }
        }
    }

/** Far enough not to fire on a tap that wandered, short enough to feel like a flick. */
private const val SWIPE_THRESHOLD_PX = 90f

/**
 * The shortcuts on their own, for the swipe. Renders nothing when the app publishes none, so
 * a swipe on such a row opens an empty popup rather than a stray one.
 *
 * Anchored to the row and not to the finger, unlike the long-press menu. That one is opened
 * from somewhere on a row that may be as tall as a widget, so it has to find the finger; this
 * one is always a swipe across a row one line high, and following the finger only meant it
 * landed somewhere slightly different every time. Against the row it is in the same place at
 * the same size on every use, which is what makes it something you reach for rather than read.
 */
@Composable
fun AppShortcutMenu(app: AppInfo, expanded: Boolean, labelSizeSp: Int, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        AppShortcutItems(app, expanded, labelSizeSp) { onDismiss() }
    }
}
