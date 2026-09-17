// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui

import android.app.Activity.RESULT_OK
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.victorialauncher.TypedKey
import dev.victorialauncher.VictoriaApp
import android.widget.Toast
import dev.victorialauncher.data.IconShape
import dev.victorialauncher.data.AppFont
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.EntryKind
import dev.victorialauncher.data.PrivateSpace
import dev.victorialauncher.data.AzStripVisibility
import dev.victorialauncher.data.EdgeSide
import dev.victorialauncher.data.HomeAlignment
import dev.victorialauncher.data.IconSide
import dev.victorialauncher.data.HomePaddings
import dev.victorialauncher.data.QuickLaunchSlot
import dev.victorialauncher.data.TextColorMode
import androidx.compose.ui.res.stringResource
import dev.victorialauncher.R
import dev.victorialauncher.data.folderIdFromToken
import dev.victorialauncher.media.isListenerEnabled
import dev.victorialauncher.service.SystemUi
import dev.victorialauncher.ui.common.IconPickerScreen
import dev.victorialauncher.ui.common.IconStyle
import dev.victorialauncher.ui.common.LocalIconConfig
import dev.victorialauncher.ui.common.clearIconCache
import dev.victorialauncher.ui.common.encodePackOverride
import dev.victorialauncher.ui.common.warmIconCache
import dev.victorialauncher.ui.home.FavoriteEntry
import dev.victorialauncher.ui.home.HomeRoute
import dev.victorialauncher.ui.home.HomeSettings
import dev.victorialauncher.ui.settings.AppPickerScreen
import dev.victorialauncher.ui.settings.FolderAppsScreen
import dev.victorialauncher.ui.settings.HiddenAppsScreen
import dev.victorialauncher.ui.settings.ManageFavoritesScreen
import dev.victorialauncher.ui.settings.SettingsScreen
import dev.victorialauncher.ui.theme.rememberContentColor
import dev.victorialauncher.widget.WidgetPickerActivity
import dev.victorialauncher.widget.WidgetSlotActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Collects the stored settings once and hosts the navigation graph.
 *
 * Everything the destinations need is read here rather than in each screen, so the DataStore
 * is collected once per key instead of once per consumer.
 */
