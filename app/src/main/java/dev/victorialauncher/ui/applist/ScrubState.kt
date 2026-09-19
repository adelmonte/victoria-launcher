// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.victorialauncher.data.EdgeSide

/**
 * Everything that changes while a finger is on the A-Z strip, in one stable holder.
 *
 * This exists for recomposition scope, not tidiness. The letter under the fingertip changes
 * up to 26 times per gesture; when the home destination read it directly, each change
 * invalidated that whole scope — and the home screen composable hangs off it. Passing this
 * holder down instead means only the composable that actually reads a field is invalidated,
 * so scrubbing no longer recomposes the home screen behind the overlay.
 *
 * [currentY] and [currentPull] are deliberately functions rather than properties: their
 * callers read them inside `graphicsLayer`, which puts the read in the draw phase and skips
 * recomposition altogether.
 */
@Stable
class ScrubState {
    /** The letter under the fingertip, or null when no scrub is in progress. */
    var letter by mutableStateOf<Char?>(null)
        private set

    /**
     * The letter a gesture asked the list to be parked at, with a count of the asking.
     *
     * Separate from [letter], which is what the fingertip is on right now and is dropped the
     * instant it lifts. A tap quick enough to land and lift inside one frame set a letter and
     * cleared it again before anything had composed, so the list opened at A having been told
     * about M and back. The request outlives the touch; the count is what tells a second ask
     * for the same letter from the first.
     */
    var placement by mutableStateOf<Placement?>(null)
        private set

    private var placementCount = 0L

    data class Placement(val letter: Char, val count: Long)

    /** True while a finger is down on an edge zone. */
    var active by mutableStateOf(false)
        private set

    /**
     * True once the finger has actually traveled, as opposed to merely landing. The letter
     * under a fingertip is reported from the down event so that a tap still places the list,
     * but the drawn-out affordances — fading every other row away — are worth their cost only
     * while a finger is genuinely moving through the alphabet. Gating them on this is what
     * stops a plain tap on the edge spending a quarter of a second fading the list out and
     * straight back in again.
     */
    var scrubbing by mutableStateOf(false)
        private set

    /** Which edge is in play, so the strip only appears on the side actually used. */
    var side by mutableStateOf(EdgeSide.RIGHT)
        private set

    private var y by mutableFloatStateOf(0f)

    // Elastic pull: follows the finger inward, then eases back to the strip on release.
    private var pull by mutableFloatStateOf(0f)
    private val releasePull = Animatable(0f)
    private var releasing by mutableStateOf(false)

    /**
     * When the strip was last tapped, so a second tap can be recognized. Deliberately not
     * snapshot state: nothing draws from it, and both edge zones share one holder so a
     * double tap works even with the strip enabled on both sides.
     */
    var lastTapUptimeMs: Long = 0L

    fun currentY(): Float? = if (active) y else null

    fun currentPull(): Float = if (releasing) releasePull.value else pull

    /**
     * Parks the strip on the edge the settings name, so it renders on the right side before
     * the first scrub of a session ever happens. Ignored mid-gesture: the side in play then
     * is whichever edge the finger is actually on.
     */
    fun syncRestingSide(edgeSide: EdgeSide) {
        if (!active) side = if (edgeSide == EdgeSide.LEFT) EdgeSide.LEFT else EdgeSide.RIGHT
    }

    fun begin(side: EdgeSide) {
        this.side = side
        active = true
        scrubbing = false
        releasing = false
        // Every gesture asks afresh, so picking the same letter twice places the list twice —
        // the second time is someone who scrolled away and wants to be back there.
        placement = null
    }

    /** Called once the gesture passes touch slop, never for a tap. */
    fun markScrubbing() {
        scrubbing = true
    }

    fun update(y: Float, inwardPx: Float, letter: Char?) {
        this.y = y
        this.pull = inwardPx
        this.letter = letter
        if (letter != null && letter != placement?.letter) {
            placement = Placement(letter, ++placementCount)
        }
    }

    /** Forgotten when the list closes, so the next opening starts where the list does. */
    fun clearPlacement() {
        placement = null
    }

    /** Releases the elastic pull back to the strip. Suspends until the spring settles. */
    suspend fun release() {
        active = false
        scrubbing = false
        letter = null
        releasing = true
        releasePull.snapTo(pull)
        releasePull.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
        releasing = false
        pull = 0f
    }

    /** Cancels a scrub without the spring, for dismissals that snap (the HOME key). */
    fun cancel() {
        active = false
        scrubbing = false
        letter = null
        placement = null
        releasing = false
        pull = 0f
    }
}