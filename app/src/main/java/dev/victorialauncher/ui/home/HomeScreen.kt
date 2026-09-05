// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.Folder
import dev.victorialauncher.data.HomePaddings
import dev.victorialauncher.data.folderToken
import dev.victorialauncher.data.PaddingSlot
import dev.victorialauncher.media.NowPlayingWidget
import dev.victorialauncher.ui.common.AppIcon
import dev.victorialauncher.ui.common.EditAppDialog
import dev.victorialauncher.ui.common.FolderIconImage
import dev.victorialauncher.ui.common.recordTouchPosition
import dev.victorialauncher.widget.WidgetSlot
import dev.victorialauncher.widget.WidgetSlotActions
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.annotation.StringRes
import dev.victorialauncher.R
import androidx.compose.ui.res.stringResource

private sealed interface HomeItem {
    data object Widget : HomeItem
    data class Favorite(val app: AppInfo) : HomeItem
    data class FolderItem(val folder: Folder) : HomeItem
}

/** A favorites row is either an app or a folder; both reorder through the same list. */
sealed interface FavoriteEntry {
    val token: String

    data class App(val app: AppInfo) : FavoriteEntry {
        override val token: String get() = app.key
    }

    data class FolderRef(val folder: Folder) : FavoriteEntry {
        override val token: String get() = folderToken(folder.id)
    }
}

private fun buildHomeItems(
    favorites: List<FavoriteEntry>,
    widgetPosition: Int,
    hasWidget: Boolean,
): List<HomeItem> {
    val pos = widgetPosition.coerceIn(0, favorites.size)
    val list = mutableListOf<HomeItem>()
    favorites.forEachIndexed { i, entry ->
        if (hasWidget && i == pos) list += HomeItem.Widget
        list += when (entry) {
            is FavoriteEntry.App -> HomeItem.Favorite(entry.app)
            is FavoriteEntry.FolderRef -> HomeItem.FolderItem(entry.folder)
        }
    }
    if (hasWidget && pos >= favorites.size) list += HomeItem.Widget
    return list
}