@Composable
fun VictoriaNavHost(
    app: VictoriaApp,
    homeIntentTick: Int,
    typedToSearch: List<TypedKey>,
    onTypedToSearchHandled: (List<TypedKey>) -> Unit,
    font: AppFont,
    hideStatusBar: Boolean,
    hideStatusBarAppList: Boolean,
    iconPackPackage: String?,
    iconOverrides: Map<String, String>,
    onPeekStatusBar: () -> Unit,
    /** Reported up so the status bar can stay put while the overlay is showing. */
    onAppListVisibleChange: (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Enumerating every installed app costs a PackageManager round trip per app; doing it in
    // the first composition is what stalled the cold start. Load it off the main thread and
    // let the home screen render against an empty list for the first frame.
    var appsAndSpace by remember { mutableStateOf(AppsAndSpace(PrivateSpace.Absent, emptyList())) }
    val allApps = appsAndSpace.apps
    // Kept beside the list because the settings screens need it for keys whose rows are
    // deliberately absent: a favorite inside a locked private space has nothing to look up.
    val privateSpace = appsAndSpace.privateSpace
    // Reloads are started from half a dozen independent places and overlap; this is what keeps
    // the most recently read answer the one on screen. See [ReloadOrder].
    val reloadOrder = remember { ReloadOrder() }

    /**
     * [known] is the state to enumerate against when the caller already has one it trusts more
     * than a fresh read would be — a lock it has just been granted, which the system has not
     * finished applying and would still describe as an open space.
     *
     * [pass] is the number this reload took from [reloadOrder] before it read anything.
     */
    suspend fun reloadApps(known: PrivateSpace?, pass: Long) {
        val (state, apps) = withContext(Dispatchers.Default) {
            // Resolved once and handed on, so the list and what the settings screens conceal
            // can never disagree about whether the space was open when it was read.
            val state = known ?: app.appRepository.privateSpace()
            state to app.appRepository.queryAllApps(state)
        }
        // Overtaken while it ran: this list was enumerated against a state that is no longer
        // the newest thing known about the device, and publishing it would put a locked
        // space's apps back under an open padlock. Nothing is lost by dropping it — the pass
        // that overtook this one publishes a list of its own.
        if (reloadOrder.mayPublish(pass)) appsAndSpace = AppsAndSpace(state, apps)
    }

    /**
     * Takes a state that has just been resolved and acts on what it conceals at once, on the
     * list already in hand, before the reload that will take a moment.
     *
     * The reload is a full enumeration: every profile, every activity, an icon cache thrown
     * away and rebuilt. That is long enough to read the names off a home screen, and a lock
     * that only takes effect at the end of it has left them there for exactly that long.
     *
     * Publishes nothing once [pass] has been superseded, for the same reason the reload does
     * not: this state was read before a newer one, and the direction a stale state goes wrong
     * in is the one that un-conceals.
     */
    fun adoptPrivateSpace(state: PrivateSpace, pass: Long) {
        if (!reloadOrder.mayPublish(pass)) return
        appsAndSpace = AppsAndSpace(
            state,
            appsAndSpace.apps.filterNot { it.kind == EntryKind.PRIVATE_SPACE || state.conceals(it.key) } +
                app.appRepository.privateSpaceRow(state),
        )
    }
    LaunchedEffect(Unit) { reloadApps(known = null, pass = reloadOrder.begin()) }

    // Before anything the user does can write to the store, so "the store is empty" still
    // means "this is a first run" when it is read.
    LaunchedEffect(Unit) { app.prefs.ensureInstallMarker() }

    DisposableEffect(Unit) {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        fun refresh() {
            scope.launch {
                // Taken before the read, so anything already enumerating is superseded by the
                // moment this pass looked rather than by the moment it finished.
                val pass = reloadOrder.begin()
                // The private space first and on its own: whatever it conceals leaves the list
                // that is on screen now, rather than when the enumeration below comes back.
                val state = withContext(Dispatchers.Default) { app.appRepository.privateSpace() }
                adoptPrivateSpace(state, pass)
                // An app that ships a new icon in an update changes none of the cache
                // key's components, so nothing else would invalidate the stale bitmap.
                clearIconCache()
                reloadApps(state, pass)
            }
        }

        // LauncherApps reports package changes across every profile. The PACKAGE_* broadcasts
        // only ever describe the profile we run in, so an app installed into a work profile or
        // a private space would never reach the list.
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = refresh()
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = refresh()
            override fun onPackageChanged(packageName: String?, user: UserHandle?) = refresh()
            override fun onPackagesAvailable(names: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
            override fun onPackagesUnavailable(names: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
        }
        runCatching { launcherApps.registerCallback(callback) }

        // Locking or unlocking a private space changes no package, so no package callback
        // ever fires for it — it arrives as one of these instead. Android 15 sends PROFILE_-
        // UNAVAILABLE then PROFILE_INACCESSIBLE on locking and the matching pair on
        // unlocking, including when the screen going off re-locks the space on its own.
        // Written as strings because the Intent constants are newer than this app's minimum.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction("android.intent.action.PROFILE_AVAILABLE")
            addAction("android.intent.action.PROFILE_UNAVAILABLE")
            addAction("android.intent.action.PROFILE_ACCESSIBLE")
            addAction("android.intent.action.PROFILE_INACCESSIBLE")
            addAction("android.intent.action.PROFILE_ADDED")
            addAction("android.intent.action.PROFILE_REMOVED")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = refresh()
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        onDispose {
            runCatching { launcherApps.unregisterCallback(callback) }
            context.unregisterReceiver(receiver)
        }
    }

    // A broadcast is the only other thing that ever says the space has locked, and it is one
    // thing: it is only heard while this is composed, the system re-locks the space by itself
    // whenever the screen goes off, and a lock that was missed stays missed until something
    // else happens to reload. Coming back to the launcher is the moment that matters, so the
    // state is read again there — a handful of binder calls, with the enumeration behind it
    // only when the answer actually changed.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_START) return@LifecycleEventObserver
            scope.launch {
                val state = withContext(Dispatchers.Default) { app.appRepository.privateSpace() }
                if (state == appsAndSpace.privateSpace) return@launch
                // Only once there is something to do with it: a number taken to decide nothing
                // changed would supersede a reload already running and leave the list as it was.
                val pass = reloadOrder.begin()
                adoptPrivateSpace(state, pass)
                clearIconCache()
                reloadApps(state, pass)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hiddenApps by app.prefs.hiddenApps.collectAsState(initial = emptySet())
    val favoriteKeys by app.prefs.favorites.collectAsState(initial = emptyList())
    val nameOverrides by app.prefs.nameOverrides.collectAsState(initial = emptyMap())
    val iconSizeDp by app.prefs.iconSizeDp.collectAsState(initial = 56)
    val labelSizeSp by app.prefs.labelSizeSp.collectAsState(initial = 16)
    val itemSpacingDp by app.prefs.itemSpacingDp.collectAsState(initial = 10)
    val sidePaddingDp by app.prefs.sidePaddingDp.collectAsState(initial = 20)
    val nowPlayingHeightDp by app.prefs.nowPlayingHeightDp.collectAsState(initial = 64)
    val homePaddings by app.prefs.homePaddings.collectAsState(initial = HomePaddings.Default)
    val edgeSide by app.prefs.edgeSide.collectAsState(initial = EdgeSide.RIGHT)
    val azStripVisibility by app.prefs.azStripVisibility.collectAsState(initial = AzStripVisibility.NEVER)
    val showAlphabet by app.prefs.showAlphabet.collectAsState(initial = true)
    val alignRight by app.prefs.alignRight.collectAsState(initial = false)
    val dimWallpaperAlpha by app.prefs.dimWallpaperAlpha.collectAsState(initial = 0.35f)
    val hapticsEnabled by app.prefs.hapticsEnabled.collectAsState(initial = true)
    val dimHomeAlpha by app.prefs.dimHomeAlpha.collectAsState(initial = 0f)
    val showFavoriteLabels by app.prefs.showFavoriteLabels.collectAsState(initial = true)
    val textColorMode by app.prefs.textColorMode.collectAsState(initial = TextColorMode.AUTO)
    val doubleTapToLock by app.prefs.doubleTapToLock.collectAsState(initial = false)
    val widgetId by app.prefs.widgetId.collectAsState(initial = -1)
    val widgetPosition by app.prefs.widgetPosition.collectAsState(initial = 0)
    val widgetHeightDp by app.prefs.widgetHeightDp.collectAsState(initial = 180)
    val nowPlayingEnabled by app.prefs.nowPlayingEnabled.collectAsState(initial = false)
    val folders by app.prefs.folders.collectAsState(initial = emptyList())
    val widgetIds by app.prefs.widgetIds.collectAsState(initial = emptyList())
    val widgetSidePaddingDp by app.prefs.widgetSidePaddingDp.collectAsState(initial = sidePaddingDp)
    val widgetOffsetXDp by app.prefs.widgetOffsetXDp.collectAsState(initial = 0)
    val swipeUpOpensList by app.prefs.swipeUpOpensList.collectAsState(initial = false)
    val appListSearchEnabled by app.prefs.appListSearchEnabled.collectAsState(initial = false)
    val appListSearchBottom by app.prefs.appListSearchBottom.collectAsState(initial = false)
    val appListSearchHidden by app.prefs.appListSearchHidden.collectAsState(initial = false)
    val sortByUsage by app.prefs.sortByUsage.collectAsState(initial = false)
    val launchCounts by app.prefs.launchCounts.collectAsState(initial = emptyMap())
    val edgeZoneWidthDp by app.prefs.edgeZoneWidthDp.collectAsState(initial = 56)
    val quickLaunchLeftKey by app.prefs.quickLaunchLeft.collectAsState(initial = null)
    val quickLaunchRightKey by app.prefs.quickLaunchRight.collectAsState(initial = null)
    val showAppIcons by app.prefs.showAppIcons.collectAsState(initial = true)
    val fontFile by app.prefs.fontFile.collectAsState(initial = null)
    val textColorCustom by app.prefs.textColorCustom.collectAsState(initial = 0xFFFFFFFF.toInt())
    val dimColor by app.prefs.dimColor.collectAsState(initial = 0xFF000000.toInt())
    val rotatesByDefault = LocalConfiguration.current.smallestScreenWidthDp >= 600
    val allowRotationPref by app.prefs.allowRotation.collectAsState(initial = null)
    val allowRotation = allowRotationPref ?: rotatesByDefault
    val iconShape by app.prefs.iconShape.collectAsState(initial = IconShape.SYSTEM)
    val themedIcons by app.prefs.themedIcons.collectAsState(initial = false)
    val alignment by app.prefs.alignment.collectAsState(initial = HomeAlignment.LEFT)
    val appListAlignment by app.prefs.appListAlignment.collectAsState(initial = HomeAlignment.LEFT)
    val iconSide by app.prefs.iconSide.collectAsState(initial = IconSide.LEFT)
    val statusBarPeekSeconds by app.prefs.statusBarPeekSeconds.collectAsState(initial = 5)
    val scrubBand by app.prefs.scrubBand.collectAsState(initial = null)
    val layoutDefaultsVersion by app.prefs.layoutDefaultsVersion.collectAsState(initial = null)
    val welcomeSeen by app.prefs.welcomeSeen.collectAsState(initial = true)
    val hasCustomLayout by app.prefs.hasCustomLayout.collectAsState(initial = true)
    val contentColor = rememberContentColor(textColorMode, textColorCustom)

    val appsByKey = remember(allApps) { allApps.associateBy { it.key } }
    // The number the hidden-apps screen itself arrives at, rather than the size of the stored
    // set: that screen lists rows, and a hidden app inside a locked private space has no row.
    // Counting the stored set in the subtitle says out loud how many apps are in there.
    val hiddenShownCount = remember(allApps, hiddenApps) { allApps.count { it.key in hiddenApps } }
    val foldersById = remember(folders) { folders.associateBy { it.id } }

    // A favorites row is an app or a folder; both come out of the same ordered token list.
    val favoriteEntries = remember(favoriteKeys, appsByKey, foldersById) {
        favoriteKeys.mapNotNull { token ->
            val folderId = folderIdFromToken(token)
            if (folderId != null) {
                foldersById[folderId]?.let { FavoriteEntry.FolderRef(it) }
            } else {
                appsByKey[token]?.let { FavoriteEntry.App(it) }
            }
        }
    }

    // Rasterise icons in the background so opening the A-Z list doesn't have to. Favorites and
    // folder members go first: they are what the home screen needs before anything else.
    val listIconPx = with(LocalDensity.current) { iconSizeDp.dp.roundToPx() }
    val priorityKeys = remember(favoriteKeys, folders) {
        favoriteKeys.toSet() + folders.flatMap { it.apps }
    }
    // Same style the rows will ask for, or the warm pass fills the cache under keys nothing
    // then looks up, and every icon is rasterised twice.
    val iconCfg = LocalIconConfig.current
    val scheme = MaterialTheme.colorScheme
    val iconStyle = remember(iconCfg, scheme) {
        IconStyle(
            shape = iconCfg.shape,
            themed = iconCfg.themed,
            background = scheme.primaryContainer.toArgb(),
            foreground = scheme.onPrimaryContainer.toArgb(),
        )
    }
    LaunchedEffect(allApps, iconPackPackage, iconOverrides, listIconPx, priorityKeys, iconStyle) {
        warmIconCache(context, allApps, iconPackPackage, iconOverrides, listIconPx, iconStyle, priorityKeys)
    }

    // Written through the document picker rather than to a path of our own: the file is the
    // user's to keep, and this way it lands wherever they keep things without the app asking
    // for storage it otherwise never needs.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // Both Locked and Unlocked have a real serial to strip; only Absent's is zero,
            // which is exportJson's own signal that there is nothing to leave out.
            val privateSerial = privateSpace.serial.takeIf { it != 0L }
            val omittedPrivateSpace = withContext(Dispatchers.IO) {
                runCatching {
                    val result = app.prefs.exportJson(privateSerial)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(result.json.toByteArray()) }
                        ?: return@runCatching null
                    result.omittedPrivateSpace
                }.getOrNull()
            }
            Toast.makeText(
                context,
                when (omittedPrivateSpace) {
                    null -> R.string.settings_backup_failed
                    true -> R.string.settings_export_done_private_omitted
                    false -> R.string.settings_export_done
                },
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            val ok = text != null && app.prefs.importJson(text)
            Toast.makeText(
                context,
                if (ok) R.string.settings_import_done else R.string.settings_backup_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val settings = HomeSettings(
        iconSizeDp = iconSizeDp,
        labelSizeSp = labelSizeSp,
        itemSpacingDp = itemSpacingDp,
        sidePaddingDp = sidePaddingDp,
        nowPlayingHeightDp = nowPlayingHeightDp,
        nowPlayingEnabled = nowPlayingEnabled,
        edgeSide = edgeSide,
        azStripVisibility = azStripVisibility,
        showAlphabet = showAlphabet,
        alignment = alignment,
        appListAlignment = appListAlignment,
        iconSide = iconSide,
        widgetSidePaddingDp = widgetSidePaddingDp,
        widgetOffsetXDp = widgetOffsetXDp,
        edgeZoneWidthDp = edgeZoneWidthDp,
        swipeUpOpensAppList = swipeUpOpensList,
        appListSearch = appListSearchEnabled,
        appListSearchBottom = appListSearchBottom,
        appListSearchHidden = appListSearchHidden,
        hideStatusBarAppList = hideStatusBarAppList,
        sortByUsage = sortByUsage,
        quickLaunchLeft = quickLaunchLeftKey?.let { appsByKey[it] },
        quickLaunchRight = quickLaunchRightKey?.let { appsByKey[it] },
        // Both flows start null/true so nothing is centered or offered before the stored
        // answer arrives; a legacy install is stamped 0 and never enters either path.
        centerFavorites = layoutDefaultsVersion == 1 && !hasCustomLayout,
        dimWallpaperAlpha = dimWallpaperAlpha,
        dimHomeAlpha = dimHomeAlpha,
        dimColor = dimColor,
        hapticsEnabled = hapticsEnabled,
        showFavoriteLabels = showFavoriteLabels,
        doubleTapToLock = doubleTapToLock,
        contentColor = contentColor,
    )

    var pendingIconTarget by remember { mutableStateOf<String?>(null) }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val target = pendingIconTarget
        if (uri != null && target != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val folderId = folderIdFromToken(target)
            scope.launch {
                if (folderId != null) {
                    app.prefs.setFolderIcon(folderId, uri.toString())
                } else {
                    app.prefs.setIconOverride(target, uri.toString())
                }
            }
        }
        pendingIconTarget = null
    }

    val widgetPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            // Appended rather than replacing: picking a widget now adds a page.
            if (id != -1) scope.launch { app.prefs.addWidgetId(id) }
        }
    }

    val widgetActions = remember {
        WidgetSlotActions(
            onAddWidget = { widgetPickerLauncher.launch(Intent(context, WidgetPickerActivity::class.java)) },
            onRemoveWidget = { id ->
                scope.launch {
                    // Releasing the host id matters: left allocated, its provider keeps
                    // broadcasting updates to a widget nobody can see.
                    if (id > 0) app.widgetHost.deleteAppWidgetId(id)
                    app.prefs.removeWidgetId(id)
                }
            },
            onWidgetSettings = { id ->
                val configure = AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.configure
                if (configure != null) {
                    val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                        component = configure
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    }
                    runCatching { context.startActivity(intent) }
                }
            },
            onAppInfo = { id ->
                AppWidgetManager.getInstance(context).getAppWidgetInfo(id)
                    ?.let { app.appRepository.openAppInfo(it.provider.packageName) }
            },
            onResize = { newHeight -> scope.launch { app.prefs.setWidgetHeightDp(newHeight) } },
            onOpenSettings = { navController.navigate("settings") },
        )
    }

    // HOME has to unwind the whole stack. The equivalent effect inside the home destination
    // can't do this: that destination isn't composed while Settings is on screen.
    LaunchedEffect(homeIntentTick) {
        if (homeIntentTick > 0 && navController.currentDestination?.route != "home") {
            navController.popBackStack("home", inclusive = false)
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeRoute(
                app = app,
                homeIntentTick = homeIntentTick,
                typedToSearch = typedToSearch,
                onTypedToSearchHandled = onTypedToSearchHandled,
                settings = settings,
                homePaddings = homePaddings,
                favorites = favoriteEntries,
                appsByKey = appsByKey,
                folders = folders,
                favoriteKeys = favoriteKeys,
                hiddenApps = hiddenApps,
                nameOverrides = nameOverrides,
                widgetIds = widgetIds,
                widgetPosition = widgetPosition,
                widgetHeightDp = widgetHeightDp,
                widgetActions = widgetActions,
                launchCounts = launchCounts,
                // Both flows start at a value that shows nothing, so the dialog can't flash
                // before the stored answer arrives. A legacy install is stamped 0 and never
                // qualifies.
                showWelcome = layoutDefaultsVersion == 1 && !welcomeSeen,
                onWelcomeDismissed = { scope.launch { app.prefs.setWelcomeSeen(true) } },
                scrubBandFractions = scrubBand,
                onSetScrubBand = { top, height -> scope.launch { app.prefs.setScrubBand(top, height) } },
                onClearScrubBand = { scope.launch { app.prefs.clearScrubBand() } },
                onPeekStatusBar = onPeekStatusBar,
                onAppListVisibleChange = onAppListVisibleChange,
                onTogglePrivateSpace = {
                    scope.launch {
                        val locked = app.appRepository.togglePrivateSpace()
                        // Taken after the system has answered, not before: on a phone with a
                        // screen lock the authentication in front of an unlock takes as long
                        // as the person does, and a pass held open across it would supersede
                        // every reload started while the dialog was up.
                        val pass = reloadOrder.begin()
                        // Granted means locked, and locked is concealed here and now: the
                        // broadcast that says so arrives well after the profile has stopped.
                        if (locked != null) adoptPrivateSpace(locked, pass)
                        clearIconCache()
                        // Enumerated against the lock rather than against what the system says
                        // this instant, which for the next moment is still an open space.
                        reloadApps(locked, pass)
                    }
                },
                onNavigate = { route -> navController.navigate(route) },
            )
        }

        composable("settings") {
            val iconPacks = remember { app.iconPackRepository.getInstalledIconPacks() }
            val listenerEnabled = remember(homeIntentTick) { isListenerEnabled(context) }
            SettingsScreen(
                hiddenCount = hiddenShownCount,
                iconPacks = iconPacks,
                iconPackPackage = iconPackPackage,
                showAppIcons = showAppIcons,
                // The Settings app rather than whatever happens to sort first: a stable,
                // recognizable icon to judge a size against on every device.
                previewApp = remember(allApps) {
                    val settingsPkg = runCatching {
                        @Suppress("DEPRECATION")
                        context.packageManager.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)
                            ?.activityInfo?.packageName
                    }.getOrNull()
                    allApps.firstOrNull { it.packageName == settingsPkg }
                        ?: allApps.firstOrNull { it.packageName == "com.android.settings" }
                        ?: allApps.firstOrNull()
                },
                iconSizeDp = iconSizeDp,
                labelSizeSp = labelSizeSp,
                itemSpacingDp = itemSpacingDp,
                font = font,
                hideStatusBar = hideStatusBar,
                hideStatusBarAppList = hideStatusBarAppList,
                statusBarPeekSeconds = statusBarPeekSeconds,
                dimWallpaperAlpha = dimWallpaperAlpha,
                hapticsEnabled = hapticsEnabled,
                dimHomeAlpha = dimHomeAlpha,
                showFavoriteLabels = showFavoriteLabels,
                textColorMode = textColorMode,
                textColorCustom = textColorCustom,
                dimColor = dimColor,
                allowRotation = allowRotation,
                fontFile = fontFile,
                iconShape = iconShape,
                themedIcons = themedIcons,
                doubleTapToLock = doubleTapToLock,
                edgeSide = edgeSide,
                edgeZoneWidthDp = edgeZoneWidthDp,
                azStripVisibility = azStripVisibility,
                showAlphabet = showAlphabet,
                sortByUsage = sortByUsage,
                appListSearch = appListSearchEnabled,
                appListSearchBottom = appListSearchBottom,
                appListSearchHidden = appListSearchHidden,
                swipeUpOpensList = swipeUpOpensList,
                alignment = alignment,
                appListAlignment = appListAlignment,
                iconSide = iconSide,
                nowPlayingEnabled = nowPlayingEnabled,
                nowPlayingListenerEnabled = listenerEnabled,
                onSetIconPack = { scope.launch { app.prefs.setIconPackPackage(it) } },
                onSetShowAppIcons = { scope.launch { app.prefs.setShowAppIcons(it) } },
                onSetIconSize = { scope.launch { app.prefs.setIconSizeDp(it) } },
                onSetLabelSize = { scope.launch { app.prefs.setLabelSizeSp(it) } },
                onSetItemSpacing = { scope.launch { app.prefs.setItemSpacingDp(it) } },
                onSetFont = { scope.launch { app.prefs.setFont(it) } },
                onSetHideStatusBar = { scope.launch { app.prefs.setHideStatusBar(it) } },
                onSetHideStatusBarAppList = { scope.launch { app.prefs.setHideStatusBarAppList(it) } },
                onSetStatusBarPeekSeconds = { scope.launch { app.prefs.setStatusBarPeekSeconds(it) } },
                onSetDimWallpaper = { scope.launch { app.prefs.setDimWallpaperAlpha(it) } },
                onSetHaptics = { scope.launch { app.prefs.setHapticsEnabled(it) } },
                onSetDimHome = { scope.launch { app.prefs.setDimHomeAlpha(it) } },
                onSetShowFavoriteLabels = { scope.launch { app.prefs.setShowFavoriteLabels(it) } },
                onSetTextColorMode = { scope.launch { app.prefs.setTextColorMode(it) } },
                onSetTextColorCustom = { scope.launch { app.prefs.setTextColorCustom(it) } },
                onSetDimColor = { scope.launch { app.prefs.setDimColor(it) } },
                onSetAllowRotation = { scope.launch { app.prefs.setAllowRotation(it) } },
                onExportSettings = { exportLauncher.launch("victoria-launcher-settings.json") },
                onImportSettings = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                onSetIconShape = { scope.launch { app.prefs.setIconShape(it); clearIconCache() } },
                onSetThemedIcons = { scope.launch { app.prefs.setThemedIcons(it); clearIconCache() } },
                // Copied in rather than referenced: a document URI is only as durable as the
                // file behind it, and a font picked from Downloads would break the moment it
                // was moved or cleaned up.
                onPickFontFile = { uri ->
                    scope.launch {
                        val path = withContext(Dispatchers.IO) {
                            runCatching {
                                val out = java.io.File(context.filesDir, "custom_font")
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    out.outputStream().use { input.copyTo(it) }
                                } ?: return@runCatching null
                                // Proves it parses before anything starts drawing with it.
                                android.graphics.Typeface.createFromFile(out)
                                out.absolutePath
                            }.getOrNull()
                        }
                        if (path == null) {
                            Toast.makeText(context, R.string.font_custom_failed, Toast.LENGTH_SHORT).show()
                        } else {
                            app.prefs.setFontFile(path)
                            app.prefs.setFont(AppFont.CUSTOM)
                        }
                    }
                },
                onSetDoubleTapToLock = { scope.launch { app.prefs.setDoubleTapToLock(it) } },
                onSetEdgeSide = { scope.launch { app.prefs.setEdgeSide(it) } },
                onSetEdgeZoneWidth = { scope.launch { app.prefs.setEdgeZoneWidthDp(it) } },
                onSetAzStripVisibility = { scope.launch { app.prefs.setAzStripVisibility(it) } },
                onSetShowAlphabet = { scope.launch { app.prefs.setShowAlphabet(it) } },
                onSetSortByUsage = { scope.launch { app.prefs.setSortByUsage(it) } },
                onSetAppListSearch = { scope.launch { app.prefs.setAppListSearchEnabled(it) } },
                onSetAppListSearchBottom = { scope.launch { app.prefs.setAppListSearchBottom(it) } },
                onSetAppListSearchHidden = { scope.launch { app.prefs.setAppListSearchHidden(it) } },
                onSetSwipeUpOpensList = { scope.launch { app.prefs.setSwipeUpOpensList(it) } },
                quickLaunchLeftLabel = quickLaunchLeftKey?.let { key ->
                    appsByKey[key]?.let { nameOverrides[it.key] ?: it.label }
                },
                quickLaunchRightLabel = quickLaunchRightKey?.let { key ->
                    appsByKey[key]?.let { nameOverrides[it.key] ?: it.label }
                },
                onOpenQuickLaunchPicker = { slot -> navController.navigate("apppicker/" + slot.name) },
                onSetAlignment = { scope.launch { app.prefs.setAlignment(it) } },
                onSetAppListAlignment = { scope.launch { app.prefs.setAppListAlignment(it) } },
                onSetIconSide = { scope.launch { app.prefs.setIconSide(it) } },
                onSetNowPlayingEnabled = { scope.launch { app.prefs.setNowPlayingEnabled(it) } },
                shadeGestureReady = remember(homeIntentTick) { SystemUi.canExpandShade() },
                lockGestureReady = remember(homeIntentTick) { SystemUi.canLockScreen() },
                onOpenAccessibilitySettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                onOpenAppInfo = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                onOpenHiddenApps = { navController.navigate("settings/hidden") },
                onOpenFavorites = { navController.navigate("favorites") },
                onOpenNotificationSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable("favorites") {
            ManageFavoritesScreen(
                allApps = allApps,
                favoriteKeys = favoriteKeys,
                folders = folders,
                privateSpace = privateSpace,
                nameOverrides = nameOverrides,
                iconSizeDp = iconSizeDp,
                onReorder = { keys -> scope.launch { app.prefs.setFavorites(keys) } },
                onSetFavorite = { appInfo, add ->
                    scope.launch {
                        if (add) app.prefs.addFavorite(appInfo.key) else app.prefs.removeFavorite(appInfo.key)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable("folder/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            FolderAppsScreen(
                folder = foldersById[id],
                allApps = allApps,
                privateSpace = privateSpace,
                nameOverrides = nameOverrides,
                iconSizeDp = iconSizeDp,
                onSetInFolder = { appInfo, inFolder ->
                    scope.launch {
                        if (inFolder) {
                            app.prefs.addAppToFolder(id, appInfo.key)
                        } else {
                            app.prefs.removeAppFromFolder(id, appInfo.key)
                        }
                    }
                },
                onReorder = { keys -> scope.launch { app.prefs.setFolderApps(id, keys) } },
                onBack = { navController.popBackStack() },
            )
        }

        composable("apppicker/{slot}") { entry ->
            val slot = runCatching {
                QuickLaunchSlot.valueOf(entry.arguments?.getString("slot").orEmpty())
            }.getOrDefault(QuickLaunchSlot.LEFT)
            AppPickerScreen(
                title = stringResource(
                    if (slot == QuickLaunchSlot.LEFT) R.string.settings_quick_launch_left
                    else R.string.settings_quick_launch_right
                ),
                allApps = allApps,
                selectedKey = if (slot == QuickLaunchSlot.LEFT) quickLaunchLeftKey else quickLaunchRightKey,
                nameOverrides = nameOverrides,
                iconSizeDp = iconSizeDp,
                onPick = { picked ->
                    scope.launch { app.prefs.setQuickLaunch(slot, picked?.key) }
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings/hidden") {
            HiddenAppsScreen(
                allApps = allApps,
                hiddenApps = hiddenApps,
                nameOverrides = nameOverrides,
                iconSizeDp = iconSizeDp,
                onToggleHidden = { appInfo, hidden ->
                    scope.launch { app.prefs.setHidden(appInfo.key, hidden) }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable("iconpicker/{key}") { entry ->
            val key = entry.arguments?.getString("key")?.let { Uri.decode(it) }.orEmpty()
            val folderId = folderIdFromToken(key)
            val targetApp = allApps.find { it.key == key }
            val label = when {
                folderId != null -> foldersById[folderId]?.name.orEmpty()
                else -> nameOverrides[key] ?: targetApp?.label.orEmpty()
            }

            fun applyIcon(value: String?) {
                scope.launch {
                    if (folderId != null) {
                        app.prefs.setFolderIcon(folderId, value)
                    } else {
                        app.prefs.setIconOverride(key, value)
                    }
                }
            }

            IconPickerScreen(
                appLabel = label,
                onPickPackIcon = { packPkg, drawableName ->
                    applyIcon(encodePackOverride(packPkg, drawableName))
                    navController.popBackStack()
                },
                onPickFromGallery = {
                    pendingIconTarget = key
                    pickImageLauncher.launch(arrayOf("image/*"))
                },
                onResetIcon = {
                    applyIcon(null)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/**
 * The app list and the private-space state it was enumerated against, held as one value.
 *
 * Two pieces of state would let a composition see a list from one read beside a state from
 * another, and every consumer needs them to agree: the settings screens decide what to conceal
 * from the state and then look the rest up in the list, and the home screen draws the padlock
 * from one and the rows from the other. Published together, there is no such moment.
 */
private data class AppsAndSpace(val privateSpace: PrivateSpace, val apps: List<AppInfo>)
