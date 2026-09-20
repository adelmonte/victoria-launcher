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
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.EdgeSide
import dev.victorialauncher.data.EntryKind
import dev.victorialauncher.data.HomeAlignment
import dev.victorialauncher.data.IconSide
import dev.victorialauncher.data.ShortcutSwipe
import dev.victorialauncher.ui.common.AppIcon
import dev.victorialauncher.ui.common.AppShortcutMenu
import dev.victorialauncher.ui.common.swipeForShortcuts
import dev.victorialauncher.ui.common.LocalIconConfig
import dev.victorialauncher.ui.common.recordTouchPosition
import dev.victorialauncher.ui.common.EditAppDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt
import dev.victorialauncher.R
import androidx.compose.ui.res.stringResource

/** Where the selected letter's section sits, as a fraction down the screen. */
private const val SECTION_TOP_FRACTION = 0.26f

/**
 * Breathing room above A and below the settings row when no scrub has placed the list.
 *
 * Small, because nothing needs holding clear any more: the end fades now switch off when there
 * is nothing scrolled past them, so the first row is crisp where it rests rather than sitting
 * inside a permanent fade.
 */
private val IDLE_TOP_PADDING = 8.dp
private val IDLE_BOTTOM_PADDING = 32.dp

/** Smallest comfortable row, so a tap beside a small icon still lands on its app. */
private val MIN_ROW_HEIGHT = 48.dp

/** How far rows are held back from the edge the A-Z strip occupies. */
private val STRIP_INSET = 56.dp

/** Sets the settings shortcut apart from the last app above it. */
private val SETTINGS_ROW_GAP = 20.dp

/**
 * Widest the list is allowed to get.
 *
 * A name is a short thing; stretched over a tablet it leaves the row mostly empty and puts the
 * A-Z strip a hand's width from what it is scrubbing. A phone held upright never reaches this.
 */
private val MAX_LIST_WIDTH = 600.dp

/** How far the list dissolves at each end. */
private val FADE_HEIGHT = 56.dp

/**
 * Gap above and below search results.
 *
 * The idle gaps exist so a scrubbed letter can be placed on the scrub line. Search has no
 * scrub and no line, so results simply start where the list does.
 */
private val SEARCH_EDGE_PADDING = 8.dp

/** How far either end of the list may be dragged past its content. */
private val MAX_EDGE_STRETCH = 40.dp

/**
 * How much of the collapse travel commits it. Shared with the swipe that opens the list, so
 * closing takes the same push as opening rather than roughly twice it.
 */
private const val COMMIT_FRACTION = 0.4f

/**
 * Damping for the edge elastic. Under 1 so a fling into an end overshoots and comes back
 * once — a bumper, not a bounce.
 */
