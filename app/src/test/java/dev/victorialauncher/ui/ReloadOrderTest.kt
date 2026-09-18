// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which of several overlapping reloads is allowed to put its answer on screen. The reloads
 * themselves are binder calls and Compose state, but the decision between them is arithmetic,
 * and it is the decision that is worth pinning down: getting it wrong leaves a locked private
 * space's apps listed under an open padlock until the launcher is left and re-entered.
 */
class ReloadOrderTest {

    @Test
    fun `the first load publishes`() {
        val order = ReloadOrder()
        val first = order.begin()
        assertTrue(order.mayPublish(first))
    }

    @Test
    fun `the same pass may publish more than once`() {
        // A pass conceals on the list already in hand and then publishes the enumeration it
        // went on to do. Both are that pass speaking, and both are current.
        val order = ReloadOrder()
        val pass = order.begin()
        assertTrue(order.mayPublish(pass))
        assertTrue(order.mayPublish(pass))
    }

    @Test
    fun `an older reload finishing last does not publish`() {
        // The case that matters: a reload that read an open space is still running when the
        // lock arrives, and comes back after the locked reload has already been to screen.
        val order = ReloadOrder()
        val readTheOpenSpace = order.begin()
        val readTheLock = order.begin()

        assertTrue("the newer pass publishes", order.mayPublish(readTheLock))
        assertFalse("and the stale one does not, however late it is", order.mayPublish(readTheOpenSpace))
    }

    @Test
    fun `a newer reload finishing last publishes over the older one`() {
        val order = ReloadOrder()
        val older = order.begin()
        val newer = order.begin()

        // The older one comes back first and is refused; the newer one then publishes.
        assertFalse(order.mayPublish(older))
        assertTrue(order.mayPublish(newer))
    }

    @Test
    fun `a state adopted between a reload starting and finishing stops it publishing`() {
        // Adopting a state is what the padlock does: it conceals on the list already in hand
        // rather than waiting for an enumeration. It happens inside a pass of its own, and
        // that pass is newer than anything still running.
        val order = ReloadOrder()
        val enumerating = order.begin()
        val theLockBeingAdopted = order.begin()

        assertFalse(order.mayPublish(enumerating))
        assertTrue(order.mayPublish(theLockBeingAdopted))
    }

    @Test
    fun `whichever pass is newest always publishes, so nothing is left un-refreshed`() {
        // A skipped reload must never be the last word. Whatever order a run of passes is
        // started in, exactly one of them — the last to begin — is still allowed to publish.
        val order = ReloadOrder()
        val passes = List(20) { order.begin() }
        assertTrue(order.mayPublish(passes.last()))
        assertTrue(passes.dropLast(1).none { order.mayPublish(it) })
    }
}
