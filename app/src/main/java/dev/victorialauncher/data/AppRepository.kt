// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import dev.victorialauncher.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A few tries spread over about a second, since a pin request's shortcut can take a moment
 *  to show up in the system's own pinned set after [LauncherApps.PinItemRequest.accept]
 *  returns, not before. */
private const val PIN_CONFIRM_ATTEMPTS = 5
private const val PIN_CONFIRM_DELAY_MS = 200L

class AppRepository(
    private val context: Context,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
) {

    private val pm: PackageManager get() = context.packageManager
    private val launcherApps: LauncherApps
        get() = context.getSystemService(LauncherApps::class.java)
    private val userManager: UserManager
        get() = context.getSystemService(UserManager::class.java)

    /**
     * Every launchable activity across every profile the launcher can see.
     *
     * LauncherApps rather than PackageManager, because queryIntentActivities only ever sees
     * the profile we are running in — a work profile or a private space is invisible to it.
     * LauncherApps also hands back the badged icon and the per-profile label, which is what
     * marks a work app as a work app.
     *
     * A locked private space simply drops out of getUserProfiles, so its apps disappear from
     * the list until it is unlocked. That is the intended behavior, not a failure to handle.
     */
    fun queryAllApps(): List<AppInfo> {
        val profiles = runCatching { userManager.userProfiles }.getOrNull().orEmpty()
        return profiles
            .flatMap { user ->
                val serial = runCatching { userManager.getSerialNumberForUser(user) }.getOrDefault(0L)
                // Asking about a profile we are not the launcher for throws rather than
                // returning nothing, and one inaccessible profile must not lose the rest.
                runCatching { launcherApps.getActivityList(null, user) }
                    .getOrNull()
                    .orEmpty()
                    .map { info ->
                        AppInfo(
                            componentName = info.componentName,
                            label = info.label?.toString() ?: info.componentName.packageName,
                            user = user,
                            userSerial = serial,
                        )
                    }
            }
            // Launching ourselves through the MAIN+LAUNCHER filter starts a task that isn't
            // rooted at HOME: it shows up in the app switcher and leaves the system unsure
            // which task is home until the default launcher is set again. Nothing good comes
            // of listing the launcher inside its own app list.
            .filterNot { it.componentName.packageName == context.packageName }
            // After that filter rather than before it: the reason for it is that starting our
            // own launcher activity corrupts the HOME task, which says nothing about a
            // shortcut, and one we published ourselves is started through LauncherApps like
            // any other.
            .plus(queryPinnedShortcuts())
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Bumped when the pinned set changes without any package changing — the confirm screen
     * accepting one, or a menu unpinning one. Nothing else would tell the list to be built
     * again, because no app was installed, removed or updated.
     */
    private val _shortcutChanges = MutableStateFlow(0)
    val shortcutChanges: StateFlow<Int> = _shortcutChanges.asStateFlow()

    private fun noteShortcutsChanged() {
        _shortcutChanges.update { it + 1 }
    }

    private data class PinnedShortcut(val info: ShortcutInfo, val user: UserHandle, val serial: Long)

    /**
     * Only the device's current home app may ask about shortcuts at all; anyone else is
     * answered with a SecurityException rather than an empty list. So every call below asks
     * this first, and a launcher that is not the one in use simply lists no shortcuts.
     */
    private fun hasShortcutHostPermission(): Boolean =
        runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    private fun pinnedShortcuts(): List<PinnedShortcut> {
        if (!hasShortcutHostPermission()) return emptyList()
        val profiles = runCatching { userManager.userProfiles }.getOrNull().orEmpty()
        return profiles.flatMap { user ->
            val serial = runCatching { userManager.getSerialNumberForUser(user) }.getOrDefault(0L)
            val query = LauncherApps.ShortcutQuery()
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            runCatching { launcherApps.getShortcuts(query, user) }
                .getOrNull()
                .orEmpty()
                .map { PinnedShortcut(it, user, serial) }
        }
    }

    /** Every pinned shortcut, as an ordinary row. */
    private fun queryPinnedShortcuts(): List<AppInfo> = pinnedShortcuts()
        // An id that would corrupt the newline-joined favorites list (CR-4) is not something
        // this store can hold at all, so such a shortcut never becomes a row in the first
        // place — PinShortcutActivity applies the same check before it can ever be pinned.
        .filter { EntryKeys.isStorableShortcutId(it.info.id) }
        .map { pinned ->
            val info = pinned.info
            AppInfo(
                // Always the publisher's own package, never `info.activity`'s — the activity
                // names which of the publisher's screens the shortcut opens, and is not
                // guaranteed to agree with `info.package` in shape even though it always does
                // in practice. Everything downstream (AppInfo.key, AppInfo.packageName, the
                // in-list package search, the icon-pack lookup skip, unpin's own
                // `pinShortcuts(pkg, …)` call) has to agree on one package, and the publisher's
                // is the only one guaranteed to be that shortcut's.
                componentName = ComponentName(info.`package`, info.activity?.className ?: ""),
                label = info.shortLabel?.toString() ?: info.longLabel?.toString() ?: info.`package`,
                user = pinned.user,
                userSerial = pinned.serial,
                kind = EntryKind.SHORTCUT,
                shortcutId = info.id,
                disabled = !info.isEnabled,
            )
        }

    /** The live ShortcutInfo behind a row, for its icon, its reason for being off, or a pin. */
    private fun findShortcut(app: AppInfo): ShortcutInfo? {
        val id = app.shortcutId ?: return null
        if (!hasShortcutHostPermission()) return null
        val query = LauncherApps.ShortcutQuery()
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            .setPackage(app.packageName)
            .setShortcutIds(listOf(id))
        return runCatching { launcherApps.getShortcuts(query, app.user ?: Process.myUserHandle()) }
            .getOrNull()
            ?.firstOrNull()
    }

    /**
     * Hands the package back every id it still has pinned, minus this one: pinShortcuts does
     * not remove one shortcut, it replaces the whole pinned set for a package in a profile,
     * so anything left out of that list is unpinned along with it.
     *
     * Then everything stored under the key goes too, but only once that call has actually
     * gone through: if it threw — the host permission was lost, the profile went away mid-
     * call — the shortcut is still pinned with the system, and forgetting its name, icon,
     * folder, hidden flag, launch count and quick-launch slot now would strand them with
     * nothing left to reconnect them to. An app can be reinstalled and find those waiting;
     * an unpinned shortcut never comes back, so what is left behind after a real unpin is
     * only clutter nothing can reach.
     */
    fun unpin(app: AppInfo) {
        if (app.kind != EntryKind.SHORTCUT) return
        val id = app.shortcutId ?: return
        val removed = EntryKeys.ShortcutRef(app.packageName, id, app.userSerial)
        val pinned = pinnedShortcuts().map {
            EntryKeys.ShortcutRef(it.info.`package`, it.info.id, it.serial)
        }
        // pinShortcuts replaces the whole pinned set for a package in a profile. If the list
        // just read does not even contain the shortcut being removed, that profile could not
        // be read this time, and what would be handed back is an empty list — unpinning every
        // other shortcut the package has there. Better to do nothing and say so.
        if (removed !in pinned) {
            Toast.makeText(context, R.string.shortcut_unpin_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val unpinned = runCatching {
            launcherApps.pinShortcuts(
                app.packageName,
                PinnedShortcuts.remainingIds(pinned, removed),
                app.user ?: Process.myUserHandle(),
            )
        }.isSuccess
        if (!unpinned) {
            Toast.makeText(context, R.string.shortcut_unpin_failed, Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch { prefs.forgetEntry(app.key) }
        noteShortcutsChanged()
    }

    /**
     * Called once [LauncherApps.PinItemRequest.accept] has returned true for a shortcut from
     * [pkg] with id [id] in profile [user] (serial [serial]): confirms the system actually
     * pinned it, then joins it to the favorites, because "add to the home screen" is what the
     * user was asked.
     *
     * Deliberately on this repository's own scope rather than the confirm screen's. That
     * screen finishes the instant it calls this, and accept() taking effect can lag its own
     * return by a moment (see [waitUntilPinned]) — if the write instead rode the screen's own
     * scope, Cancel, a tap outside, rotation, or the screen merely stopping in that window
     * would cancel it, leaving a shortcut the system has pinned but that never shows up here.
     *
     * Verifying rather than trusting accept()'s own answer is also what a forged pin request
     * cannot get past: its binder can answer accept() however it likes, but it cannot make
     * [waitUntilPinned] find a shortcut pinned that genuinely is not.
     */
    fun confirmPinnedShortcut(pkg: String, id: String, user: UserHandle, serial: Long) {
        scope.launch {
            if (waitUntilPinned(pkg, id, user)) {
                prefs.addFavorite(EntryKeys.shortcut(pkg, id, serial))
                noteShortcutsChanged()
            } else {
                // This whole coroutine runs on the repository's Default-dispatched scope, so
                // a Toast here needs Main asked for explicitly rather than however Toast.show
                // elsewhere in this class gets it for free by already being called from the UI.
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.shortcut_pin_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Polls rather than trusting one look, since the shortcut a request just accepted can take
     * a moment to reach the system's own pinned set.
     */
    private suspend fun waitUntilPinned(pkg: String, id: String, user: UserHandle): Boolean {
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

    /** Badged by the system, so a work or private-space app is recognizable at a glance. */
    fun loadIcon(app: AppInfo): Drawable {
        // A shortcut's own picture, badged with the app that published it. Falling through
        // means it has none of its own, and the publisher's app icon below is the answer.
        if (app.kind == EntryKind.SHORTCUT) shortcutIcon(app)?.let { return it }
        val user = app.user
        if (user != null) {
            val activity = runCatching {
                launcherApps.getActivityList(app.componentName.packageName, user)
                    .firstOrNull { it.componentName == app.componentName }
            }.getOrNull()
            activity?.let { info ->
                runCatching { info.getBadgedIcon(0) }.getOrNull()?.let { return it }
            }
        }
        return try {
            pm.getActivityIcon(app.componentName)
        } catch (e: PackageManager.NameNotFoundException) {
            try {
                pm.getApplicationIcon(app.componentName.packageName)
            } catch (e2: PackageManager.NameNotFoundException) {
                pm.defaultActivityIcon
            }
        }
    }

    private fun shortcutIcon(app: AppInfo): Drawable? {
        val info = findShortcut(app) ?: return null
        val density = context.resources.configuration.densityDpi
        return runCatching { launcherApps.getShortcutBadgedIconDrawable(info, density) }.getOrNull()
            ?: runCatching { launcherApps.getShortcutIconDrawable(info, density) }.getOrNull()
    }

    // An app's label comes back in the device's language, so on a Japanese phone the English
    // name is nowhere in the list. Loading it means asking the app's own resources for the
    // label a second time through an English configuration, which is a whole resource table
    // per app — so it is only ever asked for a name that has no A-Z letter of its own, and the
    // answer is kept.
    private val englishLabels = mutableMapOf<String, String?>()

    /**
     * The app's name in English, or null if it has none or it is the name we already have.
     *
     * Null for anything that is not an app: a shortcut is named by whoever pinned it, and
     * there are no resources of its own to ask a second time.
     */
    fun englishLabel(app: AppInfo): String? {
        if (app.kind != EntryKind.APP) return null
        return englishLabels.getOrPut(app.key) {
            runCatching {
                val info = pm.getActivityInfo(app.componentName, 0)
                val labelRes = if (info.labelRes != 0) info.labelRes else info.applicationInfo.labelRes
                if (labelRes == 0) return@runCatching null
                val res = pm.getResourcesForApplication(info.applicationInfo)
                val config = Configuration(res.configuration).apply { setLocale(Locale.ENGLISH) }
                res.getString(labelRes).takeIf { it.isNotBlank() }?.let { english ->
                    // The context-adjusted resources fall back to the default language when an app
                    // ships no English, which just hands the same name back.
                    Resources(res.assets, res.displayMetrics, config).getString(labelRes)
                }
            }.getOrNull()
        }
    }

    /** Returns false if the app could not be started, so callers can undo whatever they hid. */
    fun launch(app: AppInfo): Boolean {
        if (app.kind == EntryKind.SHORTCUT) return launchShortcut(app)
        if (app.componentName.packageName == context.packageName) return false
        val started = runCatching {
            // Through LauncherApps so an app in another profile starts as that profile; a
            // plain startActivity would look for it in ours and find nothing.
            launcherApps.startMainActivity(app.componentName, app.user ?: Process.myUserHandle(), null, null)
            true
        }.getOrElse {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(app.componentName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            }.getOrDefault(false)
        }
        if (started) {
            // Counted whether or not the usage sort is on, so switching it on later has a
            // history to order by instead of starting from nothing.
            scope.launch { prefs.incrementLaunchCount(app.key) }
        }
        return started
    }

    private fun launchShortcut(app: AppInfo): Boolean {
        val id = app.shortcutId ?: return false
        if (app.disabled) {
            // The publisher's own wording wherever there is any: it is the only thing that
            // can say why, and "sign in again to use this" beats a shrug.
            val message = findShortcut(app)?.disabledMessage?.toString()?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.shortcut_unavailable)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return false
        }
        // Through LauncherApps and nothing else. The Intent inside a ShortcutInfo belongs to
        // the app that published it and is never handed to a launcher; rebuilding one from
        // its parts would be starting something on that app's behalf that it did not ask for.
        val started = runCatching {
            launcherApps.startShortcut(app.packageName, id, null, null, app.user ?: Process.myUserHandle())
            true
        }.getOrDefault(false)
        if (started) {
            scope.launch { prefs.incrementLaunchCount(app.key) }
        }
        return started
    }

    fun openAppInfo(app: AppInfo) {
        // Only an app has one. The menus leave the item out for everything else; this is so a
        // caller that has not can do no harm.
        if (app.kind != EntryKind.APP) return
        val user = app.user
        if (user != null) {
            val shown = runCatching {
                launcherApps.startAppDetailsActivity(app.componentName, user, null, null)
                true
            }.getOrDefault(false)
            if (shown) return
        }
        openAppInfo(app.componentName.packageName)
    }

    fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
