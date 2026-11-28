// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow

/**
 * Where the hosted widgets are on screen right now, so a touch that lands on the edge strip can
 * be told from one meant for the widget underneath it (#185).
 *
 * The strip is drawn over the home screen, and where two siblings overlap Compose asks only the
 * top one. Leaving the down unconsumed changes nothing: the widget was never offered the touch
 * to begin with. So the strip has to know a widget is there, and hand it the tap itself.
 *
 * Plain fields rather than state. This is read once per touch, and nothing should recompose
 * because a widget moved.
 */
class HostedWidgets {

    private class Placed(val coordinates: LayoutCoordinates, val view: () -> LongPressFrameLayout?)

    private val placed = HashMap<Any, Placed>()

    /** [coordinates] are kept and asked again at each touch, so they follow the widget around. */
    fun place(key: Any, coordinates: LayoutCoordinates, view: () -> LongPressFrameLayout?) {
        placed[key] = Placed(coordinates, view)
    }

    fun remove(key: Any) {
        placed.remove(key)
    }

    /** The widget showing at [windowPosition], or null when there is none. */
    fun at(windowPosition: Offset): Tap? {
        for (entry in placed.values) {
            val coordinates = entry.coordinates
            if (!coordinates.isAttached) continue
            val view = entry.view() ?: continue
            // An empty container is a widget that failed to load: nothing there to tap.
            if (!view.isAttachedToWindow || !view.isShown || view.childCount == 0) continue
            // Clipped to what is on screen, so the pager's neighboring pages do not count.
            if (!coordinates.boundsInWindow().contains(windowPosition)) continue
            return Tap(view, coordinates.windowToLocal(windowPosition))
        }
        return null
    }

    /** A tap owed to one widget, at one place on it. */
    class Tap internal constructor(private val view: LongPressFrameLayout, private val at: Offset) {

        /**
         * Gives the widget the tap as a down and an up of its own, timed as the real ones were.
         *
         * The touch itself cannot be passed on: by the time it is known to be a tap, the strip
         * has had all of it.
         */
        fun deliver(downTime: Long, upTime: Long) {
            for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val time = if (action == MotionEvent.ACTION_DOWN) downTime else upTime
                val event = MotionEvent.obtain(downTime, time, action, at.x, at.y, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                view.dispatchTouchEvent(event)
                event.recycle()
            }
        }
    }
}