@Composable
fun HomeScreen(
    favorites: List<FavoriteEntry>,
    nameOverrides: Map<String, String>,
    iconSizeDp: Int,
    labelSizeSp: Int,
    itemSpacingDp: Int,
    sidePaddingDp: Int,
    onSetSidePadding: (Int) -> Unit,
    paddings: HomePaddings,
    widgetId: Int,
    widgetPosition: Int,
    widgetHeightDp: Int,
    hapticsEnabled: Boolean,
    nowPlayingEnabled: Boolean,
    nowPlayingHeightDp: Int,
    onResizeNowPlaying: (Int) -> Unit,
    widgetActions: WidgetSlotActions,
    onLaunch: (AppInfo) -> Unit,
    onRemoveFavorite: (AppInfo) -> Unit,
    onOpenFolderApp: (AppInfo) -> Unit,
    onRenameFolder: (Folder, String) -> Unit,
    onChangeFolderIcon: (Folder) -> Unit,
    onResetFolderIcon: (Folder) -> Unit,
    onDeleteFolder: (Folder) -> Unit,
    onManageFolder: (Folder) -> Unit,
    onMoveToFolder: (AppInfo) -> Unit,
    onRemoveFromFolder: (Folder, AppInfo) -> Unit,
    appsByKey: Map<String, AppInfo>,
    onReorderHome: (newFavoriteKeys: List<String>, newWidgetPosition: Int) -> Unit,
    onCommitPadding: (PaddingSlot, Int) -> Unit,
    onFavoritesBoundsChanged: (topPx: Float, bottomPx: Float) -> Unit,
    nowPlayingHasContent: Boolean,
    contentColor: Color,
    showFavoriteLabels: Boolean,
    alignRight: Boolean,
    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onPeekStatusBar: () -> Unit,
    onExpandShade: () -> Unit,
    onManageFavorites: () -> Unit,
    onSetName: (AppInfo, String?) -> Unit,
    onChangeIcon: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
) {    fun displayName(app: AppInfo) = nameOverrides[app.key] ?: app.label

    var menuForKey by remember { mutableStateOf<String?>(null) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var renameDialogFor by remember { mutableStateOf<AppInfo?>(null) }
    var folderMenuFor by remember { mutableStateOf<String?>(null) }
    var folderRenameFor by remember { mutableStateOf<Folder?>(null) }
    var nowPlayingMenu by remember { mutableStateOf(false) }
    var nowPlayingMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var expandedFolders by remember { mutableStateOf(setOf<String>()) }
    val touchPosition = remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // While a padding handle is being dragged we track it locally so layout follows the
    // finger, and only write the final value to storage on release.
    var liveSlot by remember { mutableStateOf<PaddingSlot?>(null) }
    var liveValue by remember { mutableIntStateOf(0) }
    fun padOf(slot: PaddingSlot): Int = if (liveSlot == slot) liveValue else paddings[slot]

    // Show the widget slot when a widget exists, and also in edit mode when there isn't one —
    // that placeholder is the only way back to the picker once a widget has been removed.
    val hasWidget = widgetId > 0
    val showWidgetSlot = hasWidget || editMode

    // Reordering runs against a local copy and is committed once on release; going through
    // storage on every swap would lag behind the finger.
    var dragOrder by remember { mutableStateOf<List<HomeItem>?>(null) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<Int, Int>() }

    val homeItems = remember(favorites, widgetPosition, showWidgetSlot) {
        buildHomeItems(favorites, widgetPosition, showWidgetSlot)
    }
    val displayItems = dragOrder ?: homeItems

    // Folders sit alongside apps in the favorites block, so both bound its padding.
    val firstRowIndex = displayItems.indexOfFirst { it !is HomeItem.Widget }
    val lastRowIndex = displayItems.indexOfLast { it !is HomeItem.Widget }

    fun tokensOf(items: List<HomeItem>) = items.mapNotNull {
        when (it) {
            is HomeItem.Favorite -> it.app.key
            is HomeItem.FolderItem -> folderToken(it.folder.id)
            HomeItem.Widget -> null
        }
    }

    fun commitDragOrder() {
        val order = dragOrder
        if (order != null) {
            val widgetPos = order.indexOfFirst { it is HomeItem.Widget }
                .let { if (it < 0) widgetPosition else it }
            onReorderHome(tokensOf(order), widgetPos)
        }
        dragOrder = null
        draggingIndex = null
        dragOffset = 0f
    }

    /** Swap with the neighbour once the held row has travelled past its midpoint. */
    fun onDragBy(amount: Float) {
        val order = dragOrder ?: return
        val from = draggingIndex ?: return
        dragOffset += amount

        val below = itemHeights[from + 1]
        if (below != null && from + 1 <= order.lastIndex && dragOffset > below / 2f) {
            dragOrder = order.toMutableList().apply { add(from + 1, removeAt(from)) }
            draggingIndex = from + 1
            dragOffset -= below
            return
        }
        val above = itemHeights[from - 1]
        if (above != null && from - 1 >= 0 && dragOffset < -above / 2f) {
            dragOrder = order.toMutableList().apply { add(from - 1, removeAt(from)) }
            draggingIndex = from - 1
            dragOffset += above
        }
    }

    // Measured span of the favorites list, handed up so the A-Z strip can match it.
    var favBoundsTop by remember { mutableFloatStateOf(0f) }
    var favBoundsBottom by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(favBoundsTop, favBoundsBottom) {
        if (favBoundsBottom > favBoundsTop) onFavoritesBoundsChanged(favBoundsTop, favBoundsBottom)
    }

    val peekPullPx = with(density) { 30.dp.toPx() }
    val deepPullPx = with(density) { 200.dp.toPx() }
    var rawPullDown by remember { mutableFloatStateOf(0f) }
    var pullActionFired by remember { mutableStateOf(false) }
    var peekFired by remember { mutableStateOf(false) }

    // Free vertical drag with spring bounce at both ends, outside edit mode.
    val offsetY = remember { Animatable(0f) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    var contentHeight by remember { mutableIntStateOf(0) }
    val minOffset = remember(viewportHeight, contentHeight) {
        minOf(0f, (viewportHeight - contentHeight).toFloat())
    }
    val editScrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportHeight = it.height }
            .draggable(
                orientation = Orientation.Vertical,
                enabled = !editMode && liveSlot == null,
                onDragStarted = { rawPullDown = 0f; pullActionFired = false; peekFired = false },
                state = rememberDraggableState { delta ->
                    // Undamped downward travel once already at the top drives the status-bar
                    // gestures; the damped offset would need more than a screen of dragging.
                    if (delta > 0f && offsetY.value >= -2f) {
                        rawPullDown += delta
                        if (!pullActionFired && rawPullDown > deepPullPx) {
                            pullActionFired = true
                            onExpandShade()
                        } else if (!peekFired && rawPullDown > peekPullPx) {
                            peekFired = true
                            onPeekStatusBar()
                        }
                    }
                    scope.launch {
                        val next = offsetY.value + delta
                        val outOfBounds = next > 0f || next < minOffset
                        val applied = if (outOfBounds) offsetY.value + delta * 0.4f else next
                        offsetY.snapTo(applied)
                    }
                },
                onDragStopped = { velocity ->
                    if (!pullActionFired && rawPullDown > peekPullPx && velocity > 2200f) {
                        onExpandShade()
                    }
                    rawPullDown = 0f
                    pullActionFired = false
                    peekFired = false

                    val decay = exponentialDecay<Float>(frictionMultiplier = 1.6f)
                    val target = decay.calculateTargetValue(offsetY.value, velocity)
                        .coerceIn(minOffset, 0f)
                    offsetY.animateTo(
                        targetValue = target,
                        initialVelocity = velocity,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                },
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    // Edit mode roughly doubles the stack's height and its handles are
                    // themselves drag targets, so it needs ordinary scrolling.
                    if (editMode) {
                        Modifier.verticalScroll(editScrollState)
                    } else {
                        // Unbounded: the stack is dragged rather than scrolled here, so it
                        // has to be allowed to measure taller than the screen. Without this
                        // anything past the bottom edge is squashed into the leftover space.
                        Modifier
                            .wrapContentHeight(align = Alignment.Top, unbounded = true)
                            .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    }
                )
                .onSizeChanged { contentHeight = it.height },
        ) {
            if (editMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 72.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.home_reorder_hint),
                        color = contentColor.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onEditModeChange(false) }) {
                        Icon(Icons.Filled.Done, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_done))
                    }
                }
                SidePaddingHandle(sidePaddingDp = sidePaddingDp, onSetSidePadding = onSetSidePadding)
                Spacer(Modifier.height(6.dp))
            }

            // With no widget on screen there is nothing above the favorites, so Now Playing
            // takes that slot instead.
            if (!hasWidget && nowPlayingHasContent) {
                NowPlayingBlock(
                    editMode = editMode,
                    heightDp = nowPlayingHeightDp,
                    sidePaddingDp = sidePaddingDp,
                    padTop = padOf(PaddingSlot.NOW_PLAYING_TOP),
                    padBottom = padOf(PaddingSlot.NOW_PLAYING_BOTTOM),
                    touchPosition = touchPosition,
                    menuExpanded = nowPlayingMenu,
                    menuOffset = nowPlayingMenuOffset,
                    onOpenMenu = { offset -> nowPlayingMenuOffset = offset; nowPlayingMenu = true },
                    onDismissMenu = { nowPlayingMenu = false },
                    onEditLayout = { nowPlayingMenu = false; onEditModeChange(true) },
                    onOpenSettings = { nowPlayingMenu = false; onOpenSettings() },
                    onResize = onResizeNowPlaying,
                    onDragPadding = { slot, v -> liveSlot = slot; liveValue = v },
                    currentPadding = { slot -> padOf(slot) },
                    onCommitPadding = { slot, v -> onCommitPadding(slot, v); liveSlot = null },
                )
            }

            if (displayItems.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.home_no_favorites_title), color = contentColor, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.home_no_favorites_body),
                        color = contentColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onManageFavorites) { Text(stringResource(R.string.home_choose_favorites)) }
                }
            }

            displayItems.forEachIndexed { index, item ->
                // Spacing handles live outside the draggable wrapper: inside it they would
                // travel with a dragged row and skew the height the swap threshold uses.
                if (item is HomeItem.Widget) {
                    PaddingHandle(
                        editMode = editMode,
                        label = R.string.handle_widget_top,
                        value = padOf(PaddingSlot.WIDGET_TOP),
                        onDrag = { d -> liveSlot = PaddingSlot.WIDGET_TOP; liveValue = d },
                        current = { padOf(PaddingSlot.WIDGET_TOP) },
                        onCommit = { onCommitPadding(PaddingSlot.WIDGET_TOP, it); liveSlot = null },
                    )
                } else if (index == firstRowIndex) {
                    PaddingHandle(
                        editMode = editMode,
                        label = R.string.handle_favorites_top,
                        value = padOf(PaddingSlot.FAVORITES_TOP),
                        onDrag = { d -> liveSlot = PaddingSlot.FAVORITES_TOP; liveValue = d },
                        current = { padOf(PaddingSlot.FAVORITES_TOP) },
                        onCommit = { onCommitPadding(PaddingSlot.FAVORITES_TOP, it); liveSlot = null },
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            itemHeights[index] = coords.size.height
                            if (item !is HomeItem.Widget) {
                                val top = coords.positionInWindow().y
                                if (index == firstRowIndex) favBoundsTop = top
                                if (index == lastRowIndex) favBoundsBottom = top + coords.size.height
                            }
                        }
                        .zIndex(if (draggingIndex == index) 1f else 0f)
                        .graphicsLayer {
                            if (draggingIndex == index) {
                                translationY = dragOffset
                                scaleX = 1.03f
                                scaleY = 1.03f
                                alpha = 0.9f
                            }
                        }
                        .then(
                            if (editMode) {
                                Modifier.pointerInput(index, displayItems.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dragOrder = displayItems
                                            draggingIndex = index
                                            dragOffset = 0f
                                        },
                                        onDragEnd = { commitDragOrder() },
                                        onDragCancel = { commitDragOrder() },
                                    ) { change, amount ->
                                        change.consume()
                                        onDragBy(amount.y)
                                    }
                                }
                            } else {
                                Modifier
                            }
                        ),
                ) {
                    when (item) {
                        HomeItem.Widget -> WidgetSlot(
                            widgetId = widgetId,
                            heightDp = widgetHeightDp,
                            hapticsEnabled = hapticsEnabled,
                            onEditLayout = { onEditModeChange(true) },
                            actions = widgetActions,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = sidePaddingDp.dp),
                        )

                        is HomeItem.FolderItem -> FolderRow(
                            folder = item.folder,
                            members = item.folder.apps.mapNotNull { appsByKey[it] },
                            expanded = item.folder.id in expandedFolders,
                            editMode = editMode,
                            iconSizeDp = iconSizeDp,
                            labelSizeSp = labelSizeSp,
                            sidePaddingDp = sidePaddingDp,
                            contentColor = contentColor,
                            showLabels = showFavoriteLabels,
                            alignRight = alignRight,
                            menuExpanded = folderMenuFor == item.folder.id,
                            menuOffset = menuOffset,
                            touchPosition = touchPosition,
                            displayName = { displayName(it) },
                            onToggleExpanded = {
                                expandedFolders = if (item.folder.id in expandedFolders) {
                                    expandedFolders - item.folder.id
                                } else {
                                    expandedFolders + item.folder.id
                                }
                            },
                            onOpenMenu = { offset -> menuOffset = offset; folderMenuFor = item.folder.id },
                            onDismissMenu = { folderMenuFor = null },
                            onManage = { folderMenuFor = null; onManageFolder(item.folder) },
                            onEdit = { folderMenuFor = null; folderRenameFor = item.folder },
                            onEditLayout = { folderMenuFor = null; onEditModeChange(true) },
                            onDelete = { folderMenuFor = null; onDeleteFolder(item.folder) },
                            onOpenApp = onOpenFolderApp,
                            onRemoveApp = { member -> onRemoveFromFolder(item.folder, member) },
                        )

                        is HomeItem.Favorite -> FavoriteRow(
                            app = item.app,
                            label = displayName(item.app),
                            editMode = editMode,
                            iconSizeDp = iconSizeDp,
                            labelSizeSp = labelSizeSp,
                            sidePaddingDp = sidePaddingDp,
                            contentColor = contentColor,
                            showLabels = showFavoriteLabels,
                            alignRight = alignRight,
                            menuExpanded = menuForKey == item.app.key,
                            menuOffset = menuOffset,
                            touchPosition = touchPosition,
                            onLaunch = { onLaunch(item.app) },
                            onOpenMenu = { offset -> menuOffset = offset; menuForKey = item.app.key },
                            onDismissMenu = { menuForKey = null },
                            onMoveToFolder = { menuForKey = null; onMoveToFolder(item.app) },
                            onEditLayout = { menuForKey = null; onEditModeChange(true) },
                            onAppInfo = { menuForKey = null; onAppInfo(item.app) },
                            onRemove = { menuForKey = null; onRemoveFavorite(item.app) },
                            onEditIconName = { menuForKey = null; renameDialogFor = item.app },
                            onOpenSettings = { menuForKey = null; onOpenSettings() },
                        )
                    }
                }

                if (item is HomeItem.Widget) {
                    if (editMode) {
                        Spacer(Modifier.height(6.dp))
                        HeightHandle(
                            label = R.string.handle_widget_height,
                            heightDp = widgetHeightDp,
                            range = 80..900,
                            onResize = { widgetActions.onResize(it) },
                        )
                    }
                    PaddingHandle(
                        editMode = editMode,
                        label = R.string.handle_widget_bottom,
                        value = padOf(PaddingSlot.WIDGET_BOTTOM),
                        onDrag = { d -> liveSlot = PaddingSlot.WIDGET_BOTTOM; liveValue = d },
                        current = { padOf(PaddingSlot.WIDGET_BOTTOM) },
                        onCommit = { onCommitPadding(PaddingSlot.WIDGET_BOTTOM, it); liveSlot = null },
                    )
                    // Now Playing sits between the widget and the favorites.
                    if (nowPlayingHasContent) {
                        NowPlayingBlock(
                            editMode = editMode,
                            heightDp = nowPlayingHeightDp,
                            sidePaddingDp = sidePaddingDp,
                            padTop = padOf(PaddingSlot.NOW_PLAYING_TOP),
                            padBottom = padOf(PaddingSlot.NOW_PLAYING_BOTTOM),
                            touchPosition = touchPosition,
                            menuExpanded = nowPlayingMenu,
                            menuOffset = nowPlayingMenuOffset,
                            onOpenMenu = { offset -> nowPlayingMenuOffset = offset; nowPlayingMenu = true },
                            onDismissMenu = { nowPlayingMenu = false },
                            onEditLayout = { nowPlayingMenu = false; onEditModeChange(true) },
                            onOpenSettings = { nowPlayingMenu = false; onOpenSettings() },
                            onResize = onResizeNowPlaying,
                            onDragPadding = { slot, v -> liveSlot = slot; liveValue = v },
                            currentPadding = { slot -> padOf(slot) },
                            onCommitPadding = { slot, v -> onCommitPadding(slot, v); liveSlot = null },
                        )
                    }
                } else if (index == lastRowIndex) {
                    PaddingHandle(
                        editMode = editMode,
                        label = R.string.handle_favorites_bottom,
                        value = padOf(PaddingSlot.FAVORITES_BOTTOM),
                        onDrag = { d -> liveSlot = PaddingSlot.FAVORITES_BOTTOM; liveValue = d },
                        current = { padOf(PaddingSlot.FAVORITES_BOTTOM) },
                        onCommit = { onCommitPadding(PaddingSlot.FAVORITES_BOTTOM, it); liveSlot = null },
                    )
                } else {
                    Spacer(Modifier.height(itemSpacingDp.dp))
                }
            }
        }
    }

    folderRenameFor?.let { folder ->
        FolderEditDialog(
            currentName = folder.name,
            hasCustomIcon = folder.icon != null,
            onConfirm = { name -> onRenameFolder(folder, name); folderRenameFor = null },
            onChangeIcon = { onChangeFolderIcon(folder); folderRenameFor = null },
            onResetIcon = { onResetFolderIcon(folder); folderRenameFor = null },
            onDismiss = { folderRenameFor = null },
        )
    }

    renameDialogFor?.let { app ->
        EditAppDialog(
            currentName = displayName(app),
            onConfirmName = { name -> onSetName(app, name); renameDialogFor = null },
            onChangeIcon = { onChangeIcon(app); renameDialogFor = null },
            onDismiss = { renameDialogFor = null },
        )
    }
}

