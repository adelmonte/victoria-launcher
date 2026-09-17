// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.SystemClock
import android.os.UserHandle
import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
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
    private var favoriteAdded: String? = null

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
        favoriteAdded?.let { key -> runBlocking { Prefs(context).removeFavorite(key) } }
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
        searchFor(PRIVATE_APP_SEARCH)
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
        favoriteAdded = key
        runBlocking { Prefs(context).addFavorite(key) }

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
        const val PRIVATE_ROW_LABEL = "Private space"
        const val PRIVATE_ROW_SEARCH = "Private spac"
        const val MISSING_ROW_LABEL = "App no longer installed"
        const val NO_FAVORITES_LABEL = "0 on your home screen"
        const val CHOOSE_FAVORITES_LABEL = "Choose favorites"
        const val FAVORITES_TITLE = "Favorites"

        /** Long enough to be a press and not a tap, short enough not to stall the run. */
        const val LONG_PRESS_MS = 800

        /** How long something absent is given to turn up before it counts as absent. */
        const val ABSENCE_MS = 3_000L
        const val QUIET_MODE_MS = 15_000L
    }
}
