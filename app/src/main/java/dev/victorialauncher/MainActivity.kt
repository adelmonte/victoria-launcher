// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import dev.victorialauncher.data.AppFont
import dev.victorialauncher.service.StatusBarFader
import dev.victorialauncher.ui.VictoriaNavHost
import dev.victorialauncher.ui.common.IconConfig
import dev.victorialauncher.ui.common.LocalIconConfig
import dev.victorialauncher.ui.theme.VictoriaTheme
import kotlinx.coroutines.delay

/** How long a pull-down keeps the status bar on screen before it fades away again. */
private const val STATUS_BAR_PEEK_MS = 5000L

class MainActivity : ComponentActivity() {

    /** Bumped whenever HOME is pressed while we're already showing, so overlays can close. */
    private var homeIntentTick by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as VictoriaApp

        setContent {
            val hideStatusBar by app.prefs.hideStatusBar.collectAsState(initial = false)
            // A short pull-down peeks the status bar, then it slides away again.
            var statusBarPeek by remember { mutableStateOf(false) }
            LaunchedEffect(statusBarPeek) {
                if (statusBarPeek) {
                    delay(STATUS_BAR_PEEK_MS)
                    statusBarPeek = false
                }
            }
            LaunchedEffect(hideStatusBar, statusBarPeek) {
                StatusBarFader.setVisible(window, visible = !hideStatusBar || statusBarPeek)
            }

            val font by app.prefs.font.collectAsState(initial = AppFont.SYSTEM)
            val iconPackPackage by app.prefs.iconPackPackage.collectAsState(initial = null)
            val iconOverrides by app.prefs.iconOverrides.collectAsState(initial = emptyMap())
            val iconConfig = remember(iconPackPackage, iconOverrides) {
                IconConfig(iconPackPackage, iconOverrides)
            }

            VictoriaTheme(font = font) {
                CompositionLocalProvider(LocalIconConfig provides iconConfig) {
                    VictoriaNavHost(
                        app = app,
                        homeIntentTick = homeIntentTick,
                        font = font,
                        hideStatusBar = hideStatusBar,
                        iconPackPackage = iconPackPackage,
                        iconOverrides = iconOverrides,
                        onPeekStatusBar = { statusBarPeek = true },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing HOME re-delivers the intent to us; treat it as "go back to the home screen".
        homeIntentTick++
    }

    override fun onStart() {
        super.onStart()
        (application as VictoriaApp).widgetHost.startListening()
    }

    override fun onStop() {
        (application as VictoriaApp).widgetHost.stopListening()
        // The fader holds a static controller for this window; don't outlive the Activity.
        StatusBarFader.release()
        super.onStop()
    }
}