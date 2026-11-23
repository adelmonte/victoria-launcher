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
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.EdgeSide
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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

    /** [atEnd] asks for the very end of the list, past the last letter's apps (#169). */
    data class Placement(val letter: Char, val count: Long, val atEnd: Boolean = false)

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

    /**
     * True while the finger has left the letter strip and is travelling over the apps. Letters
     * stop being picked, every row is shown again, and letting go launches the app under the
     * finger (or closes the list if there is none).
     */
    var onApps by mutableStateOf(false)
        private set

    /** The app row under the finger while [onApps], for the hover highlight. */
    var hoverKey by mutableStateOf<String?>(null)
        private set

    /**
     * Registered by the list: the app row at a screen y, or null for anything that is not an
     * app (a letter header, a gap, past either end). Not snapshot state; only the gesture
     * calls it.
     */
    var appAt: ((Float) -> AppInfo?)? = null

    /**
     * Where the finger is while [onApps], in screen y, so the list can scroll on its own when
     * the finger nears its top or bottom. Read once a frame by that scroll, so not snapshot
     * state: a write per touch event would recompose the list for nothing.
     */
    var appsFingerY = 0f

    /** Registered by the list: launches [AppInfo] exactly as tapping its row would. */
    var launchApp: ((AppInfo) -> Unit)? = null

    fun updateOnApps(value: Boolean) {
        if (onApps != value) onApps = value
        if (!value) hoverKey = null
    }

    fun setHover(key: String?) {
        if (hoverKey != key) hoverKey = key
    }

    /** Which edge is in play, so the strip only appears on the side actually used. */
    var side by mutableStateOf(EdgeSide.RIGHT)
        private set

    private var y by mutableFloatStateOf(0f)

    // Elastic pull: follows the finger inward, then eases back to the strip on release.
    private var pull by mutableFloatStateOf(0f)
    private val releasePull = Animatable(0f)
    private var releasing by mutableStateOf(false)

    // How far the strip has been pushed up or down by a finger run off one of its ends, with
    // "Strip follows past the ends" on. Kept for the rest of the gesture, the way a thing that
    // was pushed stays where it was pushed to, and springs home on release (#169).
    private var shift by mutableFloatStateOf(0f)
    private val releaseShift = Animatable(0f)

    /**
     * When the strip was last tapped, so a second tap can be recognized. Deliberately not
     * snapshot state: nothing draws from it, and both edge zones share one holder so a
     * double tap works even with the strip enabled on both sides.
     */
    var lastTapUptimeMs: Long = 0L

    fun currentY(): Float? = if (active) y else null

    fun currentPull(): Float = if (releasing) releasePull.value else pull

    fun currentShift(): Float = if (releasing) releaseShift.value else shift

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
        updateOnApps(false)
        scrubbing = false
        releasing = false
        shift = 0f
        // Every gesture asks afresh, so picking the same letter twice places the list twice —
        // the second time is someone who scrolled away and wants to be back there.
        placement = null
    }

    /**
     * True while a finger has been resting on a letter long enough to mean "keep going".
     *
     * A letter places the list at its first app and stops there, which is no help when a letter
     * holds a hundred of them. Holding still asks for the rest of them to come past.
     */
    var holdScrolling by mutableStateOf(false)
        private set

    fun holdScroll(value: Boolean) {
        if (holdScrolling != value) holdScrolling = value
    }

    /** Called once the gesture passes touch slop, never for a tap. */
    fun markScrubbing() {
        scrubbing = true
    }

    /**
     * [shiftPx] is how far the strip has been pushed, and [atEnd] that it is being pushed past
     * its last letter, which places the list at its very end rather than at any letter.
     */
    fun update(y: Float, inwardPx: Float, letter: Char?, shiftPx: Float = 0f, atEnd: Boolean = false) {
        this.y = y
        this.pull = inwardPx
        this.shift = shiftPx
        if (atEnd && letter != null) {
            // No letter is being pointed at, so none is shown or singled out.
            this.letter = null
            if (placement?.atEnd != true) placement = Placement(letter, ++placementCount, atEnd = true)
            return
        }
        this.letter = letter
        if (letter != null && (letter != placement?.letter || placement?.atEnd == true)) {
            placement = Placement(letter, ++placementCount)
        }
    }

    /** Forgotten when the list closes, so the next opening starts where the list does. */
    fun clearPlacement() {
        placement = null
    }

    /** Releases the elastic pull back to the strip. Suspends until the spring settles. */
    suspend fun release() {
        holdScrolling = false
        updateOnApps(false)
        active = false
        scrubbing = false
        letter = null
        releasing = true
        releasePull.snapTo(pull)
        releaseShift.snapTo(shift)
        val home = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        )
        coroutineScope {
            launch { releasePull.animateTo(0f, home) }
            launch { releaseShift.animateTo(0f, home) }
        }
        releasing = false
        pull = 0f
        shift = 0f
    }

    /** Cancels a scrub without the spring, for dismissals that snap (the HOME key). */
    fun cancel() {
        holdScrolling = false
        updateOnApps(false)
        active = false
        scrubbing = false
        letter = null
        placement = null
        releasing = false
        pull = 0f
        shift = 0f
    }
}