// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import android.content.Context

/**
 * What one clock widget has been set to show.
 *
 * Kept per widget id in a file of its own rather than in the launcher's settings: two clocks
 * on the same home screen are two widgets and can be set differently, and a widget's settings
 * belong to the widget — they are reached by its own menu and go with it when it is removed.
 *
 * Plain SharedPreferences because a widget provider is a broadcast receiver: it is asked to
 * draw and must answer now, with no scope to suspend in.
 */
data class ClockWidgetConfig(
    val hourFormat: HourFormat,
    val datePattern: String?,
    val timeSizeSp: Int,
) {
    enum class HourFormat { SYSTEM, TWELVE, TWENTY_FOUR }

    /** The date line is this much smaller than the time, so one size setting sets both. */
    val dateSizeSp: Int get() = (timeSizeSp * 0.34f).toInt().coerceAtLeast(11)

    companion object {
        private const val FILE = "clock_widget"
        private const val DEFAULT_SIZE_SP = 44

        /** Weekday, day, month — short enough for a narrow widget and clear in any language. */
        const val DEFAULT_DATE = "EEE, d MMM"

        /** Offered in the widget's own settings; the pattern is what TextClock is given. */
        val DATE_CHOICES = listOf(
            "EEE, d MMM",
            "EEEE, d MMMM",
            "d MMM yyyy",
            "dd.MM.yyyy",
            "MM/dd/yyyy",
        )

        /** Small steps, since the right size depends on the height the widget was given. */
        val SIZE_RANGE = 20..96
        const val SIZE_STEP = 2

        fun read(context: Context, widgetId: Int): ClockWidgetConfig {
            val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            val hour = runCatching {
                HourFormat.valueOf(prefs.getString(key("hour", widgetId), null) ?: HourFormat.SYSTEM.name)
            }.getOrDefault(HourFormat.SYSTEM)
            // An absent key is the default; the empty string is the deliberate choice of no
            // date at all, which is why one is not stored as the other.
            val stored = prefs.getString(key("date", widgetId), null) ?: DEFAULT_DATE
            return ClockWidgetConfig(
                hourFormat = hour,
                datePattern = stored.takeIf { it.isNotEmpty() },
                timeSizeSp = prefs.getInt(key("size", widgetId), DEFAULT_SIZE_SP),
            )
        }

        fun write(context: Context, widgetId: Int, config: ClockWidgetConfig) {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putString(key("hour", widgetId), config.hourFormat.name)
                .putString(key("date", widgetId), config.datePattern.orEmpty())
                .putInt(key("size", widgetId), config.timeSizeSp)
                .apply()
        }

        /** Dropped when the widget is, so a later widget cannot inherit its id's settings. */
        fun forget(context: Context, widgetId: Int) {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .remove(key("hour", widgetId))
                .remove(key("date", widgetId))
                .remove(key("size", widgetId))
                .apply()
        }

        private fun key(name: String, widgetId: Int) = name + "_" + widgetId
    }
}
