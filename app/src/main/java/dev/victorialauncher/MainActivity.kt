// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import dev.victorialauncher.data.AppFont
import dev.victorialauncher.data.IconShape
import dev.victorialauncher.media.NowPlayingListenerService
import dev.victorialauncher.service.StatusBarFader
import dev.victorialauncher.ui.VictoriaNavHost
import dev.victorialauncher.ui.common.IconConfig
import dev.victorialauncher.ui.common.LocalIconConfig
import dev.victorialauncher.ui.theme.VictoriaTheme
import kotlinx.coroutines.delay

/** One keystroke taken on the home screen; a null character is a backspace. */
data class TypedKey(val seq: Long, val char: Char?)

/** Smallest width that counts as a tablet, which is the platform's own threshold. */
private const val TABLET_WIDTH_DP = 600

class MainActivity : ComponentActivity() {

    /** Bumped whenever HOME is pressed while we're already showing, so overlays can close. */
    private var homeIntentTick by mutableStateOf(0)

    /**
     * What the status bar was last asked to be, so it can be asked again if it stopped obeying.
     *
     * Fully expanding the notification shade hands the system bars to the system, which cancels
     * the control this app holds over them. Collapsing the shade does not hand them back, and
     * nothing here had changed its mind — so the bar stayed up with the app still believing it
     * was hidden, until some setting changed and the request was made afresh.
     */
    private var desiredStatusBarVisible: Boolean? = null

    /**
     * Keystrokes taken on the home screen, as a growing list so none is dropped.
     *
     * Each is stamped with a count rather than replacing the last: typing quickly produced two
     * characters before the first had been read, and the second overwrote it.
     */
    private var typedToSearch by mutableStateOf<List<TypedKey>>(emptyList())
    private var nextTypedSeq = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        allowDrawingBehindTheCutout()
        // Portrait until told otherwise on a phone, free on a tablet: this is a list down the
        // side of the screen, and a phone sideways has about a third of the height it needs.
        // The setting exists because that is a judgement about a screen rather than about a
        // person — a phone in a car mount wants it anyway.
        val rotatesByDefault = resources.configuration.smallestScreenWidthDp >= TABLET_WIDTH_DP
        val app = application as VictoriaApp

        setContent {
            val hideStatusBar by app.prefs.hideStatusBar.collectAsState(initial = false)
            val hideStatusBarAppList by app.prefs.hideStatusBarAppList.collectAsState(initial = false)
            val peekSeconds by app.prefs.statusBarPeekSeconds.collectAsState(initial = 5)
            // A short pull-down peeks the status bar, then it slides away again.
            var statusBarPeek by remember { mutableStateOf(false) }
            // The two surfaces choose separately, so the overlay can keep the bar the home
            // screen hides.
            var appListOpen by remember { mutableStateOf(false) }
            LaunchedEffect(statusBarPeek, peekSeconds) {
                if (statusBarPeek) {
                    delay(peekSeconds * 1000L)
                    statusBarPeek = false
                }
            }
            val hideHere = if (appListOpen) hideStatusBarAppList else hideStatusBar
            val statusBarVisible = !hideHere || statusBarPeek
            // Keyed on the answer, not on what went into it. Re-asking for a state the bar is
            // already in restarts the fade, and a fade out begins by holding the bar fully
            // shown — so opening the list with both set to hide flashed it into view.
            LaunchedEffect(statusBarVisible) {
                desiredStatusBarVisible = statusBarVisible
                StatusBarFader.setVisible(window, visible = statusBarVisible)
            }

            val font by app.prefs.font.collectAsState(initial = AppFont.SYSTEM)
            val fontFile by app.prefs.fontFile.collectAsState(initial = null)
            val allowRotation by app.prefs.allowRotation.collectAsState(initial = null)
            LaunchedEffect(allowRotation, rotatesByDefault) {
                requestedOrientation = if (allowRotation ?: rotatesByDefault) {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }
            val iconPackPackage by app.prefs.iconPackPackage.collectAsState(initial = null)
            val iconOverrides by app.prefs.iconOverrides.collectAsState(initial = emptyMap())
            val showAppIcons by app.prefs.showAppIcons.collectAsState(initial = true)
            val themedIcons by app.prefs.themedIcons.collectAsState(initial = false)
            val iconShape by app.prefs.iconShape.collectAsState(initial = IconShape.SYSTEM)
            val iconConfig = remember(
                iconPackPackage,
                iconOverrides,
                showAppIcons,
                themedIcons,
                iconShape,
            ) {
                IconConfig(iconPackPackage, iconOverrides, showAppIcons, themedIcons, iconShape)
            }

            VictoriaTheme(font = font, fontFile = fontFile) {
                CompositionLocalProvider(LocalIconConfig provides iconConfig) {
                    VictoriaNavHost(
                        app = app,
                        homeIntentTick = homeIntentTick,
                        typedToSearch = typedToSearch,
                        onTypedToSearchHandled = { handled -> typedToSearch = typedToSearch - handled.toSet() },
                        font = font,
                        hideStatusBar = hideStatusBar,
                        hideStatusBarAppList = hideStatusBarAppList,
                        iconPackPackage = iconPackPackage,
                        iconOverrides = iconOverrides,
                        onPeekStatusBar = { statusBarPeek = true },
                        onAppListVisibleChange = { appListOpen = it },
                    )
                }
            }
        }
    }

