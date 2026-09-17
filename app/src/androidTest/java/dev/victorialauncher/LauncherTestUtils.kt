// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.content.ComponentName
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/**
 * Shared black-box helpers for the UiAutomator-driven launcher tests.
 *
 * Lives in the same package as [MainActivity] and resolves everything off the running
 * instrumentation (the target package name, the [MainActivity] class itself) rather than a
 * literal "dev.victorialauncher" string, so these keep working if the applicationId is ever
 * renamed for a fork or a rebrand.
 */
object LauncherTestUtils {

    private const val WAIT_MS = 10_000L

    fun uiDevice(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun targetPackageName(): String =
        InstrumentationRegistry.getInstrumentation().targetContext.packageName

    /**
     * Makes the app under test the system's default home app, the way a real device is set up
     * before any of this is meaningful to test. `cmd package set-home-activity` sets the
     * preference directly, so there's no chooser dialog to dismiss afterward.
     */
    fun setAsDefaultHome() {
        val component = ComponentName(targetPackageName(), MainActivity::class.java.name)
        uiDevice().executeShellCommand(
            "cmd package set-home-activity ${component.flattenToString()}",
        )
    }

    /** Presses HOME, waits for the target package's window to actually be on screen, and
     *  clears the one-time welcome dialog a fresh install shows there. */
    fun goHome() {
        val device = uiDevice()
        device.pressHome()
        device.wait(Until.hasObject(By.pkg(targetPackageName()).depth(0)), WAIT_MS)
        dismissWelcomeDialogIfShown()
    }

    /**
     * [dev.victorialauncher.ui.home.WelcomeDialog] covers the whole screen once, on a fresh
     * install, and would otherwise eat the edge-swipe meant for the app list. A short wait
     * that finds nothing is the normal case on every run after the first.
     */
    private fun dismissWelcomeDialogIfShown() {
        val device = uiDevice()
        val gotIt = device.wait(Until.findObject(By.text("Got it")), 2_000L)
        gotIt?.click()
    }

    /** Bounded retries for [openAppList]; see the comment there for why a single attempt isn't reliable. */
    private const val OPEN_LIST_ATTEMPTS = 5

    /**
     * Opens the full A-Z app list the way a person would: touch down inside the invisible
     * strip along a screen edge and drag inward — see the README's "Getting started" section.
     * The edge defaults to the right side, so this starts the touch a couple of pixels in from
     * the right edge (inside the strip regardless of its configured width) and drags most of
     * the way across, which satisfies both a tap-and-release open and a full scrub drag.
     *
     * The y position matters as much as x: Android's own edge-gesture (back) claims the
     * screen's outer edges, and the app can only ask the system to give a strip of it back
     * (`View.setSystemGestureExclusionRects`) around the vertical center of the scrub band —
     * not the full screen height. A touch-down outside that reclaimed strip goes to the
     * system's back gesture instead of the app, so this targets the vertical center of the
     * screen, where that reclaimed strip lives for the default (unedited) band.
     *
     * Even inside the right strip, the very first swipe right after HOME can race the system:
     * it takes a moment after `setSystemGestureExclusionRects` is called for the window manager
     * to actually start honoring it, and there's nothing observable to wait on for that — so
     * this retries the swipe, checking each time for the app list's own search-field
     * placeholder, rather than guessing a fixed delay.
     */
    fun openAppList() {
        val device = uiDevice()
        val startX = device.displayWidth - 2
        val endX = (device.displayWidth * 0.6f).toInt()
        val y = (device.displayHeight * 0.6f).toInt()
        repeat(OPEN_LIST_ATTEMPTS) {
            device.swipe(startX, y, endX, y, 60)
            if (device.wait(Until.hasObject(By.text("Search apps")), 1_000L)) return
        }
    }

    /** Waits for a node with this exact visible text to appear, e.g. an app label. */
    fun waitForText(text: String, timeoutMs: Long = WAIT_MS): Boolean =
        uiDevice().wait(Until.hasObject(By.text(text)), timeoutMs)
}
