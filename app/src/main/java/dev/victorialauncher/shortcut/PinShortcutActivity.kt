// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.shortcut

import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.UserManager
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.victorialauncher.R
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.data.EntryKeys
import dev.victorialauncher.ui.theme.VictoriaTheme

private const val STATE_ACCEPTED = "accepted"

/**
 * The confirmation an app's pin request raises. The system starts this by name in whichever
 * launcher is currently HOME, which makes it the one part of the launcher another app can
 * reach — so it takes nothing on trust: not the intent it was started with, not the request
 * inside it, and not that it is being shown for the first time.
 *
 * What it must not do is accept twice. A second accept would pin a second copy of the same
 * shortcut and add a second favorite, which is why the flag survives a rotation or a process
 * death in the saved state rather than living only in the composition.
 *
 * Anything it cannot answer — no request, one the system has already finished with, one
 * asking for a widget — it finishes on. Declining a pin request is exactly that: not
 * accepting it. There is nothing to send back.
 */
class PinShortcutActivity : ComponentActivity() {

    private var accepted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accepted = savedInstanceState?.getBoolean(STATE_ACCEPTED) == true

        val launcherApps = getSystemService(LauncherApps::class.java)
        // The request travels as an extra, so an intent sent by hand on this action — empty,
        // or carrying junk — simply has none, and a broken one is caught rather than crashing
        // a screen that appeared over whatever the user was doing.
        val request = runCatching { launcherApps.getPinItemRequest(intent) }.getOrNull()
        val shortcut = request
            ?.takeIf {
                !accepted &&
                    runCatching { it.isValid }.getOrDefault(false) &&
                    it.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT
            }
            ?.shortcutInfo
        if (request == null || shortcut == null) {
            finish()
            return
        }

        val density = resources.configuration.densityDpi
        // A request's shortcut is not pinned yet, but LauncherApps will still draw it: that is
        // the whole point of being handed one before answering.
        val icon = runCatching { launcherApps.getShortcutBadgedIconDrawable(shortcut, density) }.getOrNull()
            ?: runCatching { launcherApps.getShortcutIconDrawable(shortcut, density) }.getOrNull()
        val label = shortcut.shortLabel?.toString()
            ?: shortcut.longLabel?.toString()
            ?: shortcut.`package`
        val publisher = runCatching {
            packageManager.getApplicationInfo(shortcut.`package`, 0).loadLabel(packageManager).toString()
        }.getOrNull() ?: shortcut.`package`

        setContent {
            VictoriaTheme {
                AlertDialog(
                    // Tapping away is a decision too, and the same one Cancel is.
                    onDismissRequest = { finish() },
                    icon = { ShortcutIcon(icon) },
                    title = { Text(stringResource(R.string.shortcut_pin_title)) },
                    text = {
                        Column {
                            Text(label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.shortcut_pin_from, publisher),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { accept(request, shortcut) }) {
                            Text(stringResource(R.string.shortcut_pin_add))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { finish() }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_ACCEPTED, accepted)
    }

    /**
     * Set before accept() rather than after, so a second tap that arrives while the system is
     * still working on the first finds the door already shut.
     *
     * accept() answering false means the system would not pin it after all — an expired
     * request, a publisher that has since been uninstalled — and then there is nothing to put
     * on the home screen.
     */
    private fun accept(request: LauncherApps.PinItemRequest, shortcut: ShortcutInfo) {
        if (accepted) return
        accepted = true
        if (runCatching { request.accept() }.getOrDefault(false)) {
            val serial = runCatching {
                getSystemService(UserManager::class.java).getSerialNumberForUser(shortcut.userHandle)
            }.getOrDefault(0L)
            // Straight into the favorites, because "add to the home screen" is the question
            // that was asked; the write is on the app's own scope so it outlives this screen.
            (application as VictoriaApp).appRepository.addPinnedShortcut(
                EntryKeys.shortcut(shortcut.`package`, shortcut.id, serial)
            )
        }
        finish()
    }
}

@Composable
private fun ShortcutIcon(drawable: Drawable?) {
    AndroidView(
        modifier = Modifier.size(48.dp),
        factory = { ctx -> ImageView(ctx) },
        update = { it.setImageDrawable(drawable) },
    )
}
