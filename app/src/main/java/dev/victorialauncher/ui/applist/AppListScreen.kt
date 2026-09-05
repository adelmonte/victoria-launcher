// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.EdgeSide
import dev.victorialauncher.ui.common.AppIcon
import dev.victorialauncher.ui.common.recordTouchPosition
import dev.victorialauncher.ui.common.EditAppDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import dev.victorialauncher.R
import androidx.compose.ui.res.stringResource

/** Where the selected letter's section sits, as a fraction down the screen. */
private const val SECTION_TOP_FRACTION = 0.26f

/** How long a background tap waits for a second one before it dismisses the list. */
private const val DOUBLE_TAP_WINDOW_MS = 280L

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListScreen(
    model: AppListModel,
    nameOverrides: Map<String, String>,
    scrub: ScrubState,
    dimAlpha: Float,
    iconSizeDp: Int,
    labelSizeSp: Int,
    band: ScrubBand,
    viewportHeightPx: Int,
    visible: Boolean,
    favoriteKeys: Set<String>,
    onLaunch: (AppInfo) -> Unit,
    onSetFavorite: (AppInfo, Boolean) -> Unit,
    onSetName: (AppInfo, String?) -> Unit,
    onChangeIcon: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onHideApp: (AppInfo) -> Unit,
    onMoveToFolder: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    contentColor: Color,
    showAlphabet: Boolean,
    alignRight: Boolean,
    doubleTapToLock: Boolean,
    onDoubleTapLock: () -> Unit,
) {
    fun displayName(app: AppInfo) = nameOverrides[app.key] ?: app.label

    // Reading these here confines the invalidation to this composable: the home screen
    // behind the overlay never sees the letter change. currentY/currentPull stay as
    // function references so their callers read them in the draw phase, not composition.
    val scrubLetter = scrub.letter
    val activeSide = scrub.side
    val scrubY = remember(scrub) { scrub::currentY }
    val pullPx = remember(scrub) { scrub::currentPull }

    val listState = rememberLazyListState()
    // Rows outside the scrubbed letter fade out; the section itself never moves, because it
    // is the same list the whole time.
    val othersAlpha by animateFloatAsState(
        targetValue = if (scrubLetter != null) 0f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "othersAlpha",
    )
    val letters = remember(model) { model.letterIndex.map { it.first } }
    val sectionTopPx = (viewportHeightPx * SECTION_TOP_FRACTION).roundToInt()

    // The LazyColumn always holds the full list — while scrubbing it's just hidden and
    // pre-scrolled, with the letter's apps drawn over the top. Filtering the rows themselves
    // meant that on release the unfiltered list was briefly parked back at A.
    val scrubRowIndex = remember(model, scrubLetter) {
        val letter = scrubLetter ?: return@remember -1
        model.letterIndex.firstOrNull { it.first == letter }?.second ?: -1
    }

    // Row indices of the highlighted section. Applied *after* the scroll lands, otherwise
    // the new letter lights up a frame before the list moves to it — that was the jitter.
    var highlightRange by remember { mutableStateOf(IntRange.EMPTY) }
    // Height of the highlighted section, so the tail below the list is only as long as that
    // section needs to reach the line — not a blanket screenful.
    var highlightHeightPx by remember { mutableIntStateOf(0) }

    // Dismissing on the first tap is what made double-tap-to-lock unreachable: the overlay
    // stops receiving touches the moment it hides, so the second tap never arrived. With the
    // setting on, the dismiss waits out the double-tap window and a second tap cancels it.
    var pendingDismiss by remember { mutableStateOf<Job?>(null) }

    // The overlay stays composed while hidden, so this has to be cleared explicitly or the
    // tail padding from the last scrub sticks around forever.
    LaunchedEffect(visible) {
        if (!visible) {
            pendingDismiss?.cancel()
            pendingDismiss = null
            highlightRange = IntRange.EMPTY
            highlightHeightPx = 0
            listState.scrollToItem(0)
        }
    }

    // A manual scroll means the scrub placement has served its purpose, so the alignment
    // padding can go. Drop it once the scroll settles rather than mid-drag, and scroll back
    // by exactly what the padding gave up so the content doesn't lurch when it disappears.
    val userScrolling = listState.isScrollInProgress
    var scrolledSinceScrub by remember { mutableStateOf(false) }
    val idlePaddingPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    LaunchedEffect(userScrolling, scrubLetter) {
        if (scrubLetter != null) {
            scrolledSinceScrub = false
            return@LaunchedEffect
        }
        if (userScrolling) {
            scrolledSinceScrub = true
        } else if (scrolledSinceScrub && !highlightRange.isEmpty()) {
            scrolledSinceScrub = false
            val shrinkBy = (sectionTopPx - idlePaddingPx).toFloat()
            highlightRange = IntRange.EMPTY
            highlightHeightPx = 0
            if (shrinkBy > 0f) listState.scrollBy(-shrinkBy)
        }
    }

    LaunchedEffect(scrubRowIndex, model) {
        if (scrubRowIndex < 0) return@LaunchedEffect
        listState.scrollToItem(scrubRowIndex)
        val after = model.rows.drop(scrubRowIndex + 1).indexOfFirst { it is AppListRow.Header }
        val end = if (after < 0) model.rows.size else scrubRowIndex + 1 + after
        highlightRange = scrubRowIndex until end
        highlightHeightPx = listState.layoutInfo.visibleItemsInfo
            .filter { it.index in highlightRange }
            .sumOf { it.size }
    }



    // Pull-to-collapse, done the way pull-to-refresh is done: one nested-scroll connection
    // that actually *consumes* the drag.
    //
    // The previous versions watched the raw pointer stream without consuming, so the list
    // scrolled and the overlay tracked the pull at the same time, and reversing direction
    // left the two disagreeing. Consuming means the list can't scroll while there's a pull
    // outstanding, and winding back up spends the pull before the list moves again — so the
    // gesture is always in exactly one state.
    val dismissPullPx = with(LocalDensity.current) { 150.dp.toPx() }
    val maxPullPx = with(LocalDensity.current) { 320.dp.toPx() }
    var overPull by remember { mutableFloatStateOf(0f) }
    val collapseAnim = remember { Animatable(0f) }
    var collapsing by remember { mutableStateOf(false) }
    val collapseProvider: () -> Float = { if (collapsing) collapseAnim.value else overPull }

    val scope = rememberCoroutineScope()

    val bounce = remember { Animatable(0f) }
    val maxBouncePx = with(LocalDensity.current) { 26.dp.toPx() }

    val listConnection = remember(dismissPullPx, maxPullPx, maxBouncePx, viewportHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Spend the outstanding pull before the list is allowed to scroll again.
                if (collapsing || available.y >= 0f || overPull <= 0f) return Offset.Zero
                val used = maxOf(available.y, -overPull)
                overPull = (overPull + used).coerceAtLeast(0f)
                return Offset(0f, used)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Only a finger at the top of the list starts a pull; a fling that runs out
                // of content must not.
                if (collapsing || source != NestedScrollSource.Drag || available.y <= 0f) {
                    return Offset.Zero
                }
                // Rubber band: the further it goes, the less each pixel counts.
                val resistance = 1f - (overPull / maxPullPx).coerceIn(0f, 0.75f)
                overPull = (overPull + available.y * resistance).coerceIn(0f, maxPullPx)
                return available
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (collapsing || overPull <= 0f) return Velocity.Zero
                val pulled = overPull
                val flungDown = available.y > 800f
                collapsing = true
                collapseAnim.snapTo(pulled)
                overPull = 0f

                if (pulled > dismissPullPx || (flungDown && pulled > dismissPullPx / 3f)) {
                    collapseAnim.animateTo(
                        targetValue = viewportHeightPx.toFloat(),
                        animationSpec = tween(240, easing = FastOutLinearInEasing),
                    )
                    onDismiss()
                } else {
                    collapseAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    )
                }
                collapsing = false
                collapseAnim.snapTo(0f)
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val leftover = available.y
                if (leftover == 0f || collapsing) return Velocity.Zero
                // Bumper: a short firm nudge that settles without oscillating.
                val peak = (leftover / 60f).coerceIn(-maxBouncePx, maxBouncePx)
                bounce.animateTo(peak, tween(90, easing = FastOutLinearInEasing))
                bounce.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
                return Velocity.Zero
            }
        }
    }

    // Long-press anywhere in the list (not just favorites) to edit that app.
    var menuForKey by remember { mutableStateOf<String?>(null) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var editDialogFor by remember { mutableStateOf<AppInfo?>(null) }
    val touchPosition = remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current


    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(listConnection)
            .pointerInput(doubleTapToLock) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val start = down.position
                    var claimed = false
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) claimed = true
                        val delta = change.position - start
                        if (delta.getDistance() > viewConfiguration.touchSlop) moved = true

                        if (!change.pressed) break
                    }

                    // A tap that no row, letter or scroll claimed = a tap on the wallpaper.
                    if (!claimed && !moved) {
                        val pending = pendingDismiss
                        when {
                            pending != null -> {
                                pending.cancel()
                                pendingDismiss = null
                                onDoubleTapLock()
                            }

                            doubleTapToLock -> {
                                pendingDismiss = scope.launch {
                                    delay(DOUBLE_TAP_WINDOW_MS)
                                    pendingDismiss = null
                                    onDismiss()
                                }
                            }

                            // Off by default, so the common case keeps dismissing instantly.
                            else -> onDismiss()
                        }
                    }
                }
            },
    ) {
      Box(
          modifier = Modifier
              .fillMaxSize()
              .graphicsLayer {
                  val pulled = collapseProvider()
                  val progress = (pulled / dismissPullPx).coerceIn(0f, 1f)
                  translationY = pulled * 0.6f
                  val scale = 1f - 0.12f * progress
                  scaleX = scale
                  scaleY = scale
                  alpha = 1f - 0.85f * progress
              }
              .background(Color.Black.copy(alpha = dimAlpha)),
      ) {
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = bounce.value },
            // Room above A and below Z so any letter can sit on the same line; without it
            // the ends clamp and land somewhere else entirely.
            contentPadding = with(LocalDensity.current) {
                val needsTail = scrubLetter != null || !highlightRange.isEmpty()
                val tailPx = if (needsTail) {
                    (viewportHeightPx - sectionTopPx - highlightHeightPx).coerceAtLeast(96)
                } else {
                    0
                }
                PaddingValues(
                    top = if (needsTail) sectionTopPx.toDp() else 64.dp,
                    bottom = maxOf(tailPx, 32).toDp(),
                )
            },
        ) {
            itemsIndexed(
                items = model.rows,
                key = { index, row ->
                    when (row) {
                        is AppListRow.Header -> "header:${row.text}"
                        is AppListRow.Entry -> row.app.key
                    }
                },
            ) { index, row ->
                val rowAlpha = if (index in highlightRange) 1f else othersAlpha
                Box(modifier = Modifier.graphicsLayer { alpha = rowAlpha }) {
                when (row) {
                    is AppListRow.Header -> SectionHeader(row.text, labelSizeSp, contentColor, alignRight)
                    is AppListRow.Entry -> AppRow(
                        contentColor = contentColor,
                        alignRight = alignRight,
                        app = row.app,
                        label = displayName(row.app),
                        iconSizeDp = iconSizeDp,
                        labelSizeSp = labelSizeSp,
                        isFavorite = favoriteKeys.contains(row.app.key),
                        menuExpanded = menuForKey == row.app.key,
                        menuOffset = menuOffset,
                        touchPosition = touchPosition,
                        onLaunch = { onLaunch(row.app) },
                        onLongPress = {
                            menuOffset = with(density) {
                                DpOffset(touchPosition.value.x.toDp(), touchPosition.value.y.toDp())
                            }
                            menuForKey = row.app.key
                        },
                        onDismissMenu = { menuForKey = null },
                        onSetFavorite = { onSetFavorite(row.app, it) },
                        onEdit = { editDialogFor = row.app },
                        onAppInfo = { onAppInfo(row.app) },
                        onHide = { onHideApp(row.app) },
                        onMoveToFolder = { onMoveToFolder(row.app) },
                    )
                }
                }
            }

            // Settings shortcut, pinned after Z.
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = othersAlpha }
                        .clickable(onClick = onOpenSettings)
                        .padding(horizontal = 28.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, tint = contentColor.copy(alpha = 0.8f))
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.action_open_settings),
                        color = contentColor.copy(alpha = 0.8f),
                        fontSize = labelSizeSp.sp,
                    )
                }
            }
        }
        }

        // Fade the list out as it scrolls off the top.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent),
                    )
                ),
        )

        if (showAlphabet) {
            EdgeScrubber(
                letters = letters,
                scrubY = scrubY,
                pullPx = pullPx,
                band = band,
                side = activeSide,
                modifier = Modifier.align(
                    if (activeSide == EdgeSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd
                ),
            )
        }

        editDialogFor?.let { target ->
            EditAppDialog(
                currentName = displayName(target),
                onConfirmName = { name -> onSetName(target, name); editDialogFor = null },
                onChangeIcon = { onChangeIcon(target); editDialogFor = null },
                onDismiss = { editDialogFor = null },
            )
        }

        // Bubble for the current letter, dragged out from the strip and springing back.
        if (scrubLetter != null) {
            val bubble = 72.dp
            val halfPx = with(LocalDensity.current) { (bubble / 2).toPx() }
            val insetPx = with(LocalDensity.current) { 122.dp.toPx() }
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .align(if (activeSide == EdgeSide.LEFT) Alignment.TopStart else Alignment.TopEnd)
                    .offset {
                        val x = insetPx + pullPx()
                        IntOffset(
                            x = if (activeSide == EdgeSide.LEFT) x.roundToInt() else -x.roundToInt(),
                            y = ((scrubY() ?: 0f) - halfPx).roundToInt(),
                        )
                    }
                    .size(bubble),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        scrubLetter.toString(),
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
      }
    }
}

