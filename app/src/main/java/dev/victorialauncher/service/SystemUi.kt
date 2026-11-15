// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.service

import android.content.Context
import android.content.Intent

object SystemUi {
    /**
     * Starts the phone's voice assistant listening, the way holding the power button does (#167).
     *
     * Voice command first: it asks for listening rather than for the assistant's own screen,
     * which is the whole difference from launching the assistant's app. Assist is the fallback
     * for an assistant that only answers that.
     *
     * @return false when nothing on the phone took either.
     */
    fun startVoiceAssistant(context: Context): Boolean {
        for (action in listOf(Intent.ACTION_VOICE_COMMAND, Intent.ACTION_ASSIST)) {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        return false
    }

    /** Set once reflection has actually opened the shade on this device; see below. */
    @Volatile
    private var reflectionWorked = false

    /**
     * Pulls down the notification shade.
     *
     * There is no public API for this. StatusBarManager.expandNotificationsPanel is a hidden
     * method, and whether reflection can still reach it is entirely up to the build: stock
     * Android blocks it, while a number of OEM and custom ROMs either leave it off the
     * restricted list or exempt whichever app currently holds the home role. That is the whole
     * explanation for it working on one phone and not the next — same app, same permission,
     * different ROM policy. The accessibility action is the only sanctioned route, so it is
     * tried first and reflection is the fallback rather than the other way round.
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
            reflectionWorked = true
            true
        }.getOrDefault(false)
    }

    /**
     * Whether the gesture has a way through; drives the prompt in settings. Reflection counts
     * once it has been seen to work, so a device where the pull-down already opens the shade
     * stops being told to turn on an accessibility service it does not need.
     */
    fun canExpandShade(): Boolean = VictoriaAccessibilityService.isConnected || reflectionWorked

    /** Locking has no fallback: the accessibility action is the only way to do it at all. */
    fun canLockScreen(): Boolean = VictoriaAccessibilityService.isConnected

    fun lockScreen(): Boolean = VictoriaAccessibilityService.lockScreen()
}