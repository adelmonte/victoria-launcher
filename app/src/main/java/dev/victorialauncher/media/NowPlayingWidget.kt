// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.media

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import dev.victorialauncher.data.HomeAlignment
import dev.victorialauncher.R
import androidx.compose.ui.res.stringResource

fun isListenerEnabled(context: Context) =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/**
 * Brings up whatever is playing, and reports whether anything could be.
 *
 * The launcher intent goes first even though a session may nominate its own activity, which
 * would land on the player's own screen. Sending that PendingIntent starts an activity on
 * behalf of an app that is in the background, which Android drops on sight from 12 onwards
 * unless the sender explicitly grants the privilege — and `send()` reports success either
 * way, so there is no way to notice it went nowhere and fall back. Starting the intent
 * ourselves has no such problem: we are the foreground app. In practice the launcher intent
 * resumes the player's existing task anyway, which is the same screen.
 */
fun openNowPlayingApp(context: Context): Boolean {
    val controller = NowPlayingBus.state.value?.controller ?: return false

    context.packageManager.getLaunchIntentForPackage(controller.packageName)?.let { launch ->
        if (runCatching {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
        ) {
            return true
        }
    }

    // Nothing launchable — a background service publishing a session, say. The session's own
    // activity is all that is left, so it is worth a try even knowing it may be dropped.
    val sessionActivity = controller.sessionActivity ?: return false
    return runCatching { sessionActivity.send() }.isSuccess
}

@Composable
fun NowPlayingWidget(
    heightDp: Int = 64,
    contentColor: Color = Color.White,
    alignment: HomeAlignment = HomeAlignment.LEFT,
    /**
     * Draw an empty card when nothing is playing, rather than nothing at all.
     *
     * For edit layout, where the block has a height and paddings to set and silence is no
     * reason to be unable to set them — the card is most often adjusted when it is in the way,
     * which is exactly when music has stopped.
     */
    showPlaceholder: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var listenerEnabled by remember { mutableStateOf(isListenerEnabled(context)) }

    // The permission is granted in a separate system Settings screen, so re-check whenever
    // this screen comes back into the foreground instead of only once at first composition.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerEnabled = isListenerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!listenerEnabled) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(heightDp.dp),
            color = contentColor.copy(alpha = 0.08f),
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.now_playing_enable_access),
                    color = contentColor,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = stringResource(R.string.settings_open_settings),
                        tint = contentColor,
                    )
                }
            }
        }
        return
    }

    // Null unless something is actually playing, paused or buffering — see the listener
    // service. Nothing to show means nothing is drawn and no space is taken.
    val nowPlaying by NowPlayingBus.state.collectAsState()
    val current = nowPlaying ?: run {
        if (showPlaceholder) {
            Surface(
                modifier = modifier.fillMaxWidth().height(heightDp.dp),
                color = contentColor.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxHeight().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = contentColor.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.now_playing_placeholder),
                        color = contentColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        return
    }

    val scope = rememberCoroutineScope()
    val dismissX = remember(current.controller.sessionToken) { Animatable(0f) }
    val dismissThresholdPx = with(LocalDensity.current) { 120.dp.toPx() }

    // Everything inside scales with the card's height, so resizing it in edit mode grows
    // the artwork, text and controls together.
    val artSize = (heightDp * 0.62f).dp
    val titleSp = (heightDp * 0.22f).coerceIn(11f, 30f).sp
    val artistSp = (heightDp * 0.17f).coerceIn(9f, 24f).sp
    val controlSize = (heightDp * 0.42f).coerceIn(18f, 64f).dp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .offset { IntOffset(dismissX.value.roundToInt(), 0) }
            .graphicsLayer { alpha = (1f - (dismissX.value / (dismissThresholdPx * 2.5f))).coerceIn(0f, 1f) }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    scope.launch { dismissX.snapTo((dismissX.value + delta).coerceAtLeast(0f)) }
                },
                onDragStopped = { velocity ->
                    if (dismissX.value > dismissThresholdPx || velocity > 1200f) {
                        dismissX.animateTo(dismissThresholdPx * 6f, tween(180))
                        // Stopping the session is as far as an ordinary app can go: playback
                        // ends and the session drops, which collapses this block away.
                        runCatching { current.controller.transportControls.stop() }
                        NowPlayingBus.update(null)
                    } else {
                        dismissX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    }
                },
            ),
        // No scrim: the block sits on the wallpaper like the favorites above it do, so the
        // only thing separating it from them is its own spacing.
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Mirrored for a right-handed layout so the artwork sits under the favorites'
            // icons rather than opposite them. Centering leaves it alone: a full-width card
            // has nothing to center against.
            val artwork: @Composable () -> Unit = { Box(
                modifier = Modifier.size(artSize),
                contentAlignment = Alignment.Center,
            ) {
                val art = current.art
                if (art != null) {
                    Image(
                        bitmap = art.asImageBitmap(),
                        contentDescription = null,
                        // Cropped, not fitted: album art that isn't square used to letterbox
                        // inside its box and sit visibly smaller than the square it was given.
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(artSize * 0.75f),
                    )
                }
            } }
            val details: @Composable RowScope.() -> Unit = { Column(modifier = Modifier.weight(1f)) {
                Text(
                    current.title.ifBlank { stringResource(R.string.now_playing_unknown_title) },
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = titleSp,
                )
                Text(
                    current.artist,
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = artistSp,
                )
            } }
            val controls: @Composable () -> Unit = { Row(
                horizontalArrangement = Arrangement.spacedBy((heightDp * 0.10f).coerceIn(6f, 24f).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportButton(
                    Icons.Filled.SkipPrevious,
                    stringResource(R.string.now_playing_previous),
                    controlSize,
                    contentColor,
                ) {
                    current.controller.transportControls.skipToPrevious()
                }
                TransportButton(
                    if (current.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    stringResource(R.string.now_playing_play_pause),
                    controlSize,
                    contentColor,
                ) {
                    if (current.isPlaying) {
                        current.controller.transportControls.pause()
                    } else {
                        current.controller.transportControls.play()
                    }
                }
                TransportButton(
                    Icons.Filled.SkipNext,
                    stringResource(R.string.now_playing_next),
                    controlSize,
                    contentColor,
                ) {
                    current.controller.transportControls.skipToNext()
                }
            } }

            if (alignment == HomeAlignment.RIGHT) {
                controls()
                Spacer(Modifier.width(12.dp))
                details()
                Spacer(Modifier.width(12.dp))
                artwork()
            } else {
                artwork()
                Spacer(Modifier.width(12.dp))
                details()
                controls()
            }
        }
    }
}

@Composable
private fun RowScope.TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    size: Dp,
    tint: Color,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(size)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.7f))
    }
}