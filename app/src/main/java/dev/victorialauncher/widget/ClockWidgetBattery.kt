// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat

/**
 * Keeps the battery line on the clock widget current.
 *
 * A widget cannot watch anything by itself — it is drawn once and then left alone — and the
 * battery level is not something a RemoteViews can be told to follow the way TextClock follows
 * the time. So the launcher watches, and redraws the widgets that asked for the line.
 *
 * Registered here rather than declared in the manifest because ACTION_BATTERY_CHANGED is one of
 * the broadcasts the system refuses to deliver to a manifest receiver. That is no loss: this
 * process is the one drawing the widget, so it is alive exactly when the number can be seen.
 */
object ClockWidgetBattery {

    /** Last level drawn, so the many broadcasts that change nothing visible cost nothing. */
    private var lastPercent = -1
    private var watching = false

    /** The level right now, for a draw that happens between broadcasts. */
    fun percent(context: Context): Int? = runCatching {
        // A null receiver reads the sticky value without registering anything.
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        status?.let { percentOf(it) }
    }.getOrNull()

    fun watch(context: Context) {
        if (watching) return
        watching = true
        val app = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val percent = percentOf(intent) ?: return
                if (percent == lastPercent) return
                lastPercent = percent
                redraw(app)
            }
        }
        // The sticky broadcast arrives as soon as this registers, so the first draw is right.
        runCatching {
            ContextCompat.registerReceiver(
                app,
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    private fun percentOf(intent: Intent): Int? {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return level * 100 / scale
    }

    /** Only the widgets showing a battery line, since a redraw of the rest would change nothing. */
    private fun redraw(context: Context) {
        val manager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull() ?: return
        val ids = runCatching {
            manager.getAppWidgetIds(ClockWidgetProvider.componentName(context))
        }.getOrNull() ?: return
        ids.filter { ClockWidgetConfig.read(context, it).showBattery }
            .forEach { runCatching { ClockWidgetProvider.render(context, manager, it) } }
    }
}
