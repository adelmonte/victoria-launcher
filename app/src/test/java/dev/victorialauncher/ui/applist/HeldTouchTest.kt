// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import org.junit.Assert.assertEquals
import org.junit.Test

class HeldTouchTest {

    private val slop = 24f

    @Test
    fun `let go where it landed is a tap for the widget`() {
        assertEquals(HeldTouch.TAP, heldTouch(pressed = false, travelPx = 0f, slopPx = slop))
        // A fingertip is never quite still; inside the slop it is still a tap.
        assertEquals(HeldTouch.TAP, heldTouch(pressed = false, travelPx = slop, slopPx = slop))
    }

    @Test
    fun `travel past the slop is a scrub`() {
        assertEquals(HeldTouch.SCRUB, heldTouch(pressed = true, travelPx = slop + 1f, slopPx = slop))
    }

    @Test
    fun `a flick that ends in the same event is a scrub and not a tap`() {
        assertEquals(HeldTouch.SCRUB, heldTouch(pressed = false, travelPx = slop + 1f, slopPx = slop))
    }

    @Test
    fun `still down and still inside the slop is not decided yet`() {
        assertEquals(HeldTouch.WAITING, heldTouch(pressed = true, travelPx = 0f, slopPx = slop))
        assertEquals(HeldTouch.WAITING, heldTouch(pressed = true, travelPx = slop, slopPx = slop))
    }
}
