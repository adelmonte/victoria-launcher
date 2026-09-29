// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrubberGeometryTest {

    @Test
    fun `favorites tall enough keep their own span`() {
        val band = ScrubberGeometry.bandForFavorites(
            topPx = 400f, bottomPx = 1000f, viewportPx = 2000f, minHeightPx = 400f,
        )
        assertEquals(400f, band.topPx, 0.01f)
        assertEquals(600f, band.heightPx, 0.01f)
    }

    @Test
    fun `a single favorite still gets a strip worth aiming at`() {
        // One row tall: the whole alphabet had a row's height to share between its letters.
        val band = ScrubberGeometry.bandForFavorites(
            topPx = 900f, bottomPx = 1000f, viewportPx = 2000f, minHeightPx = 400f,
        )
        assertEquals(400f, band.heightPx, 0.01f)
        // Grown around its own middle, so it stays where the favorites are.
        assertEquals(950f, band.topPx + band.heightPx / 2f, 0.01f)
    }

    @Test
    fun `growing never pushes the band off either end`() {
        val atTop = ScrubberGeometry.bandForFavorites(
            topPx = 0f, bottomPx = 50f, viewportPx = 2000f, minHeightPx = 400f,
        )
        assertEquals(0f, atTop.topPx, 0.01f)

        val atBottom = ScrubberGeometry.bandForFavorites(
            topPx = 1950f, bottomPx = 2000f, viewportPx = 2000f, minHeightPx = 400f,
        )
        assertEquals(2000f, atBottom.topPx + atBottom.heightPx, 0.01f)
    }

    @Test
    fun `a minimum taller than the screen is capped by the screen`() {
        val band = ScrubberGeometry.bandForFavorites(
            topPx = 100f, bottomPx = 200f, viewportPx = 600f, minHeightPx = 900f,
        )
        assertEquals(600f, band.heightPx, 0.01f)
        assertEquals(0f, band.topPx, 0.01f)
    }

    @Test
    fun `no favorites measured yet is not grown into nonsense`() {
        val band = ScrubberGeometry.bandForFavorites(
            topPx = 500f, bottomPx = 400f, viewportPx = 2000f, minHeightPx = 400f,
        )
        assertTrue(band.heightPx >= 0f)
    }
}
