// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.media

import android.graphics.Bitmap
import android.media.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What's currently playable, if anything. A session only counts while it is playing, paused
 * or buffering — a stopped or empty session publishes null so the home screen collapses the
 * block entirely rather than reserving space for nothing.
 */
data class NowPlaying(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val art: Bitmap?,
    val controller: MediaController,
)

/**
 * Bridges the NotificationListenerService (system-managed, no direct handle available to the
 * UI layer) and the Compose UI, which just observes this singleton's state.
 */
object NowPlayingBus {
    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state

    fun update(value: NowPlaying?) {
        _state.value = value
    }
}