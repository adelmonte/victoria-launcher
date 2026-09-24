// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.ui.common.TouchAnchoredMenu
import kotlinx.coroutines.delay
import dev.victorialauncher.R
import androidx.compose.ui.res.stringResource

/** How long the page dots stay up after a swipe settles. */
private const val DOTS_LINGER_MS = 1200L

data class WidgetSlotActions(
    val onAddWidget: () -> Unit,
    val onRemoveWidget: (widgetId: Int) -> Unit,
    val onWidgetSettings: (widgetId: Int) -> Unit,
    val onAppInfo: (widgetId: Int) -> Unit,
    val onResize: (heightDp: Int) -> Unit,
    val onOpenSettings: () -> Unit,
)

/**
 * The home screen's widget area: one widget on screen at a time, swiped between.
 *
 * Every page shares [heightDp] rather than carrying its own, so swiping never moves the
 * favorites underneath — a widget that changed the layout of the rest of the screen as it
 * scrolled past would be far more distracting than the widget itself.
 */
@Composable
fun WidgetSlot(
    widgetIds: List<Int>,
    heightDp: Int,
    onEditLayout: () -> Unit,
    actions: WidgetSlotActions,
    /** Bumped when HOME is pressed on a home screen already showing, so the stack goes back. */
    homeIntentTick: Int,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var menuForId by remember { mutableStateOf(-1) }
    val density = LocalDensity.current

    fun openMenu(widgetId: Int, x: Float, y: Float) {
        menuForId = widgetId
        menuOffset = with(density) { DpOffset(x.toDp(), y.toDp()) }
        menuExpanded = true
    }

    Box(modifier = modifier.height(heightDp.dp)) {
        if (widgetIds.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { actions.onAddWidget() },
                            onLongPress = { offset -> openMenu(-1, offset.x, offset.y) },
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
        } else {
            val pagerState = rememberPagerState(pageCount = { widgetIds.size })
            // HOME means the home screen as you left it set up, not as you last flicked
            // through it. Animated rather than snapped: the screen is in front of you when it
            // happens, and a stack that jumps has nothing to say about where it went.
            LaunchedEffect(homeIntentTick) {
                if (homeIntentTick > 0 && pagerState.currentPage != 0) {
                    pagerState.animateScrollToPage(0)
                }
            }
            HorizontalPager(
                state = pagerState,
                // Keyed by widget id so adding or removing one doesn't rebuild every host
                // view; neighbors stay alive so a swipe doesn't land on a blank page.
                key = { widgetIds[it] },
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val id = widgetIds[page]
                WidgetPage(widgetId = id, onPressAndHold = { x, y -> openMenu(id, x, y) })
            }

            if (widgetIds.size > 1) {
                // Only worth showing while there is something to say; they fade out rather
                // than sitting permanently on top of somebody's clock.
                var dotsVisible by remember { mutableStateOf(false) }
                LaunchedEffect(pagerState.isScrollInProgress) {
                    if (pagerState.isScrollInProgress) {
                        dotsVisible = true
                    } else {
                        delay(DOTS_LINGER_MS)
                        dotsVisible = false
                    }
                }
                val dotsAlpha by animateFloatAsState(
                    targetValue = if (dotsVisible) 1f else 0f,
                    label = "widgetDots",
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .graphicsLayer { alpha = dotsAlpha },
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(widgetIds.size) { index ->
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = Color.White.copy(alpha = if (index == pagerState.currentPage) 0.9f else 0.35f),
                                    shape = CircleShape,
                                ),
                        )
                    }
                }
            }
        }

        TouchAnchoredMenu(expanded = menuExpanded, offset = menuOffset, onDismissRequest = { menuExpanded = false }) {
            val id = menuForId
            if (id > 0) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_app_info)) },
                    leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    onClick = { menuExpanded = false; actions.onAppInfo(id) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit_layout)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = { menuExpanded = false; onEditLayout() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.widget_change_settings)) },
                    leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                    onClick = { menuExpanded = false; actions.onWidgetSettings(id) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.widget_add_another)) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { menuExpanded = false; actions.onAddWidget() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.widget_remove_this)) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = { menuExpanded = false; actions.onRemoveWidget(id) },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.widget_add)) },
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

/** One hosted widget. [onLongPress] carries the press position in this page's local pixels. */
@Composable
private fun WidgetPage(widgetId: Int, onPressAndHold: (x: Float, y: Float) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VictoriaApp
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val providerInfo: AppWidgetProviderInfo? = remember(widgetId) {
        if (widgetId > 0) appWidgetManager.getAppWidgetInfo(widgetId) else null
    }
    val density = LocalDensity.current
    var slotSizeDp by remember { mutableStateOf(0 to 0) }
    var reportedSizeDp by remember(widgetId) { mutableStateOf(0 to 0) }

    if (providerInfo == null) {
        // The provider is gone — its app was uninstalled or disabled. Draw something that
        // still takes a long press, or this page is a blank gap with no way to remove it.
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .pointerInput(widgetId) {
                    detectTapGestures(onLongPress = { offset -> onPressAndHold(offset.x, offset.y) })
                },
            color = Color.Black.copy(alpha = 0.25f),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.widget_unavailable), color = Color.White.copy(alpha = 0.8f))
            }
        }
        return
    }

    // A real long-press (finger held still) opens the edit menu; an ordinary tap or drag
    // still reaches the widget's own view untouched — see LongPressFrameLayout.
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                slotSizeDp = with(density) { size.width.toDp().value.toInt() to size.height.toDp().value.toInt() }
            },
        factory = { ctx ->
            // A widget draws with code from the app that provides it, and one that throws on
            // the way up would otherwise take the launcher down with it — leaving a home
            // screen that crashes on sight and no obvious way back. An empty slot is a poor
            // widget but it is still a home screen.
            val hostView = runCatching {
                app.widgetHost.createView(ctx, widgetId, providerInfo).apply {
                    setAppWidget(widgetId, providerInfo)
                }
            }.getOrNull()
            LongPressFrameLayout(ctx).apply {
                hostView?.let { addView(it) }
                onLongPress = { x, y -> onPressAndHold(x, y) }
            }
        },
        update = { container ->
            val hostView = container.getChildAt(0) as? AppWidgetHostView
            runCatching { hostView?.setAppWidget(widgetId, providerInfo) }
            // Widgets lay themselves out for the size they were *told*, not the size of the
            // view; without this they render for some other size and get clipped.
            //
            // Pass the widget's existing options rather than an empty Bundle — a blank one
            // replaces them outright, dropping the size hints (and on Android 12+ the size
            // list) that responsive widgets pick their layout from, which is how a widget
            // ends up drawing half its text. Only report real changes, since this ran on
            // every recomposition.
            val (wDp, hDp) = slotSizeDp
            if (hostView != null && wDp > 0 && hDp > 0 && slotSizeDp != reportedSizeDp) {
                reportedSizeDp = slotSizeDp
                val options = runCatching { appWidgetManager.getAppWidgetOptions(widgetId) }
                    .getOrNull() ?: Bundle()
                hostView.updateAppWidgetSize(options, wDp, hDp, wDp, hDp)
            }
            container.onLongPress = { x, y -> onPressAndHold(x, y) }
        },
    )
}