/** One favorite: icon, optional name, press highlight and its context menu. */
@Composable
private fun FavoriteRow(
    app: AppInfo,
    label: String,
    editMode: Boolean,
    iconSizeDp: Int,
    labelSizeSp: Int,
    sidePaddingDp: Int,
    contentColor: Color,
    showLabels: Boolean,
    alignRight: Boolean,
    menuExpanded: Boolean,
    menuOffset: DpOffset,
    touchPosition: MutableState<Offset>,
    onLaunch: () -> Unit,
    onOpenMenu: (DpOffset) -> Unit,
    onDismissMenu: () -> Unit,
    onMoveToFolder: () -> Unit,
    onEditLayout: () -> Unit,
    onAppInfo: () -> Unit,
    onRemove: () -> Unit,
    onEditIconName: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val density = LocalDensity.current

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = (sidePaddingDp - 8).coerceAtLeast(0).dp)
                .background(
                    color = if (pressed) contentColor.copy(alpha = 0.15f) else Color.Transparent,
                    shape = RoundedCornerShape(18.dp),
                )
                .then(
                    // In edit mode the row must not claim the long press, or the reorder
                    // drag above it never starts.
                    if (editMode) {
                        Modifier
                    } else {
                        Modifier
                            .recordTouchPosition(touchPosition)
                            .combinedClickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = onLaunch,
                                onLongClick = {
                                    onOpenMenu(
                                        with(density) {
                                            DpOffset(touchPosition.value.x.toDp(), touchPosition.value.y.toDp())
                                        }
                                    )
                                },
                            )
                    }
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (alignRight) {
                if (showLabels) {
                    Text(
                        label,
                        color = contentColor,
                        fontSize = labelSizeSp.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End,
                    )
                    Spacer(Modifier.width(16.dp))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                AppIcon(app = app, sizeDp = iconSizeDp)
            } else {
                AppIcon(app = app, sizeDp = iconSizeDp)
                if (showLabels) {
                    Spacer(Modifier.width(16.dp))
                    Text(label, color = contentColor, fontSize = labelSizeSp.sp, modifier = Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu, offset = menuOffset) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_move_to_folder)) },
                leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                onClick = onMoveToFolder,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit_layout)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = onEditLayout,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_app_info)) },
                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                onClick = onAppInfo,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_remove)) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = onRemove,
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit_icon_and_name)) },
                leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                onClick = onEditIconName,
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_open_settings)) },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = onOpenSettings,
            )
        }
    }
}

