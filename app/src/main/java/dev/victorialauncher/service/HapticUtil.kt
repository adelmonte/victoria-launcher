// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

object HapticUtil {
    /**
     * Whether the ticks go straight to the vibrator, for anyone who wants them here with touch
     * feedback off everywhere else (#187). Set from the setting as it changes, so the many
     * places that tick need not carry it.
     */
    @Volatile var ignoreSystemSetting = false

    /**
     * A short tick as the finger crosses into a new letter on the A-Z strip.
     *
     * Routed through [View.performHapticFeedback] by default, so it stays silent for anyone who
     * has turned touch feedback off system-wide, unless [ignoreSystemSetting] says otherwise.
     */
    fun tick(view: View, enabled: Boolean) {
        if (!enabled) return
        if (ignoreSystemSetting && vibrate(view.context)) return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun vibrate(context: Context): Boolean = runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (vibrator == null || !vibrator.hasVibrator()) return@runCatching false
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        } else {
            VibrationEffect.createOneShot(TICK_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
        true
    }.getOrDefault(false)

    private const val TICK_MS = 8L
}