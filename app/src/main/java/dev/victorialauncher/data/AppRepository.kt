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
     * Every launchable activity across every profile the launcher can see.
     *
     * LauncherApps rather than PackageManager, because queryIntentActivities only ever sees
     * the profile we are running in — a work profile or a private space is invisible to it.
     * LauncherApps also hands back the badged icon and the per-profile label, which is what
     * marks a work app as a work app.
     */
    fun queryAllApps(privateSpace: PrivateSpace = privateSpace()): List<AppInfo> {
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
            // A locked private space answers LauncherApps exactly as an open one does: the
            // profile is still listed and every activity in it is still handed back, measured
            // on Android 15. Nothing about the lock hides them, so the launcher has to — by
            // serial, rather than by trusting an empty answer that never comes.
            .filterNot { privateSpace.conceals(it.key) }
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase() }
            // After the own-package filter, which would otherwise drop it: the row is ours.
            .plus(privateSpaceRow(privateSpace))
    }

    /**
     * The private space as this launcher can see it right now.
     *
     * Asked fresh every time rather than remembered, because the system locks the space on its
     * own — it re-locks when the screen goes off — and a remembered answer would go stale with
     * nothing here being called.
     *
     * Every call is runCatching-wrapped: they throw when the launcher is not the default home,
     * which is the condition Android puts on the permission, and a launcher that is not the
     * default home must behave exactly as it did before any of this existed.
     */
    fun privateSpace(): PrivateSpace {
        // getLauncherUserInfo arrived in API 35, which is also the first Android to have a
        // private space at all, so below it there is nothing to look for.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return PrivateSpace.Absent
        // LauncherApps.profiles rather than UserManager.userProfiles: without the permission
        // UserManager still lists the private profile while LauncherApps does not, and it is
        // LauncherApps that decides whether anything inside it can be read.
        val profiles = runCatching { launcherApps.profiles }.getOrNull().orEmpty()
        for (user in profiles) {
            val type = runCatching { launcherApps.getLauncherUserInfo(user)?.userType }.getOrNull()
            val quiet = runCatching { userManager.isQuietModeEnabled(user) }.getOrNull() ?: continue
            val serial = runCatching { userManager.getSerialNumberForUser(user) }.getOrDefault(0L)
            // Zero is the main profile's serial, so a profile reporting it means the lookup
            // failed. Concealing by serial would then conceal nothing, which is the one
            // outcome worth refusing outright.
            if (serial == 0L) continue
            return when (privateSpaceKind(type, quiet)) {
                PrivateSpaceKind.ABSENT -> continue
                PrivateSpaceKind.LOCKED -> PrivateSpace.Locked(user, serial)
                PrivateSpaceKind.UNLOCKED -> PrivateSpace.Unlocked(user, serial)
            }
        }
        return PrivateSpace.Absent
    }

    /**
     * Locks an open space and asks for a locked one to be opened. What that takes is the
     * system's to decide: on a phone with a screen lock it puts its own authentication in
     * front of the unlock, and this returns false until that has been answered.
     */
    fun togglePrivateSpace(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return false
        val (lock, user) = when (val state = privateSpace()) {
            PrivateSpace.Absent -> return false
            is PrivateSpace.Locked -> false to state.user
            is PrivateSpace.Unlocked -> true to state.user
        }
        return runCatching { userManager.requestQuietModeEnabled(lock, user) }.getOrDefault(false)
    }

    /**
     * The row that opens and closes the space, or nothing when there is no space to open.
     *
     * It is not an app and has no activity of its own, so it names itself after this package
     * and says which padlock it is in its class name — which is also what tells the icon cache
     * the two apart, since that cache is keyed by the row and not by the state of the device.
     */
    private fun privateSpaceRow(state: PrivateSpace): List<AppInfo> {
        val className = when (state) {
            PrivateSpace.Absent -> return emptyList()
            is PrivateSpace.Locked -> PRIVATE_SPACE_LOCKED_CLASS
            is PrivateSpace.Unlocked -> PRIVATE_SPACE_UNLOCKED_CLASS
        }
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