    /**
     * Lets the window reach the whole screen, cutout included.
     *
     * Drawing edge to edge is not on its own enough to be allowed up there. The default rule is
     * that a window may extend into the cutout only while the cutout is covered by a system bar
     * — so with the status bar hidden the window is pushed below it instead, and the strip it
     * leaves behind belongs to nobody: the wallpaper shows through the dim on the home screen
     * and through the settings screen's own background.
     *
     * ALWAYS rather than SHORT_EDGES wherever there is one, because this rotates: sideways the
     * cutout is on a long edge, which SHORT_EDGES does not cover and which would letterbox the
     * window all over again. What the insets mean is unchanged — the screens that must keep
     * clear of the cutout already pad themselves by it.
     */
    private fun allowDrawingBehindTheCutout() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    /**
     * Typing on a hardware keyboard opens the app list and starts searching.
     *
     * A phone with keys has nowhere else for a keystroke to go on a home screen, and reaching
     * for the search field first is the long way round to the thing you already started
     * spelling. Only printable characters count: the volume keys, the arrows and everything
     * else keep doing their own jobs.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
            return super.onKeyDown(keyCode, event)
        }
        // Backspace once something has been typed, or the only way to fix a typo is to throw
        // the whole search away and start it again.
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            typedToSearch = typedToSearch + TypedKey(nextTypedSeq++, null)
            return true
        }
        val typed = event.unicodeChar.takeIf { it != 0 }?.toChar()
        if (typed == null || typed.isISOControl()) return super.onKeyDown(keyCode, event)
        // A space is a character in half the app names there are, but it is not something to
        // open a search with — on its own it would start a query that looks like nothing.
        if (typed == ' ' && typedToSearch.isEmpty()) return super.onKeyDown(keyCode, event)
        typedToSearch = typedToSearch + TypedKey(nextTypedSeq++, typed)
        return true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Focus comes back when the shade closes, which is exactly when the bar may have been
        // left behind. Asked again only when it is actually wrong: asking for the state it is
        // already in restarts the fade, and a fade out begins by showing the bar in full.
        if (!hasFocus) return
        val want = desiredStatusBarVisible ?: return
        val actual = ViewCompat.getRootWindowInsets(window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: return
        if (actual != want) StatusBarFader.setVisible(window, visible = want)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing HOME re-delivers the intent to us; treat it as "go back to the home screen".
        homeIntentTick++
    }

    override fun onStart() {
        super.onStart()
        (application as VictoriaApp).widgetHost.startListening()
        NowPlayingListenerService.rebindIfPermitted(this)
    }

    override fun onStop() {
        (application as VictoriaApp).widgetHost.stopListening()
        // The fader holds a static controller for this window; don't outlive the Activity.
        StatusBarFader.release()
        super.onStop()
    }
}