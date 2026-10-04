// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import dev.victorialauncher.data.EdgeSide
import dev.victorialauncher.service.HapticUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How far the strip may be dragged inward before the pull stops growing. */
/**
 * How far past the letters counts as leaving rather than reaching for the first or last of
 * them, counted in letters.
 *
 * Letters and not a share of the band: a fifth of the band sounds small and is about five
 * letters, which puts the threshold nowhere near the end of the alphabet you just dragged off.
 * A letter and a half is far enough that reaching for A from above still lands on A, and near
 * enough that leaving feels like leaving.
 */
/** How long a finger has to sit still on a letter before the list starts moving on its own. */
private const val HOLD_START_MS = 450L

/** Further than this and it was a scrub, not a rest. */
private const val HOLD_SLOP_DP = 6f

private const val OFF_STRIP_DISMISS_LETTERS = 1.5f

/** Bounds, so a short alphabet does not make this a flick and a long one does not bury it. */
private const val OFF_STRIP_DISMISS_MIN_DP = 24f
private const val OFF_STRIP_DISMISS_MAX_DP = 72f

private const val MAX_PULL_DP = 400f

/** How close two taps on the strip have to be to count as one gesture. */
private const val DOUBLE_TAP_WINDOW_MS = 300L

/**
 * The invisible strip along a screen edge that opens the app list and then scrubs it, so one
 * unbroken touch does both.
 *
 * Writes straight into [state] rather than reporting upward through callbacks: the letter
 * changes ~26 times per gesture, and routing that through the caller is what used to
 * recompose the home screen on every one of them.
 */
