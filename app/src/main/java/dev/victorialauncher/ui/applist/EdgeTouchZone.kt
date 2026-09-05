// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import dev.victorialauncher.data.EdgeSide
import dev.victorialauncher.service.HapticUtil
import kotlinx.coroutines.launch

/** How far the strip may be dragged inward before the pull stops growing. */
private const val MAX_PULL_DP = 400f

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
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val fromLeft = side == EdgeSide.LEFT

    Box(
        modifier = modifier
            .width(widthDp)
            .fillMaxHeight()
            .pointerInput(letters, band, hapticsEnabled, fromLeft) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    state.begin(side)
                    onOpen()

                    var lastIndex = -1
                    fun report(x: Float, y: Float) {
                        // Same geometry the visible strip uses, so the letter under the
                        // fingertip is the one that swells.
                        val index = ScrubberGeometry.indexForY(y, band.topPx, band.heightPx, letters.size)
                        if (index != lastIndex) {
                            lastIndex = index
                            HapticUtil.tick(view, hapticsEnabled)
                        }
                        // How far the finger has pulled in toward the middle of the screen.
                        val inward = if (fromLeft) x - size.width else -x
                        state.update(y, inward.coerceIn(0f, MAX_PULL_DP * density), letters.getOrNull(index))
                    }

                    report(down.position.x, down.position.y)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        report(change.position.x, change.position.y)
                        change.consume()
                    }
                    scope.launch { state.release() }
                }
            },
    )
}