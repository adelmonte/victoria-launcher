// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Black-box smoke test for the app's basic job: being a working HOME launcher.
 *
 * Driven through UiAutomator rather than a Compose test rule, because what matters here
 * crosses process boundaries a Compose tree can't see — the system's default-home selection,
 * HOME presses redelivered as intents, and eventually (future tests) another app's pin-shortcut
 * request and the system confirm dialog it raises.
 */
@RunWith(AndroidJUnit4::class)
class LauncherSmokeTest {

    private lateinit var device: UiDevice
    private lateinit var targetPackage: String

    @Before
    fun setUp() {
        device = LauncherTestUtils.uiDevice()
        targetPackage = LauncherTestUtils.targetPackageName()
        LauncherTestUtils.setAsDefaultHome()
    }

    // Instrumented test names become DEX method names, which (unlike JVM-only unit tests)
    // can't contain spaces — so these stay camelCase rather than the backtick style used
    // in app/src/test.

    @Test
    fun pressingHomeBringsTheLaunchersOwnUiToTheForeground() {
        LauncherTestUtils.goHome()
        assertEquals(targetPackage, device.currentPackageName)
    }

    @Test
    fun theEdgeGestureOpensTheAppListWhichListsSettings() {
        LauncherTestUtils.goHome()
        LauncherTestUtils.openAppList()
        // Wherever Settings sorts among what is installed, this brings its row on screen.
        // Only part of the name is typed: the box holding the whole of it would itself be the
        // text waited for below, and the check could then never fail.
        LauncherTestUtils.filterAppList("Setti")
        assertTrue(
            "expected the A-Z app list to be open and showing a Settings entry",
            LauncherTestUtils.waitForText("Settings"),
        )
    }
}
