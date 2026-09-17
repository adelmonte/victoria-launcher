// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import android.graphics.Rect
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.victorialauncher.TypedKey
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.EntryKind
import dev.victorialauncher.data.restoreConcealed
import dev.victorialauncher.data.AzStripVisibility
import dev.victorialauncher.data.EdgeSide
import dev.victorialauncher.data.HomeAlignment
import dev.victorialauncher.data.IconSide
import dev.victorialauncher.data.Folder
import dev.victorialauncher.data.HomePaddings
import dev.victorialauncher.data.QuickLaunchSlot
import dev.victorialauncher.data.PaddingSlot
import dev.victorialauncher.data.folderToken
import dev.victorialauncher.media.NowPlayingBus
import dev.victorialauncher.media.isListenerEnabled
import dev.victorialauncher.service.SystemUi
import dev.victorialauncher.ui.applist.AppListModel
import dev.victorialauncher.ui.applist.AppListScreen
import dev.victorialauncher.ui.applist.BandEditOverlay
import dev.victorialauncher.ui.applist.EdgeScrubber
import dev.victorialauncher.ui.applist.EdgeTouchZone
import dev.victorialauncher.ui.applist.ScrubBand
import dev.victorialauncher.ui.applist.ScrubState
import dev.victorialauncher.ui.applist.buildAppListModel
import dev.victorialauncher.ui.common.FolderPickerDialog
import dev.victorialauncher.widget.WidgetSlotActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Immutable
import android.content.res.Configuration
import android.net.Uri
import dev.victorialauncher.R

/** Width of the invisible strip at each screen edge that opens the app list. */
/** Android only honors this much gesture exclusion per side, so spend it on the strip. */
private const val GESTURE_EXCLUSION_CAP_DP = 200

/** How long a launch is given to take us off screen before the overlay closes itself. */
private const val LAUNCH_CLOSE_TIMEOUT_MS = 2000L

/** Long enough to read as a settle, short enough not to stand between you and the icons. */
private const val HOME_FADE_MS = 220

/**
 * How far a swipe up carries the app list in. The same distance a pull collapses it over, so
 * opening is literally the closing animation run backwards.
 */
private val SWIPE_OPEN_DISTANCE = 150.dp

/**
 * The home destination: the home screen itself, the app-list overlay layered over it, and the
 * edge zones that move between them.
 *
 * The overlay is kept composed and measured even while hidden — just never placed — because
 * building the whole list from scratch on every open is what made it take a beat to appear.
 */
