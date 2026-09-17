// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.shortcut

import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.UserHandle
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
import androidx.lifecycle.lifecycleScope
import dev.victorialauncher.R
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.data.EntryKeys
import dev.victorialauncher.ui.theme.VictoriaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STATE_ACCEPTED = "accepted"

/** A few tries spread over about a second, since [LauncherApps.PinItemRequest.accept] can take
 *  effect on the system side a moment after it returns, not before. */
private const val PIN_CONFIRM_ATTEMPTS = 5
private const val PIN_CONFIRM_DELAY_MS = 200L

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
 * the system *separately* reports the shortcut pinned, never on `accept()`'s own say-so.
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
     * on the home screen. A true answer is not trusted on its own either (SEC-M1): it only
     * means the system accepted the *request*, so the favorite is added only once
     * [waitUntilPinned] finds that shortcut in the system's own pinned set, which is the same
     * fact [dev.victorialauncher.data.AppRepository.queryPinnedShortcuts] would otherwise go
     * looking for on the next reload anyway.
     */
    private fun accept(request: LauncherApps.PinItemRequest, shortcut: ShortcutInfo) {
        if (accepted) return
        accepted = true
        if (!runCatching { request.accept() }.getOrDefault(false)) {
            finish()
            return
        }
        val launcherApps = getSystemService(LauncherApps::class.java)
        val pkg = shortcut.`package`
        val id = shortcut.id
        val user = shortcut.userHandle
        val serial = runCatching {
            getSystemService(UserManager::class.java).getSerialNumberForUser(user)
        }.getOrDefault(0L)
        lifecycleScope.launch {
            // The polling itself off the main thread, since it is blocking binder calls
            // repeated over about a second; finish() and the repository write go back onto it,
            // since an Activity method is not something to call from elsewhere.
            val pinned = withContext(Dispatchers.Default) { waitUntilPinned(launcherApps, pkg, id, user) }
            if (pinned) {
                // Straight into the favorites, because "add to the home screen" is the
                // question that was asked; the write is on the app's own scope so it outlives
                // this screen.
                (application as VictoriaApp).appRepository.addPinnedShortcut(
                    EntryKeys.shortcut(pkg, id, serial)
                )
            }
            finish()
        }
    }

    /**
     * Polls the system's own pinned-shortcut set rather than trusting accept()'s return value
     * alone (SEC-M1): a forged request can answer `accept()` however it likes from its own
     * binder, but it cannot make [LauncherApps.getShortcuts] report a shortcut pinned that
     * genuinely is not. `accept()` can also take a moment to land, hence the retries rather
     * than one look.
     */
    private suspend fun waitUntilPinned(
        launcherApps: LauncherApps,
        pkg: String,
        id: String,
        user: UserHandle,
    ): Boolean {
        val query = LauncherApps.ShortcutQuery()
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            .setPackage(pkg)
            .setShortcutIds(listOf(id))
        repeat(PIN_CONFIRM_ATTEMPTS) { attempt ->
            val pinned = runCatching { launcherApps.getShortcuts(query, user) }
                .getOrNull()
                .orEmpty()
                .any { it.id == id }
            if (pinned) return true
            if (attempt < PIN_CONFIRM_ATTEMPTS - 1) delay(PIN_CONFIRM_DELAY_MS)
        }
        return false
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