private const val EDGE_STRETCH_DAMPING = 0.55f

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun AppListScreen(
    model: AppListModel,
    nameOverrides: Map<String, String>,
    scrub: ScrubState,
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
    onUnpinShortcut: (AppInfo) -> Unit,
    onHideApp: (AppInfo, Boolean) -> Unit,
    /** Which apps are hidden, so a search that turns one up can say so and put it back. */
    hiddenApps: Set<String>,
    onMoveToFolder: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    contentColor: Color,
    showAlphabet: Boolean,
    edgeSide: EdgeSide,
    /** Hoisted so a swipe that overshoots the opening animation can keep scrolling it. */
    listState: LazyListState,
    /** How far the list still has to travel to be fully open; 0 once it has arrived. */
    enterPullPx: Float,
    /** Whether the status bar is set to stay hidden here, so its gap is not reserved. */
    statusBarHidden: Boolean,
    /** The model a query runs against, which may carry hidden apps the list itself omits. */
    searchModel: AppListModel,
    /** Whether a sideways swipe on a row offers its app's shortcuts. */
    shortcutSwipe: ShortcutSwipe,
    searchEnabled: Boolean,
    searchAtBottom: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    alignment: HomeAlignment,
    iconSide: IconSide,
) {
    fun displayName(app: AppInfo) = nameOverrides[app.key] ?: app.label

    // The gesture handlers below outlive the composition that created them, so they must not
    // capture this frame's callbacks — a dismiss half a minute old still has to close the
    // list that is up now.
    val currentDismiss by rememberUpdatedState(onDismiss)

    // Reading these here confines the invalidation to this composable: the home screen
    // behind the overlay never sees the letter change. currentY/currentPull stay as
    // function references so their callers read them in the draw phase, not composition.
    val scrubLetter = scrub.letter
    val scrubPlacement = scrub.placement
    val scrubbing = scrub.scrubbing
    val activeSide = scrub.side
    val scrubY = remember(scrub) { scrub::currentY }
    val pullPx = remember(scrub) { scrub::currentPull }

    // Searching is a different mode from scrubbing: the letters shrink to whatever matched,
    // so the strip is hidden and placement stays out of it until the query is cleared.
    val searching = searchEnabled && query.isNotBlank()
    val displayModel = remember(model, searchModel, query, searchEnabled) {
        if (!searching) {
            model
        } else {
            val term = query.trim()
            searchModel.filtered { app ->
                displayName(app).contains(term, ignoreCase = true) ||
                    // An app names itself in the language of the device, so on a Japanese
                    // phone Settings calls itself 設定 and no amount of typing "settings"
                    // reaches it. Package names are ASCII almost without exception, so the
                    // English word is usually sitting right there in com.android.settings.
                    app.componentName.packageName.contains(term, ignoreCase = true)
            }
        }
    }
    // Animated rather than switched, so an end does not snap from crisp to faded the moment
    // the first pixel scrolls past it.
    val topFade by animateFloatAsState(
        if (listState.canScrollBackward) 1f else 0f,
        label = "topFade",
    )
    val bottomFade by animateFloatAsState(
        if (listState.canScrollForward) 1f else 0f,
        label = "bottomFade",
    )

    /** Height of the pinned search field, so list-relative offsets can be compared to taps. */
    var searchHeightPx by remember { mutableIntStateOf(0) }

    // Each query is a fresh list, so it starts at the top. Without this the offset from
    // whatever was scrolled before carries over, and a query with few matches lands the
    // results somewhere past the end of the screen.
    LaunchedEffect(query) {
        if (query.isNotBlank()) listState.scrollToItem(0)
    }

    // Rows outside the scrubbed letter fade out; the section itself never moves, because it
    // is the same list the whole time. Only ever read inside a graphicsLayer, so the fade
    // runs in the draw phase instead of recomposing every visible row 60 times a second.
    val othersAlpha by animateFloatAsState(
        // Only while a finger is travelling through the alphabet: a tap on the edge sets a
        // letter too, and fading out for it cost a quarter of a second of ghosted list on
        // every open.
        targetValue = if (scrubLetter != null && scrubbing) 0f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "othersAlpha",
    )
    val sectionTopPx = (viewportHeightPx * SECTION_TOP_FRACTION).roundToInt()

    // The LazyColumn always holds the full list — while scrubbing it's just hidden and
    // pre-scrolled, with the letter's apps drawn over the top. Filtering the rows themselves
    // meant that on release the unfiltered list was briefly parked back at A.
    // From the placement and not from the live letter: the letter is gone by the time a quick
    // tap has composed anything, and the list is placed by what was asked for, not by what is
    // still under a finger.
    val scrubRowIndex = remember(displayModel, scrubPlacement) {
        val letter = scrubPlacement?.letter ?: return@remember -1
        displayModel.letterIndex.firstOrNull { it.first == letter }?.second ?: -1
    }

    // Row indices of the highlighted section. Applied *after* the scroll lands, otherwise
    // the new letter lights up a frame before the list moves to it — that was the jitter.
    var highlightRange by remember { mutableStateOf(IntRange.EMPTY) }

    // Pull-to-collapse from either end, done the way pull-to-refresh is done: one
    // nested-scroll connection that actually *consumes* the drag. Held signed throughout —
    // positive is pulled down off the top, negative is pulled up off the bottom — so one
    // gesture serves both ends rather than each needing its own path.
    //
    // The previous versions watched the raw pointer stream without consuming, so the list
    // scrolled and the overlay tracked the pull at the same time, and reversing direction
    // left the two disagreeing. Consuming means the list can't scroll while there's a pull
    // outstanding, and winding back up spends the pull before the list moves again — so the
    // gesture is always in exactly one state.
    val density = LocalDensity.current
    // The travel a collapse is drawn over, and the share of it that commits — deliberately
    // the same fraction the swipe-up uses to open, so the two gestures answer alike.
    val dismissPullPx = with(density) { 150.dp.toPx() }
    val dismissCommitPx = dismissPullPx * COMMIT_FRACTION
    // Per-frame drag big enough to call a flick rather than a deliberate push.
    val fastDragPx = with(density) { 12.dp.toPx() }
    // How far a gesture may have scrolled the list and still count as starting at the end.
    val nearEndPx = with(density) { 48.dp.toPx() }
    val maxPullPx = with(density) { 320.dp.toPx() }
    val maxStretchPx = with(density) { MAX_EDGE_STRETCH.toPx() }
    // The idle gaps are there so a scrubbed letter can be placed on the scrub line with room
    // to spare. A pinned search field already separates the list from the screen edge, and the
    // full gap on top of it read as dead space — and as a gap that changed size the moment you
    // started typing. Whichever end the field is on gets the search gap either way.
    val restingTopPadding =
        if (searchEnabled && !searchAtBottom) SEARCH_EDGE_PADDING else IDLE_TOP_PADDING
    val restingBottomPadding =
        if (searchEnabled && searchAtBottom) SEARCH_EDGE_PADDING else IDLE_BOTTOM_PADDING
    val idleTopPaddingPx = with(density) { restingTopPadding.roundToPx() }
    val idleBottomPaddingPx = with(density) { restingBottomPadding.roundToPx() }

    /** Signed: positive pulled down off the top of the list, negative up off the bottom. */
    var overPull by remember { mutableFloatStateOf(0f) }
    val collapseAnim = remember { Animatable(0f) }
    var collapsing by remember { mutableStateOf(false) }
    // Entering and collapsing are the same transform in opposite directions, so the distance
    // left to travel on the way in is fed through the very same path.
    val collapseProvider: () -> Float = {
        when {
            collapsing -> collapseAnim.value
            enterPullPx > 0f -> enterPullPx
            else -> overPull
        }
    }

    // Both ends of the list share one elastic. A drag past an end stretches it, a fling into
    // an end seeds it with the leftover *velocity*, and it always springs back to rest.
    // Seeding from velocity rather than jumping to a fixed peak is what stops a second fling
    // from snapping the list: the new spring carries on from wherever the old one was.
    var stretchPx by remember { mutableFloatStateOf(0f) }
    val stretchAnim = remember { Animatable(0f) }
    var stretchSettling by remember { mutableStateOf(false) }
    val stretchProvider: () -> Float = {
        val raw = if (stretchSettling) stretchAnim.value else stretchPx
        raw.coerceIn(-maxStretchPx, maxStretchPx)
    }

    // Everything the overlay left behind has to be cleared explicitly, because it stays
    // composed while hidden: the tail padding and the collapse transform from the last scrub
    // would otherwise still be there the next time it opens.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(visible) {
        if (!visible) {
            focusManager.clearFocus()
            highlightRange = IntRange.EMPTY
            overPull = 0f
            stretchPx = 0f
            collapsing = false
            scrub.clearPlacement()
            return@LaunchedEffect
        }
        // Back to the top on the way in rather than on the way out, and only when nothing has
        // asked for a letter.
        //
        // Hidden, this overlay is measured but never placed, and a scroll waits for a layout
        // that is not coming — so a scroll-to-top issued on close sat pending from then until
        // the list was next placed, which is the moment it opens. It and the tap's own scroll
        // then woke on the same layout pass and raced for the scroll mutex, and when the stale
        // one landed second the list opened at A having been told to go to N. Only ever on the
        // first open after the launcher started, because only then is one left pending.
        if (scrub.placement == null) listState.scrollToItem(0)
    }

    // How much of the scrub placement padding is still sitting on screen at each end, over and
    // above the gap the list idles with. Positive means that end has further to travel.
    fun topGapRemaining(): Float {
        val info = listState.layoutInfo
        val first = info.visibleItemsInfo.firstOrNull() ?: return 0f
        // Not the first row at the top means the gap is long gone above us.
        if (first.index > 0) return 0f
        return (first.offset - info.viewportStartOffset).toFloat() - idleTopPaddingPx
    }

    /**
     * How much further the list could still scroll forward. Large whenever the end is not even
     * on screen; zero when parked against it.
     */
    fun forwardRoom(): Float {
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull() ?: return Float.MAX_VALUE
        if (last.index < displayModel.rows.size) return Float.MAX_VALUE
        val bottom = (last.offset - info.viewportStartOffset + last.size).toFloat()
        return bottom + idleBottomPaddingPx - viewportHeightPx
    }

    /**
     * Whether the placement padding can be retired without anything appearing to move.
     *
     * A list short enough to show both ends at once can never clear either gap by scrolling,
     * so it is let through rather than left holding the padding for good.
     */
    fun placementSettled(): Boolean {
        val items = listState.layoutInfo.visibleItemsInfo
        val first = items.firstOrNull() ?: return true
        if (first.index == 0 && items.last().index >= displayModel.rows.size) return true
        return topGapRemaining() <= 0f
    }

    // A manual scroll means the scrub placement has served its purpose, so the alignment gap
    // can go — but it is ordinary scrollable space, so the finger is allowed to travel through
    // it rather than having it pulled out from underneath. That matters at A, which the scrub
    // parks against the top of the scroll range: there is nothing above to scroll back into, so
    // retiring the gap there could only shift the rows themselves and the first section would
    // snap upward. Waiting until it has scrolled off the top makes the change invisible, and
    // once gone the idle gap is all that is left, so there is no scrolling back into it unless
    // A is picked again.
    var userDragged by remember { mutableStateOf(false) }
    LaunchedEffect(userDragged, scrubLetter) {
        if (!userDragged || scrubLetter != null || highlightRange.isEmpty()) {
            return@LaunchedEffect
        }
        snapshotFlow { placementSettled() }.first { it }
        if (highlightRange.isEmpty()) return@LaunchedEffect

        // The top padding falls from the scrub line back to the idle gap, so that is exactly
        // how far the content would rise — compensating by anything else (this used to use a
        // bare 8dp) leaves the list jumping by the difference.
        //
        // Except against the bottom, where shrinking the padding shortens the scroll range by
        // the same amount and the clamp slides us back by whatever no longer fits, doing part
        // of the job already. Compensating the full amount on top of that is itself a jump, so
        // only what the clamp cannot absorb is asked for.
        val shrinkBy = (sectionTopPx - idleTopPaddingPx).toFloat()
        val compensate = minOf(shrinkBy, forwardRoom()).coerceAtLeast(0f)
        highlightRange = IntRange.EMPTY
        // dispatchRawDelta rather than scrollBy: the drag that got us here holds the scroll
        // mutex at UserInput priority, and a scrollBy would just be canceled by it.
        if (compensate > 0f) listState.dispatchRawDelta(-compensate)
    }

    LaunchedEffect(scrubRowIndex, scrubPlacement, displayModel) {
        if (scrubRowIndex < 0) return@LaunchedEffect
        listState.scrollToItem(scrubRowIndex)
        // The next letter's header ends this section. Walking the rows to find it copied the
        // whole tail of the list on every one of the ~26 letter changes in a gesture.
        val end = displayModel.letterIndex.firstOrNull { it.second > scrubRowIndex }?.second ?: displayModel.rows.size
        highlightRange = scrubRowIndex until end
        // This placement is fresh, so the next drag is the one that retires it.
        userDragged = false
    }

    val scope = rememberCoroutineScope()

    val listConnection = remember(dismissPullPx, maxPullPx, maxStretchPx, fastDragPx, nearEndPx, viewportHeightPx, listState) {
        object : NestedScrollConnection {
            /** True between the first drag of a gesture and the fling that ends it. */
            private var dragging = false

            /**
             * Whether the drag in progress began with the list already parked against that
             * end, which is what makes a pull from it a collapse rather than a scroll.
             */
            private var topPullEligible = false
            private var bottomPullEligible = false

            /**
             * Whether this gesture may collapse the overlay.
             *
             * Requiring it to *begin* against the end meant scrolling back to the top and
             * pushing on did nothing, and a collapse only answered on a second, separate
             * drag. What actually needs guarding against is a flick slamming into the end and
             * shrinking the overlay mid-scroll — so a gesture arms if it barely scrolled the
             * list at all, or if it is travelling slowly by the time it gets there. A long
             * fast flick satisfies neither.
             */
            private var pullArmed = false

            /** How far this gesture has scrolled the list, to tell a pull from a flick. */
            private var scrolledInGesture = 0f

            /** The edge spring, held so a finger arriving mid-settle can take it over. */
            private var settleJob: Job? = null

            private fun stretch(delta: Float) {
                // Rubber band: the further it goes, the less each pixel counts.
                val resistance = 1f - (abs(stretchPx) / maxStretchPx).coerceIn(0f, 0.9f)
                stretchPx = (stretchPx + delta * resistance).coerceIn(-maxStretchPx, maxStretchPx)
            }

            /**
             * Runs the edge spring without making the fling wait for it.
             *
             * Awaited inside onPostFling it held the connection in its settling state for the
             * spring's whole duration, and every guard below turns a drag away while that is
             * true — so a flick that coasted into the end swallowed the next pull entirely and
             * the collapse only answered on the swipe after. Launched separately it can simply
             * be cancelled the moment a finger comes back down.
             */
            private fun startSettle(velocity: Float) {
                settleJob?.cancel()
                settleJob = scope.launch { settleStretch(velocity) }
            }

            /** Ends a settle in progress and hands its position back to the finger. */
            private fun takeOverSettle() {
                settleJob?.cancel()
                settleJob = null
                if (stretchSettling) {
                    stretchPx = stretchAnim.value.coerceIn(-maxStretchPx, maxStretchPx)
                    stretchSettling = false
                }
            }

            private suspend fun settleStretch(velocity: Float) {
                val headroom = (1f - abs(stretchPx) / maxStretchPx).coerceIn(0f, 1f)
                stretchSettling = true
                try {
                    stretchAnim.snapTo(stretchPx.coerceIn(-maxStretchPx, maxStretchPx))
                    stretchPx = 0f
                    stretchAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = EDGE_STRETCH_DAMPING,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        // Scaled by what headroom is left before the clamp: seeding a full
                        // fling on top of an already-stretched edge drives the spring past
                        // maxStretchPx, and the draw clamps it there for a few frames — a
                        // flat spot in the middle of the motion, which reads as a hitch.
                        initialVelocity = (velocity * headroom).coerceIn(
                            -maxStretchPx * 12f,
                            maxStretchPx * 12f,
                        ),
                    )
                } finally {
                    // Handing the live value back means an interrupted settle continues from
                    // where it was instead of snapping flat.
                    stretchPx = stretchAnim.value.coerceIn(-maxStretchPx, maxStretchPx)
                    stretchSettling = false
                }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && !dragging) {
                    dragging = true
                    // Cancellation is not instant, so the state is handed over here and now
                    // rather than a frame later when the coroutine notices.
                    takeOverSettle()
                    // A finger on the list, as opposed to a programmatic scrub scroll.
                    userDragged = true
                    // Collapsing has to be a deliberate pull from rest. Letting a scroll that
                    // merely *arrives* at an end turn into one is what made a fast flick
                    // shrink and fade the whole list halfway through the gesture.
                    topPullEligible = !listState.canScrollBackward
                    bottomPullEligible = !listState.canScrollForward
                    pullArmed = false
                    scrolledInGesture = 0f
                }
                if (collapsing || stretchSettling) return Offset.Zero
                // Spend whatever is outstanding before the list is allowed to move again, so
                // winding a gesture back never has the two running at once.
                if (overPull > 0f && available.y < 0f) {
                    val used = maxOf(available.y, -overPull)
                    overPull = (overPull + used).coerceAtLeast(0f)
                    return Offset(0f, used)
                }
                if (overPull < 0f && available.y > 0f) {
                    val used = minOf(available.y, -overPull)
                    overPull = (overPull + used).coerceAtMost(0f)
                    return Offset(0f, used)
                }
                if (stretchPx > 0f && available.y < 0f) {
                    val used = maxOf(available.y, -stretchPx)
                    stretchPx += used
                    return Offset(0f, used)
                }
                if (stretchPx < 0f && available.y > 0f) {
                    val used = minOf(available.y, -stretchPx)
                    stretchPx += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Only a finger stretches an end; a fling that runs out of content is dealt
                // with in onPostFling, where its velocity is still known.
                if (collapsing || stretchSettling || source != NestedScrollSource.Drag) {
                    return Offset.Zero
                }
                scrolledInGesture += abs(consumed.y)
                if (available.y == 0f) return Offset.Zero
                if (scrolledInGesture <= nearEndPx || abs(available.y) <= fastDragPx) pullArmed = true
                val startedAtThisEnd = if (available.y > 0f) topPullEligible else bottomPullEligible
                val pulling = startedAtThisEnd || pullArmed
                if (pulling) {
                    // Signed: pulled down off the top is positive, pulled up off the bottom is
                    // negative, and every reader below works off the sign rather than a
                    // separate flag for which end is in play.
                    val resistance = 1f - (abs(overPull) / maxPullPx).coerceIn(0f, 0.75f)
                    overPull = (overPull + available.y * resistance).coerceIn(-maxPullPx, maxPullPx)
                    return available
                }
                stretch(available.y)
                return available
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                dragging = false
                pullArmed = false
                scrolledInGesture = 0f
                if (collapsing || stretchSettling) return Velocity.Zero
                if (overPull != 0f) {
                    val pulled = overPull
                    val away = if (pulled > 0f) 1f else -1f
                    // Flung on past the threshold counts even when the pull itself is short.
                    val flungAway = available.y * away > 800f
                    val dismissing = abs(pulled) > dismissCommitPx ||
                        (flungAway && abs(pulled) > dismissCommitPx * 0.4f)
                    collapsing = true
                    try {
                        collapseAnim.snapTo(pulled)
                        overPull = 0f
                        if (dismissing) {
                            collapseAnim.animateTo(
                                // Off whichever edge it was heading for.
                                targetValue = viewportHeightPx.toFloat() * away,
                                animationSpec = tween(240, easing = FastOutLinearInEasing),
                            )
                            currentDismiss()
                        } else {
                            collapseAnim.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                            )
                        }
                    } finally {
                        // Also on the way out of a canceled fling — the next gesture landing
                        // on top of this one — or the overlay stays parked halfway down the
                        // screen for good. Nothing of it is on screen by then either way,
                        // because a hidden overlay is measured but never placed.
                        collapsing = false
                    }
                    return available
                }
                if (stretchPx != 0f) {
                    startSettle(available.y)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (collapsing || stretchSettling || available.y == 0f) return Velocity.Zero
                startSettle(available.y)
                return available
            }
        }
    }

    // Long-press anywhere in the list (not just favorites) to edit that app.
    var menuForKey by remember { mutableStateOf<String?>(null) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var editDialogFor by remember { mutableStateOf<AppInfo?>(null) }
    val touchPosition = remember { mutableStateOf(Offset.Zero) }

    // The vertical span the rows actually occupy. A tap inside it belongs to the list even
    // when it misses a label — a section header, the gap under the last app of a letter —
    // and dismissing on those is what made the list feel like it was fighting you.
    fun isOnListContent(y: Float): Boolean {
        val info = listState.layoutInfo
        val first = info.visibleItemsInfo.firstOrNull() ?: return false
        val last = info.visibleItemsInfo.last()
        return y >= first.offset - info.viewportStartOffset &&
            y < last.offset + last.size - info.viewportStartOffset
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(listConnection)
            .pointerInput(listState) {
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

                    // A tap no row, letter or scroll claimed, landing clear of the list
                    // itself = a tap on the wallpaper.
                    // Taps are in overlay space and the list is in its own; only a field above it
                    // shifts the two apart.
                    val listOffset = if (searchEnabled && !searchAtBottom) searchHeightPx else 0
                    if (claimed || moved || isOnListContent(start.y - listOffset)) return@awaitEachGesture

                    currentDismiss()
                }
            },
    ) {
      Box(
          modifier = Modifier
              .fillMaxSize()
              .graphicsLayer {
                  val pulled = collapseProvider()
                  val progress = (abs(pulled) / dismissPullPx).coerceIn(0f, 1f)
                  translationY = pulled * 0.6f
                  val scale = 1f - 0.12f * progress
                  scaleX = scale
                  scaleY = scale
                  alpha = 1f - 0.85f * progress
              },
      ) {
        // The keyboard covers the bottom of the overlay, and with a field pinned down there
        // the window used to be panned bodily up to reveal it, taking the top of the results
        // off screen with it. Insetting instead leaves the overlay where it is and gives the
        // list the space that is actually left.
        // Ignoring visibility deliberately: a plain statusBarsPadding follows the bar as it
        // fades, and the inset drops to zero the instant the fade ends, so the whole overlay
        // jumped up into the space. The gap is held by what the setting asks for instead, which
        // does not change while the list is open.
        Column(
            modifier = Modifier
                // Sideways the camera cutout runs down an edge rather than along the top, and
                // the list ran under it. Horizontal sides only, so upright is unchanged.
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                // Capped and held against the strip's own side, so on a wide screen the names
                // stay next to the letters being scrubbed instead of a screen away from them.
                .widthIn(max = MAX_LIST_WIDTH)
                .align(if (activeSide == EdgeSide.LEFT) Alignment.TopStart else Alignment.TopEnd)
                .fillMaxSize()
                .then(
                    if (statusBarHidden) {
                        Modifier
                    } else {
                        Modifier.windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                    }
                )
                .imePadding()
        ) {
        if (searchEnabled && !searchAtBottom) {
            // Pinned above the list rather than scrolling with it as a first item: every row
            // index the scrub placement works from would shift by one, and the field would
            // disappear the moment you scrolled.
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                contentColor = contentColor,
                activeSide = activeSide,
                showAlphabet = showAlphabet,
                modifier = Modifier.onSizeChanged { searchHeightPx = it.height },
            )
        }
        Box(modifier = Modifier.weight(1f)) {
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = stretchProvider()
                    // The mask below blends against this layer alone, so it can take the
                    // rows' alpha away without touching anything drawn behind them.
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    // Dissolves the rows at both ends rather than painting black over them.
                    // A painted fade had to be opaque enough to hide text, which left a hard
                    // step wherever its edge met the overlay — glaring against a pinned search
                    // field, and darker still where two of them met. Taking the rows' own
                    // alpha instead leaves the background exactly as it was, so they fade into
                    // it however light or dark the wallpaper dim has made it.
                    val fade = (FADE_HEIGHT.toPx() / size.height).coerceIn(0f, 0.5f)
                    if (fade > 0f) {
                        // Each end fades only while something is actually scrolled past it.
                        // A fade that is always on dims the first and last rows even at rest,
                        // which is what the gap above A was there to hold them clear of.
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 1f - topFade),
                                fade to Color.Black,
                                1f - fade to Color.Black,
                                1f to Color.Black.copy(alpha = 1f - bottomFade),
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                },
            // Room above A and below Z so any letter can sit on the same line; without it
            // the ends clamp and land somewhere else entirely.
            // Room above A so it can sit on the scrub line like every other letter; without
            // it the top clamps and A lands somewhere else entirely. There is deliberately no
            // matching room below Z. Reaching the line from the bottom would take most of a
            // screen of empty space past the settings row, which reads as the list being
            // broken rather than as placement, so the last letter simply lands as high as its
            // own content allows.
            contentPadding = with(density) {
                val top = when {
                    searching -> SEARCH_EDGE_PADDING
                    scrubLetter != null || !highlightRange.isEmpty() -> sectionTopPx.toDp()
                    else -> restingTopPadding
                }
                // The strip is drawn over this list, not beside it, so the side it occupies
                // has to be held clear. Only that side: with both edges enabled the strip is
                // still only ever on the one you opened from, and insetting the other leaves
                // a margin against nothing.
                PaddingValues(
                    start = if (showAlphabet && activeSide == EdgeSide.LEFT) STRIP_INSET else 0.dp,
                    end = if (showAlphabet && activeSide == EdgeSide.RIGHT) STRIP_INSET else 0.dp,
                    top = top,
                    bottom = if (searching) SEARCH_EDGE_PADDING else restingBottomPadding,
                )
            },
        ) {
            itemsIndexed(
                items = displayModel.rows,
                key = { _, row ->
                    when (row) {
                        is AppListRow.Header -> "header:${row.text}"
                        is AppListRow.Entry -> row.app.key
                    }
                },
                // Headers and app rows are laid out nothing alike; telling the list so lets
                // it reuse each kind against its own pool while scrubbing.
                contentType = { _, row -> row is AppListRow.Header },
            ) { index, row ->
                Box(
                    // Read in the draw phase on purpose: the scrub fade would otherwise
                    // recompose every visible row on every frame of the 180ms tween.
                    modifier = Modifier.graphicsLayer {
                        alpha = if (index in highlightRange) 1f else othersAlpha
                    },
                ) {
                when (row) {
                    is AppListRow.Header -> SectionHeader(row.text, labelSizeSp, contentColor, alignment)
                    is AppListRow.Entry -> AppRow(
                        shortcutSwipe = shortcutSwipe,
                        contentColor = contentColor,
                        alignment = alignment,
                        iconSide = iconSide,
                        app = row.app,
                        label = displayName(row.app),
                        iconSizeDp = iconSizeDp,
                        labelSizeSp = labelSizeSp,
                        isFavorite = favoriteKeys.contains(row.app.key),
                        menuExpanded = menuForKey == row.app.key,
                        menuOffset = menuOffset,
                        touchPosition = touchPosition,
                        onLaunch = { onLaunch(row.app) },
                        onLongPress = { offset -> menuOffset = offset; menuForKey = row.app.key },
                        onDismissMenu = { menuForKey = null },
                        onSetFavorite = { onSetFavorite(row.app, it) },
                        onEdit = { editDialogFor = row.app },
                        onAppInfo = { onAppInfo(row.app) },
                        onUnpin = { onUnpinShortcut(row.app) },
                        onHide = { onHideApp(row.app, row.app.key !in hiddenApps) },
                        isHidden = row.app.key in hiddenApps,
                        onMoveToFolder = { onMoveToFolder(row.app) },
                    )
                }
                }
            }

            // Settings shortcut, pinned after Z.
            item(key = "settings", contentType = "settings") {
                // Set apart from the apps above it: it is the one row here that is not one.
                Spacer(Modifier.height(SETTINGS_ROW_GAP))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = othersAlpha }
                        .clickable(onClick = onOpenSettings)
                        .heightIn(min = MIN_ROW_HEIGHT)
                        .padding(horizontal = 28.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = when (alignment) {
                        HomeAlignment.LEFT -> Arrangement.Start
                        HomeAlignment.CENTER -> Arrangement.Center
                        HomeAlignment.RIGHT -> Arrangement.End
                    },
                ) {
                    if (alignment == HomeAlignment.RIGHT) {
                        Text(
                            stringResource(R.string.action_open_settings),
                            color = contentColor.copy(alpha = 0.8f),
                            fontSize = labelSizeSp.sp,
                        )
                        Spacer(Modifier.width(16.dp))
                        Icon(Icons.Filled.Settings, contentDescription = null, tint = contentColor.copy(alpha = 0.8f))
                    } else {
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
        }

        }
        if (searchEnabled && searchAtBottom) {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                contentColor = contentColor,
                activeSide = activeSide,
                showAlphabet = showAlphabet,
                atBottom = true,
            )
        }
        }

        if (showAlphabet && !searching) {
            EdgeScrubber(
                letters = displayModel.letters,
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
                onChangeIcon = { name ->
                    if (name.trim() != displayName(target)) onSetName(target, name.trim())
                    onChangeIcon(target)
                    editDialogFor = null
                },
                onDismiss = { editDialogFor = null },
            )
        }

        // Bubble for the current letter, dragged out from the strip and springing back.
        if (scrubLetter != null) {
            val bubble = 72.dp
            val halfPx = with(density) { (bubble / 2).toPx() }
            val insetPx = with(density) { SCRUB_BUBBLE_INSET_DP.dp.toPx() }
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
private fun SectionHeader(text: String, labelSizeSp: Int, contentColor: Color, alignment: HomeAlignment) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        contentAlignment = when (alignment) {
            HomeAlignment.LEFT -> Alignment.CenterStart
            HomeAlignment.CENTER -> Alignment.Center
            HomeAlignment.RIGHT -> Alignment.CenterEnd
        },
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
    shortcutSwipe: ShortcutSwipe,
    contentColor: Color,
    alignment: HomeAlignment,
    iconSide: IconSide,
    touchPosition: MutableState<Offset>,
    app: AppInfo,
    label: String,
    iconSizeDp: Int,
    labelSizeSp: Int,
    isFavorite: Boolean,
    menuExpanded: Boolean,
    menuOffset: DpOffset,
    onLaunch: () -> Unit,
    onLongPress: (DpOffset) -> Unit,
    onDismissMenu: () -> Unit,
    onSetFavorite: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onAppInfo: () -> Unit,
    onUnpin: () -> Unit,
    onHide: () -> Unit,
    onMoveToFolder: () -> Unit,
    /** Hidden apps only ever reach this list through a search. */
    isHidden: Boolean,
) {
    // Same press treatment as the home screen: the stock ripple all but vanishes against a
    // wallpaper, and without any feedback a tap that did register reads as one that didn't.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val density = LocalDensity.current
    var shortcutMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Ahead of the inset, so the long-press menu is still placed against the
                // whole row rather than 20dp to the left of the finger.
                .recordTouchPosition(touchPosition)
                .swipeForShortcuts(shortcutSwipe, app.kind == EntryKind.APP) {
                    shortcutMenu = true
                }
                .padding(horizontal = 20.dp)
                .background(
                    color = if (pressed) contentColor.copy(alpha = 0.15f) else Color.Transparent,
                    shape = RoundedCornerShape(18.dp),
                )
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onLaunch,
                    onLongClick = {
                        onLongPress(
                            with(density) {
                                DpOffset(touchPosition.value.x.toDp(), touchPosition.value.y.toDp())
                            }
                        )
                    },
                )
                // The whole row is the target, not the label: at small icon sizes the strip
                // left to tap was thinner than a fingertip.
                .heightIn(min = MIN_ROW_HEIGHT)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = when (alignment) {
                HomeAlignment.LEFT -> Arrangement.Start
                HomeAlignment.CENTER -> Arrangement.Center
                HomeAlignment.RIGHT -> Arrangement.End
            },
        ) {
            val gap = if (LocalIconConfig.current.showIcons) 16.dp else 0.dp
            // fill = false so the label takes only the width it needs and the icon stays
            // beside it; a plain weight spans the row and pins the icon to the far edge.
            val labelModifier = Modifier.weight(1f, fill = false)
            val text: @Composable () -> Unit = {
                Text(
                    label,
                    // Dimmed, because a hidden app only ever turns up here through a search
                    // and nothing else on the row says it is one. A shortcut its publisher
                    // has switched off gets the same treatment, for the same reason: it is
                    // listed, but tapping it only explains why it will not open.
                    color = if (isHidden || app.disabled) contentColor.copy(alpha = 0.5f) else contentColor,
                    fontSize = labelSizeSp.sp,
                    modifier = labelModifier,
                    textAlign = when (alignment) {
                        HomeAlignment.LEFT -> TextAlign.Start
                        HomeAlignment.CENTER -> TextAlign.Center
                        HomeAlignment.RIGHT -> TextAlign.End
                    },
                )
            }
            // Centerd rows balance the icon with a spacer on the label's far side, so the
            // text lands on the screen's center line rather than the pair straddling it.
            val showIcons = LocalIconConfig.current.showIcons
            val balance: @Composable () -> Unit = {
                if (alignment == HomeAlignment.CENTER && showIcons) {
                    Spacer(Modifier.width(iconSizeDp.dp + gap))
                }
            }
            if (iconSide == IconSide.RIGHT) {
                balance()
                text()
                Spacer(Modifier.width(gap))
                AppIcon(app = app, sizeDp = iconSizeDp)
            } else {
                AppIcon(app = app, sizeDp = iconSizeDp)
                Spacer(Modifier.width(gap))
                text()
                balance()
            }
        }

        AppShortcutMenu(app, shortcutMenu, labelSizeSp) { shortcutMenu = false }

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
            // Only an installed app has a settings screen to open; a row that stands for
            // something else — a shortcut, the private space — would send the system looking
            // for a package that is not there.
            if (app.kind == EntryKind.APP) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_app_info)) },
                    leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    onClick = { onDismissMenu(); onAppInfo() },
                )
            }
            // Hands the shortcut back to the app that pinned it, which is the only way one
            // ever leaves: nothing uninstalls a shortcut.
            if (app.kind == EntryKind.SHORTCUT) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_remove_shortcut)) },
                    leadingIcon = { Icon(Icons.Filled.LinkOff, contentDescription = null) },
                    onClick = { onDismissMenu(); onUnpin() },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_move_to_folder)) },
                leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                onClick = { onDismissMenu(); onMoveToFolder() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (isHidden) R.string.applist_unhide else R.string.applist_hide
                        )
                    )
                },
                leadingIcon = {
                    Icon(
                        if (isHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = null,
                    )
                },
                onClick = { onDismissMenu(); onHide() },
            )
        }
    }
}

