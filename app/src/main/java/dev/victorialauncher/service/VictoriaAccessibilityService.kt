// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * Only exists so the swipe-down gesture can open the notification shade. Android has no
 * public API for that — reflection into StatusBarManager is blocked on modern builds — and
 * `performGlobalAction` is the sanctioned route. The service reads nothing: it ignores every
 * event it receives.
 */
class VictoriaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: VictoriaAccessibilityService? = null

        val isConnected: Boolean get() = instance != null

        fun openNotificationShade(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) ?: false

        /** Locking the screen has no public API either; this is the sanctioned route. */
        fun lockScreen(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
        }
    }
}