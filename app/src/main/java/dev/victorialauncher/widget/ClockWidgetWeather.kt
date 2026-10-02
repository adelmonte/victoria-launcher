// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import java.util.Locale

/**
 * The last weather another app sent us, and whether it is still worth drawing.
 *
 * Victoria has no network permission and is not getting one, so there is no fetching here: a
 * weather app that already has the forecast broadcasts it, and this is where what arrived is
 * kept. Nothing asks for a location either — the sender says where it is.
 *
 * Written to disk rather than held in memory because a widget outlives the process that drew
 * it. A launcher gets killed often, and weather that vanishes every time it does is worse than
 * no weather at all.
 */
object ClockWidgetWeather {

    private const val FILE = "clock_widget_weather"
    private const val KEY_TEMP_K = "tempK"
    private const val KEY_CONDITION = "condition"
    private const val KEY_RECEIVED_AT = "receivedAt"

    /**
     * Past this, say nothing rather than something old.
     *
     * A forecast is a claim about now. Breezy sends on its own schedule and after a long doze
     * the last one can be hours stale, which is the one case where a blank row is the honest
     * answer.
     */
    private const val FRESH_FOR_MS = 3 * 60 * 60 * 1000L

    /** Kelvin, and wide enough for anywhere on Earth without accepting a parse gone wrong. */
    private val PLAUSIBLE_K = 150..350

    /** Enough for "Partly cloudy"; past this something is wrong with the sender, not the sky. */
    private const val MAX_CONDITION_CHARS = 40

    data class Reading(val tempK: Int, val condition: String)

    /** Null when nothing has arrived, or when what did is too old to repeat. */
    fun current(context: Context): Reading? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val tempK = prefs.getInt(KEY_TEMP_K, 0)
        if (tempK !in PLAUSIBLE_K) return null
        val receivedAt = prefs.getLong(KEY_RECEIVED_AT, 0L)
        if (receivedAt <= 0L || System.currentTimeMillis() - receivedAt > FRESH_FOR_MS) return null
        return Reading(tempK, prefs.getString(KEY_CONDITION, null).orEmpty())
    }

    /** Returns false for anything that does not look like a temperature, having stored nothing. */
    fun store(context: Context, tempK: Int, condition: String?): Boolean {
        if (tempK !in PLAUSIBLE_K) return false
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_TEMP_K, tempK)
            .putString(KEY_CONDITION, condition?.take(MAX_CONDITION_CHARS)?.trim().orEmpty())
            .putLong(KEY_RECEIVED_AT, System.currentTimeMillis())
            .apply()
        return true
    }

    fun formatted(reading: Reading, fahrenheit: Boolean): String {
        val celsius = reading.tempK - 273.15
        val value = if (fahrenheit) celsius * 9 / 5 + 32 else celsius
        val unit = if (fahrenheit) "F" else "C"
        val degrees = String.format(Locale.getDefault(), "%.0f°%s", value, unit)
        return if (reading.condition.isEmpty()) degrees else "$degrees  ${reading.condition}"
    }

    /** Where Fahrenheit is what people mean by a temperature, so the first draw is not wrong. */
    fun fahrenheitByDefault(): Boolean =
        Locale.getDefault().country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")

    /**
     * Turns the receiver on, or off again once no widget wants weather.
     *
     * It ships disabled. A receiver another app can reach is surface that should not exist
     * while the feature is switched off, and this is the one way to have none at all rather
     * than some that is merely careful.
     */
    fun setReceiverEnabled(context: Context, enabled: Boolean) {
        runCatching {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, ClockWidgetWeatherReceiver::class.java),
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