/** A folder row, which expands in place to show its apps. */
@Composable
private fun FolderRow(
    folder: Folder,
    members: List<AppInfo>,
    expanded: Boolean,
    editMode: Boolean,
    iconSizeDp: Int,
    labelSizeSp: Int,
    sidePaddingDp: Int,
    contentColor: Color,
    showLabels: Boolean,
    alignRight: Boolean,
    menuExpanded: Boolean,
    menuOffset: DpOffset,
    touchPosition: MutableState<Offset>,
    displayName: (AppInfo) -> String,
    onToggleExpanded: () -> Unit,
    onOpenMenu: (DpOffset) -> Unit,
    onDismissMenu: () -> Unit,
    onManage: () -> Unit,
    onEdit: () -> Unit,
    onEditLayout: () -> Unit,
    onDelete: () -> Unit,
    onOpenApp: (AppInfo) -> Unit,
    onRemoveApp: (AppInfo) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val density = LocalDensity.current

    Column {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = (sidePaddingDp - 8).coerceAtLeast(0).dp)
                    .background(
                        color = if (pressed) contentColor.copy(alpha = 0.15f) else Color.Transparent,
                        shape = RoundedCornerShape(18.dp),
                    )
                    .then(
                        if (editMode) {
                            Modifier
                        } else {
                            Modifier
                                .recordTouchPosition(touchPosition)
                                .combinedClickable(
                                    interactionSource = interaction,
                                    indication = null,
                                    onClick = onToggleExpanded,
                                    onLongClick = {
                                        onOpenMenu(
                                            with(density) {
                                                DpOffset(touchPosition.value.x.toDp(), touchPosition.value.y.toDp())
                                            }
                                        )
                                    },
                                )
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!alignRight) FolderIcon(members, iconSizeDp, contentColor, folder.icon)
                if (showLabels) {
                    if (!alignRight) Spacer(Modifier.width(16.dp))
                    Text(
                        folder.name,
                        color = contentColor,
                        fontSize = labelSizeSp.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = if (alignRight) TextAlign.End else TextAlign.Start,
                    )
                    Text(
                        "${members.size}",
                        color = contentColor.copy(alpha = 0.5f),
                        fontSize = (labelSizeSp - 3).coerceAtLeast(9).sp,
                    )
                    if (alignRight) Spacer(Modifier.width(16.dp))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (alignRight) FolderIcon(members, iconSizeDp, contentColor, folder.icon)
            }

            DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu, offset = menuOffset) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_folder_choose_apps)) },
                    leadingIcon = { Icon(Icons.Filled.Checklist, contentDescription = null) },
                    onClick = onManage,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit_icon_and_name)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = onEdit,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit_layout)) },
                    leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                    onClick = onEditLayout,
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_folder_delete)) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = onDelete,
                )
            }
        }

        // Members expand in place rather than opening a separate screen.
        AnimatedVisibility(visible = expanded && !editMode) {
            Column {
                members.forEach { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (sidePaddingDp + 24).dp, end = sidePaddingDp.dp)
                            .combinedClickable(
                                onClick = { onOpenApp(member) },
                                onLongClick = { onRemoveApp(member) },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app = member, sizeDp = (iconSizeDp * 0.8f).toInt())
                        if (showLabels) {
                            Spacer(Modifier.width(16.dp))
                            Text(displayName(member), color = contentColor, fontSize = labelSizeSp.sp)
                        }
                    }
                }
                if (members.isEmpty()) {
                    Text(
                        stringResource(R.string.home_folder_empty),
                        color = contentColor.copy(alpha = 0.6f),
                        fontSize = (labelSizeSp - 3).coerceAtLeast(10).sp,
                        modifier = Modifier.padding(start = (sidePaddingDp + 24).dp, top = 4.dp, bottom = 4.dp),
                    )
                }
            }
        }
    }
}

