// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.victorialauncher.data.EdgeSide
import dev.victorialauncher.data.RippleParams
import dev.victorialauncher.data.ScrubStyle
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/** Letters sit this far in from the screen edge so they aren't crowding it. */
private const val EDGE_INSET_DP = 28f

/**
 * How far the bulge pushes the column away from the edge at its peak.
 *
 * Has to clear the fingertip without reaching the bubble. The thumb is already an edge-zone in
 * from the screen edge, so at 75dp the peak landed about 7dp from it — underneath, hidden by
 * the hand pointing at it — while the bubble beginning at 122dp left only about 19dp of room
 * to move into. The two only go further out together, which is what [SCRUB_BUBBLE_INSET_DP]
 * is for.
 */
private const val BELL_AMPLITUDE_DP = 112f

/** Half the letter cell, so the peak is measured by its edge rather than its middle. */
private const val LETTER_HALF_DP = 10f

/**
 * Where the letter bubble starts, kept a fixed gap beyond the peak of the bell.
 *
 * Derived rather than written down twice: the bubble sat at a number of its own, so growing
 * the bulge to clear a fingertip walked the strip straight into it.
 */
const val SCRUB_BUBBLE_INSET_DP = EDGE_INSET_DP + BELL_AMPLITUDE_DP + LETTER_HALF_DP + 18f

// ---- Ripple simulation -----------------------------------------------------------------------
private const val SIM_DT = 1f / 240f

/**
 * A 1D damped wave equation sampled at the letters, written for the deviation
 * w = u − bell from the bell curve under the finger:
 *
 *   u_tt = c²·∂²w/∂x² − γ·u_t − k·w
 *
 * With the finger held still the bell is constant, so w decays to zero and the strip comes to
 * rest on exactly the bell curve. Moving the finger drags the bell away from the strip, which
 * then springs after it and sheds ripples along the way; lifting it drops the bell to zero
 * and the strip rings back down to flat.
 */
private class RippleString(private val n: Int) {
    val u = FloatArray(n)          // outward displacement, px
    private val v = FloatArray(n)
    private val w = FloatArray(n)
    private val a = FloatArray(n)

    fun step(dt: Float, bell: FloatArray, p: RippleParams) {
        val c2 = p.speed * p.speed
        for (i in 0 until n) w[i] = u[i] - bell[i]
        for (i in 0 until n) {
            val left = if (i > 0) w[i - 1] else 0f           // ends pinned to the bell's edge
            val right = if (i < n - 1) w[i + 1] else 0f
            val laplacian = left - 2f * w[i] + right
            a[i] = c2 * laplacian - p.damping * v[i] - p.stiffness * w[i]
        }
        for (i in 0 until n) {                                // semi-implicit Euler
            v[i] += a[i] * dt
            u[i] += v[i] * dt
        }
    }

    fun isAtRest(): Boolean {
        for (i in 0 until n) if (abs(u[i]) > 0.05f || abs(v[i]) > 0.5f) return false
        return true
    }

    fun reset() { u.fill(0f); v.fill(0f) }
}

/**
 * The A-Z strip. The letters never change size — the *column* bows outward around the
 * fingertip on a gaussian, so the alphabet traces a bell curve and settles flat again when
 * the finger lifts. Scaling the glyphs (what this used to do) just stretched them until
 * they looked pixelated.
 *
 * [scrubY] and [pullPx] are read as lambdas inside a graphicsLayer so the whole strip
 * animates in the draw phase; recomposing 26 Text nodes per pointer move made this jitter.
 */
