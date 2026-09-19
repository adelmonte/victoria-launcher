// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import dev.victorialauncher.R

/**
 * A clock and date, so a fresh launcher has something on it without hunting for a widget that
 * suits it.
 *
 * The views tick by themselves — TextClock runs inside whichever host draws it and follows the
 * system's 12- or 24-hour setting — so this provider is only ever asked to say where a tap
 * goes. There is no update interval and nothing is scheduled.
 */
class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
    }

    /** Settings belong to one widget, so each is drawn from its own. */
    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { ClockWidgetConfig.forget(context, it) }
    }

    companion object {
        fun componentName(context: Context) =
            ComponentName(context, ClockWidgetProvider::class.java)

        /**
         * Draws one widget as its own settings ask.
         *
         * Forcing 12 or 24 hours means giving TextClock the same pattern for both, since it
         * picks between them by the system setting and would otherwise ignore the choice.
         *
         * The request codes carry the widget id, or two clocks would share one PendingIntent
         * and the second would silently take the first's target. Three per widget, one for each
         * row that can be tapped.
         */
        fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val config = ClockWidgetConfig.read(context, widgetId)
            val twelve = "h:mm"
            val twentyFour = "HH:mm"
            val views = RemoteViews(context.packageName, R.layout.widget_clock).apply {
                when (config.hourFormat) {
                    ClockWidgetConfig.HourFormat.SYSTEM -> {
                        setCharSequence(R.id.widget_time, "setFormat12Hour", twelve)
                        setCharSequence(R.id.widget_time, "setFormat24Hour", twentyFour)
                    }
                    ClockWidgetConfig.HourFormat.TWELVE -> {
                        setCharSequence(R.id.widget_time, "setFormat12Hour", twelve)
                        setCharSequence(R.id.widget_time, "setFormat24Hour", twelve)
                    }
                    ClockWidgetConfig.HourFormat.TWENTY_FOUR -> {
                        setCharSequence(R.id.widget_time, "setFormat12Hour", twentyFour)
                        setCharSequence(R.id.widget_time, "setFormat24Hour", twentyFour)
                    }
                }
                setTextViewTextSize(R.id.widget_time, TypedValue.COMPLEX_UNIT_SP, config.timeSizeSp.toFloat())
                setTextColor(R.id.widget_time, config.colorAt(config.timeOpacity))

                val date = config.datePattern
                if (date == null) {
                    setViewVisibility(R.id.widget_date, View.GONE)
                } else {
                    setViewVisibility(R.id.widget_date, View.VISIBLE)
                    setCharSequence(R.id.widget_date, "setFormat12Hour", date)
                    setCharSequence(R.id.widget_date, "setFormat24Hour", date)
                    setTextViewTextSize(R.id.widget_date, TypedValue.COMPLEX_UNIT_SP, config.dateSizeSp.toFloat())
                    setTextColor(R.id.widget_date, config.colorAt(config.dateOpacity))
                }

                // Read on each draw rather than followed: a RemoteViews can be told to keep up
                // with the clock, but there is no such thing for the battery.
                val battery = if (config.showBattery) ClockWidgetBattery.percent(context) else null
                if (battery == null) {
                    setViewVisibility(R.id.widget_battery, View.GONE)
                } else {
                    setViewVisibility(R.id.widget_battery, View.VISIBLE)
                    setTextViewText(R.id.widget_battery, context.getString(R.string.widget_clock_battery_percent, battery))
                    setTextViewTextSize(R.id.widget_battery, TypedValue.COMPLEX_UNIT_SP, config.batterySizeSp.toFloat())
                    setTextColor(R.id.widget_battery, config.colorAt(config.batteryOpacity))
                }

                setOnClickPendingIntent(R.id.widget_time, open(context, clockIntent(context), widgetId * 3))
                setOnClickPendingIntent(R.id.widget_date, open(context, calendarIntent(), widgetId * 3 + 1))
                setOnClickPendingIntent(R.id.widget_battery, open(context, batteryIntent(context), widgetId * 3 + 2))
            }
            manager.updateAppWidget(widgetId, views)
        }

        private fun open(context: Context, intent: Intent, requestCode: Int): PendingIntent? =
            runCatching {
                PendingIntent.getActivity(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }.getOrNull()

        private fun clockIntent(context: Context): Intent {
            val showAlarms = Intent(AlarmClock.ACTION_SHOW_ALARMS)
            val handler = runCatching {
                context.packageManager.resolveActivity(showAlarms, 0)
            }.getOrNull()?.activityInfo
            if (handler != null && handler.exported) {
                return showAlarms
                    .setClassName(handler.packageName, handler.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val launch = handler?.packageName
                ?.let { runCatching { context.packageManager.getLaunchIntentForPackage(it) }.getOrNull() }
            return (launch ?: showAlarms).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        /**
         * Battery settings, or the settings app if this device has no such screen. A
         * PendingIntent aimed at nothing fails silently at the tap, which reads as a dead
         * widget rather than as a missing screen.
         */
        private fun batteryIntent(context: Context): Intent {
            val usage = Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
            val resolved = runCatching {
                context.packageManager.resolveActivity(usage, 0)
            }.getOrNull() != null
            return (if (resolved) usage else Intent(Settings.ACTION_SETTINGS))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        private fun calendarIntent() = Intent(Intent.ACTION_VIEW)
            .setData(CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
