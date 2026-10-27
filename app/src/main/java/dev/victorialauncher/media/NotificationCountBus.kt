// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.media

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One notification as the home screen shows it: a line of text, and where tapping it goes. */
data class NotificationLine(
    val key: String,
    val text: String,
    val intent: PendingIntent?,
    /** Whether the notification asked to go away once opened, as tapping it in the shade would. */
    val autoCancel: Boolean,
)

/** How many of an app's notifications the home screen lists under it, newest first. */
const val MAX_NOTIFICATION_LINES = 3

/**
 * How many notifications each package is currently showing.
 *
 * Bridges the NotificationListenerService to the UI for the same reason [NowPlayingBus] does:
 * the system owns the service and hands the app no handle to it.
 *
 * Also the newest notifications' text per package, for the home screen to show only when
 * asked to: it puts whatever arrived in front of whoever is looking at the phone, so the
 * setting is off unless someone turns it on. Held in memory and never written anywhere.
 */
object NotificationCountBus {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts

    private val _previews = MutableStateFlow<Map<String, List<NotificationLine>>>(emptyMap())
    val previews: StateFlow<Map<String, List<NotificationLine>>> = _previews

    /** Set by the service while it is connected, since only it can dismiss a notification. */
    var canceller: ((String) -> Unit)? = null

    fun update(counts: Map<String, Int>, previews: Map<String, List<NotificationLine>>) {
        _counts.value = counts
        _previews.value = previews
    }

    /** Clears the notification, as swiping it away in the shade would. */
    fun dismiss(key: String) {
        runCatching { canceller?.invoke(key) }
    }

    /** Opens what the notification opens, as the shade would. False if there was nothing to open. */
    fun open(context: Context, line: NotificationLine): Boolean {
        val intent = line.intent ?: return false
        val sent = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                intent.send(context, 0, null, null, null, null, options.toBundle())
            } else {
                intent.send()
            }
        }.isSuccess
        if (sent && line.autoCancel) runCatching { canceller?.invoke(line.key) }
        return sent
    }

    /** Nothing is known once the service goes, and a stale badge is worse than no badge. */
    fun clear() {
        _counts.value = emptyMap()
        _previews.value = emptyMap()
    }
}
