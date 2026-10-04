// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import dev.victorialauncher.ui.theme.fontFamilyOf
import dev.victorialauncher.ui.theme.rememberWallpaperPalette
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.items
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import dev.victorialauncher.data.CrashLog
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import dev.victorialauncher.BuildConfig
import dev.victorialauncher.data.AppFont
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.AzStripVisibility
import dev.victorialauncher.data.IconShape
import dev.victorialauncher.data.EdgeSide
import dev.victorialauncher.data.EDGE_ZONE_INSET_RANGE
import dev.victorialauncher.data.HOLD_SCROLL_RANGE
import dev.victorialauncher.data.SECTION_TOP_RANGE
import dev.victorialauncher.data.FREQUENT_RANGE
import dev.victorialauncher.data.FavoritesSource
import dev.victorialauncher.data.ShortcutSwipe
import dev.victorialauncher.data.HomeAlignment
import dev.victorialauncher.data.IconSide
import dev.victorialauncher.data.QuickLaunchSlot
import dev.victorialauncher.data.IconPackRepository
import dev.victorialauncher.data.TextColorMode
import dev.victorialauncher.ui.common.AppIcon
import dev.victorialauncher.ui.theme.toFontFamily
import dev.victorialauncher.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    hiddenCount: Int,
    iconPacks: List<IconPackRepository.IconPackInfo>,
    iconPackPackage: String?,
    previewApp: AppInfo?,
    iconSizeDp: Int,
    labelSizeSp: Int,
    itemSpacingDp: Int,
    font: AppFont,
    hideStatusBar: Boolean,
    hideStatusBarAppList: Boolean,
    dimWallpaperAlpha: Float,
    hapticsEnabled: Boolean,
    dimHomeAlpha: Float,
    showFavoriteLabels: Boolean,
    textColorMode: TextColorMode,
    textColorCustom: Int,
    dimColor: Int,
    allowRotation: Boolean,
    shortcutSwipe: ShortcutSwipe,
    fontFile: String?,
    iconShape: IconShape,
    themedIcons: Boolean,
    doubleTapToLock: Boolean,
    edgeSide: EdgeSide,
    azStripVisibility: AzStripVisibility,
    showAlphabet: Boolean,
    sortByUsage: Boolean,
    appListSearch: Boolean,
    appListSearchBottom: Boolean,
    appListSearchHidden: Boolean,
    swipeUpOpensList: Boolean,
    alignment: HomeAlignment,
    appListAlignment: HomeAlignment,
    iconSide: IconSide,
    nowPlayingEnabled: Boolean,
    nowPlayingListenerEnabled: Boolean,
    showAppIcons: Boolean,
    showFavoriteIcons: Boolean,
    onSetShowFavoriteIcons: (Boolean) -> Unit,
    showListHeaders: Boolean,
    onSetShowListHeaders: (Boolean) -> Unit,
    showSettingsRow: Boolean,
    onSetShowSettingsRow: (Boolean) -> Unit,
    edgeZoneBandOnly: Boolean,
    onSetEdgeZoneBandOnly: (Boolean) -> Unit,
    holdScroll: Boolean,
    onSetHoldScroll: (Boolean) -> Unit,
    dimOthersScrubbing: Boolean,
    onSetDimOthersScrubbing: (Boolean) -> Unit,
    markWorkApps: Boolean,
    onSetMarkWorkApps: (Boolean) -> Unit,
    edgeZoneInsetDp: Int,
    onSetEdgeZoneInsetDp: (Int) -> Unit,
    webSearchFallback: Boolean,
    onSetWebSearchFallback: (Boolean) -> Unit,
    swipeUpOpensSearch: Boolean,
    onSetSwipeUpOpensSearch: (Boolean) -> Unit,
    holdScrollSpeed: Int,
    onSetHoldScrollSpeed: (Int) -> Unit,
    notificationBadges: Boolean,
    onSetNotificationBadges: (Boolean) -> Unit,
    sectionTopPercent: Int,
    onSetSectionTopPercent: (Int) -> Unit,
    closeFolderOnLaunch: Boolean,
    onSetCloseFolderOnLaunch: (Boolean) -> Unit,
    favoritesSource: FavoritesSource,
    onSetFavoritesSource: (FavoritesSource) -> Unit,
    frequentCount: Int,
    onSetFrequentCount: (Int) -> Unit,
    onSetIconPack: (String?) -> Unit,
    onSetShowAppIcons: (Boolean) -> Unit,
    onSetIconSize: (Int) -> Unit,
    onSetLabelSize: (Int) -> Unit,
    onSetItemSpacing: (Int) -> Unit,
    onSetFont: (AppFont) -> Unit,
    statusBarPeekSeconds: Int,
    onSetHideStatusBar: (Boolean) -> Unit,
    onSetHideStatusBarAppList: (Boolean) -> Unit,
    onSetStatusBarPeekSeconds: (Int) -> Unit,
    onSetDimWallpaper: (Float) -> Unit,
    onSetHaptics: (Boolean) -> Unit,
    onSetDimHome: (Float) -> Unit,
    onSetShowFavoriteLabels: (Boolean) -> Unit,
    onSetTextColorMode: (TextColorMode) -> Unit,
    onSetTextColorCustom: (Int) -> Unit,
    onSetDimColor: (Int) -> Unit,
    onSetAllowRotation: (Boolean) -> Unit,
    onSetShortcutSwipe: (ShortcutSwipe) -> Unit,
    onExportSettings: () -> Unit,
    onImportSettings: () -> Unit,
    onPickFontFile: (Uri) -> Unit,
    onSetIconShape: (IconShape) -> Unit,
    onSetThemedIcons: (Boolean) -> Unit,
    onSetDoubleTapToLock: (Boolean) -> Unit,
    edgeZoneWidthDp: Int,
    onSetEdgeSide: (EdgeSide) -> Unit,
    onSetEdgeZoneWidth: (Int) -> Unit,
    onSetAzStripVisibility: (AzStripVisibility) -> Unit,
    onSetShowAlphabet: (Boolean) -> Unit,
    onSetSortByUsage: (Boolean) -> Unit,
    onSetSwipeUpOpensList: (Boolean) -> Unit,
    onSetAppListSearch: (Boolean) -> Unit,
    onSetAppListSearchBottom: (Boolean) -> Unit,
    onSetAppListSearchHidden: (Boolean) -> Unit,
    quickLaunchSlide: Boolean,
    onSetQuickLaunchSlide: (Boolean) -> Unit,
    quickLaunchLeftLabel: String?,
    quickLaunchRightLabel: String?,
    onOpenQuickLaunchPicker: (QuickLaunchSlot) -> Unit,
    cornerButtonLabel: String?,
    onOpenCornerPicker: () -> Unit,
    autoKeyboard: Boolean,
    onSetAutoKeyboard: (Boolean) -> Unit,
    lastLetterToLine: Boolean,
    onSetLastLetterToLine: (Boolean) -> Unit,
    onSetAlignment: (HomeAlignment) -> Unit,
    onSetAppListAlignment: (HomeAlignment) -> Unit,
    onSetIconSide: (IconSide) -> Unit,
    onSetNowPlayingEnabled: (Boolean) -> Unit,
    shadeGestureReady: Boolean,
    lockGestureReady: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenHiddenApps: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // A width in dp means nothing until you see it against the screen it is measured on, so
    // adjusting it paints the zone down the edges it would actually occupy. It fades out on
    // its own rather than needing dismissing.
    var edgePreviewShown by remember { mutableStateOf(false) }
    // Driven by the act of adjusting, not by the value. Keyed on the value it also fired on
    // first composition, and again when the stored setting arrived and replaced the initial
    // one — so opening settings flashed a preview nobody asked for.
    var edgePreviewTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(edgePreviewTick) {
        if (edgePreviewTick == 0) return@LaunchedEffect
        edgePreviewShown = true
        delay(1400)
        edgePreviewShown = false
    }
    val edgePreviewAlpha by animateFloatAsState(
        if (edgePreviewShown) 1f else 0f,
        label = "edgePreviewAlpha",
    )

    // The same idea for the scrub line: a percentage means nothing until you see where on this
    // screen it falls, so setting it draws the line it describes.
    var sectionPreviewShown by remember { mutableStateOf(false) }
    var sectionPreviewTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(sectionPreviewTick) {
        if (sectionPreviewTick == 0) return@LaunchedEffect
        sectionPreviewShown = true
        delay(1400)
        sectionPreviewShown = false
    }
    val sectionPreviewAlpha by animateFloatAsState(
        if (sectionPreviewShown) 1f else 0f,
        label = "sectionPreviewAlpha",
    )

    // One screen, shown a section at a time. Separate destinations would mean threading every
    // one of these settings through a route of its own, for a list that is only ever reached
    // from here — so the sections stay where they are and the screen shows one of them.
    // Saveable, not merely remembered. Settings that change the window — the rotation lock is
    // the plain one — rebuild the Activity, and a plain remember goes with it, dropping whoever
    // was three rows into a section back at the top menu.
    var openSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    // Typed across every section at once, since the whole trouble with a settings screen is
    // knowing which section the thing you want was filed under.
    var query by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = openSection != null || query.isNotBlank()) {
        if (query.isNotBlank()) query = "" else openSection = null
    }


    // Every row of settings as one list: what it is called, what else it answers to, the
    // section it belongs in, and the control itself. One list rather than a block per section
    // because the rows have to serve two readers — someone walking a section, and someone
    // typing at the search box — and a row written twice is a row that drifts.
    val entries = listOf(
        // ---- Appearance ----
        SettingsEntry(SettingsSection.APPEARANCE, stringResource(R.string.settings_preview)) {
            RowPreview(previewApp, iconSizeDp, labelSizeSp, font, fontFile, itemSpacingDp)
        },
        SettingsEntry(SettingsSection.APPEARANCE, stringResource(R.string.settings_icon_pack), "icons themed pack") {
            IconPackRow(
                iconPacks, iconPackPackage, showAppIcons, showFavoriteIcons, themedIcons,
                onSetIconPack, onSetShowAppIcons, onSetShowFavoriteIcons, onSetThemedIcons,
            )
        },
        SettingsEntry(SettingsSection.APPEARANCE, stringResource(R.string.settings_icons_favorites), "icons") {
            SwitchRow(stringResource(R.string.settings_icons_favorites), showFavoriteIcons, onSetShowFavoriteIcons)
        },
        SettingsEntry(SettingsSection.APPEARANCE, stringResource(R.string.settings_icons_app_list), "icons") {
            SwitchRow(stringResource(R.string.settings_icons_app_list), showAppIcons, onSetShowAppIcons)
        },
        SettingsEntry(SettingsSection.APPEARANCE, stringResource(R.string.settings_icon_size), "size") {
            SliderRow(
                label = stringResource(R.string.settings_icon_size),
                value = iconSizeDp.toFloat(), range = 32f..96f,
                valueLabel = "${iconSizeDp}dp",
                onValueChange = { onSetIconSize(it.toInt()) },
            )
        },
        SettingsEntry(SettingsSection.APPEARANCE, stringResource(R.string.settings_text_size), "font label size") {
            SliderRow(
                label = stringResource(R.string.settings_text_size),
                value = labelSizeSp.toFloat(), range = 10f..28f,
                valueLabel = "${labelSizeSp}sp",
                onValueChange = { onSetLabelSize(it.toInt()) },
            )
        },
        SettingsEntry(SettingsSection.APPEARANCE, stringResource(R.string.settings_favorite_spacing), "gap spacing") {
            SliderRow(
                label = stringResource(R.string.settings_favorite_spacing),
                value = itemSpacingDp.toFloat(), range = 0f..40f,
                valueLabel = "${itemSpacingDp}dp",
                onValueChange = { onSetItemSpacing(it.toInt()) },
            )
        },
        SettingsEntry(SettingsSection.APPEARANCE, stringResource(R.string.settings_font), "typeface") {
            FontRow(font, fontFile, onSetFont, onPickFontFile)
        },
        SettingsEntry(SettingsSection.APPEARANCE, stringResource(R.string.settings_text_color), "colour") {
            TextColorRow(textColorMode, textColorCustom, onSetTextColorMode, onSetTextColorCustom)
        },
        SettingsEntry(SettingsSection.APPEARANCE, stringResource(R.string.icon_shape), "shape") {
            IconShapeRow(iconShape, onSetIconShape)
        },
        SettingsEntry(SettingsSection.APPEARANCE, stringResource(R.string.settings_alignment_favorites), "align left right center") {
            AlignmentRow(stringResource(R.string.settings_alignment_favorites), alignment, onSetAlignment)
        },
        SettingsEntry(SettingsSection.APPEARANCE, stringResource(R.string.settings_alignment_applist), "align left right center") {
            AlignmentRow(stringResource(R.string.settings_alignment_applist), appListAlignment, onSetAppListAlignment)
        },
        SettingsEntry(SettingsSection.APPEARANCE, stringResource(R.string.settings_icon_side), "left right") {
            IconSideRow(iconSide, onSetIconSide)
        },
        SettingsEntry(SettingsSection.APPEARANCE, stringResource(R.string.settings_show_names), "labels") {
            SwitchRow(stringResource(R.string.settings_show_names), showFavoriteLabels, onSetShowFavoriteLabels)
        },
        SettingsEntry(
            SettingsSection.APPEARANCE, stringResource(R.string.settings_dim_color), "colour wallpaper",
            // Only worth offering once something is actually dimmed.
            visible = dimHomeAlpha > 0f || dimWallpaperAlpha > 0f,
        ) { DimColorRow(dimColor, onSetDimColor) },

        // ---- Home screen ----
        SettingsEntry(SettingsSection.HOME, stringResource(R.string.settings_favorites_source), "frequent most used manual") {
            FavoritesSourceRow(favoritesSource, frequentCount, onSetFavoritesSource, onSetFrequentCount)
        },
        SettingsEntry(SettingsSection.HOME, stringResource(R.string.settings_close_folder), "folder") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_close_folder),
                detail = stringResource(R.string.settings_close_folder_detail),
                checked = closeFolderOnLaunch, onCheckedChange = onSetCloseFolderOnLaunch,
            )
        },
        SettingsEntry(SettingsSection.HOME, stringResource(R.string.settings_hide_status_bar), "status bar") {
            SwitchRow(stringResource(R.string.settings_hide_status_bar), hideStatusBar, onSetHideStatusBar)
        },
        SettingsEntry(
            SettingsSection.HOME, stringResource(R.string.settings_status_bar_timeout), "status bar peek",
            visible = hideStatusBar,
        ) {
            SliderRow(
                label = stringResource(R.string.settings_status_bar_timeout),
                value = statusBarPeekSeconds.toFloat(), range = 1f..30f,
                valueLabel = "${statusBarPeekSeconds}s",
                onValueChange = { onSetStatusBarPeekSeconds(it.roundToInt()) },
            )
        },
        SettingsEntry(SettingsSection.HOME, stringResource(R.string.settings_dim_home), "dim wallpaper") {
            SliderRow(
                label = stringResource(R.string.settings_dim_home),
                value = dimHomeAlpha, range = 0f..0.85f,
                valueLabel = "${(dimHomeAlpha * 100).roundToInt()}%",
                // Rounded to whole percent, so dragging lands where the buttons do.
                onValueChange = { onSetDimHome((it * 100).roundToInt() / 100f) },
                step = 0.01f,
            )
        },
        SettingsEntry(SettingsSection.HOME, stringResource(R.string.settings_allow_rotation), "landscape rotate") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_allow_rotation),
                detail = stringResource(R.string.settings_allow_rotation_detail),
                checked = allowRotation, onCheckedChange = onSetAllowRotation,
            )
        },

        // ---- App list ----
        SettingsEntry(SettingsSection.APP_LIST, stringResource(R.string.settings_search_bar), "search") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_search_bar),
                detail = stringResource(R.string.settings_search_bar_detail),
                checked = appListSearch, onCheckedChange = onSetAppListSearch,
            )
        },
        SettingsEntry(
            SettingsSection.APP_LIST, stringResource(R.string.settings_auto_keyboard), "keyboard search",
            visible = appListSearch,
        ) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_auto_keyboard),
                detail = stringResource(R.string.settings_auto_keyboard_detail),
                checked = autoKeyboard, onCheckedChange = onSetAutoKeyboard,
            )
        },
        SettingsEntry(
            SettingsSection.APP_LIST, stringResource(R.string.settings_search_bar_bottom), "search position",
            visible = appListSearch,
        ) {
            SwitchRow(stringResource(R.string.settings_search_bar_bottom), appListSearchBottom, onSetAppListSearchBottom)
        },
        SettingsEntry(
            SettingsSection.APP_LIST, stringResource(R.string.settings_search_hidden), "search hidden",
            visible = appListSearch,
        ) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_search_hidden),
                detail = stringResource(R.string.settings_search_hidden_detail),
                checked = appListSearchHidden, onCheckedChange = onSetAppListSearchHidden,
            )
        },
        SettingsEntry(SettingsSection.APP_LIST, stringResource(R.string.settings_list_headers), "letters headings") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_list_headers),
                detail = stringResource(R.string.settings_list_headers_detail),
                checked = showListHeaders, onCheckedChange = onSetShowListHeaders,
            )
        },
        SettingsEntry(SettingsSection.APP_LIST, stringResource(R.string.settings_settings_row), "settings shortcut row") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_settings_row),
                detail = stringResource(R.string.settings_settings_row_detail),
                checked = showSettingsRow, onCheckedChange = onSetShowSettingsRow,
            )
        },
        SettingsEntry(SettingsSection.APP_LIST, stringResource(R.string.settings_sort_by_usage), "frequent order") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_sort_by_usage),
                detail = stringResource(R.string.settings_sort_by_usage_detail),
                checked = sortByUsage, onCheckedChange = onSetSortByUsage,
            )
        },
        SettingsEntry(SettingsSection.APP_LIST, stringResource(R.string.settings_az_visibility), "alphabet strip a-z") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.settings_az_visibility), style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AzStripVisibility.entries.forEach { option ->
                        FilledChip(stringResource(option.labelRes()), azStripVisibility == option) {
                            onSetAzStripVisibility(option)
                        }
                    }
                }
            }
        },
        SettingsEntry(SettingsSection.APP_LIST, stringResource(R.string.settings_show_alphabet), "letters a-z strip") {
            SwitchRow(stringResource(R.string.settings_show_alphabet), showAlphabet, onSetShowAlphabet)
        },
        SettingsEntry(SettingsSection.APP_LIST, stringResource(R.string.settings_edge_side), "a-z strip left right") {
            EdgeSideRow(edgeSide) { edgePreviewTick++; onSetEdgeSide(it) }
        },
        SettingsEntry(SettingsSection.APP_LIST, stringResource(R.string.settings_web_search), "search web browser") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_web_search),
                detail = stringResource(R.string.settings_web_search_detail),
                checked = webSearchFallback, onCheckedChange = onSetWebSearchFallback,
            )
        },
        SettingsEntry(
            SettingsSection.APP_LIST, stringResource(R.string.settings_swipe_up_search), "swipe up search keyboard",
            visible = swipeUpOpensList,
        ) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_swipe_up_search),
                detail = stringResource(R.string.settings_swipe_up_search_detail),
                checked = swipeUpOpensSearch, onCheckedChange = onSetSwipeUpOpensSearch,
            )
        },
        SettingsEntry(SettingsSection.APP_LIST, stringResource(R.string.settings_mark_work_apps), "work profile duplicate apps") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_mark_work_apps),
                detail = stringResource(R.string.settings_mark_work_apps_detail),
                checked = markWorkApps, onCheckedChange = onSetMarkWorkApps,
            )
        },
        SettingsEntry(SettingsSection.APP_LIST, stringResource(R.string.settings_dim_others), "dim fade a-z scrub") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_dim_others),
                detail = stringResource(R.string.settings_dim_others_detail),
                checked = dimOthersScrubbing, onCheckedChange = onSetDimOthersScrubbing,
            )
        },
        SettingsEntry(SettingsSection.APP_LIST, stringResource(R.string.settings_hold_scroll), "a-z hold scroll") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_hold_scroll),
                detail = stringResource(R.string.settings_hold_scroll_detail),
                checked = holdScroll, onCheckedChange = onSetHoldScroll,
            )
        },
        SettingsEntry(
            SettingsSection.APP_LIST, stringResource(R.string.settings_hold_scroll_speed), "a-z hold scroll",
            visible = holdScroll,
        ) {
            SliderRow(
                label = stringResource(R.string.settings_hold_scroll_speed),
                value = holdScrollSpeed.toFloat(),
                range = HOLD_SCROLL_RANGE.first.toFloat()..HOLD_SCROLL_RANGE.last.toFloat(),
                valueLabel = holdScrollSpeed.toString(),
                onValueChange = { onSetHoldScrollSpeed(it.toInt()) },
            )
        },
        SettingsEntry(SettingsSection.APP_LIST, stringResource(R.string.settings_edge_band_only), "a-z strip height touch") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_edge_band_only),
                detail = stringResource(R.string.settings_edge_band_only_detail),
                checked = edgeZoneBandOnly, onCheckedChange = onSetEdgeZoneBandOnly,
            )
        },
        SettingsEntry(SettingsSection.APP_LIST, stringResource(R.string.settings_edge_inset), "back gesture edge conflict") {
            SliderRow(
                label = stringResource(R.string.settings_edge_inset),
                value = edgeZoneInsetDp.toFloat(),
                range = EDGE_ZONE_INSET_RANGE.first.toFloat()..EDGE_ZONE_INSET_RANGE.last.toFloat(),
                valueLabel = edgeZoneInsetDp.toString(),
                onValueChange = { onSetEdgeZoneInsetDp(it.toInt()) },
            )
        },
        SettingsEntry(SettingsSection.APP_LIST, stringResource(R.string.settings_edge_zone_width), "a-z strip width") {
            SliderRow(
                label = stringResource(R.string.settings_edge_zone_width),
                value = edgeZoneWidthDp.toFloat(), range = 32f..96f,
                valueLabel = "${edgeZoneWidthDp}dp",
                onValueChange = { edgePreviewTick++; onSetEdgeZoneWidth(it.roundToInt()) },
            )
        },
        SettingsEntry(SettingsSection.APP_LIST, stringResource(R.string.settings_last_letter), "letter lands end z") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_last_letter),
                detail = stringResource(R.string.settings_last_letter_detail),
                checked = lastLetterToLine, onCheckedChange = onSetLastLetterToLine,
            )
        },
        SettingsEntry(SettingsSection.APP_LIST, stringResource(R.string.settings_section_top), "letter lands scroll position") {
            SliderRow(
                label = stringResource(R.string.settings_section_top),
                value = sectionTopPercent.toFloat(),
                range = SECTION_TOP_RANGE.first.toFloat()..SECTION_TOP_RANGE.last.toFloat(),
                valueLabel = "$sectionTopPercent%",
                onValueChange = { sectionPreviewTick++; onSetSectionTopPercent(it.toInt()) },
            )
        },
        SettingsEntry(SettingsSection.APP_LIST, stringResource(R.string.settings_hide_status_bar_applist), "status bar") {
            SwitchRow(
                stringResource(R.string.settings_hide_status_bar_applist),
                hideStatusBarAppList, onSetHideStatusBarAppList,
            )
        },
        SettingsEntry(SettingsSection.APP_LIST, stringResource(R.string.settings_dim_applist), "dim wallpaper") {
            SliderRow(
                label = stringResource(R.string.settings_dim_applist),
                value = dimWallpaperAlpha, range = 0f..0.85f,
                valueLabel = "${(dimWallpaperAlpha * 100).roundToInt()}%",
                onValueChange = { onSetDimWallpaper((it * 100).roundToInt() / 100f) },
                step = 0.01f,
            )
        },

        // ---- Gestures ----
        SettingsEntry(SettingsSection.GESTURES, stringResource(R.string.settings_haptics), "vibrate") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_haptics),
                detail = stringResource(R.string.settings_haptics_detail),
                checked = hapticsEnabled, onCheckedChange = onSetHaptics,
            )
        },
        SettingsEntry(SettingsSection.GESTURES, stringResource(R.string.settings_swipe_up_list), "swipe up") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_swipe_up_list),
                detail = stringResource(R.string.settings_swipe_up_list_detail),
                checked = swipeUpOpensList, onCheckedChange = onSetSwipeUpOpensList,
            )
        },
        SettingsEntry(SettingsSection.GESTURES, stringResource(R.string.settings_swipe_shortcuts), "swipe shortcuts") {
            ShortcutSwipeRow(shortcutSwipe, onSetShortcutSwipe)
        },
        SettingsEntry(SettingsSection.GESTURES, stringResource(R.string.settings_quick_launch_left), "swipe launch") {
            QuickLaunchRow(
                label = stringResource(R.string.settings_quick_launch_left),
                value = quickLaunchLeftLabel,
                onClick = { onOpenQuickLaunchPicker(QuickLaunchSlot.LEFT) },
            )
        },
        SettingsEntry(SettingsSection.GESTURES, stringResource(R.string.settings_quick_launch_right), "swipe launch") {
            QuickLaunchRow(
                label = stringResource(R.string.settings_quick_launch_right),
                value = quickLaunchRightLabel,
                onClick = { onOpenQuickLaunchPicker(QuickLaunchSlot.RIGHT) },
            )
        },
        SettingsEntry(SettingsSection.GESTURES, stringResource(R.string.settings_quick_launch_slide), "animation") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_quick_launch_slide),
                detail = stringResource(R.string.settings_quick_launch_slide_detail),
                checked = quickLaunchSlide, onCheckedChange = onSetQuickLaunchSlide,
            )
        },
        SettingsEntry(SettingsSection.HOME, stringResource(R.string.settings_corner_button), "button shortcut corner") {
            QuickLaunchRow(
                label = stringResource(R.string.settings_corner_button),
                value = cornerButtonLabel,
                onClick = onOpenCornerPicker,
            )
        },
        SettingsEntry(SettingsSection.GESTURES, stringResource(R.string.settings_double_tap_lock), "lock screen") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_double_tap_lock),
                detail = stringResource(R.string.settings_double_tap_lock_detail),
                checked = doubleTapToLock, onCheckedChange = onSetDoubleTapToLock,
            )
        },
        SettingsEntry(
            SettingsSection.GESTURES, stringResource(R.string.settings_lock_needs_accessibility), "lock accessibility",
            // Switching it on does nothing at all without the permission, so the way to grant
            // it belongs right here rather than buried in a toast later.
            visible = doubleTapToLock && !lockGestureReady,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_lock_needs_accessibility),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f),
                )
                AccessibilityActions(onOpenAccessibilitySettings, onOpenAppInfo)
            }
        },
        SettingsEntry(SettingsSection.GESTURES, stringResource(R.string.settings_shade_gesture), "notification shade pull") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_shade_gesture), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(
                            if (shadeGestureReady) R.string.settings_shade_ready
                            else R.string.settings_shade_not_ready
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                if (!shadeGestureReady) AccessibilityActions(onOpenAccessibilitySettings, onOpenAppInfo)
            }
        },

        // ---- Notifications ----
        SettingsEntry(SettingsSection.NOTIFICATIONS, stringResource(R.string.settings_notification_badges), "count badge icons") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_notification_badges),
                detail = stringResource(R.string.settings_notification_badges_detail),
                checked = notificationBadges, onCheckedChange = onSetNotificationBadges,
            )
        },
        SettingsEntry(SettingsSection.NOTIFICATIONS, stringResource(R.string.settings_now_playing_show), "media music") {
            SwitchRow(stringResource(R.string.settings_now_playing_show), nowPlayingEnabled, onSetNowPlayingEnabled)
        },
        SettingsEntry(
            SettingsSection.NOTIFICATIONS, stringResource(R.string.settings_notification_access_detail), "permission access",
            visible = nowPlayingEnabled || notificationBadges,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            if (nowPlayingListenerEnabled) R.string.settings_notification_access_granted
                            else R.string.settings_notification_access_missing
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.settings_notification_access_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                FilledChip(
                    stringResource(R.string.settings_open_settings),
                    selected = false,
                    onClick = onOpenNotificationSettings,
                )
            }
        },

        // ---- Apps ----
        SettingsEntry(SettingsSection.APPS, stringResource(R.string.settings_favorites), "manage") {
            BackupRow(
                label = stringResource(R.string.settings_favorites),
                detail = stringResource(R.string.settings_favorites_detail),
                onClick = onOpenFavorites,
            )
        },
        SettingsEntry(SettingsSection.APPS, stringResource(R.string.settings_hidden_apps), "hide") {
            BackupRow(
                label = stringResource(R.string.settings_hidden_apps),
                detail = if (hiddenCount == 0) stringResource(R.string.settings_hidden_none)
                    else stringResource(R.string.settings_hidden_count, hiddenCount),
                onClick = onOpenHiddenApps,
            )
        },
    ).filter { it.visible }

    // What the list below actually draws: the matches while something is typed, the open
    // section otherwise, and nothing at all on the top menu.
    val entriesToShow = remember(entries, query, openSection) {
        val matching = when {
            query.isNotBlank() -> entries.filter { it.matches(query) }
            openSection != null -> entries.filter { it.section == openSection }
            else -> emptyList()
        }
        matching.groupBy { it.section }.toList()
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(openSection?.labelRes ?: R.string.settings_title))
                },
                navigationIcon = {
                    IconButton(onClick = { if (openSection != null) openSection = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .padding(padding)
                .background(surface)
                .fillMaxWidth(),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.settings_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.icon_picker_clear_search),
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (openSection == null && query.isBlank()) {
                items(SettingsSection.entries) { section ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openSection = section }
                                .padding(horizontal = 16.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(section.labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                modifier = Modifier.padding(4.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
            }

            entriesToShow.forEach { (section, rows) ->
                item {
                    Section(stringResource(section.labelRes)) {
                        rows.forEachIndexed { index, entry ->
                            if (index > 0) RowDivider()
                            entry.content()
                        }
                    }
                }
            }

            if (query.isNotBlank() && entriesToShow.isEmpty()) item {
                Text(
                    stringResource(R.string.settings_search_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            }

            if (openSection == SettingsSection.BACKUP) item {
                Section(stringResource(R.string.settings_section_backup)) {
                    BackupRow(
                        label = stringResource(R.string.settings_export),
                        detail = stringResource(R.string.settings_export_detail),
                        onClick = onExportSettings,
                    )
                    RowDivider()
                    BackupRow(
                        label = stringResource(R.string.settings_import),
                        detail = stringResource(R.string.settings_import_detail),
                        onClick = onImportSettings,
                    )
                }
            }

            if (openSection == SettingsSection.ABOUT) item {
                Section(stringResource(R.string.settings_section_about)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    // Only after a crash, so there is nothing here to explain the rest of the
                    // time. A launcher draws other apps' widgets with their own code, so the
                    // trace is often the only thing that says whose crash it was.
                    val crash = remember { CrashLog.read(context) }
                    var crashCleared by remember { mutableStateOf(false) }
                    if (crash != null && !crashCleared) {
                        RowDivider()
                        BackupRow(
                            label = stringResource(R.string.settings_crash_copy),
                            detail = stringResource(R.string.settings_crash_detail),
                            onClick = {
                                clipboard.setPrimaryClip(ClipData.newPlainText("Victoria crash", crash))
                                Toast.makeText(context, R.string.settings_crash_copied, Toast.LENGTH_SHORT).show()
                                CrashLog.clear(context)
                                crashCleared = true
                            },
                        )
                    }
                }
            }
        }
    }
        if (sectionPreviewAlpha > 0f) {
            // Where the top of a scrubbed letter's section comes to rest, drawn across the
            // screen it is measured against.
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .fillMaxHeight(sectionTopPercent / 100f),
                contentAlignment = Alignment.BottomStart,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f * sectionPreviewAlpha)
                        ),
                )
            }
        }
        if (edgePreviewAlpha > 0f) {
            val stripe = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f * edgePreviewAlpha)
            if (edgeSide != EdgeSide.RIGHT) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(edgeZoneWidthDp.dp)
                        .fillMaxHeight()
                        .background(stripe),
                )
            }
            if (edgeSide != EdgeSide.LEFT) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(edgeZoneWidthDp.dp)
                        .fillMaxHeight()
                        .background(stripe),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        SectionLabel(title)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        // The row is the target, so the switch itself is not one. With both taking a tap, a
        // press on the switch toggled it and then the row toggled it back, which read as a
        // switch that would not stay where it was put.
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun SwitchRowWithDetail(
    label: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        // See SwitchRow: the row owns the tap, so the switch does not take one of its own.
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

/** Slider plus a pair of steppers, since dragging to an exact value is fiddly. */
/** Shared with the clock widget's own settings, so a size is set the same way everywhere. */
@Composable
internal fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    step: Float = 1f,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(
                onClick = { onValueChange((value - step).coerceIn(range)) },
                enabled = value > range.start,
            ) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.settings_less))
            }
            Text(
                valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(
                onClick = { onValueChange((value + step).coerceIn(range)) },
                enabled = value < range.endInclusive,
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.settings_more))
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPackRow(
    packs: List<IconPackRepository.IconPackInfo>,
    selected: String?,
    showIcons: Boolean,
    showFavoriteIcons: Boolean,
    themed: Boolean,
    onSelect: (String?) -> Unit,
    onSetShowIcons: (Boolean) -> Unit,
    onSetShowFavoriteIcons: (Boolean) -> Unit,
    onSetThemed: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_icon_pack), style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledChip(
                stringResource(R.string.settings_icon_pack_default),
                showIcons && !themed && selected == null,
            ) {
                onSetShowIcons(true)
                onSetShowFavoriteIcons(true)
                onSetThemed(false)
                onSelect(null)
            }
            // A chip rather than a switch: themed icons and an icon pack are two answers to
            // the same question, and a switch beside the packs would let you pick both and
            // then wonder which won.
            FilledChip(stringResource(R.string.settings_icon_pack_themed), showIcons && themed) {
                onSetShowIcons(true)
                onSelect(null)
                onSetThemed(true)
            }
            packs.forEach { pack ->
                FilledChip(pack.label, showIcons && !themed && selected == pack.packageName) {
                    onSetShowIcons(true)
                    onSetThemed(false)
                    onSelect(pack.packageName)
                }
            }
            // Not a pack but a choice about packs: no icons anywhere, in one tap. The two
            // switches below can say the same thing and more — either surface on its own —
            // but turning both off is the common answer and should not take two.
            FilledChip(
                stringResource(R.string.settings_icon_pack_no_icons),
                !showIcons && !showFavoriteIcons,
            ) {
                onSetShowIcons(false)
                onSetShowFavoriteIcons(false)
            }
        }
        if (packs.isEmpty()) {
            Text(
                stringResource(R.string.settings_icon_pack_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontRow(
    selected: AppFont,
    fontFile: String?,
    onSelect: (AppFont) -> Unit,
    onPickFile: (Uri) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPickFile(uri)
    }
    // Font files are served under a pile of inconsistent mime types — font/ttf, x-font-ttf,
    // application/octet-stream, sometimes nothing at all — so the filter would hide the file
    // as often as it helped.
    fun open() = picker.launch(arrayOf("*/*"))

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_font), style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppFont.entries.forEach { f ->
                // Each chip is rendered in the font it selects, so the choice previews itself.
                FilledChip(
                    label = stringResource(f.labelRes()),
                    selected = selected == f,
                    fontFamily = fontFamilyOf(f, fontFile),
                    onClick = {
                        // Nothing to select until there is a file, so the first tap asks for one.
                        if (f == AppFont.CUSTOM && fontFile == null) open() else onSelect(f)
                    },
                )
            }
        }
        if (selected == AppFont.CUSTOM) {
            Text(
                stringResource(R.string.font_custom_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = { open() }, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.font_custom_pick))
            }
        }
    }
}

