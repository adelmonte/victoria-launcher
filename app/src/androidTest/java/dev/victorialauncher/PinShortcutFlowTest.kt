// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.victorialauncher.shortcut.PinShortcutActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pin request as an app actually makes it: through ShortcutManager, answered by tapping
 * the confirmation the system raises.
 *
 * Driven from outside the launcher's own process boundaries on purpose. The system decides
 * which launcher is HOME, which activity to raise for a pin request, and whether the pin took
 * — none of that is visible to a Compose test tree, and all of it is what can break.
 *
 * The request is made through the target app's own context because ShortcutService checks the
 * calling package against the calling uid, and the test code runs in the target app's process.
 * So the launcher is both publisher and host here; that is a quirk of the harness, not of the
 * feature, and it exercises the same path either way.
 */
@RunWith(AndroidJUnit4::class)
class PinShortcutFlowTest {

    private lateinit var device: UiDevice
    private lateinit var targetPackage: String

    private val shortcutLabel = "Pinned example page"

    @Before
    fun setUp() {
        device = LauncherTestUtils.uiDevice()
        targetPackage = LauncherTestUtils.targetPackageName()
        LauncherTestUtils.setAsDefaultHome()
        LauncherTestUtils.goHome()
    }

    // Instrumented test names become DEX method names, which can't contain spaces — so these
    // stay camelCase rather than the backtick style used in app/src/test.

    @Test
    fun acceptingAPinRequestPutsTheShortcutOnTheHomeScreen() {
        pinThroughTheConfirmation("pin-shortcut-flow-test", shortcutLabel)
    }

    @Test
    fun removingAShortcutUnpinsItWithTheSystemRatherThanJustHidingTheRow() {
        val id = "pin-shortcut-remove-test"
        val label = "Pinned page to remove"
        pinThroughTheConfirmation(id, label)

        val row = device.wait(Until.findObject(By.text(label)), 10_000L)
        assertNotNull("expected the pinned shortcut's row to long-press", row)
        row.longClick()
        val remove = device.wait(Until.findObject(By.text("Remove shortcut")), 10_000L)
        assertNotNull("expected the row's menu to offer Remove shortcut", remove)
        remove.click()

        assertTrue("expected the row to go from the home screen", device.wait(Until.gone(By.text(label)), 10_000L))
        val manager = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(ShortcutManager::class.java)
        assertFalse(
            "the row went but the shortcut is still pinned, so only the launcher forgot it",
            manager.pinnedShortcuts.any { it.id == id },
        )
    }

    /** Publishes a pin request and answers it the way a person would: by tapping Add. */
    private fun pinThroughTheConfirmation(id: String, label: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(ShortcutManager::class.java)
        assertTrue(
            "the launcher under test must be the default home before it can host a pin request",
            manager.isRequestPinShortcutSupported,
        )

        val shortcut = ShortcutInfo.Builder(context, id)
            .setShortLabel(label)
            .setIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org")))
            .build()
        assertTrue(
            "the pin request was refused before any confirmation was shown",
            manager.requestPinShortcut(shortcut, null),
        )

        val add = device.wait(Until.findObject(By.text("Add")), 10_000L)
        assertNotNull("expected the pin confirmation to offer Add", add)
        add.click()

        LauncherTestUtils.goHome()
        assertTrue(
            "expected the accepted shortcut to be listed on the home screen",
            LauncherTestUtils.waitForText(label),
        )
    }

    @Test
    fun anEmptyPinIntentFinishesInsteadOfCrashing() {
        // Nothing stops another app starting an exported activity with whatever it likes.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.startActivity(
            Intent(context, PinShortcutActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        assertTrue(
            "expected the launcher still to be what is on screen, with no dialog and no crash",
            device.wait(Until.hasObject(By.pkg(targetPackage).depth(0)), 10_000L),
        )
    }
}
