// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.service

import android.content.Context

object SystemUi {
    /**
     * Pulls down the notification shade.
     *
     * There is no public API for this. Reflection into StatusBarManager is what launchers
     * traditionally used, but it throws on Android 12+ for ordinary apps, so we prefer the
     * accessibility service (which the user has to enable once) and keep reflection as a
     * fallback for older builds.
     *
     * @return true if the shade was actually opened.
     */
    fun expandNotificationShade(context: Context): Boolean {
        if (VictoriaAccessibilityService.openNotificationShade()) return true

        return runCatching {
            val service = context.getSystemService("statusbar")
            val method = Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
            method.invoke(service)
            true
        }.getOrDefault(false)
    }

    /** Whether the reliable path is available; drives the prompt in settings. */
    fun canExpandShade(): Boolean = VictoriaAccessibilityService.isConnected

    fun lockScreen(): Boolean = VictoriaAccessibilityService.lockScreen()
}