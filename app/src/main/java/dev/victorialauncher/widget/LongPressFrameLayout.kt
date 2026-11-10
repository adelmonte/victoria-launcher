// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/** How much more vertical than sideways a drag must be before the widget takes it for a scroll. */
internal const val DIRECTION_MARGIN = 1.25f

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

    /**
     * Given, told the content's own height in pixels on every measure, whatever height this
     * view is being made to fill.
     *
     * Asked here rather than of the Compose side: the hosted view hiding a row did lay itself
     * out again, but only ever inside the height it already had, so a clock could grow to fit
     * and never shrink back.
     */
    var onNaturalHeight: ((Int) -> Unit)? = null
        set(value) {
            val turnedOn = field == null && value != null
            field = value
            // Nothing else may be about to lay this out, and the first answer is the one wanted.
            if (turnedOn) {
                lastNaturalHeight = -1
                requestLayout()
            }
        }
    private var lastNaturalHeight = -1

    private var longPressFired = false

    /** Set once the widget has taken this gesture, so the claim is made once and not per move. */
    private var claimed = false

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                longPressFired = true
                onLongPress?.invoke(e.x, e.y)
            }
        },
    )

    /**
     * Hands this gesture to the widget's own scrolling, and to nothing else: the home screen
     * stops seeing it, and the press can no longer turn into a long-press.
     *
     * Decided on the Compose side, which sees each move before the home screen does — see
     * [claimsVerticalScroll].
     */
    fun claimScroll() {
        if (claimed) return
        claimed = true
        parent?.requestDisallowInterceptTouchEvent(true)
        val now = android.os.SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        gestureDetector.onTouchEvent(cancel)
        cancel.recycle()
    }

    // The long-press timer is fed here rather than from onInterceptTouchEvent. A list inside
    // the widget that starts scrolling asks this view to stop intercepting, after which
    // onInterceptTouchEvent hears nothing more of the gesture — the timer never saw the finger
    // move, and a slow scroll opened the menu partway through.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            longPressFired = false
            claimed = false
        }
        if (!claimed) gestureDetector.onTouchEvent(ev)
        val handled = super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            longPressFired = false
            claimed = false
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return handled
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
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
    fun hasScrollableContent(): Boolean {
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val report = onNaturalHeight
        val content = getChildAt(0)
        if (report != null && content != null && MeasureSpec.getSize(widthMeasureSpec) > 0) {
            content.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val natural = content.measuredHeight + paddingTop + paddingBottom
            if (natural > 0 && natural != lastNaturalHeight) {
                lastNaturalHeight = natural
                // After the pass rather than inside it: the answer resizes the slot this is
                // being measured for.
                post { report(natural) }
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = true
}
