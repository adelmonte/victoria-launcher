// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
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
            .width(widthDp)
            .fillMaxHeight()
            .pointerInput(letters, band, hapticsEnabled, fromLeft) {
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
                    val letterHeight = if (letters.isEmpty()) 0f else band.heightPx / letters.size
                    val overshoot = (letterHeight * OFF_STRIP_DISMISS_LETTERS)
                        .coerceIn(OFF_STRIP_DISMISS_MIN_DP * density, OFF_STRIP_DISMISS_MAX_DP * density)
                    fun report(x: Float, y: Float) {
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
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
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