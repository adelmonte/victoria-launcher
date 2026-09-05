// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.R
import androidx.compose.ui.res.stringResource

data class WidgetSlotActions(
    val onAddWidget: () -> Unit,
    val onRemoveWidget: () -> Unit,
    val onWidgetSettings: () -> Unit,
    val onAppInfo: () -> Unit,
    val onResize: (Int) -> Unit,
    val onOpenSettings: () -> Unit,
)

@Composable
fun WidgetSlot(
    widgetId: Int,
    heightDp: Int,
    hapticsEnabled: Boolean,
    onEditLayout: () -> Unit,
    actions: WidgetSlotActions,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as VictoriaApp
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val providerInfo: AppWidgetProviderInfo? = remember(widgetId) {
        if (widgetId > 0) appWidgetManager.getAppWidgetInfo(widgetId) else null
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    val density = LocalDensity.current
    var slotSizeDp by remember { mutableStateOf(0 to 0) }
    var reportedSizeDp by remember(widgetId) { mutableStateOf(0 to 0) }

    Box(modifier = modifier.height(heightDp.dp)) {
        if (widgetId > 0 && providerInfo != null) {
            // A real long-press (finger held still) opens the edit menu; an ordinary tap or
            // drag still reaches the widget's own view untouched — see LongPressFrameLayout.
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        slotSizeDp = with(density) { size.width.toDp().value.toInt() to size.height.toDp().value.toInt() }
                    },
                factory = { ctx ->
                    val hostView = app.widgetHost.createView(ctx, widgetId, providerInfo).apply {
                        setAppWidget(widgetId, providerInfo)
                    }
                    LongPressFrameLayout(ctx).apply {
                        addView(hostView)
                        onLongPress = { x, y ->
                            menuOffset = with(density) { DpOffset(x.toDp(), y.toDp()) }
                            menuExpanded = true
                        }
                    }
                },
                update = { container ->
                    val hostView = container.getChildAt(0) as? AppWidgetHostView
                    hostView?.setAppWidget(widgetId, providerInfo)
                    // Widgets lay themselves out for the size they were *told*, not the size of
                    // the view; without this they render for some other size and get clipped.
                    //
                    // Pass the widget's existing options rather than an empty Bundle — a blank
                    // one replaces them outright, dropping the size hints (and on Android 12+
                    // the size list) that responsive widgets pick their layout from, which is
                    // how a widget ends up drawing half its text. Only report real changes,
                    // since this ran on every recomposition.
                    val (wDp, hDp) = slotSizeDp
                    if (hostView != null && wDp > 0 && hDp > 0 && slotSizeDp != reportedSizeDp) {
                        reportedSizeDp = slotSizeDp
                        val options = runCatching { appWidgetManager.getAppWidgetOptions(widgetId) }
                            .getOrNull() ?: Bundle()
                        hostView.updateAppWidgetSize(options, wDp, hDp, wDp, hDp)
                    }
                    container.onLongPress = { x, y ->
                        menuOffset = with(density) { DpOffset(x.toDp(), y.toDp()) }
                        menuExpanded = true
                    }
                },
            )
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { actions.onAddWidget() },
                            onLongPress = { offset ->
                                menuOffset = with(density) { DpOffset(offset.x.toDp(), offset.y.toDp()) }
                                menuExpanded = true
                            },
                        )
                    },
                color = Color.Black.copy(alpha = 0.25f),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.widget_add), tint = Color.White)
                    Text(stringResource(R.string.widget_add), color = Color.White.copy(alpha = 0.8f))
                }
            }
        }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }, offset = menuOffset) {
            if (widgetId > 0) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_app_info)) },
                    leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    onClick = { menuExpanded = false; actions.onAppInfo() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit_layout)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = { menuExpanded = false; onEditLayout() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.widget_change_settings)) },
                    leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                    onClick = { menuExpanded = false; actions.onWidgetSettings() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.widget_add_custom)) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { menuExpanded = false; actions.onAddWidget() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_remove)) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = { menuExpanded = false; actions.onRemoveWidget() },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.widget_add_custom)) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { menuExpanded = false; actions.onAddWidget() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit_layout)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = { menuExpanded = false; onEditLayout() },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_open_settings)) },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = { menuExpanded = false; actions.onOpenSettings() },
            )
        }

    }
}