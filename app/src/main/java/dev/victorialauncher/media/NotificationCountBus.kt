// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * How many notifications each package is currently showing.
 *
 * Bridges the NotificationListenerService to the UI for the same reason [NowPlayingBus] does:
 * the system owns the service and hands the app no handle to it.
 *
 * A count and nothing else. The text of a notification is the notification's business, and a
 * launcher that copied it onto the home screen would be putting whatever arrived in front of
 * whoever happened to be looking at the phone.
 */
object NotificationCountBus {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts

    fun update(value: Map<String, Int>) {
        _counts.value = value
    }

    /** Nothing is known once the service goes, and a stale badge is worse than no badge. */
    fun clear() {
        _counts.value = emptyMap()
    }
}