/** Now Playing with its own padding handles and height handle. */
@Composable
private fun NowPlayingBlock(
    editMode: Boolean,
    heightDp: Int,
    sidePaddingDp: Int,
    padTop: Int,
    padBottom: Int,
    touchPosition: MutableState<Offset>,
    menuExpanded: Boolean,
    menuOffset: DpOffset,
    onOpenMenu: (DpOffset) -> Unit,
    onDismissMenu: () -> Unit,
    onEditLayout: () -> Unit,
    onOpenSettings: () -> Unit,
    onResize: (Int) -> Unit,
    onDragPadding: (PaddingSlot, Int) -> Unit,
    currentPadding: (PaddingSlot) -> Int,
    onCommitPadding: (PaddingSlot, Int) -> Unit,
) {
    val density = LocalDensity.current

    PaddingHandle(
        editMode = editMode,
        label = R.string.handle_now_playing_top,
        value = padTop,
        onDrag = { onDragPadding(PaddingSlot.NOW_PLAYING_TOP, it) },
        current = { currentPadding(PaddingSlot.NOW_PLAYING_TOP) },
        onCommit = { onCommitPadding(PaddingSlot.NOW_PLAYING_TOP, it) },
    )
    Box {
        NowPlayingWidget(
            heightDp = heightDp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sidePaddingDp.dp)
                .recordTouchPosition(touchPosition)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        onOpenMenu(
                            with(density) {
                                DpOffset(touchPosition.value.x.toDp(), touchPosition.value.y.toDp())
                            }
                        )
                    },
                ),
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu, offset = menuOffset) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit_layout)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = onEditLayout,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_open_settings)) },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = onOpenSettings,
            )
        }
    }
    if (editMode) {
        Spacer(Modifier.height(6.dp))
        HeightHandle(
            label = R.string.handle_now_playing_height,
            heightDp = heightDp,
            range = 48..220,
            onResize = onResize,
        )
    }
    PaddingHandle(
        editMode = editMode,
        label = R.string.handle_now_playing_bottom,
        value = padBottom,
        onDrag = { onDragPadding(PaddingSlot.NOW_PLAYING_BOTTOM, it) },
        current = { currentPadding(PaddingSlot.NOW_PLAYING_BOTTOM) },
        onCommit = { onCommitPadding(PaddingSlot.NOW_PLAYING_BOTTOM, it) },
    )
}
@Composable
private fun FolderIcon(
    members: List<AppInfo>,
    sizeDp: Int,
    contentColor: Color,
    iconOverride: String?,
) {
    var drewOverride = false
    if (iconOverride != null) {
        drewOverride = FolderIconImage(iconOverride, sizeDp)
    }
    if (drewOverride) return

    val preview = members.take(4)
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .background(contentColor.copy(alpha = 0.12f), RoundedCornerShape(sizeDp.dp / 4)),
        contentAlignment = Alignment.Center,
    ) {
        if (preview.isEmpty()) {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.7f),
                modifier = Modifier.size((sizeDp * 0.55f).dp),
            )
        } else {
            val cell = (sizeDp * 0.36f).toInt()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                preview.chunked(2).forEach { rowApps ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        rowApps.forEach { AppIcon(app = it, sizeDp = cell) }
                    }
                }
            }
        }
    }
}

