// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui

import android.app.Activity.RESULT_OK
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.data.AppFont
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.EdgeSide
import dev.victorialauncher.data.HomePaddings
import dev.victorialauncher.data.TextColorMode
import dev.victorialauncher.data.folderIdFromToken
import dev.victorialauncher.media.isListenerEnabled
import dev.victorialauncher.service.SystemUi
import dev.victorialauncher.ui.common.IconPickerScreen
import dev.victorialauncher.ui.common.clearIconCache
import dev.victorialauncher.ui.common.encodePackOverride
import dev.victorialauncher.ui.common.warmIconCache
import dev.victorialauncher.ui.home.FavoriteEntry
import dev.victorialauncher.ui.home.HomeRoute
import dev.victorialauncher.ui.home.HomeSettings
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
    font: AppFont,
    hideStatusBar: Boolean,
    iconPackPackage: String?,
    iconOverrides: Map<String, String>,
    onPeekStatusBar: () -> Unit,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Enumerating every installed app costs a PackageManager round trip per app; doing it in
    // the first composition is what stalled the cold start. Load it off the main thread and
    // let the home screen render against an empty list for the first frame.
    var allApps by remember { mutableStateOf(emptyList<AppInfo>()) }
    suspend fun reloadApps() {
        allApps = withContext(Dispatchers.Default) { app.appRepository.queryAllApps() }
    }
    LaunchedEffect(Unit) { reloadApps() }

    DisposableEffect(Unit) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                scope.launch {
                    // An app that ships a new icon in an update changes none of the cache
                    // key's components, so nothing else would invalidate the stale bitmap.
                    clearIconCache()
                    reloadApps()
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
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
    val alwaysShowAz by app.prefs.alwaysShowAz.collectAsState(initial = false)
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
    val contentColor = rememberContentColor(textColorMode)

    val appsByKey = remember(allApps) { allApps.associateBy { it.key } }
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
    LaunchedEffect(allApps, iconPackPackage, iconOverrides, listIconPx, priorityKeys) {
        warmIconCache(context, allApps, iconPackPackage, iconOverrides, listIconPx, priorityKeys)
    }

    val settings = HomeSettings(
        iconSizeDp = iconSizeDp,
        labelSizeSp = labelSizeSp,
        itemSpacingDp = itemSpacingDp,
        sidePaddingDp = sidePaddingDp,
        nowPlayingHeightDp = nowPlayingHeightDp,
        nowPlayingEnabled = nowPlayingEnabled,
        edgeSide = edgeSide,
        alwaysShowAz = alwaysShowAz,
        showAlphabet = showAlphabet,
        alignRight = alignRight,
        dimWallpaperAlpha = dimWallpaperAlpha,
        dimHomeAlpha = dimHomeAlpha,
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
            if (id != -1) {
                // Replacing a widget has to release the one it replaces, or its ID stays
                // allocated in the host and its provider keeps broadcasting updates forever.
                val previous = widgetId
                if (previous > 0 && previous != id) app.widgetHost.deleteAppWidgetId(previous)
                scope.launch { app.prefs.setWidgetId(id) }
            }
        }
    }

    val widgetActions = remember(widgetId) {
        WidgetSlotActions(
            onAddWidget = { widgetPickerLauncher.launch(Intent(context, WidgetPickerActivity::class.java)) },
            onRemoveWidget = {
                scope.launch {
                    if (widgetId > 0) app.widgetHost.deleteAppWidgetId(widgetId)
                    app.prefs.setWidgetId(-1)
                }
            },
            onWidgetSettings = {
                val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)
                val configure = info?.configure
                if (configure != null) {
                    val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                        component = configure
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    }
                    runCatching { context.startActivity(intent) }
                }
            },
            onAppInfo = {
                val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)
                info?.let { app.appRepository.openAppInfo(it.provider.packageName) }
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
                settings = settings,
                homePaddings = homePaddings,
                favorites = favoriteEntries,
                appsByKey = appsByKey,
                folders = folders,
                favoriteKeys = favoriteKeys,
                hiddenApps = hiddenApps,
                nameOverrides = nameOverrides,
                widgetId = widgetId,
                widgetPosition = widgetPosition,
                widgetHeightDp = widgetHeightDp,
                widgetActions = widgetActions,
                onPeekStatusBar = onPeekStatusBar,
                onNavigate = { route -> navController.navigate(route) },
            )
        }

        composable("settings") {
            val iconPacks = remember { app.iconPackRepository.getInstalledIconPacks() }
            val listenerEnabled = remember(homeIntentTick) { isListenerEnabled(context) }
            SettingsScreen(
                hiddenCount = hiddenApps.size,
                iconPacks = iconPacks,
                iconPackPackage = iconPackPackage,
                previewApp = allApps.firstOrNull(),
                iconSizeDp = iconSizeDp,
                labelSizeSp = labelSizeSp,
                itemSpacingDp = itemSpacingDp,
                font = font,
                hideStatusBar = hideStatusBar,
                dimWallpaperAlpha = dimWallpaperAlpha,
                hapticsEnabled = hapticsEnabled,
                dimHomeAlpha = dimHomeAlpha,
                showFavoriteLabels = showFavoriteLabels,
                textColorMode = textColorMode,
                doubleTapToLock = doubleTapToLock,
                edgeSide = edgeSide,
                alwaysShowAz = alwaysShowAz,
                showAlphabet = showAlphabet,
                alignRight = alignRight,
                nowPlayingEnabled = nowPlayingEnabled,
                nowPlayingListenerEnabled = listenerEnabled,
                onSetIconPack = { scope.launch { app.prefs.setIconPackPackage(it) } },
                onSetIconSize = { scope.launch { app.prefs.setIconSizeDp(it) } },
                onSetLabelSize = { scope.launch { app.prefs.setLabelSizeSp(it) } },
                onSetItemSpacing = { scope.launch { app.prefs.setItemSpacingDp(it) } },
                onSetFont = { scope.launch { app.prefs.setFont(it) } },
                onSetHideStatusBar = { scope.launch { app.prefs.setHideStatusBar(it) } },
                onSetDimWallpaper = { scope.launch { app.prefs.setDimWallpaperAlpha(it) } },
                onSetHaptics = { scope.launch { app.prefs.setHapticsEnabled(it) } },
                onSetDimHome = { scope.launch { app.prefs.setDimHomeAlpha(it) } },
                onSetShowFavoriteLabels = { scope.launch { app.prefs.setShowFavoriteLabels(it) } },
                onSetTextColorMode = { scope.launch { app.prefs.setTextColorMode(it) } },
                onSetDoubleTapToLock = { scope.launch { app.prefs.setDoubleTapToLock(it) } },
                onSetEdgeSide = { scope.launch { app.prefs.setEdgeSide(it) } },
                onSetAlwaysShowAz = { scope.launch { app.prefs.setAlwaysShowAz(it) } },
                onSetShowAlphabet = { scope.launch { app.prefs.setShowAlphabet(it) } },
                onSetAlignRight = { scope.launch { app.prefs.setAlignRight(it) } },
                onSetNowPlayingEnabled = { scope.launch { app.prefs.setNowPlayingEnabled(it) } },
                shadeGestureReady = remember(homeIntentTick) { SystemUi.canExpandShade() },
                onOpenAccessibilitySettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
                nameOverrides = nameOverrides,
                iconSizeDp = iconSizeDp,
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
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings/hidden") {
            HiddenAppsScreen(
                allApps = allApps,
                hiddenApps = hiddenApps,
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