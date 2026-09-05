// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.service

import android.view.HapticFeedbackConstants
import android.view.View

object HapticUtil {
    /**
     * A short tick as the finger crosses into a new letter on the A-Z strip.
     *
     * Deliberately routed through [View.performHapticFeedback] without
     * `FLAG_IGNORE_VIEW_SETTING`, so it stays silent for anyone who has turned touch feedback
     * off system-wide.
     */
    fun tick(view: View, enabled: Boolean) {
        if (!enabled) return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}