/** Folders get the same treatment as apps: their own name and their own icon. */
@Composable
private fun FolderEditDialog(
    currentName: String,
    hasCustomIcon: Boolean,
    onConfirm: (String) -> Unit,
    onChangeIcon: () -> Unit,
    onResetIcon: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_edit_icon_and_name)) },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.home_folder_name_label)) })
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onChangeIcon) { Text(stringResource(R.string.action_change_icon)) }
                if (hasCustomIcon) {
                    TextButton(onClick = onResetIcon) { Text(stringResource(R.string.home_folder_use_previews)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Drag sideways to inset every home element equally from the screen edges. */
@Composable
private fun SidePaddingHandle(sidePaddingDp: Int, onSetSidePadding: (Int) -> Unit) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .padding(horizontal = 12.dp)
            .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    val deltaDp = with(density) { delta.toDp().value }
                    onSetSidePadding((sidePaddingDp + deltaDp).roundToInt().coerceIn(0, 96))
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.handle_side_padding, sidePaddingDp), color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
    }
}

/** Drag bar for an element's own height, shown below it in edit mode. */
@Composable
private fun HeightHandle(
    @StringRes label: Int,
    heightDp: Int,
    range: IntRange,
    onResize: (Int) -> Unit,
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .padding(horizontal = 12.dp)
            .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    val deltaDp = with(density) { delta.toDp().value }
                    onResize((heightDp + deltaDp).roundToInt().coerceIn(range.first, range.last))
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.handle_vertical, stringResource(label), heightDp), color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
    }
}

/**
 * A block of vertical space that becomes a drag handle in edit mode, so spacing above and
 * below each element is set by dragging it rather than by a slider.
 */
@Composable
private fun PaddingHandle(
    editMode: Boolean,
    @StringRes label: Int,
    value: Int,
    current: () -> Int,
    onDrag: (Int) -> Unit,
    onCommit: (Int) -> Unit,
) {
    if (!editMode) {
        Spacer(Modifier.height(value.dp))
        return
    }

    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(maxOf(value, 28).dp)
            .padding(horizontal = 12.dp)
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    val deltaDp = with(density) { delta.toDp().value }
                    onDrag((current() + deltaDp).roundToInt().coerceIn(0, 400))
                },
                onDragStopped = { onCommit(current()) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.handle_vertical, stringResource(label), value),
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 11.sp,
        )
    }
}