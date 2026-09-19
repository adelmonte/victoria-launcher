// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
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
     * The last serial a private space positively reported, kept only so that a read which
     * fails does not un-conceal what an earlier read concealed. In memory and never written
     * down: it is a fact about this device right now, and a file recording that a private
     * space exists is itself something for someone to find on the phone.
     */
    @Volatile
    private var lastKnownPrivateSerial: Long = 0L

    /**
     * Every launchable activity across every profile the launcher may show, decided profile by
     * profile before any of it is turned into rows.
     *
     * LauncherApps rather than PackageManager, because queryIntentActivities only ever sees
     * the profile we are running in — a work profile or a private space is invisible to it.
     * LauncherApps also hands back the badged icon and the per-profile label, which is what
     * marks a work app as a work app.
     *
     * The decision is made here, by UserHandle, rather than by filtering finished rows by
     * serial afterwards, because a profile whose serial could not be read produces rows whose
     * keys are indistinguishable from the main profile's — there is nothing left to filter by
     * at that point, and those keys would be stored into favorites and launch counts.
     */
    fun queryAllApps(privateSpace: PrivateSpace = privateSpace()): List<AppInfo> {
        val profiles = runCatching { userManager.userProfiles }.getOrNull().orEmpty()
        val mainUser = Process.myUserHandle()
        return profiles
            .flatMap { user ->
                val isMain = user == mainUser
                val serial = runCatching { userManager.getSerialNumberForUser(user) }.getOrNull()
                // Only asked about a profile the answers could change anything for: the one we
                // run in is always listed, and below Android 15 there is no private space for
                // either answer to describe.
                val classify = !isMain && Build.VERSION.SDK_INT >= PRIVATE_SPACE_SDK
                val listed = shouldListProfile(
                    isMainUser = isMain,
                    sdk = Build.VERSION.SDK_INT,
                    userType = if (classify) userType(user) else null,
                    quietMode = if (classify) quietMode(user) else null,
                    serial = serial,
                )
                if (!listed) return@flatMap emptyList()
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
                            // Null only reaches here for the profile we run in, whose serial is
                            // zero anyway; every other profile without one was dropped above.
                            userSerial = serial ?: 0L,
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
            // Belt as well as braces, and after the shortcuts so that it covers them too. The
            // profiles above are read one call at a time, so the lock can be granted between
            // the state this list was asked for and the quiet mode read here — and it is
            // granted before the profile has actually stopped. A caller that has just locked
            // the space hands that state in, and this drops what it names however the system
            // happens to be answering at this instant.
            .filterNot { privateSpace.conceals(it.key) }
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase() }
            // After the own-package filter, which would otherwise drop it: the row is ours.
            .plus(privateSpaceRow(privateSpace))
    }

    /**
     * What kind of profile this is, or null when the platform will not say — which everything
     * here reads as "it might be the private one" and treats accordingly.
     */
    private fun userType(user: UserHandle): String? =
        if (Build.VERSION.SDK_INT < PRIVATE_SPACE_SDK) null
        else runCatching { launcherApps.getLauncherUserInfo(user)?.userType }.getOrNull()

    /** Whether the profile is switched off, or null when the platform will not say. */
    private fun quietMode(user: UserHandle): Boolean? =
        runCatching { userManager.isQuietModeEnabled(user) }.getOrNull()

    /**
     * The private space as this launcher can see it right now.
     *
     * Asked fresh every time rather than remembered, because the system locks the space on its
     * own — it re-locks when the screen goes off — and a remembered answer would go stale with
     * nothing here being called.
     *
     * Every call is runCatching-wrapped: they throw when the launcher is not the default home,
     * which is the condition Android puts on the permission, and a launcher that is not the
     * default home must behave exactly as it did before any of this existed. What a failure
     * never produces is [PrivateSpace.Absent], which conceals nothing: not being able to read
     * the space is not the same as there not being one, and only the second of those is safe
     * to act on.
     */
    fun privateSpace(): PrivateSpace {
        // getLauncherUserInfo arrived in API 35, which is also the first Android to have a
        // private space at all, so below it there is nothing to look for.
        if (Build.VERSION.SDK_INT < PRIVATE_SPACE_SDK) return PrivateSpace.Absent
        val mainUser = Process.myUserHandle()
        // LauncherApps.profiles rather than UserManager.userProfiles: without the permission
        // UserManager still lists the private profile while LauncherApps does not, and it is
        // LauncherApps that decides whether anything inside it can be read.
        val profiles = runCatching { launcherApps.profiles }.getOrNull()
            // The list itself refusing is not an answer of "there is no private space".
            ?: return uncertain(user = null)
        // A profile that would not say what it is could be the private one, so a pass that
        // found no private space but did meet one of those has not established anything.
        var unclassified = false
        for (user in profiles) {
            if (user == mainUser) continue
            val type = userType(user)
            if (type == null) {
                unclassified = true
                continue
            }
            if (type != USER_TYPE_PROFILE_PRIVATE) continue
            // Zero is the main profile's serial, so a profile reporting it means the lookup
            // failed. Concealing by a zero serial conceals nothing, which is the one outcome
            // worth refusing outright — so it becomes an uncertain space rather than an open
            // one, and falls back to the last serial this space was known by.
            val serial = runCatching { userManager.getSerialNumberForUser(user) }.getOrNull()
                ?.takeIf { it != 0L }
                ?: return uncertain(user)
            lastKnownPrivateSerial = serial
            return when (privateSpaceKind(type, quietMode(user))) {
                PrivateSpaceKind.UNLOCKED -> PrivateSpace.Unlocked(user, serial)
                // Locked, and also the unreachable case: the type was matched just above, and
                // locked is the reading to take if that ever stopped being true.
                else -> PrivateSpace.Locked(user, serial)
            }
        }
        // Nothing here said it was a private space. That is only an absence if every profile
        // did say what it was, and if no space has answered earlier in this session — a space
        // that has gone missing from a list it used to be in is a read that failed, not a
        // space that was deleted, and the difference is not one to guess in this direction.
        return if (unclassified || lastKnownPrivateSerial != 0L) uncertain(null) else PrivateSpace.Absent
    }

    /**
     * A space we know is there, or might be, and cannot describe: concealed by the last serial
     * it was known by, which is zero when it has never given one up.
     */
    private fun uncertain(user: UserHandle?): PrivateSpace =
        PrivateSpace.Uncertain(user, lastKnownPrivateSerial)

    /**
     * Locks an open space and asks for a locked one to be opened. What that takes is the
     * system's to decide: on a phone with a screen lock it puts its own authentication in
     * front of the unlock, and this answers null until that has been answered.
     *
     * Returns the state to hold from now on when a lock was granted, so whoever pressed the
     * row can conceal at that moment rather than waiting for the broadcast that follows. Null
     * when nothing was granted, and null for an unlock: nothing is exposed by an unlock being
     * a moment late, and there is nothing to list until the profile is actually up again.
     *
     * Suspending because this is several binder calls and, on a phone with a screen lock, the
     * system's own authentication — none of which belongs on the main thread, and all of which
     * starts from a press on a row.
     */
    suspend fun togglePrivateSpace(): PrivateSpace? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < PRIVATE_SPACE_SDK) return@withContext null
        val state = privateSpace()
        // Nothing to ask about without a profile to ask about it. An uncertain space with one
        // is treated as locked, like everywhere else, so pressing the row tries to open it.
        val user = state.user ?: return@withContext null
        val lock = state is PrivateSpace.Unlocked
        val granted = runCatching { userManager.requestQuietModeEnabled(lock, user) }.getOrDefault(false)
        if (!granted || !lock) return@withContext null
        // The profile takes a moment to actually stop after the lock is granted. This state is
        // what the caller holds and hands to the enumeration that follows: asked again right
        // now, the system would describe an open space and list everything inside it.
        PrivateSpace.Locked(user, state.serial)
    }

    /**
     * The row that opens and closes the space, or nothing when there is no space to open.
     *
     * It is not an app and has no activity of its own, so it names itself after this package
     * and says which padlock it is in its class name — which is also what tells the icon cache
     * the two apart, since that cache is keyed by the row and not by the state of the device.
     */
    fun privateSpaceRow(state: PrivateSpace): List<AppInfo> {
        // Offered for any profile this pass could name, including one it could not describe:
        // that one is treated as locked, and a locked space has to keep its way back in. And
        // for a space that named itself earlier in this session and will not now, where there
        // is no profile left to point at — see PrivateSpace.offersPadlockRow.
        if (!state.offersPadlockRow) return emptyList()
        // Unlocked is the only state with an open padlock, and it always names its profile,
        // so a row offered without one is always the closed padlock.
        val className =
            if (state is PrivateSpace.Unlocked) PRIVATE_SPACE_UNLOCKED_CLASS else PRIVATE_SPACE_LOCKED_CLASS
        return listOf(
            AppInfo(
                componentName = ComponentName(context.packageName, className),
                label = context.getString(R.string.private_space),
                kind = EntryKind.PRIVATE_SPACE,
            )
        )
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

    /**
     * The shortcuts an app publishes about itself — what a long press on it offers.
     *
     * Both the ones declared in its manifest and the ones it adds as it runs, ordered the way
     * the app ranked them. Asked of the activity first, since an app with more than one
     * launcher icon ranks them per icon; a package that answers nothing that way is asked as a
     * whole rather than left looking as though it publishes none.
     */
    fun appShortcuts(app: AppInfo): List<ShortcutInfo> {
        if (app.kind != EntryKind.APP) return emptyList()
        val user = app.user ?: Process.myUserHandle()
        val flags = LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC

        fun query(withActivity: Boolean): List<ShortcutInfo> {
            val q = LauncherApps.ShortcutQuery()
                .setPackage(app.componentName.packageName)
                .setQueryFlags(flags)
            if (withActivity) q.setActivity(app.componentName)
            // Throws rather than returning nothing when this launcher does not hold the home
            // role, which is a state it can be in for a moment after being switched to.
            return runCatching { launcherApps.getShortcuts(q, user) }.getOrNull().orEmpty()
        }

        val found = query(withActivity = true).ifEmpty { query(withActivity = false) }
        return found.filter { it.isEnabled }.sortedBy { it.rank }
    }

    /** Starts one of [appShortcuts]; the publisher's own Intent is never touched. */
    fun startAppShortcut(shortcut: ShortcutInfo): Boolean = runCatching {
        launcherApps.startShortcut(shortcut, null, null)
        true
    }.getOrDefault(false)

    /** The icon for a shortcut offered in a menu. */
    fun shortcutIcon(shortcut: ShortcutInfo, densityDpi: Int): Drawable? =
        runCatching { launcherApps.getShortcutIconDrawable(shortcut, densityDpi) }.getOrNull()

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
        // Ours, and drawn from our own resources, so there is nothing to ask the system for.
        if (app.kind == EntryKind.PRIVATE_SPACE) return privateSpaceIcon(app)
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

    /** A closed padlock while the space is locked, an open one while it is not. */
    private fun privateSpaceIcon(app: AppInfo): Drawable {
        val locked = app.componentName.className == PRIVATE_SPACE_LOCKED_CLASS
        val id = if (locked) R.mipmap.ic_private_space_locked else R.mipmap.ic_private_space_unlocked
        return ContextCompat.getDrawable(context, id) ?: pm.defaultActivityIcon
    }

    /**
     * Returns false if the app could not be started, so callers can undo whatever they hid.
     *
     * [slideFrom] replaces the system's own opening animation with one that carries the app in
     * from an edge. Only a gesture that moved sideways has any business asking for it, and only
     * then because the app arriving the same way the finger went is the whole point.
     */
    fun launch(app: AppInfo, slideFrom: SlideFrom? = null): Boolean {
        if (app.kind == EntryKind.SHORTCUT) return launchShortcut(app, slideFrom)
        // Only an app has an activity to start. The private space row is opened by the screen
        // that knows what it is, and arriving here means it was routed wrongly — which must not
        // launch anything and must not be counted as a launch.
        if (app.kind != EntryKind.APP) return false
        if (app.componentName.packageName == context.packageName) return false
        val options = slideOptions(slideFrom)
        val started = runCatching {
            // Through LauncherApps so an app in another profile starts as that profile; a
            // plain startActivity would look for it in ours and find nothing.
            launcherApps.startMainActivity(app.componentName, app.user ?: Process.myUserHandle(), null, options)
            true
        }.getOrElse {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(app.componentName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    options,
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

    /**
     * The animation as a bundle, or null to leave the system's alone.
     *
     * A ROM is free to ignore it, and one that has animations turned off will. Nothing here
     * depends on it being honored — it is the opening of the app that matters.
     */
    private fun slideOptions(slideFrom: SlideFrom?): Bundle? = slideFrom?.let { side ->
        val enter = if (side == SlideFrom.RIGHT) R.anim.app_open_enter_right else R.anim.app_open_enter_left
        val exit = if (side == SlideFrom.RIGHT) R.anim.app_open_exit_left else R.anim.app_open_exit_right
        runCatching { ActivityOptions.makeCustomAnimation(context, enter, exit).toBundle() }.getOrNull()
    }

    private fun launchShortcut(app: AppInfo, slideFrom: SlideFrom? = null): Boolean {
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
            launcherApps.startShortcut(
                app.packageName,
                id,
                null,
                slideOptions(slideFrom),
                app.user ?: Process.myUserHandle(),
            )
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
