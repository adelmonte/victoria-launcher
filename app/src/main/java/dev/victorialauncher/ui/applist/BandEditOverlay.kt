// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.victorialauncher.R
import dev.victorialauncher.data.EdgeSide
import kotlin.math.roundToInt

/**
 * How far the wallpaper is knocked back behind the overlay.
 *
 * Light, because there is nothing left to hide: the home screen is taken away while this is
 * open, so the scrim only has to keep the hint and the buttons legible on a busy wallpaper.
 */
private const val SCRIM_ALPHA = 0.3f

/** Small enough to be useless below this, so the two handles can never cross. */
private val MIN_BAND_HEIGHT = 120.dp

/**
 * Sets how much of the screen the A-Z strip spans.
 *
 * The strip normally matches the favorites list, which reads well until somebody keeps three
 * favorites and the alphabet is squeezed into a couple of centimetres. Dragging either handle
 * here pins the range by hand; Reset hands it back to following the favorites.
 */
@Composable
fun BandEditOverlay(
    band: ScrubBand,
    side: EdgeSide,
    viewportHeightPx: Int,
    onBandChange: (ScrubBand) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val minHeightPx = with(density) { MIN_BAND_HEIGHT.toPx() }
    val alignment = if (side == EdgeSide.LEFT) Alignment.TopStart else Alignment.TopEnd

    val viewportPx = viewportHeightPx.toFloat()

    // Every bound is coerced against a range that cannot invert. Written the obvious way,
    // a band taller than the viewport gives coerceIn a minimum above its maximum, which
    // throws rather than clamping — and the strip ends up somewhere unreachable.
    fun moveTop(delta: Float) {
        val top = (band.topPx + delta).coerceIn(0f, (band.bottomPx - minHeightPx).coerceAtLeast(0f))
        onBandChange(ScrubBand(topPx = top, heightPx = band.bottomPx - top))
    }

    fun moveBottom(delta: Float) {
        val lowest = band.topPx + minHeightPx
        val bottom = (band.bottomPx + delta).coerceIn(lowest, viewportPx.coerceAtLeast(lowest))
        onBandChange(ScrubBand(topPx = band.topPx, heightPx = bottom - band.topPx))
    }

    /** Slides the whole range without resizing it; the handles only ever moved one edge. */
    fun moveWhole(delta: Float) {
        val highest = (viewportPx - band.heightPx).coerceAtLeast(0f)
        val top = (band.topPx + delta).coerceIn(0f, highest)
        onBandChange(ScrubBand(topPx = top, heightPx = band.heightPx))
    }

    Box(modifier = modifier.fillMaxSize()) {
        // A sibling drawn first, not a wrapper: as a wrapper it intercepted on the way down
        // and ate its own buttons. Behind everything else it only ever catches what the
        // handles and buttons did not, which is the whole job — without it the scrim is just
        // paint and taps launch whatever app is underneath.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                            if (event.changes.none { it.pressed }) break
                        }
                    }
                },
        )

        Box(
            modifier = Modifier
                .align(alignment)
                .offset { IntOffset(0, band.topPx.roundToInt()) }
                .width(96.dp)
                .height(with(density) { band.heightPx.toDp() })
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    RoundedCornerShape(12.dp),
                )
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { moveWhole(it) },
                ),
        )

        BandHandle(alignment, band.topPx, R.string.applist_band_top) { moveTop(it) }
        BandHandle(alignment, band.bottomPx, R.string.applist_band_bottom) { moveBottom(it) }

        // Pinned to the bottom rather than the middle, where a handle can sit on top of them.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.applist_band_hint),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = onReset) { Text(stringResource(R.string.action_reset)) }
                TextButton(onClick = onDone) { Text(stringResource(R.string.action_done)) }
            }
        }
    }
}

@Composable
private fun BoxScope.BandHandle(
    alignment: Alignment,
    y: Float,
    descriptionRes: Int,
    onDrag: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val half = with(density) { 22.dp.toPx() }
    Box(
        modifier = Modifier
            .align(alignment)
            .offset { IntOffset(0, (y - half).roundToInt()) }
            .size(88.dp, 44.dp)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                RoundedCornerShape(22.dp),
            )
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { onDrag(it) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.DragHandle,
            contentDescription = stringResource(descriptionRes),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