@Composable
fun EdgeTouchZone(
    side: EdgeSide,
    widthDp: Dp,
    letters: List<Char>,
    band: ScrubBand,
    /** Whether the live part of the edge is only as tall as the band. */
    bandOnly: Boolean,
    /** Whether resting on a letter walks the list on through it. */
    holdScroll: Boolean,
    /** How much of the very edge is left to the system's own gestures. */
    insetDp: Dp,
    hapticsEnabled: Boolean,
    state: ScrubState,
    /** Whether the app list is already showing, so a tap on the strip knows which it is. */
    listOpen: Boolean,
    onOpen: () -> Unit,
    /** Closes an already-open list, the way tapping the empty space above it does. */
    onDismiss: () -> Unit,
    /** Null when double-tap-to-lock is off, so a second tap is simply another tap. */
    onDoubleTap: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val fromLeft = side == EdgeSide.LEFT
    // The gesture handler outlives the composition that built it, so it must not close over
    // this frame's callbacks.
    val currentDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentListOpen by rememberUpdatedState(listOpen)

    Box(
        modifier = modifier
            // Held off the edge rather than made narrower: the point is to leave the outermost
            // pixels to the system, so the strip has to start inside them.
            .padding(start = if (fromLeft) insetDp else 0.dp, end = if (fromLeft) 0.dp else insetDp)
            .width(widthDp)
            // The whole side, or only as much of it as the letters occupy. Full height is the
            // old behaviour and still the default; confined, the edge stops taking touches
            // meant for whatever shares that side of the screen.
            .then(
                if (!bandOnly) {
                    Modifier.fillMaxHeight()
                } else {
                    Modifier
                        .offset { IntOffset(0, band.topPx.roundToInt()) }
                        .height((band.heightPx / density).dp)
                }
            )
            .pointerInput(letters, band, hapticsEnabled, fromLeft, bandOnly) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    // Read before onOpen changes it: a tap that opened the list must not
                    // also be read as a tap on an open one.
                    val openAtDown = currentListOpen
                    state.begin(side)
                    onOpen()

                    var lastIndex = -1
                    // The edge strip runs the height of the screen while the letters occupy
                    // only a band of it. Clamping a touch above the band onto A yanked the
                    // list to A from somewhere A visibly is not — so nothing is picked until
                    // the finger has actually been on the letters. Once it has, clamping
                    // resumes, and A can be grabbed and dragged well above its own position.
                    var enteredBand = false
                    // Dragged far enough off an end of the letters to mean "done with this".
                    // Armed rather than acted on, so the list goes when the finger lifts and
                    // not the instant it crosses — which would take the list away mid-drag.
                    var armedToClose = false
                    val zoneTopPx = if (bandOnly) band.topPx else 0f
                    val letterHeight = if (letters.isEmpty()) 0f else band.heightPx / letters.size
                    val overshoot = (letterHeight * OFF_STRIP_DISMISS_LETTERS)
                        .coerceIn(OFF_STRIP_DISMISS_MIN_DP * density, OFF_STRIP_DISMISS_MAX_DP * density)
                    fun report(x: Float, localY: Float) {
                        // A touch arrives in the zone's own coordinates while the band is
                        // measured against the whole screen. Those were the same thing while
                        // the zone ran the full height; confined to the band it starts at the
                        // band's top, so local y has to be put back into the space the geometry
                        // is written in — otherwise every y reads as above the band, no letter
                        // is ever picked, and the strip looks dead.
                        val y = localY + zoneTopPx
                        if (!enteredBand && ScrubberGeometry.isWithin(y, band.topPx, band.heightPx)) {
                            enteredBand = true
                        }
                        // Same geometry the visible strip uses, so the letter under the
                        // fingertip is the one that swells.
                        val index = ScrubberGeometry.indexForY(y, band.topPx, band.heightPx, letters.size)
                        if (enteredBand && index != lastIndex) {
                            lastIndex = index
                            HapticUtil.tick(view, hapticsEnabled)
                        }
                        // Past either end of the letters by more than a thumb's width. The
                        // overshoot matters: a short one still clamps to A or Z, which is what
                        // lets the first and last letters be grabbed from outside their own
                        // position, and only past that does leaving mean leaving.
                        val off = enteredBand && (
                            y < band.topPx - overshoot || y > band.bottomPx + overshoot
                        )
                        if (off != armedToClose) {
                            armedToClose = off
                            // Said out loud, or nothing tells you that letting go now does
                            // something other than what it did a moment ago.
                            HapticUtil.tick(view, hapticsEnabled)
                        }
                        // How far the finger has pulled in toward the middle of the screen.
                        val inward = if (fromLeft) x - size.width else -x
                        state.update(
                            y,
                            inward.coerceIn(0f, MAX_PULL_DP * density),
                            // No letter while the list is on its way out: placing it at A as
                            // you leave is work nobody asked for and nobody sees.
                            if (enteredBand && !armedToClose) letters.getOrNull(index) else null,
                        )
                    }

                    report(down.position.x, down.position.y)

                    // Two things start the same way, told apart by whether the finger moves:
                    // travelling is a scrub, letting go without it is a tap that a second tap
                    // turns into a lock. Opening the list on the down is untouched by either.
                    var moved = false
                    // Where the finger last actually went somewhere, and when. Resting on a
                    // letter still delivers events — a fingertip is never quite still — so the
                    // hold is judged by distance travelled rather than by events arriving.
                    var restAt = down.position
                    // The dwell is timed beside the gesture rather than inside it. Waiting for
                    // the wait with a timeout around awaitPointerEvent meant abandoning and
                    // restarting the await every few frames, and an event arriving in one of
                    // those gaps was simply lost — which showed up as the strip lagging and its
                    // letters jumping, for everyone, whether this option was on or not.
                    var holdJob: Job? = null
                    fun armHold() {
                        holdJob?.cancel()
                        if (!holdScroll) return
                        holdJob = scope.launch {
                            delay(HOLD_START_MS)
                            // Read when it fires, not when it was armed: by now the finger may
                            // have left the letters or crossed the line that means leaving.
                            if (enteredBand && !armedToClose) state.holdScroll(true)
                        }
                    }
                    fun cancelHold() {
                        holdJob?.cancel()
                        holdJob = null
                        state.holdScroll(false)
                    }
                    armHold()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if ((change.position - restAt).getDistance() > HOLD_SLOP_DP * density) {
                            // Moved on: this is a scrub again, not a rest. The clock restarts
                            // from here rather than from where the finger first landed.
                            restAt = change.position
                            cancelHold()
                            armHold()
                        }
                        // A tap places the list without ever counting as a scrub, so the
                        // overlay does not spend the tap fading itself out and back in.
                        if (!moved &&
                            (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                        ) {
                            moved = true
                            state.markScrubbing()
                        }
                        report(change.position.x, change.position.y)
                        change.consume()
                    }

                    cancelHold()
                    scope.launch { state.release() }

                    // Dragged off the end and let go: the whole gesture was open, look, leave,
                    // without the finger coming up in between.
                    if (armedToClose) {
                        currentDismiss()
                        return@awaitEachGesture
                    }

                    if (!moved) {
                        val doubleTap = currentDoubleTap
                        val previous = state.lastTapUptimeMs
                        if (doubleTap != null && down.uptimeMillis - previous <= DOUBLE_TAP_WINDOW_MS) {
                            state.lastTapUptimeMs = 0L
                            doubleTap()
                        } else {
                            state.lastTapUptimeMs = down.uptimeMillis
                            // Nothing was picked, so this was a tap on bare strip. Above and
                            // below the letters the strip is empty space like the space above
                            // the list, and it dismisses for the same reason.
                            if (!enteredBand && openAtDown) currentDismiss()
                        }
                    }
                }
            },
    )
}