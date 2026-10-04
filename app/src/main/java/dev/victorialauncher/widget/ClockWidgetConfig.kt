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
    val showWeather: Boolean,
    val weatherSizeSp: Int,
    val weatherFahrenheit: Boolean,
    /** Package the weather row opens, or null for a row that is not a tap target. */
    val weatherPackage: String?,
    val showNotifications: Boolean,
    val notificationsSizeSp: Int,
    val showAgenda: Boolean,
    val agendaSizeSp: Int,
    /** How many upcoming events to show, at most. */
    val agendaCount: Int,
    /** How far ahead to look, in hours. */
    val agendaLookaheadHours: Int,
    /** "in 20 minutes" rather than a clock time. Reads better, goes stale. */
    val agendaRelative: Boolean,
    val showAlarm: Boolean,
    val textColor: Int,
    val timeOpacity: Int,
    val dateOpacity: Int,
    val batteryOpacity: Int,
    val weatherOpacity: Int,
    val notificationsOpacity: Int,
    val agendaOpacity: Int,
) {
    enum class HourFormat { SYSTEM, TWELVE, TWENTY_FOUR }

    /** The color as one row should draw it, since each row is faded on its own. */
    fun colorAt(opacity: Int): Int = opacityOf(textColor, opacity)

    companion object {
        private const val FILE = "clock_widget"
        private const val DEFAULT_SIZE_SP = 44
        private const val DEFAULT_DATE_SIZE_SP = 16
        private const val DEFAULT_BATTERY_SIZE_SP = 14
        private const val DEFAULT_ROW_SIZE_SP = 14
        private const val DEFAULT_COLOR = 0xFFFFFFFF.toInt()
        private const val DEFAULT_OPACITY = 100

        /** Weekday, day, month — short enough for a narrow widget and clear in any language. */
        const val DEFAULT_DATE = "EEE, d MMM"

        /**
         * Whether a pattern is one TextClock can be handed.
         *
         * Checked before it is ever stored. A widget is drawn inside another app's process, so
         * a pattern that throws does not fail here where someone could see why — it fails over
         * there, as a widget that will not draw, with nothing to say which setting did it.
         */
        fun isValidDatePattern(pattern: String): Boolean {
            if (pattern.isBlank() || pattern.length > MAX_DATE_PATTERN) return false
            return runCatching {
                android.text.format.DateFormat.format(pattern, java.util.Date()).toString()
            }.getOrNull()?.isNotEmpty() == true
        }

        /** Long enough for any real format and short enough not to be a essay. */
        const val MAX_DATE_PATTERN = 40

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

        /** More than three and the widget is an agenda rather than a clock. */
        val AGENDA_COUNT_RANGE = 1..3

        /** An hour out to a week; past that it is not what is coming up. */
        val AGENDA_HOURS_RANGE = 1..168

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
                showWeather = prefs.getBoolean(key("weather", widgetId), false),
                weatherSizeSp = prefs.getInt(key("weatherSize", widgetId), DEFAULT_ROW_SIZE_SP),
                // Asked of the locale only the first time, so changing it later does not
                // silently undo a choice made by hand.
                weatherFahrenheit = prefs.getBoolean(
                    key("weatherF", widgetId),
                    ClockWidgetWeather.fahrenheitByDefault(),
                ),
                // Asked for rather than guessed: Android has a category for the calendar app and
                // the music app and no such thing for weather, and the broadcast does not say
                // who sent it. A list of package names we happened to think of would be wrong
                // for whoever is not on it.
                weatherPackage = prefs.getString(key("weatherPkg", widgetId), null),
                showNotifications = prefs.getBoolean(key("notifications", widgetId), false),
                showAgenda = prefs.getBoolean(key("agenda", widgetId), false),
                agendaSizeSp = prefs.getInt(key("agendaSize", widgetId), DEFAULT_ROW_SIZE_SP),
                agendaCount = prefs.getInt(key("agendaCount", widgetId), 1).coerceIn(AGENDA_COUNT_RANGE),
                agendaLookaheadHours = prefs.getInt(key("agendaHours", widgetId), 24)
                    .coerceIn(AGENDA_HOURS_RANGE),
                agendaRelative = prefs.getBoolean(key("agendaRelative", widgetId), true),
                showAlarm = prefs.getBoolean(key("alarm", widgetId), false),
                notificationsSizeSp = prefs.getInt(key("notificationsSize", widgetId), DEFAULT_ROW_SIZE_SP),
                batterySizeSp = prefs.getInt(key("batterySize", widgetId), DEFAULT_BATTERY_SIZE_SP),
                textColor = prefs.getInt(key("color", widgetId), DEFAULT_COLOR),
                timeOpacity = prefs.getInt(key("timeOpacity", widgetId), DEFAULT_OPACITY),
                dateOpacity = prefs.getInt(key("dateOpacity", widgetId), DEFAULT_OPACITY),
                batteryOpacity = prefs.getInt(key("batteryOpacity", widgetId), DEFAULT_OPACITY),
                weatherOpacity = prefs.getInt(key("weatherOpacity", widgetId), DEFAULT_OPACITY),
                notificationsOpacity = prefs.getInt(key("notificationsOpacity", widgetId), DEFAULT_OPACITY),
                agendaOpacity = prefs.getInt(key("agendaOpacity", widgetId), DEFAULT_OPACITY),
            )
        }

        fun write(context: Context, widgetId: Int, config: ClockWidgetConfig) {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putString(key("hour", widgetId), config.hourFormat.name)
                .putString(key("date", widgetId), config.datePattern.orEmpty())
                .putInt(key("size", widgetId), config.timeSizeSp)
                .putInt(key("dateSize", widgetId), config.dateSizeSp)
                .putBoolean(key("battery", widgetId), config.showBattery)
                .putBoolean(key("weather", widgetId), config.showWeather)
                .putInt(key("weatherSize", widgetId), config.weatherSizeSp)
                .putBoolean(key("weatherF", widgetId), config.weatherFahrenheit)
                .putString(key("weatherPkg", widgetId), config.weatherPackage)
                .putBoolean(key("notifications", widgetId), config.showNotifications)
                .putBoolean(key("agenda", widgetId), config.showAgenda)
                .putInt(key("agendaSize", widgetId), config.agendaSizeSp)
                .putInt(key("agendaCount", widgetId), config.agendaCount)
                .putInt(key("agendaHours", widgetId), config.agendaLookaheadHours)
                .putBoolean(key("agendaRelative", widgetId), config.agendaRelative)
                .putBoolean(key("alarm", widgetId), config.showAlarm)
                .putInt(key("notificationsSize", widgetId), config.notificationsSizeSp)
                .putInt(key("batterySize", widgetId), config.batterySizeSp)
                .putInt(key("color", widgetId), config.textColor)
                .putInt(key("timeOpacity", widgetId), config.timeOpacity)
                .putInt(key("dateOpacity", widgetId), config.dateOpacity)
                .putInt(key("batteryOpacity", widgetId), config.batteryOpacity)
                .putInt(key("weatherOpacity", widgetId), config.weatherOpacity)
                .putInt(key("notificationsOpacity", widgetId), config.notificationsOpacity)
                .putInt(key("agendaOpacity", widgetId), config.agendaOpacity)
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
                .remove(key("weather", widgetId))
                .remove(key("weatherSize", widgetId))
                .remove(key("weatherF", widgetId))
                .remove(key("weatherPkg", widgetId))
                .remove(key("weatherOpacity", widgetId))
                .remove(key("notifications", widgetId))
                .remove(key("notificationsSize", widgetId))
                .remove(key("notificationsOpacity", widgetId))
                .remove(key("agenda", widgetId))
                .remove(key("agendaSize", widgetId))
                .remove(key("agendaCount", widgetId))
                .remove(key("agendaHours", widgetId))
                .remove(key("agendaRelative", widgetId))
                .remove(key("agendaOpacity", widgetId))
                .remove(key("alarm", widgetId))
                .apply()
        }

        private fun key(name: String, widgetId: Int) = name + "_" + widgetId
    }
}