@Composable
fun EdgeScrubber(
    letters: List<Char>,
    scrubY: () -> Float?,
    pullPx: () -> Float,
    band: ScrubBand,
    side: EdgeSide,
    modifier: Modifier = Modifier,
    style: ScrubStyle = ScrubStyle.BELL,
    ripple: RippleParams = RippleParams(),
) {
    if (letters.isEmpty()) return
    val density = LocalDensity.current.density
    val spacingPx = band.heightPx / letters.size
    // Wide enough that a good stretch of the alphabet takes part in the curve.
    val sigmaPx = 2.6f * spacingPx
    val bellPx = BELL_AMPLITUDE_DP * density

    val centerYs = remember(letters.size, band.topPx, band.heightPx) {
        FloatArray(letters.size) {
            ScrubberGeometry.letterCenterY(it, band.topPx, band.heightPx, letters.size)
        }
    }
    val sim = remember(letters.size) { RippleString(letters.size) }
    // Read only inside graphicsLayer, so each simulation tick invalidates the draw phase alone.
    var frame by remember { mutableIntStateOf(0) }
    val scrubYState by rememberUpdatedState(scrubY)
    val pullState by rememberUpdatedState(pullPx)
    val rippleState by rememberUpdatedState(ripple)   // live-tunable without restarting the loop

    LaunchedEffect(style, sim, centerYs, sigmaPx, bellPx) {
        if (style != ScrubStyle.RIPPLE) {
            sim.reset()
            return@LaunchedEffect
        }
        val bell = FloatArray(centerYs.size)
        var last = 0L
        var acc = 0f
        while (true) {
            // Idle until the finger lands (or until something is still rippling).
            if (sim.isAtRest() && scrubYState() == null) {
                sim.reset(); frame++
                snapshotFlow { scrubYState() != null }.first { it }
                last = 0L
            }
            withFrameNanos { now ->
                val dt = if (last == 0L) SIM_DT else ((now - last) / 1e9f).coerceAtMost(1f / 30f)
                last = now
                acc += dt

                val y = scrubYState()
                val amplitude = bellPx + pullState()
                for (i in bell.indices) {
                    bell[i] = if (y == null) 0f else {
                        val d = y - centerYs[i]
                        amplitude * exp(-(d * d) / (2f * sigmaPx * sigmaPx))
                    }
                }
                val params = rippleState
                while (acc >= SIM_DT) { sim.step(SIM_DT, bell, params); acc -= SIM_DT }
                frame++
            }
        }
    }

    Box(
        modifier = modifier
            // Wide enough for the inset, the letter cell and the whole outward bulge — at
            // 56dp the horizontal padding ate the entire width and the curve had nowhere to
            // go, and the bulge has since grown enough to clear a fingertip.
            .width(210.dp)
            .fillMaxHeight()
            .padding(horizontal = EDGE_INSET_DP.dp),
    ) {
        letters.forEachIndexed { index, c ->
            val centerY = centerYs[index]

            Box(
                modifier = Modifier
                    .align(if (side == EdgeSide.LEFT) Alignment.TopStart else Alignment.TopEnd)
                    .offset { IntOffset(0, (centerY - spacingPx / 2f).roundToInt()) }
                    .width(20.dp)
                    .graphicsLayer {
                        val outward: Float
                        val gain: Float
                        if (style == ScrubStyle.RIPPLE) {
                            @Suppress("UNUSED_VARIABLE") val tick = frame // subscribe to sim updates
                            // Ripples overshoot negative; keep letters off the screen edge.
                            outward = sim.u[index].coerceAtLeast(-EDGE_INSET_DP * density)
                            gain = (abs(outward) / bellPx).coerceIn(0f, 1f)
                        } else {
                            val y = scrubY()
                            gain = if (y == null) {
                                0f
                            } else {
                                val d = y - centerY
                                exp(-(d * d) / (2f * sigmaPx * sigmaPx))
                            }
                            outward = bellPx * gain + pullPx() * gain
                        }

                        // Position only — the glyph is never scaled.
                        translationX = if (side == EdgeSide.LEFT) outward else -outward
                        alpha = 0.55f + 0.45f * gain
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = c.toString(), fontSize = 13.sp, color = Color.White)
            }
        }
    }
}