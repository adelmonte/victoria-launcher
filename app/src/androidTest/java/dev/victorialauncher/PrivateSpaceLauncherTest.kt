// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.victorialauncher.data.Folder
import dev.victorialauncher.data.Prefs
import dev.victorialauncher.data.USER_TYPE_PROFILE_PRIVATE
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a private space has to do, seen the way its owner sees it: from outside the app, on a
 * device where one exists.
 *
 * The app whose name is looked for has to be inside the private space and nowhere else, or a
 * row bearing that name says nothing about which profile it came from — so the set-up takes it
 * away from the main profile for the duration and gives it back afterwards.
 *
 * A device without a private space cannot answer any of this, so these skip rather than fail
 * there. Both the space and that app being in it are set up outside the test; see the
 * project's notes on preparing an emulator.
 */
@RunWith(AndroidJUnit4::class)
class PrivateSpaceLauncherTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val device: UiDevice get() = LauncherTestUtils.uiDevice()
    private val userManager: UserManager get() = context.getSystemService(UserManager::class.java)
    private val launcherApps: LauncherApps get() = context.getSystemService(LauncherApps::class.java)

    private lateinit var privateUser: UserHandle
    private val favoritesAdded = mutableListOf<String>()
    private val foldersAdded = mutableListOf<String>()

    @Before
    fun setUp() {
        assumeTrue("private space arrived in Android 15", Build.VERSION.SDK_INT >= 35)
        LauncherTestUtils.setAsDefaultHome()
        val user = privateProfile()
        assumeTrue("this device has no private space", user != null)
        privateUser = user!!
        assumeTrue(
            "$PRIVATE_APP_PACKAGE is not installed in the private space",
            activityIn(privateUser) != null,
        )
        setLocked(false)
        // Taken away from the main profile so the name below can only have come from the
        // private one. Given back in tearDown, whatever the test did.
        device.executeShellCommand("pm uninstall --user 0 $PRIVATE_APP_PACKAGE")
        // Searching is what would reach a hidden app by name, so it is switched on here: it
        // must not be a way round the private space either.
        runBlocking {
            Prefs(context).setAppListSearchEnabled(true)
            Prefs(context).setAppListSearchHidden(true)
        }
    }

    @After
    fun tearDown() {
        if (!this::privateUser.isInitialized) return
        device.executeShellCommand("pm install-existing --user 0 $PRIVATE_APP_PACKAGE")
        runBlocking {
            favoritesAdded.forEach { key -> Prefs(context).removeFavorite(key) }
            foldersAdded.forEach { id -> Prefs(context).deleteFolder(id) }
        }
        favoritesAdded.clear()
        foldersAdded.clear()
        setLocked(false)
    }

    @Test
    fun anUnlockedSpaceListsItsAppsAlongsideTheRowThatLocksIt() {
        setLocked(false)
        searchFor(PRIVATE_APP_SEARCH)
        assertTrue(
            "expected the private space's own $PRIVATE_APP_LABEL to be listed while it is unlocked",
            LauncherTestUtils.waitForText(PRIVATE_APP_LABEL),
        )

        searchFor(PRIVATE_ROW_SEARCH)
        assertTrue(
            "expected the $PRIVATE_ROW_LABEL row to be listed",
            LauncherTestUtils.waitForText(PRIVATE_ROW_LABEL),
        )
    }

    @Test
    fun lockingTheSpaceTakesItsAppsOutOfTheListAndOutOfSearch() {
        setLocked(true)
        // One query that matches an app in each profile, and the main profile's has to be
        // there. That is what makes the other one's absence mean anything: a list that never
        // opened, or a filter that matched nothing at all, would otherwise pass this.
        searchFor(SHARED_SEARCH)
        assertTrue(
            "the search found no $MAIN_APP_LABEL either, so it proves nothing about $PRIVATE_APP_LABEL",
            LauncherTestUtils.waitForText(MAIN_APP_LABEL),
        )
        assertFalse(
            "a locked private space's app was reachable by searching the list",
            LauncherTestUtils.waitForText(PRIVATE_APP_LABEL, ABSENCE_MS),
        )

        searchFor(PRIVATE_ROW_SEARCH)
        assertTrue(
            "the row that unlocks the space has to stay, or there is no way back in",
            LauncherTestUtils.waitForText(PRIVATE_ROW_LABEL),
        )
    }

    @Test
    fun aFavoriteInsideALockedSpaceLeavesTheHomeScreenAndTheFavoritesScreen() {
        val key = privateAppKey()
        favorite(key)

        setLocked(false)
        LauncherTestUtils.goHome()
        assertTrue(
            "expected the favorite to be on the home screen while the space is unlocked",
            LauncherTestUtils.waitForText(PRIVATE_APP_LABEL),
        )

        setLocked(true)
        LauncherTestUtils.goHome()
        assertFalse(
            "a locked private space's app was still on the home screen",
            LauncherTestUtils.waitForText(PRIVATE_APP_LABEL, ABSENCE_MS),
        )

        openFavoritesScreen()
        assertFalse(
            "the favorites screen offered up a row for the hidden favorite",
            LauncherTestUtils.waitForText(MISSING_ROW_LABEL, ABSENCE_MS),
        )
        assertTrue(
            "the count has to match what is listed, which is nothing",
            LauncherTestUtils.waitForText(NO_FAVORITES_LABEL),
        )

        // Still stored, so it comes back rather than being quietly forgotten.
        assertTrue(
            "the favorite must survive the space being locked",
            runBlocking { Prefs(context).favorites.first() }.contains(key),
        )
    }

    @Test
    fun pressingTheRowWhileLockedAsksForTheSpaceToBeOpened() {
        setLocked(true)
        searchFor(PRIVATE_ROW_SEARCH)
        assertTrue(LauncherTestUtils.waitForText(PRIVATE_ROW_LABEL))

        device.findObject(By.text(PRIVATE_ROW_LABEL)).click()

        // This emulator has no screen lock, so the system asks for nothing and the space
        // simply opens. On a phone with one, its own authentication comes up first.
        assertTrue(
            "pressing the row did not open the space",
            waitForQuietMode(false),
        )
    }

    @Test
    fun pressingThePadlockTakesAPrivateFavoriteOffTheHomeScreenAtOnce() {
        favorite(privateAppKey())
        // A favorite from each profile, because the private one's absence is only meaningful
        // beside something still there: the two are drawn in the same pass, so once the main
        // profile's is back on screen the other one would be too if it had survived.
        favorite(mainAppKey())
        setLocked(false)
        LauncherTestUtils.goHome()
        assertTrue(
            "expected the favorite to be on the home screen while the space is unlocked",
            LauncherTestUtils.waitForText(PRIVATE_APP_LABEL),
        )

        searchFor(PRIVATE_ROW_SEARCH)
        assertTrue(LauncherTestUtils.waitForText(PRIVATE_ROW_LABEL))
        device.findObject(By.text(PRIVATE_ROW_LABEL)).click()

        // The whole point of the press: what it hides has to be gone before anyone holding the
        // phone could read it, which is why this window is a short one. Re-reading every app of
        // every profile, which is what used to have to finish first, takes far longer.
        assertTrue(
            "the home screen never came back after the padlock was pressed",
            device.wait(Until.hasObject(By.text(MAIN_APP_LABEL)), CONCEAL_MS),
        )
        assertFalse(
            "the private favorite was still on the home screen beside the main profile's",
            device.hasObject(By.text(PRIVATE_APP_LABEL)),
        )
        assertTrue("pressing the row did not lock the space", waitForQuietMode(true))
    }

    /**
     * The space locks itself whenever the screen goes off, so it is routinely locked while the
     * launcher is not the thing on screen, and what says so can be missed.
     *
     * This is the whole return path — the broadcast and the check made on becoming visible
     * both run here, and nothing in a test can silence one to leave the other. What it pins
     * down is the behavior that matters: after time away, a space that locked while we were
     * gone is concealed by the time the home screen is back.
     */
    @Test
    fun aSpaceLockedWhileTheLauncherWasAwayIsConcealedOnTheWayBack() {
        favorite(privateAppKey())
        favorite(mainAppKey())
        setLocked(false)
        LauncherTestUtils.goHome()
        assertTrue(LauncherTestUtils.waitForText(PRIVATE_APP_LABEL))

        // Away, rather than merely covered: the launcher is stopped while this happens.
        device.executeShellCommand("am start -a android.settings.SETTINGS")
        check(device.wait(Until.hasObject(By.pkg("com.android.settings")), 10_000L)) {
            "the settings app never came to the foreground"
        }
        setLocked(true)

        LauncherTestUtils.goHome()
        assertTrue(
            "the home screen came back without its main-profile favorite, so it proves nothing",
            LauncherTestUtils.waitForText(MAIN_APP_LABEL),
        )
        assertFalse(
            "a private favorite was still on the home screen after the space locked while away",
            LauncherTestUtils.waitForText(PRIVATE_APP_LABEL, ABSENCE_MS),
        )
    }

    /**
     * The dialog that moves an app into a folder says how big each folder is, and that number
     * is read while the space is locked — so counting what the folder stores rather than what
     * it can show would print the number of private apps in it, folder by folder, beside a
     * home-screen badge that counts them the other way.
     */
    @Test
    fun theFolderPickerCountsOnlyWhatALockedSpaceStillShows() {
        folder(FOLDER_NAME, listOf(mainAppKey(), privateAppKey()))
        setLocked(true)

        // Long-pressing a row in the A-Z list is how the dialog is reached; any app will do,
        // and this one is in the main profile so it is there to be pressed while locked.
        searchFor(SHARED_SEARCH)
        assertTrue(
            "the list never showed $MAIN_APP_LABEL, so there was nothing to long-press",
            LauncherTestUtils.waitForText(MAIN_APP_LABEL),
        )
        device.findObject(By.text(MAIN_APP_LABEL)).longClick()
        assertTrue(
            "long-pressing the row did not open its menu",
            LauncherTestUtils.waitForText(MOVE_TO_FOLDER_LABEL),
        )
        device.findObject(By.text(MOVE_TO_FOLDER_LABEL)).click()

        assertTrue(
            "the folder picker never listed the folder",
            LauncherTestUtils.waitForText(FOLDER_NAME),
        )
        assertTrue(
            "the picker has to count the one member a locked space leaves showing",
            LauncherTestUtils.waitForText(SHOWN_MEMBER_COUNT),
        )
        assertFalse(
            "the picker counted the private member, which says how many are in the space",
            device.hasObject(By.text(STORED_MEMBER_COUNT)),
        )
    }

    /** Stores a folder for the duration, and deletes it again in tearDown. */
    private fun folder(name: String, apps: List<String>) {
        val id = "test-" + SystemClock.uptimeMillis().toString(36)
        foldersAdded += id
        runBlocking { Prefs(context).upsertFolder(Folder(id = id, name = name, apps = apps)) }
    }

    /** Stores a favorite for the duration, and takes it away again in tearDown. */
    private fun favorite(key: String) {
        favoritesAdded += key
        runBlocking { Prefs(context).addFavorite(key) }
    }

    /**
     * Types a query into the open list, starting from an empty box.
     *
     * The list is opened afresh for each one because it keeps showing "nothing found" when a
     * query that matched nothing is replaced by one that does — behavior this app has
     * independently of any of this, reproducible with two ordinary app names. Opening the list
     * also waits for its empty search box, so there is something to wait for rather than a
     * guessed delay.
     */
    private fun searchFor(term: String) {
        LauncherTestUtils.goHome()
        LauncherTestUtils.openAppList()
        LauncherTestUtils.filterAppList(term)
    }

    /** The favorites screen, reached the way anyone reaches it: the wallpaper's own menu. */
    private fun openFavoritesScreen() {
        val x = device.displayWidth / 2
        // Favorites sit at the bottom, so the empty wallpaper to look for is above them. Tried
        // at a few heights rather than one, because how much of it is empty depends on what is
        // on the home screen.
        for (fraction in listOf(0.2f, 0.12f, 0.3f)) {
            val y = (device.displayHeight * fraction).toInt()
            device.executeShellCommand("input swipe $x $y $x $y $LONG_PRESS_MS")
            if (device.wait(Until.hasObject(By.text(CHOOSE_FAVORITES_LABEL)), 2_000L)) {
                device.findObject(By.text(CHOOSE_FAVORITES_LABEL)).click()
                check(device.wait(Until.hasObject(By.text(FAVORITES_TITLE)), 5_000L)) {
                    "the favorites screen did not open"
                }
                return
            }
        }
        error("the wallpaper's menu never opened")
    }

    private fun privateProfile(): UserHandle? =
        runCatching { launcherApps.profiles }.getOrNull().orEmpty().firstOrNull { user ->
            runCatching { launcherApps.getLauncherUserInfo(user)?.userType }
                .getOrNull() == USER_TYPE_PROFILE_PRIVATE
        }

    private fun activityIn(user: UserHandle) =
        runCatching { launcherApps.getActivityList(PRIVATE_APP_PACKAGE, user) }
            .getOrNull()
            ?.firstOrNull()

    /** The same key the launcher stores for that app: its component, plus the profile. */
    private fun privateAppKey(): String {
        val activity = checkNotNull(activityIn(privateUser))
        val serial = userManager.getSerialNumberForUser(privateUser)
        return activity.componentName.flattenToString() + "|u" + serial
    }

    /** The main profile's keys have no profile in them, which is what makes them the old ones. */
    private fun mainAppKey(): String {
        val activity = checkNotNull(
            runCatching { launcherApps.getActivityList(MAIN_APP_PACKAGE, Process.myUserHandle()) }
                .getOrNull()
                ?.firstOrNull()
        ) { "$MAIN_APP_PACKAGE is not installed in the main profile" }
        return activity.componentName.flattenToString()
    }

    private fun setLocked(locked: Boolean) {
        if (userManager.isQuietModeEnabled(privateUser) == locked) return
        userManager.requestQuietModeEnabled(locked, privateUser)
        check(waitForQuietMode(locked)) { "the private space never became ${if (locked) "locked" else "unlocked"}" }
    }

    private fun waitForQuietMode(expected: Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + QUIET_MODE_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (userManager.isQuietModeEnabled(privateUser) == expected) return true
            SystemClock.sleep(250)
        }
        return false
    }

    private companion object {
        const val PRIVATE_APP_PACKAGE = "com.google.android.deskclock"
        const val PRIVATE_APP_LABEL = "Clock"

        /** Only part of the name, so the search box never holds the text waited for below. */
        const val PRIVATE_APP_SEARCH = "Cloc"

        /**
         * An app that is in the main profile and stays there, so a search can show that the
         * list it is being asked about is a live one.
         */
        const val MAIN_APP_PACKAGE = "com.android.chrome"
        const val MAIN_APP_LABEL = "Chrome"

        /** Matches both names, and neither exactly, so the search box is never what is found. */
        const val SHARED_SEARCH = "C"

        const val PRIVATE_ROW_LABEL = "Private space"
        const val PRIVATE_ROW_SEARCH = "Private spac"
        const val MISSING_ROW_LABEL = "App no longer installed"
        const val MOVE_TO_FOLDER_LABEL = "Move to folder…"

        /** A folder holding one main-profile app and one private one, read while locked. */
        const val FOLDER_NAME = "Mixed folder"
        const val SHOWN_MEMBER_COUNT = "1 apps"
        const val STORED_MEMBER_COUNT = "2 apps"
        const val NO_FAVORITES_LABEL = "0 on your home screen"
        const val CHOOSE_FAVORITES_LABEL = "Choose favorites"
        const val FAVORITES_TITLE = "Favorites"

        /** Long enough to be a press and not a tap, short enough not to stall the run. */
        const val LONG_PRESS_MS = 800

        /** How long something absent is given to turn up before it counts as absent. */
        const val ABSENCE_MS = 3_000L

        /** How long a lock may take to reach the screen before it is no longer immediate. */
        const val CONCEAL_MS = 1_500L
        const val QUIET_MODE_MS = 15_000L
    }
}
