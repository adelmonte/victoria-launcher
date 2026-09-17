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
 * `getPinItemRequest` only reads a parcelable out of the intent; its own `isValid`/`accept`
 * answer from a binder that any app holding the request's extra could have implemented itself,
 * so on their own they prove nothing about who is actually asking. A caller's uid would settle
 * that, but `Activity.getLaunchedFromUid()` only reports one for an activity started with
 * `startActivityForResult`, which this is not — measured on the API 35 emulator, a genuine
 * system-issued request comes back `-1` (unavailable), the same as a forged one would, so that
 * check would guard nothing and is deliberately not here. What actually stands between a
 * forged request and a planted favorite is that win or lose, a favorite is only ever added once
 * the system *separately* reports the shortcut pinned, never on `accept()`'s own say-so — and
 * that check, along with the write it guards, lives in
 * [dev.victorialauncher.data.AppRepository.confirmPinnedShortcut] rather than here, so that
 * this screen finishing the instant it calls it can never cancel it.
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

        // Tapping through a decoy overlaid on Add must not silently grant a pin request meant
        // for something else on screen (SEC-L2). setHideOverlayWindows needs a permission this
        // app does not ask for, so this is the guard that does not.
        window.decorView.filterTouchesWhenObscured = true

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
            ?.takeIf { EntryKeys.isStorableShortcutId(it.id) }
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
     * on the home screen. A true answer is not trusted on its own either: it only means the
     * system accepted the *request*, and the favorite is added only once
     * [dev.victorialauncher.data.AppRepository.confirmPinnedShortcut] finds that shortcut in
     * the system's own pinned set. finish() happens right after handing off to it rather than
     * waiting on that check, because this screen is `noHistory` and declares no
     * `configChanges` — Cancel, a tap outside, rotation, or the screen merely stopping would
     * otherwise be able to cancel the wait and lose the write even though the system had
     * already pinned the shortcut.
     */
    private fun accept(request: LauncherApps.PinItemRequest, shortcut: ShortcutInfo) {
        if (accepted) return
        accepted = true
        if (!runCatching { request.accept() }.getOrDefault(false)) {
            finish()
            return
        }
        val pkg = shortcut.`package`
        val id = shortcut.id
        val user = shortcut.userHandle
        val serial = runCatching {
            getSystemService(UserManager::class.java).getSerialNumberForUser(user)
        }.getOrDefault(0L)
        (application as VictoriaApp).appRepository.confirmPinnedShortcut(pkg, id, user, serial)
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