/** The app list's own search box, styled against the wallpaper rather than a surface. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    contentColor: Color,
    activeSide: EdgeSide,
    showAlphabet: Boolean,
    atBottom: Boolean = false,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.applist_search), color = contentColor.copy(alpha = 0.6f)) },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, tint = contentColor.copy(alpha = 0.7f))
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.icon_picker_clear_search),
                        tint = contentColor.copy(alpha = 0.7f),
                    )
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = contentColor,
            unfocusedTextColor = contentColor,
            cursorColor = contentColor,
            focusedBorderColor = contentColor.copy(alpha = 0.5f),
            unfocusedBorderColor = contentColor.copy(alpha = 0.3f),
            // No fill of its own. A fixed black tint read as a separate panel laid over the
            // overlay, and how separate depended on the wallpaper dim underneath it: barely
            // visible undimmed, a slab of black at full dim. Transparent composites to
            // exactly the overlay's own background at every dim, so the outline and the icons
            // are what say this is a field.
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
        modifier = modifier
            // The overlay draws under both system bars, so without this the field sits behind
            // the clock at the top, or the gesture pill at the bottom.
            .then(
                if (atBottom) {
                    // The column's own ime padding has already cleared the keyboard, so
                    // adding the gesture bar on top of it would double the gap.
                    Modifier.windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime))
                } else {
                    // The status bar is the column's problem now, so that the rows clear it too.
                    Modifier
                }
            )
            .fillMaxWidth()
            .padding(
                // Lines up with the rows' own inset instead of hugging the screen edge, and
                // clears the A-Z strip on whichever side it occupies.
                start = (if (showAlphabet && activeSide == EdgeSide.LEFT) STRIP_INSET else 0.dp) + 20.dp,
                end = (if (showAlphabet && activeSide == EdgeSide.RIGHT) STRIP_INSET else 0.dp) + 20.dp,
                top = if (atBottom) 8.dp else 12.dp,
                bottom = if (atBottom) 12.dp else 8.dp,
            ),
    )
}
