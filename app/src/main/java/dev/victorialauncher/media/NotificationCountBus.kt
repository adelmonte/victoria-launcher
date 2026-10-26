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
 * Also the newest notification's text per package, for the home screen to show only when
 * asked to: it puts whatever arrived in front of whoever is looking at the phone, so the
 * setting is off unless someone turns it on. Held in memory and never written anywhere.
 */
object NotificationCountBus {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts

    private val _previews = MutableStateFlow<Map<String, String>>(emptyMap())
    val previews: StateFlow<Map<String, String>> = _previews

    fun update(counts: Map<String, Int>, previews: Map<String, String>) {
        _counts.value = counts
        _previews.value = previews
    }

    /** Nothing is known once the service goes, and a stale badge is worse than no badge. */
    fun clear() {
        _counts.value = emptyMap()
        _previews.value = emptyMap()
    }
}
