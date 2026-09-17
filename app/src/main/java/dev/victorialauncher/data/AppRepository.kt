// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.victorialauncher.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
            // Belt as well as braces. The profiles above are read one call at a time, so the
            // lock can be granted between the state this list was asked for and the quiet mode
            // read here — and it is granted before the profile has actually stopped. A caller
            // that has just locked the space hands that state in, and this drops what it names
            // however the system happens to be answering at this instant.
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
     * front of the unlock, and this returns false until that has been answered.
     */
    fun togglePrivateSpace(): Boolean {
        if (Build.VERSION.SDK_INT < PRIVATE_SPACE_SDK) return false
        val state = privateSpace()
        // Nothing to ask about without a profile to ask about it. An uncertain space with one
        // is treated as locked, like everywhere else, so pressing the row tries to open it.
        val user = state.user ?: return false
        val lock = state is PrivateSpace.Unlocked
        return runCatching { userManager.requestQuietModeEnabled(lock, user) }.getOrDefault(false)
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
        // that one is treated as locked, and a locked space has to keep its way back in.
        if (state.user == null) return emptyList()
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

    /** Badged by the system, so a work or private-space app is recognizable at a glance. */
    fun loadIcon(app: AppInfo): Drawable {
        if (app.kind == EntryKind.PRIVATE_SPACE) return privateSpaceIcon(app)
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

    // An app's label comes back in the device's language, so on a Japanese phone the English
    // name is nowhere in the list. Loading it means asking the app's own resources for the
    // label a second time through an English configuration, which is a whole resource table
    // per app — so it is only ever asked for a name that has no A-Z letter of its own, and the
    // answer is kept.
    private val englishLabels = mutableMapOf<String, String?>()

    /** The app's name in English, or null if it has none or it is the name we already have. */
    fun englishLabel(app: AppInfo): String? = englishLabels.getOrPut(app.key) {
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

    /** A closed padlock while the space is locked, an open one while it is not. */
    private fun privateSpaceIcon(app: AppInfo): Drawable {
        val locked = app.componentName.className == PRIVATE_SPACE_LOCKED_CLASS
        val id = if (locked) R.mipmap.ic_private_space_locked else R.mipmap.ic_private_space_unlocked
        return ContextCompat.getDrawable(context, id) ?: pm.defaultActivityIcon
    }

    /** Returns false if the app could not be started, so callers can undo whatever they hid. */
    fun launch(app: AppInfo): Boolean {
        // Only an app has an activity to start. Anything else is started by the screen that
        // knows what it is, and arriving here means it was routed wrongly — which must not
        // launch anything and must not be counted as a launch.
        if (app.kind != EntryKind.APP) return false
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

    fun openAppInfo(app: AppInfo) {
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
