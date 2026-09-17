// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.app.ActivityManager
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.net.Uri
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.victorialauncher.data.EntryKeys
import dev.victorialauncher.data.Prefs
import dev.victorialauncher.shortcut.PinShortcutActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
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

    // Every id this test class pins, so tearDown can unpin exactly what it pinned and nothing
    // that was already on a shared emulator for some other reason.
    private val pinnedIds = mutableListOf<String>()

    @Before
    fun setUp() {
        device = LauncherTestUtils.uiDevice()
        targetPackage = LauncherTestUtils.targetPackageName()
        LauncherTestUtils.setAsDefaultHome()
        LauncherTestUtils.goHome()
    }

    /**
     * Leaves the shared emulator the way this test class found it: pinShortcuts with every id
     * this class pinned removed unpins them all in one call (the same replace-the-whole-set
     * shape [dev.victorialauncher.data.PinnedShortcuts] exists to get right elsewhere), and
     * forgetEntry drops whatever favorites/name/icon/launch-count rows they left behind. The
     * orientation lock is undone unconditionally, even for a test that never touched it, since
     * it is shared state on this emulator and would otherwise leave every test after it running
     * sideways.
     */
    @After
    fun tearDown() = runBlocking {
        device.setOrientationNatural()
        device.unfreezeRotation()

        if (pinnedIds.isEmpty()) return@runBlocking
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        runCatching { launcherApps.pinShortcuts(targetPackage, emptyList(), Process.myUserHandle()) }
        val prefs = Prefs(context.applicationContext)
        pinnedIds.forEach { id -> prefs.forgetEntry(EntryKeys.shortcut(targetPackage, id, 0L)) }
        pinnedIds.clear()
    }

    // Instrumented test names become DEX method names, which can't contain spaces — so these
    // stay camelCase rather than the backtick style used in app/src/test.

    @Test
    fun acceptingAPinRequestPutsTheShortcutOnTheHomeScreen() {
        pinThroughTheConfirmation("pin-shortcut-flow-test", shortcutLabel)
    }

    /**
     * [PinShortcutActivity] is `noHistory` and declares no `configChanges`, so a rotation
     * recreates or finishes it. The write used to ride that screen's own coroutine scope and
     * could lose out to exactly this: the confirm screen going away the instant after Add is
     * tapped, before its ~1 s poll of the system had a chance to finish. Now that the poll and
     * the favorite write live on the repository's application-scope instead, rotating right
     * after the tap must not be able to touch them.
     */
    @Test
    fun rotatingTheDeviceRightAfterAddStillPinsTheShortcut() {
        val id = "pin-shortcut-rotate-test"
        val label = "Pinned page surviving rotation"
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
        // Tracked before tapping Add, same as pinThroughTheConfirmation: if the assertion
        // below fails, the shortcut can still be pinned with the system and tearDown must
        // still clean it up.
        pinnedIds += id

        val add = device.wait(Until.findObject(By.text("Add")), 10_000L)
        assertNotNull("expected the pin confirmation to offer Add", add)
        add.click()
        device.setOrientationLeft()

        LauncherTestUtils.goHome()
        assertTrue(
            "expected the shortcut to still be pinned even though the confirm screen was " +
                "rotated away the instant after Add was tapped",
            LauncherTestUtils.waitForText(label),
        )
    }

    @Test
    fun removingOneSiblingShortcutLeavesTheOtherPinnedAndOnScreen() {
        // pinShortcuts REPLACES the whole pinned set for a package in one call (see
        // PinnedShortcuts's own doc comment) — this is the regression that shape makes possible:
        // unpinning one shortcut from a package must not silently unpin every other one from it.
        val keptId = "pin-shortcut-sibling-keep"
        val keptLabel = "Pinned page to keep"
        val removedId = "pin-shortcut-sibling-remove"
        val removedLabel = "Pinned page to remove sibling"
        pinThroughTheConfirmation(keptId, keptLabel)
        pinThroughTheConfirmation(removedId, removedLabel)

        val row = device.wait(Until.findObject(By.text(removedLabel)), 10_000L)
        assertNotNull("expected the second pinned shortcut's row to long-press", row)
        row.longClick()
        val remove = device.wait(Until.findObject(By.text("Remove shortcut")), 10_000L)
        assertNotNull("expected the row's menu to offer Remove shortcut", remove)
        remove.click()

        assertTrue(
            "expected only the removed shortcut's row to go",
            device.wait(Until.gone(By.text(removedLabel)), 10_000L),
        )
        assertTrue(
            "expected the sibling shortcut to still be on the home screen",
            LauncherTestUtils.waitForText(keptLabel),
        )
        val manager = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(ShortcutManager::class.java)
        assertFalse(
            "the removed shortcut must no longer be pinned",
            manager.pinnedShortcuts.any { it.id == removedId },
        )
        assertTrue(
            "the sibling shortcut from the same package must still be pinned with the system, " +
                "not just still shown",
            manager.pinnedShortcuts.any { it.id == keptId },
        )
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
        // Tracked before tapping Add: if the assertion below fails, the shortcut is still
        // pinned with the system and tearDown must still clean it up.
        pinnedIds += id

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
        // Nothing stops another app starting an exported activity with whatever it likes. The
        // old version of this assertion (only "some window of ours is on screen") could never
        // fail: the confirm dialog is drawn by this same package, so it would have passed
        // whether or not the dialog wrongly appeared. This checks the two things that
        // distinguish "finished cleanly" from either failure mode.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.startActivity(
            Intent(context, PinShortcutActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        assertFalse(
            "an empty pin intent must never raise the confirm dialog",
            device.wait(Until.hasObject(By.text(context.getString(R.string.shortcut_pin_title))), 3_000L),
        )
        val activityManager = context.getSystemService(ActivityManager::class.java)
        assertTrue(
            "a crash would restart the process; finding it still running is what shows " +
                "onCreate returned instead of throwing",
            activityManager.runningAppProcesses.orEmpty().any { it.processName == targetPackage },
        )
        assertTrue(
            "expected the launcher still to be what is on screen",
            device.wait(Until.hasObject(By.pkg(targetPackage).depth(0)), 10_000L),
        )
    }
}