@Composable
fun HomeRoute(
    app: VictoriaApp,
    homeIntentTick: Int,
    typedToSearch: List<TypedKey>,
    onTypedToSearchHandled: (List<TypedKey>) -> Unit,
    settings: HomeSettings,
    homePaddings: HomePaddings,
    favorites: List<FavoriteEntry>,
    appsByKey: Map<String, AppInfo>,
    folders: List<Folder>,
    favoriteKeys: List<String>,
    hiddenApps: Set<String>,
    nameOverrides: Map<String, String>,
    widgetIds: List<Int>,
    widgetPosition: Int,
    widgetHeightDp: Int,
    widgetActions: WidgetSlotActions,
    launchCounts: Map<String, Int>,
    showWelcome: Boolean,
    onWelcomeDismissed: () -> Unit,
    scrubBandFractions: Pair<Float, Float>?,
    onSetScrubBand: (Float, Float) -> Unit,
    onClearScrubBand: () -> Unit,
    onPeekStatusBar: () -> Unit,
    onAppListVisibleChange: (Boolean) -> Unit,
    /** Locks an open private space or asks for a locked one to be opened, off this thread. */
    onTogglePrivateSpace: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Hidden apps are left out of the model the list draws from, so reaching them by name
    // needs a model of its own. Only built when the setting asks for it, and it falls back to
    // the list's own model otherwise, so nothing is grouped and sorted twice for nothing.
    val includeHiddenInSearch = settings.appListSearchHidden && hiddenApps.isNotEmpty()

    // Grouping, sorting and flattening every installed app is too much to do in composition,
    // and it re-runs whenever a name override changes.
    val listModel by produceState(
        initialValue = AppListModel(emptyList(), emptyList()),
        appsByKey,
        hiddenApps,
        nameOverrides,
        // Empty unless the sort is on, so an ordinary launch doesn't rebuild the whole list.
        if (settings.sortByUsage) launchCounts else emptyMap(),
    ) {
        val apps = appsByKey.values.toList()
        val counts = if (settings.sortByUsage) launchCounts else emptyMap()
        value = withContext(Dispatchers.Default) {
            buildAppListModel(
                apps,
                hiddenApps,
                { nameOverrides[it.key] ?: it.label },
                counts,
                englishName = { app.appRepository.englishLabel(it) },
            )
        }
    }

    val hiddenSearchModel by produceState<AppListModel?>(
        initialValue = null,
        appsByKey,
        nameOverrides,
        includeHiddenInSearch,
        if (settings.sortByUsage) launchCounts else emptyMap(),
    ) {
        val apps = appsByKey.values.toList()
        val counts = if (settings.sortByUsage) launchCounts else emptyMap()
        value = if (!includeHiddenInSearch) {
            null
        } else {
            withContext(Dispatchers.Default) {
                buildAppListModel(
                    apps,
                    emptySet(),
                    { nameOverrides[it.key] ?: it.label },
                    counts,
                    englishName = { app.appRepository.englishLabel(it) },
                )
            }
        }
    }
    val searchModel = hiddenSearchModel ?: listModel

    var appListVisible by remember { mutableStateOf(false) }
    val scrub = remember { ScrubState() }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var favBand by remember { mutableStateOf<ScrubBand?>(null) }
    var homeEditMode by remember { mutableStateOf(false) }
    var folderPickerFor by remember { mutableStateOf<AppInfo?>(null) }

    val nowPlaying by NowPlayingBus.state.collectAsState()
    val listenerGranted = remember(homeIntentTick) { isListenerEnabled(context) }
    // Don't reserve the block (or its padding) unless there is something to render:
    // no live session means the whole thing collapses, padding included.
    val nowPlayingHasContent = settings.nowPlayingEnabled && (!listenerGranted || nowPlaying != null)
    // While the band is being edited the live value wins; otherwise a range the user set by
    // hand wins over the measured favorites, which is what makes it stop following them.
    var bandEditMode by remember { mutableStateOf(false) }
    val homeContentAlpha by animateFloatAsState(
        if (bandEditMode) 0f else 1f,
        label = "homeContentAlpha",
    )
    // Edit mode borrows the app list's dim. Its controls are small text over whatever the
    // wallpaper happens to be, and on a busy one they were barely readable — the home screen's
    // own dim is usually off, since nothing normally sits there needing to be read.
    val homeDim =
        if (homeEditMode) maxOf(settings.dimHomeAlpha, settings.dimWallpaperAlpha) else settings.dimHomeAlpha
    var liveBand by remember { mutableStateOf<ScrubBand?>(null) }
    // Clamped on the way back in as well as on the way out: a range stored from a bad
    // measurement would otherwise put the strip off screen for good, with no gesture left to
    // reach it and fix it.
    val storedBand = scrubBandFractions
        ?.takeIf { (top, height) -> top.isFinite() && height.isFinite() && height > 0f }
        ?.let { (top, height) ->
            val bandHeight = (viewportHeightPx * height).coerceIn(0f, viewportHeightPx.toFloat())
            val bandTop = (viewportHeightPx * top)
                .coerceIn(0f, (viewportHeightPx - bandHeight).coerceAtLeast(0f))
            ScrubBand(topPx = bandTop, heightPx = bandHeight)
        }
    val band = liveBand ?: storedBand ?: favBand ?: ScrubBand.fallbackFor(viewportHeightPx)

    /** Locking is a strip gesture now, so the toast that explains it lives with the strip. */
    fun lockOrExplain() {
        if (!SystemUi.lockScreen()) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_enable_accessibility_lock),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // How far in the overlay is: 0 sitting off the bottom, the full distance fully open. The
    // edge zones jump it straight to open, a swipe drags it there by hand.
    val openDistancePx = with(LocalDensity.current) { SWIPE_OPEN_DISTANCE.toPx() }
    val openAnim = remember { Animatable(0f) }
    val appListState = rememberLazyListState()
    // Set once a swipe has carried past fully open and started scrolling the list, so the
    // flick that ends it can be handed on rather than stopping dead.
    var swipeScrolledList by remember { mutableStateOf(false) }
    val flingDecay = rememberSplineBasedDecay<Float>()

    // Set while a launched app is expected to take over the screen; see closeAfterLaunch.
    var launchClose by remember { mutableStateOf<Job?>(null) }

    // The home screen fades back in when we put the list away ourselves, but not when the
    // system did it for us: laying our own fade over Android's home transition — or running it
    // on resume for a close that happened while we were backgrounded — just reads as noise.
    var snapHome by remember { mutableStateOf(false) }

    // Hoisted so closing the list clears it; the overlay stays composed while hidden, so a
    // query left behind would still be filtering the next time it opened.
    var appListQuery by remember { mutableStateOf("") }

    // A keystroke on the home screen opens the list already searching for it. Only when there
    // is a search field to type into: without one the query filters a list showing no sign of
    // why, and there would be nothing on screen to clear it with.
    LaunchedEffect(typedToSearch) {
        if (typedToSearch.isEmpty()) return@LaunchedEffect
        val taken = typedToSearch
        onTypedToSearchHandled(taken)
        if (!settings.appListSearch || homeEditMode || bandEditMode) return@LaunchedEffect
        if (!appListVisible) {
            appListVisible = true
            openAnim.snapTo(openDistancePx)
        }
        appListQuery = taken.fold(appListQuery) { text, key ->
            if (key.char == null) text.dropLast(1) else text + key.char
        }
    }

    fun closeAppList(snap: Boolean = false) {
        appListQuery = ""
        scope.launch { openAnim.snapTo(0f) }
        launchClose?.cancel()
        launchClose = null
        snapHome = snap
        appListVisible = false
        scrub.cancel()
    }

    // Closing the overlay the moment an app is launched puts the home screen on screen for
    // the few frames before that app's window arrives, which reads as a jolt back to home.
    // Leave the list up and let it go when we are actually backgrounded instead: by then
    // nothing of ours is visible, so the close costs nothing and can snap.
    fun closeAfterLaunch() {
        launchClose?.cancel()
        launchClose = scope.launch {
            // Nothing ever came to the foreground. Rather than leave the list stuck open,
            // put it away the ordinary animated way.
            delay(LAUNCH_CLOSE_TIMEOUT_MS)
            launchClose = null
            closeAppList()
        }
    }

    // The one place a row press is turned into something happening. Every row in the launcher
    // arrives here, so a row that is not an app is recognised once, here, rather than in each
    // of the four places a press is wired up.
    fun launchEntry(entry: AppInfo): Boolean = when (entry.kind) {
        // Handed off rather than done here: locking or unlocking is several binder calls and
        // possibly the system's own authentication screen, which is not a press's work to do
        // on the main thread. True because the press was acted on — there is simply nothing
        // coming to the foreground to wait for.
        EntryKind.PRIVATE_SPACE -> {
            onTogglePrivateSpace()
            true
        }
        else -> app.appRepository.launch(entry)
    }

    LaunchedEffect(settings.edgeSide) { scrub.syncRestingSide(settings.edgeSide) }

    // BACK on the home screen must do nothing whatsoever.
    //
    // Without this it reaches the activity and finishes it, which is the default. A home
    // activity that finishes leaves the system to show whatever home-ish task is underneath
    // — and when the default launcher was switched *to* Victoria, the previous launcher's
    // task is still sitting there, so it comes back to the front. That looks like Victoria
    // handing control over, but it is simply this one exiting. A reboot clears the old task,
    // which is why it seemed to come and go, and why killing that launcher's process drops
    // to the system one instead.
    //
    // Registered before the handlers below so it stays the lowest priority: the dispatcher
    // runs the most recently added enabled callback first, so the overlay, edit mode and the
    // band editor all still get their turn at BACK before this swallows it.
    BackHandler(enabled = true) {}

    LaunchedEffect(appListVisible) { onAppListVisibleChange(appListVisible) }
    // Leaving the home destination entirely takes the overlay with it.
    DisposableEffect(Unit) { onDispose { onAppListVisibleChange(false) } }

    BackHandler(enabled = appListVisible) { closeAppList() }

    // Every stepper commits as it is tapped, so there is nothing to save on the way out —
    // but leaving edit mode had to be done through the Done button, and BACK simply escaped
    // to the system and left the home screen stuck in it.
    BackHandler(enabled = homeEditMode) { homeEditMode = false }

    fun commitBand() {
        liveBand?.let { edited ->
            if (viewportHeightPx > 0) {
                onSetScrubBand(edited.topPx / viewportHeightPx, edited.heightPx / viewportHeightPx)
            }
        }
        liveBand = null
        bandEditMode = false
    }

    BackHandler(enabled = bandEditMode) { commitBand() }

    // Leaving the launcher (screen off, another app) should always drop us back to the home
    // screen rather than reopening onto the overlay.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) closeAppList(snap = true)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(homeIntentTick) {
        if (homeIntentTick > 0) closeAppList(snap = true)
    }

    LaunchedEffect(settings.edgeSide, settings.edgeZoneWidthDp, view, band) {
        view.post {
            val density = view.resources.displayMetrics.density
            val widthPx = (settings.edgeZoneWidthDp * density).toInt()
            val h = view.height
            val w = view.width
            if (h > 0 && w > 0) {
                // Android's back-gesture claims the outer edges, and only honors 200dp of
                // exclusion per side — so spend it on the scrub band rather than spreading it
                // uselessly over the whole screen height.
                val capPx = (GESTURE_EXCLUSION_CAP_DP * density).toInt()
                val top = band.topPx.toInt().coerceIn(0, h)
                val bottom = band.bottomPx.toInt().coerceIn(top, h)
                // The band is usually taller than the cap, and whatever is left over stays
                // the system's: a touch there waits on the back-gesture detector before it
                // reaches us, which is the delay at the very edge. Centering what we are
                // given splits the unprotected remainder between the two ends rather than
                // leaving all of it below the halfway mark.
                val height = bottom - top
                val exTop = if (height > capPx) top + (height - capPx) / 2 else top
                val exBottom = if (height > capPx) exTop + capPx else bottom
                val rects = buildList {
                    if (settings.edgeSide != EdgeSide.RIGHT) add(Rect(0, exTop, widthPx, exBottom))
                    if (settings.edgeSide != EdgeSide.LEFT) add(Rect(w - widthPx, exTop, w, exBottom))
                }
                ViewCompat.setSystemGestureExclusionRects(view, rects)
            }
        }
    }
    DisposableEffect(view) {
        // Settings and the other screens must get the system back-gesture back.
        onDispose { ViewCompat.setSystemGestureExclusionRects(view, emptyList()) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportHeightPx = it.height },
    ) {
        // Hidden entirely while the list is up, so only the wallpaper sits behind it — and
        // kept composed the same way, so coming back is instant.
        // Opening answers a finger on the edge and must not lag behind it, so it snaps. Coming
        // back gets a short decelerating fade: the wallpaper is already there, so this is only
        // the icons settling in, and cutting them in on a single frame is what read as a jolt.
        val homeAlpha by animateFloatAsState(
            targetValue = if (appListVisible) 0f else 1f,
            animationSpec = if (appListVisible || snapHome) {
                snap()
            } else {
                tween(HOME_FADE_MS, easing = LinearOutSlowInEasing)
            },
            label = "homeAlpha",
        )

        // One dim for the wallpaper, belonging to neither screen and drawn under both.
        //
        // Each screen used to carry its own, which meant a handover rather than a change: on
        // the way back the list's faded out with the list while the home screen's was still
        // faded in, so a home dim of 80% and a list dim of 25% went 25 to nothing to 80. One
        // layer moving between the two values has nothing to hand over.
        val wallpaperDim by animateFloatAsState(
            targetValue = when {
                // The range is set against the wallpaper itself.
                bandEditMode -> 0f
                appListVisible -> settings.dimWallpaperAlpha
                else -> homeDim
            },
            animationSpec = if (appListVisible || snapHome) {
                snap()
            } else {
                tween(HOME_FADE_MS, easing = LinearOutSlowInEasing)
            },
            label = "wallpaperDim",
        )
        if (wallpaperDim > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(settings.dimColor).copy(alpha = wallpaperDim)),
            )
        }
        Box(
            modifier = Modifier
                .graphicsLayer { alpha = homeAlpha }
                .then(
                    // Hidden, but still laid out: an AppWidgetHostView that is never placed
                    // loses its layout and comes back with its text collapsed.
                    if (appListVisible) {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    event.changes.forEach { it.consume() }
                                    if (event.changes.none { it.pressed }) break
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                )
                // Setting the range takes the home screen out of the picture entirely, edit
                // layout's own controls with it. The band is judged against the wallpaper it
                // will sit on, not against the chrome it happens to overlap.
                .graphicsLayer { alpha = homeContentAlpha },
        ) {
            HomeScreen(
                favorites = favorites,
                nameOverrides = nameOverrides,
                iconSizeDp = settings.iconSizeDp,
                labelSizeSp = settings.labelSizeSp,
                itemSpacingDp = settings.itemSpacingDp,
                sidePaddingDp = settings.sidePaddingDp,
                widgetSidePaddingDp = settings.widgetSidePaddingDp,
                widgetOffsetXDp = settings.widgetOffsetXDp,
                onSetSidePadding = { scope.launch { app.prefs.setSidePaddingDp(it) } },
                onSetWidgetSidePadding = { scope.launch { app.prefs.setWidgetSidePaddingDp(it) } },
                onSetWidgetOffsetX = { scope.launch { app.prefs.setWidgetOffsetXDp(it) } },
                paddings = homePaddings,
                widgetIds = widgetIds,
                widgetPosition = widgetPosition,
                widgetHeightDp = widgetHeightDp,
                hapticsEnabled = settings.hapticsEnabled,
                nowPlayingEnabled = settings.nowPlayingEnabled,
                nowPlayingHeightDp = settings.nowPlayingHeightDp,
                onResizeNowPlaying = { scope.launch { app.prefs.setNowPlayingHeightDp(it) } },
                widgetActions = widgetActions,
                onLaunch = { launchEntry(it) },
                onRemoveFavorite = { scope.launch { app.prefs.removeFavorite(it.key) } },
                onOpenFolderApp = { launchEntry(it) },
                onRenameFolder = { folder, name ->
                    scope.launch { app.prefs.upsertFolder(folder.copy(name = name)) }
                },
                onDeleteFolder = { folder -> scope.launch { app.prefs.deleteFolder(folder.id) } },
                onChangeFolderIcon = { folder -> onNavigate(iconPickerRoute(folderToken(folder.id))) },
                onResetFolderIcon = { folder -> scope.launch { app.prefs.setFolderIcon(folder.id, null) } },
                onManageFolder = { folder -> onNavigate("folder/" + folder.id) },
                onRemoveFromFolder = { folder, appInfo ->
                    scope.launch { app.prefs.removeAppFromFolder(folder.id, appInfo.key) }
                },
                appsByKey = appsByKey,
                onMoveToFolder = { folderPickerFor = it },
                onReorderHome = { newFavKeys, newWidgetPos ->
                    scope.launch {
                        // The home screen draws only the favorites that resolve to a row, and
                        // this hands back the order of what it drew. Written as it stands, that
                        // would replace the stored list and delete every favorite that drew
                        // nothing — which is exactly what a locked private space's favorites
                        // do. Whatever drew no row goes back where it was.
                        val drawn = newFavKeys.toSet()
                        app.prefs.setFavorites(restoreConcealed(favoriteKeys, newFavKeys) { it !in drawn })
                        app.prefs.setWidgetPosition(newWidgetPos)
                    }
                },
                onCommitPadding = { slot: PaddingSlot, value: Int ->
                    scope.launch { app.prefs.setHomePadding(slot, value) }
                },
                onFavoritesBoundsChanged = { top, bottom ->
                    // Measured in window space, so a home screen still travelling under the
                    // open overlay would drag the alphabet along with it. Whatever the band
                    // was when the list opened is what it stays.
                    //
                    // Edit mode is ignored for the same reason and a better one: it lays the
                    // favorites out differently, and the strip should match where they
                    // actually sit rather than where they sit while being rearranged.
                    if (appListVisible || homeEditMode) return@HomeScreen
                    favBand = ScrubBand(topPx = top, heightPx = bottom - top)
                },
                nowPlayingHasContent = nowPlayingHasContent,
                contentColor = settings.contentColor,
                showFavoriteLabels = settings.showFavoriteLabels,
                alignment = settings.alignment,
                iconSide = settings.iconSide,
                editMode = homeEditMode,
                onEditModeChange = { homeEditMode = it },
                onEditScrubBand = { liveBand = band; bandEditMode = true },
                centerFavorites = settings.centerFavorites,
                swipeUpOpensAppList = settings.swipeUpOpensAppList,
                onSwipeUpDrag = { total, delta ->
                    appListVisible = true
                    scope.launch { openAnim.snapTo(total.coerceAtMost(openDistancePx)) }
                    // Once it is all the way in the finger is usually still moving, so the
                    // rest of the drag goes to the list rather than stopping dead against it.
                    if (total > openDistancePx) {
                        swipeScrolledList = true
                        appListState.dispatchRawDelta(delta)
                    }
                },
                onSwipeUpEnd = { velocity ->
                    scope.launch {
                        val settled = openAnim.value > openDistancePx * 0.4f || velocity < -1200f
                        if (settled) {
                            openAnim.animateTo(
                                targetValue = openDistancePx,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                        } else {
                            openAnim.animateTo(0f, tween(160, easing = FastOutLinearInEasing))
                            closeAppList()
                        }
                    }
                    // The scrolling half of this gesture was fed the drag raw, which carries
                    // no momentum of its own — so a flick that starts after the list is
                    // already moving has to have its velocity handed over explicitly, or the
                    // list stops the instant the finger leaves.
                    if (swipeScrolledList && velocity < 0f) {
                        scope.launch {
                            appListState.scroll {
                                var travelled = 0f
                                AnimationState(initialValue = 0f, initialVelocity = -velocity)
                                    .animateDecay(flingDecay) {
                                        scrollBy(value - travelled)
                                        travelled = value
                                    }
                            }
                        }
                    }
                    swipeScrolledList = false
                },
                quickLaunchEnabled = settings.quickLaunchLeft != null || settings.quickLaunchRight != null,
                onQuickLaunch = { slot ->
                    val target = when (slot) {
                        QuickLaunchSlot.LEFT -> settings.quickLaunchLeft
                        QuickLaunchSlot.RIGHT -> settings.quickLaunchRight
                    }
                    target?.let { launchEntry(it) }
                },
                onPeekStatusBar = onPeekStatusBar,
                onExpandShade = {
                    // Say so rather than failing silently — that way a pull that does nothing
                    // tells you whether the gesture or the permission is at fault.
                    if (!SystemUi.expandNotificationShade(context)) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_enable_accessibility_shade),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onManageFavorites = { onNavigate("favorites") },
                onSetName = { appInfo, name -> scope.launch { app.prefs.setNameOverride(appInfo.key, name) } },
                onChangeIcon = { appInfo -> onNavigate(iconPickerRoute(appInfo.key)) },
                onAppInfo = { app.appRepository.openAppInfo(it) },
                onOpenSettings = { onNavigate("settings") },
            )
        }

        // Kept composed and measured even while hidden, just never placed. Not placing it
        // means it neither draws nor receives touches.
        val overlayAlpha = if (appListVisible) 1f else 0f
        Box(
            modifier = Modifier
                .graphicsLayer { alpha = overlayAlpha }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    if (appListVisible) {
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    } else {
                        layout(0, 0) {}
                    }
                },
        ) {
            AppListScreen(
                model = listModel,
                nameOverrides = nameOverrides,
                scrub = scrub,
                iconSizeDp = settings.iconSizeDp,
                labelSizeSp = settings.labelSizeSp,
                band = band,
                viewportHeightPx = viewportHeightPx,
                visible = appListVisible,
                favoriteKeys = remember(favoriteKeys) { favoriteKeys.toSet() },
                onLaunch = { appInfo ->
                    // A launch that never got off the ground leaves nothing to wait for, and
                    // the private-space row has nothing coming to the foreground either — it
                    // changes what this very list holds, so it wants the list out of the way.
                    val acted = launchEntry(appInfo)
                    if (acted && appInfo.kind == EntryKind.APP) closeAfterLaunch() else closeAppList()
                },
                onSetFavorite = { appInfo, add ->
                    scope.launch {
                        if (add) app.prefs.addFavorite(appInfo.key) else app.prefs.removeFavorite(appInfo.key)
                    }
                },
                onSetName = { appInfo, name -> scope.launch { app.prefs.setNameOverride(appInfo.key, name) } },
                onChangeIcon = { appInfo ->
                    closeAppList()
                    onNavigate(iconPickerRoute(appInfo.key))
                },
                onAppInfo = { app.appRepository.openAppInfo(it) },
                onHideApp = { appInfo, hide -> scope.launch { app.prefs.setHidden(appInfo.key, hide) } },
                hiddenApps = hiddenApps,
                onMoveToFolder = { appInfo -> closeAppList(); folderPickerFor = appInfo },
                onOpenSettings = { closeAppList(); onNavigate("settings") },
                onDismiss = { closeAppList() },
                contentColor = settings.contentColor,
                showAlphabet = settings.showAlphabet,
                edgeSide = settings.edgeSide,
                statusBarHidden = settings.hideStatusBarAppList,
                searchModel = searchModel,
                searchEnabled = settings.appListSearch,
                searchAtBottom = settings.appListSearchBottom,
                listState = appListState,
                // Distance still to travel, which is exactly what the collapse transform
                // takes: the list arrives scaled down and faded, and grows into place.
                enterPullPx = openDistancePx - openAnim.value,
                query = appListQuery,
                onQueryChange = { appListQuery = it },
                alignment = settings.appListAlignment,
                iconSide = settings.iconSide,
            )
        }

        // The band editor draws no letters of its own: the strip is what shows the range
        // being dragged, so it is given whether or not the always-on setting asked for it.
        // Edit layout is the other way round — its own controls sit where the strip does, and
        // the two were drawn on top of each other.
        // Sideways the strip is worth the room it takes; upright the same setting can be too
        // much, so a phone in a car mount can have it there without carrying it everywhere.
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val showIdleStrip = when {
            appListVisible -> false
            bandEditMode -> true
            homeEditMode -> false
            else -> when (settings.azStripVisibility) {
                AzStripVisibility.NEVER -> false
                AzStripVisibility.ALWAYS -> true
                AzStripVisibility.LANDSCAPE -> landscape
            }
        }
        if (showIdleStrip) {
            EdgeScrubber(
                letters = listModel.letters,
                scrubY = { null },
                pullPx = { 0f },
                band = band,
                side = scrub.side,
                modifier = Modifier.align(
                    if (scrub.side == EdgeSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd
                ),
            )
        }

        if (showWelcome) {
            WelcomeDialog(onDismiss = onWelcomeDismissed)
        }

        folderPickerFor?.let { target ->
            FolderPickerDialog(
                appLabel = nameOverrides[target.key] ?: target.label,
                folders = folders,
                // The rows this launcher can actually draw, which is what the home screen's
                // own badge counts. A folder's stored keys include whatever a locked private
                // space is hiding.
                memberCount = { folder -> folder.apps.count { it in appsByKey } },
                onPickFolder = { folder ->
                    scope.launch { app.prefs.addAppToFolder(folder.id, target.key) }
                    folderPickerFor = null
                },
                onCreateFolder = { name ->
                    scope.launch {
                        val folder = Folder(
                            id = System.currentTimeMillis().toString(36),
                            name = name,
                            apps = listOf(target.key),
                        )
                        app.prefs.upsertFolder(folder)
                        // Drop the app's own row and give the folder one in its place.
                        app.prefs.removeFavorite(target.key)
                        app.prefs.addFavorite(folderToken(folder.id))
                    }
                    folderPickerFor = null
                },
                onDismiss = { folderPickerFor = null },
            )
        }

        if (bandEditMode) {
            BandEditOverlay(
                band = liveBand ?: band,
                side = scrub.side,
                viewportHeightPx = viewportHeightPx,
                contentColor = settings.contentColor,
                onBandChange = { liveBand = it },
                // Stays open rather than exiting: dropping the stored range hands the
                // strip back to the favorites, and the point of a reset is watching it land
                // there. Done then leaves without storing anything, since there is nothing
                // being edited any more.
                onReset = { liveBand = null; onClearScrubBand() },
                onDone = { commitBand() },
            )
        }

        // Edge zones sit on top of everything, so one unbroken touch opens the list and then
        // scrubs it as the finger moves.
        if (!homeEditMode && !bandEditMode && appListQuery.isEmpty()) {
            val sides = remember(settings.edgeSide) {
                when (settings.edgeSide) {
                    EdgeSide.LEFT -> listOf(EdgeSide.LEFT)
                    EdgeSide.RIGHT -> listOf(EdgeSide.RIGHT)
                    EdgeSide.BOTH -> listOf(EdgeSide.LEFT, EdgeSide.RIGHT)
                }
            }
            sides.forEach { side ->
                EdgeTouchZone(
                    side = side,
                    widthDp = settings.edgeZoneWidthDp.dp,
                    letters = listModel.letters,
                    band = band,
                    hapticsEnabled = settings.hapticsEnabled,
                    state = scrub,
                    listOpen = appListVisible,
                    onDismiss = { closeAppList() },
                    onOpen = {
                        appListVisible = true
                        // Opened by touching the edge, so there is nothing to animate in.
                        scope.launch { openAnim.snapTo(openDistancePx) }
                    },
                    onDoubleTap = if (settings.doubleTapToLock) ({ lockOrExplain() }) else null,
                    modifier = Modifier.align(
                        if (side == EdgeSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd
                    ),
                )
            }
        }
    }
}

private fun iconPickerRoute(key: String) = "iconpicker/" + Uri.encode(key)

/** Display and behavior settings the home destination reads, grouped so they travel as one. */
@Immutable
data class HomeSettings(
    val iconSizeDp: Int,
    val labelSizeSp: Int,
    val itemSpacingDp: Int,
    val sidePaddingDp: Int,
    val nowPlayingHeightDp: Int,
    val nowPlayingEnabled: Boolean,
    val edgeSide: EdgeSide,
    val azStripVisibility: AzStripVisibility,
    val showAlphabet: Boolean,
    val alignment: HomeAlignment,
    val appListAlignment: HomeAlignment,
    val iconSide: IconSide,
    val widgetSidePaddingDp: Int,
    val widgetOffsetXDp: Int,
    val edgeZoneWidthDp: Int,
    val swipeUpOpensAppList: Boolean,
    val appListSearch: Boolean,
    val appListSearchBottom: Boolean,
    val appListSearchHidden: Boolean,
    val hideStatusBarAppList: Boolean,
    val sortByUsage: Boolean,
    val quickLaunchLeft: AppInfo?,
    val quickLaunchRight: AppInfo?,
    /** Place the favorites by measurement, until the user sets a padding of their own. */
    val centerFavorites: Boolean,
    val dimWallpaperAlpha: Float,
    val dimHomeAlpha: Float,
    val dimColor: Int,
    val hapticsEnabled: Boolean,
    val showFavoriteLabels: Boolean,
    val doubleTapToLock: Boolean,
    val contentColor: Color,
)