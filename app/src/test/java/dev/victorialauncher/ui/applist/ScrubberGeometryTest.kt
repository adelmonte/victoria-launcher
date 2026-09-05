// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrubberGeometryTest {

    // A 260px band of 26 letters puts each letter in a 10px slot starting at y=100.
    private val top = 100f
    private val height = 260f
    private val count = 26

    @Test
    fun `y at the top of the band selects the first letter`() {
        assertEquals(0, ScrubberGeometry.indexForY(top, top, height, count))
    }

    @Test
    fun `y inside a slot selects that slot`() {
        assertEquals(3, ScrubberGeometry.indexForY(top + 35f, top, height, count))
    }

    @Test
    fun `y above the band clamps to the first letter`() {
        assertEquals(0, ScrubberGeometry.indexForY(-500f, top, height, count))
    }

    @Test
    fun `y below the band clamps to the last letter`() {
        assertEquals(count - 1, ScrubberGeometry.indexForY(9999f, top, height, count))
    }

    @Test
    fun `an empty alphabet never indexes out of range`() {
        assertEquals(0, ScrubberGeometry.indexForY(120f, top, height, count = 0))
    }

    @Test
    fun `a zero-height band never divides by zero`() {
        assertEquals(0, ScrubberGeometry.indexForY(120f, top, heightPx = 0f, count = count))
    }

    @Test
    fun `letter centres sit in the middle of their slot`() {
        assertEquals(105f, ScrubberGeometry.letterCenterY(0, top, height, count), 0.01f)
        assertEquals(115f, ScrubberGeometry.letterCenterY(1, top, height, count), 0.01f)
    }

    @Test
    fun `centre and index agree, so the letter under the finger is the one that swells`() {
        for (index in 0 until count) {
            val centre = ScrubberGeometry.letterCenterY(index, top, height, count)
            assertEquals(index, ScrubberGeometry.indexForY(centre, top, height, count))
        }
    }

    @Test
    fun `an empty alphabet reports the band top rather than dividing by zero`() {
        assertEquals(top, ScrubberGeometry.letterCenterY(0, top, height, count = 0), 0.01f)
    }

    @Test
    fun `the fallback band is derived from the viewport`() {
        val band = ScrubBand.fallbackFor(1000)
        assertEquals(350f, band.topPx, 0.01f)
        assertEquals(500f, band.heightPx, 0.01f)
        assertEquals(850f, band.bottomPx, 0.01f)
    }
}