// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Records where a touch went down without consuming anything, so a row can position its
 * long-press menu at the finger while still letting `combinedClickable` and any scrolling or
 * dragging parent arbitrate the gesture normally.
 *
 * Doing this with `detectTapGestures` instead consumes the down event, which silently stops
 * the parent from ever seeing the drag.
 */
fun Modifier.recordTouchPosition(state: MutableState<Offset>): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        state.value = down.position
    }
}