@Composable
private fun RowPreview(
    app: AppInfo?,
    iconSizeDp: Int,
    labelSizeSp: Int,
    font: AppFont,
    fontFile: String?,
    itemSpacingDp: Int,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            stringResource(R.string.settings_preview),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        // Two rows, because one cannot show the gap between them: spacing was the only
        // thing on this screen with no way to see what the number meant.
        Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            repeat(2) { index ->
                if (index > 0) Spacer(Modifier.height(itemSpacingDp.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (app != null) {
                        AppIcon(app = app, sizeDp = iconSizeDp)
                        Spacer(Modifier.width(16.dp))
                        Text(app.label, fontSize = labelSizeSp.sp, fontFamily = fontFamilyOf(font, fontFile))
                    } else {
                        Text(
                            stringResource(R.string.settings_preview_sample),
                            fontSize = labelSizeSp.sp,
                            fontFamily = fontFamilyOf(font, fontFile),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconShapeRow(selected: IconShape, onSelect: (IconShape) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.icon_shape), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.icon_shape_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconShape.entries.forEach { shape ->
                FilledChip(stringResource(shape.labelRes()), selected == shape) { onSelect(shape) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextColorRow(
    selected: TextColorMode,
    customArgb: Int,
    onSelect: (TextColorMode) -> Unit,
    onSetCustom: (Int) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_text_color), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.settings_text_color_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        // Wraps: five chips no longer fit on one line where three did, and a plain Row
        // answered that by breaking the last label down the screen a letter at a time.
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextColorMode.entries.forEach { mode ->
                FilledChip(stringResource(mode.labelRes()), selected == mode) {
                    onSelect(mode)
                    if (mode == TextColorMode.CUSTOM) picking = true
                }
            }
        }
        if (selected == TextColorMode.CUSTOM) {
            TextButton(onClick = { picking = true }, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.text_color_pick))
            }
        }
    }

    if (picking) {
        ColorPickerDialog(
            initial = customArgb,
            onConfirm = { onSetCustom(it); picking = false },
            onDismiss = { picking = false },
        )
    }
}

/**
 * The settings screen shows one of these at a time; the first screen is the list of them.
 *
 * Grouped by the surface each option acts on rather than by what kind of option it is. A-Z
 * settings used to be spread through Behavior while the search options sat nowhere near the
 * list they search, which meant knowing the implementation to find anything.
 */
private enum class SettingsSection(@StringRes val labelRes: Int) {
    APPEARANCE(R.string.settings_section_appearance),
    HOME(R.string.settings_section_home),
    APP_LIST(R.string.settings_section_app_list),
    GESTURES(R.string.settings_section_gestures),
    NOTIFICATIONS(R.string.settings_section_notifications),
    APPS(R.string.settings_section_apps),
    BACKUP(R.string.settings_section_backup),
    ABOUT(R.string.settings_section_about),
}

/**
 * One row of settings, as data rather than as a call in a section body.
 *
 * [keywords] is what else the row answers to, for the search box: the words someone would
 * reach for who does not already know what this screen calls the thing.
 */
private class SettingsEntry(
    val section: SettingsSection,
    val title: String,
    val keywords: String = "",
    val visible: Boolean = true,
    val content: @Composable () -> Unit,
) {
    fun matches(query: String): Boolean {
        val q = query.trim()
        return title.contains(q, ignoreCase = true) || keywords.contains(q, ignoreCase = true)
    }
}

@Composable
private fun BackupRow(label: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.padding(4.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun DimColorRow(dimColor: Int, onSetDimColor: (Int) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { picking = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_dim_color), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.settings_dim_color_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(Color(dimColor), CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), CircleShape)
        )
    }
    if (picking) {
        ColorPickerDialog(
            initial = dimColor,
            onConfirm = { onSetDimColor(it); picking = false },
            onDismiss = { picking = false },
        )
    }
}

/** A swatch to tap or a hex value to type; enough for picking a text color, and no library. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColorPickerDialog(initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var hex by remember { mutableStateOf(String.format("%06X", initial and 0xFFFFFF)) }
    val parsed = remember(hex) { hex.toIntOrNull(16)?.let { 0xFF000000.toInt() or it } }

    // The palette Android derived from the wallpaper, offered first: picking a color that
    // already belongs to the wallpaper is most of what anyone wants here, and typing its hex
    // is not something anyone knows off-hand.
    // The same palette the Material text color comes from, so the swatch you pick here and
    // the color that option gives you are drawn from one scheme. MaterialTheme's own follows
    // the system dark mode instead, which disagrees the moment a light wallpaper meets a dark
    // system theme.
    val scheme = rememberWallpaperPalette() ?: MaterialTheme.colorScheme
    val fromWallpaper = listOf(
        scheme.primary, scheme.secondary, scheme.tertiary,
        scheme.primaryContainer, scheme.surfaceVariant, scheme.surface,
    ).map { it.toArgb() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.text_color_pick)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.color_from_wallpaper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    fromWallpaper.forEach { argb ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(argb), CircleShape)
                                .border(
                                    width = if (parsed == argb) 3.dp else 1.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    shape = CircleShape,
                                )
                                .clickable { hex = String.format("%06X", argb and 0xFFFFFF) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SWATCHES.forEach { argb ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(argb), CircleShape)
                                .border(
                                    width = if (parsed == argb) 3.dp else 1.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    shape = CircleShape,
                                )
                                .clickable { hex = String.format("%06X", argb and 0xFFFFFF) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = hex,
                    onValueChange = { hex = it.filter { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' }.take(6) },
                    singleLine = true,
                    label = { Text("#RRGGBB") },
                    isError = parsed == null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) {
                Text(stringResource(R.string.action_done))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private val SWATCHES = listOf(
    0xFFFFFFFF.toInt(), 0xFFE3E8EC.toInt(), 0xFF9AA5B1.toInt(), 0xFF10161C.toInt(),
    0xFFEF5350.toInt(), 0xFFFFA726.toInt(), 0xFFFFEE58.toInt(), 0xFF66BB6A.toInt(),
    0xFF26C6DA.toInt(), 0xFF42A5F5.toInt(), 0xFF7E57C2.toInt(), 0xFFEC407A.toInt(),
)

/**
 * Getting to the accessibility toggle, and to the screen that unblocks it.
 *
 * Android 13 and later refuse to let an app installed outside an app store be switched on
 * under Accessibility at all — the toggle is there but greyed, with no explanation offered at
 * the point of failure. It has to be unblocked first from the app's own info screen, under the
 * overflow menu, so that screen is one tap away here rather than something to go hunting for.
 */
@Composable
private fun AccessibilityActions(onOpenAccessibilitySettings: () -> Unit, onOpenAppInfo: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledChip(stringResource(R.string.settings_app_info), selected = false, onClick = onOpenAppInfo)
        FilledChip(stringResource(R.string.settings_enable), selected = false, onClick = onOpenAccessibilitySettings)
    }
}

@Composable
private fun QuickLaunchRow(label: String, value: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                value ?: stringResource(R.string.settings_quick_launch_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun AlignmentRow(label: String, selected: HomeAlignment, onSelect: (HomeAlignment) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeAlignment.entries.forEach { option ->
                FilledChip(
                    label = stringResource(option.labelRes()),
                    selected = option == selected,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun IconSideRow(selected: IconSide, onSelect: (IconSide) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_icon_side), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconSide.entries.forEach { option ->
                FilledChip(
                    label = stringResource(option.labelRes()),
                    selected = option == selected,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

/**
 * Where the favorites come from: the list you keep, or the apps you actually open.
 *
 * The count only appears for the computed one, since the manual list is as long as you made it.
 */
@Composable
private fun FavoritesSourceRow(
    selected: FavoritesSource,
    count: Int,
    onSelect: (FavoritesSource) -> Unit,
    onSetCount: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_favorites_source), style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FavoritesSource.entries.forEach { source ->
                FilledChip(stringResource(source.labelRes()), selected == source) { onSelect(source) }
            }
        }
    }
    if (selected == FavoritesSource.FREQUENT) {
        SliderRow(
            label = stringResource(R.string.settings_frequent_count),
            value = count.toFloat(),
            range = FREQUENT_RANGE.first.toFloat()..FREQUENT_RANGE.last.toFloat(),
            valueLabel = count.toString(),
            onValueChange = { onSetCount(it.toInt()) },
        )
    }
}

@Composable
private fun ShortcutSwipeRow(selected: ShortcutSwipe, onSelect: (ShortcutSwipe) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_swipe_shortcuts), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.settings_swipe_shortcuts_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShortcutSwipe.entries.forEach { mode ->
                FilledChip(stringResource(mode.labelRes()), selected == mode) { onSelect(mode) }
            }
        }
    }
}

@Composable
private fun EdgeSideRow(selected: EdgeSide, onSelect: (EdgeSide) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_edge_side), style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EdgeSide.entries.forEach { side ->
                FilledChip(stringResource(side.labelRes()), selected == side) { onSelect(side) }
            }
        }
    }
}

/** Shared with the clock widget's own settings, which offers the same kind of choices. */
@Composable
internal fun FilledChip(
    label: String,
    selected: Boolean,
    fontFamily: FontFamily? = null,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = fontFamily,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}