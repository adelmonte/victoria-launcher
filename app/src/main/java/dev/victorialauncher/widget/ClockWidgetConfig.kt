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
    val dateSizeSp: Int,
    val showBattery: Boolean,
    val batterySizeSp: Int,
    val textColor: Int,
    val timeOpacity: Int,
    val dateOpacity: Int,
    val batteryOpacity: Int,
) {
    enum class HourFormat { SYSTEM, TWELVE, TWENTY_FOUR }

    /** The color as one row should draw it, since each row is faded on its own. */
    fun colorAt(opacity: Int): Int = opacityOf(textColor, opacity)

    companion object {
        private const val FILE = "clock_widget"
        private const val DEFAULT_SIZE_SP = 44
        private const val DEFAULT_DATE_SIZE_SP = 16
        private const val DEFAULT_BATTERY_SIZE_SP = 14
        private const val DEFAULT_COLOR = 0xFFFFFFFF.toInt()
        private const val DEFAULT_OPACITY = 100

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
        val SIZE_RANGE = 16..160
        val DATE_SIZE_RANGE = 10..72

        /** Stops short of nothing at all: an invisible row looks like a broken widget. */
        val OPACITY_RANGE = 10..100

        /** Shared with the settings preview, so what is shown there is what is drawn. */
        fun opacityOf(color: Int, opacity: Int): Int {
            val alpha = (opacity.coerceIn(OPACITY_RANGE) * 255 / 100) shl 24
            return alpha or (color and 0x00FFFFFF)
        }

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
                dateSizeSp = prefs.getInt(key("dateSize", widgetId), DEFAULT_DATE_SIZE_SP),
                showBattery = prefs.getBoolean(key("battery", widgetId), false),
                batterySizeSp = prefs.getInt(key("batterySize", widgetId), DEFAULT_BATTERY_SIZE_SP),
                textColor = prefs.getInt(key("color", widgetId), DEFAULT_COLOR),
                timeOpacity = prefs.getInt(key("timeOpacity", widgetId), DEFAULT_OPACITY),
                dateOpacity = prefs.getInt(key("dateOpacity", widgetId), DEFAULT_OPACITY),
                batteryOpacity = prefs.getInt(key("batteryOpacity", widgetId), DEFAULT_OPACITY),
            )
        }

        fun write(context: Context, widgetId: Int, config: ClockWidgetConfig) {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putString(key("hour", widgetId), config.hourFormat.name)
                .putString(key("date", widgetId), config.datePattern.orEmpty())
                .putInt(key("size", widgetId), config.timeSizeSp)
                .putInt(key("dateSize", widgetId), config.dateSizeSp)
                .putBoolean(key("battery", widgetId), config.showBattery)
                .putInt(key("batterySize", widgetId), config.batterySizeSp)
                .putInt(key("color", widgetId), config.textColor)
                .putInt(key("timeOpacity", widgetId), config.timeOpacity)
                .putInt(key("dateOpacity", widgetId), config.dateOpacity)
                .putInt(key("batteryOpacity", widgetId), config.batteryOpacity)
                .apply()
        }

        /** Dropped when the widget is, so a later widget cannot inherit its id's settings. */
        fun forget(context: Context, widgetId: Int) {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .remove(key("hour", widgetId))
                .remove(key("date", widgetId))
                .remove(key("size", widgetId))
                .remove(key("dateSize", widgetId))
                .remove(key("battery", widgetId))
                .remove(key("batterySize", widgetId))
                .remove(key("color", widgetId))
                .remove(key("timeOpacity", widgetId))
                .remove(key("dateOpacity", widgetId))
                .remove(key("batteryOpacity", widgetId))
                .apply()
        }

        private fun key(name: String, widgetId: Int) = name + "_" + widgetId
    }
}
