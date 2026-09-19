// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Wraps an embedded AppWidgetHostView so a genuine long-press (finger held still) opens our
 * edit menu, while an ordinary tap or drag still reaches the widget underneath untouched.
 *
 * A plain Compose pointerInput long-press detector can't do this: once it starts tracking a
 * gesture it owns the whole touch stream, so the widget's own buttons (play/pause, a weather
 * tap-to-open) would stop working. This mirrors how scrollable containers arbitrate gestures
 * with their children: don't intercept on ACTION_DOWN, keep watching via onInterceptTouchEvent,
 * and only steal the stream once our own long-press timer actually fires.
 */
class LongPressFrameLayout(context: Context) : FrameLayout(context) {

    /** x/y are the press position in this view's local pixel coordinates. */
    var onLongPress: ((x: Float, y: Float) -> Unit)? = null

    private var longPressFired = false

    /** Where the current gesture began, so its direction can be judged as it moves. */
    private var downX = 0f
    private var downY = 0f
    private var decided = false

    /** Half a touch slop: enough to read a direction, short enough to beat the home screen. */
    private val decisionSlop get() = touchSlop / 2
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                longPressFired = true
                onLongPress?.invoke(e.x, e.y)
            }
        },
    )

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressFired = false
                decided = false
                downX = ev.x
                downY = ev.y
            }

            MotionEvent.ACTION_MOVE -> if (!decided) {
                val dx = abs(ev.x - downX)
                val dy = abs(ev.y - downY)
                // Decided early, and only for up and down.
                //
                // Claiming on the press instead took sideways with it, and handing it back
                // once the drag turned out to be sideways does not give the pager its chance
                // again — so a widget that scrolls could never be swiped past. Waiting for a
                // full touch slop was the other way round: the home screen decides at exactly
                // that distance, and whichever ran first won, which is how a list inside a
                // widget lost its scroll to the notification shade.
                //
                // Half a slop is far enough to tell a direction and short enough to answer
                // before the home screen does. Sideways is never claimed, so the pager keeps
                // it, and a widget with nothing to scroll is never claimed for either.
                if (dy > decisionSlop && dy > dx) {
                    decided = true
                    if (hasScrollableContent()) parent?.requestDisallowInterceptTouchEvent(true)
                } else if (dx > decisionSlop && dx > dy) {
                    decided = true
                }
            }
        }
        gestureDetector.onTouchEvent(ev)
        if (longPressFired) {
            // Our own menu is taking over, so the hold on the parent is no longer wanted.
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return longPressFired
    }

    /**
     * Whether anything inside the widget can actually be scrolled right now.
     *
     * Asked of the view tree rather than the widget's declaration: a list that is too short to
     * scroll answers no, which is the right answer — there is nothing to take the gesture for.
     */
    private fun hasScrollableContent(): Boolean {
        fun scrollable(view: View): Boolean {
            if (view.canScrollVertically(1) || view.canScrollVertically(-1)) return true
            if (view !is ViewGroup) return false
            for (i in 0 until view.childCount) {
                if (scrollable(view.getChildAt(i))) return true
            }
            return false
        }
        for (i in 0 until childCount) {
            if (scrollable(getChildAt(i))) return true
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            longPressFired = false
            decided = false
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
}