@Composable
private fun SectionHeader(text: String, labelSizeSp: Int, contentColor: Color, alignRight: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 0.dp),
        contentAlignment = if (alignRight) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = (labelSizeSp + 2).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun AppRow(
    contentColor: Color,
    alignRight: Boolean,
    touchPosition: MutableState<Offset>,
    app: AppInfo,
    label: String,
    iconSizeDp: Int,
    labelSizeSp: Int,
    isFavorite: Boolean,
    menuExpanded: Boolean,
    menuOffset: DpOffset,
    onLaunch: () -> Unit,
    onLongPress: () -> Unit,
    onDismissMenu: () -> Unit,
    onSetFavorite: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onAppInfo: () -> Unit,
    onHide: () -> Unit,
    onMoveToFolder: () -> Unit,
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .recordTouchPosition(touchPosition)
                .combinedClickable(onClick = onLaunch, onLongClick = onLongPress)
                .padding(horizontal = 28.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app = app, sizeDp = iconSizeDp)
            Spacer(Modifier.width(16.dp))
            Text(label, color = contentColor, fontSize = labelSizeSp.sp)
        }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu, offset = menuOffset) {
            DropdownMenuItem(
                text = { Text(stringResource(if (isFavorite) R.string.applist_remove_favorite else R.string.applist_add_favorite)) },
                leadingIcon = {
                    Icon(
                        if (isFavorite) Icons.Filled.StarBorder else Icons.Filled.Star,
                        contentDescription = null,
                    )
                },
                onClick = { onDismissMenu(); onSetFavorite(!isFavorite) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit_icon_and_name)) },
                leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                onClick = { onDismissMenu(); onEdit() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_app_info)) },
                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                onClick = { onDismissMenu(); onAppInfo() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_move_to_folder)) },
                leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                onClick = { onDismissMenu(); onMoveToFolder() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.applist_hide)) },
                leadingIcon = { Icon(Icons.Filled.VisibilityOff, contentDescription = null) },
                onClick = { onDismissMenu(); onHide() },
            )
        }
    }
}