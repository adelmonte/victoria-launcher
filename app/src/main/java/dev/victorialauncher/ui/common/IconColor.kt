// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.data.AppInfo
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * An icon's color as one sortable number, for listing apps by color instead of by name.
 *
 * Below [ACHROMATIC_BASE] it is the icon's main hue in tenths of a degree, 0..3599, red first.
 * From [ACHROMATIC_BASE] up it is an icon with no color worth the name, white, grey or black,
 * ordered from white to black: [ACHROMATIC_BASE] plus darkness in thousandths. So sorting by
 * the number gives the rainbow and then the greys, which is the order the strip draws them in.
 */
const val ACHROMATIC_BASE = 10_000

/** The color of an icon nothing could be read from, a mid grey. */
const val UNKNOWN_ICON_COLOR = ACHROMATIC_BASE + 500

/** Small on purpose: the main color survives being shrunk, and 1024 pixels is quick to read. */
private const val SAMPLE_PX = 32

/** Of the icon's opaque pixels, the share that must be colorful for it to count as colored. */
private const val COLORFUL_SHARE = 0.08f

private val colorCache = ConcurrentHashMap<String, Int>()

/** Called with the bitmap cache's own clear, for the same reason: an update can change an icon. */
internal fun clearIconColors() = colorCache.clear()

/**
 * The color key of every app in [apps], keyed by [AppInfo.key], from the icon as it is shown:
 * an icon pack's or a chosen override where there is one. Worked out once per icon and kept.
 */
suspend fun iconColorKeys(
    context: Context,
    apps: List<AppInfo>,
    iconPack: String?,
    overrides: Map<String, String>,
): Map<String, Int> {
    val victoriaApp = context.applicationContext as VictoriaApp
    fun keyOf(app: AppInfo): Int {
        val override = overrides[app.key]
        val cacheKey = "${app.key}|${app.componentName.className}|$iconPack|$override"
        colorCache[cacheKey]?.let { return it }
        val color = runCatching {
            iconColorKey(resolveDrawable(context, victoriaApp, app, iconPack, override))
        }.getOrDefault(UNKNOWN_ICON_COLOR)
        colorCache[cacheKey] = color
        return color
    }
    return withContext(Dispatchers.Default) {
        apps.chunked(16)
            .map { chunk -> async { chunk.map { it.key to keyOf(it) } } }
            .awaitAll()
            .flatten()
            .toMap()
    }
}

private fun iconColorKey(source: android.graphics.drawable.Drawable): Int {
    // A copy, so setting bounds here cannot move the picture somewhere else is drawing.
    val drawable = source.constantState?.newDrawable()?.mutate() ?: source
    val bitmap = Bitmap.createBitmap(SAMPLE_PX, SAMPLE_PX, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, SAMPLE_PX, SAMPLE_PX)
    drawable.draw(Canvas(bitmap))
    val pixels = IntArray(SAMPLE_PX * SAMPLE_PX)
    bitmap.getPixels(pixels, 0, SAMPLE_PX, 0, 0, SAMPLE_PX, SAMPLE_PX)
    bitmap.recycle()
    return colorKeyOf(pixels)
}

/**
 * The main color of [pixels] as a key (see [ACHROMATIC_BASE]).
 *
 * Hue is voted for by every colorful pixel, weighted by how colorful it is, so a white
 * background behind a red logo comes out red rather than pink. An icon with too few colorful
 * pixels to say is filed among the greys by how light it is overall.
 */
internal fun colorKeyOf(pixels: IntArray): Int {
    val votes = FloatArray(36)
    val hsv = FloatArray(3)
    var opaque = 0
    var colorful = 0
    var lightness = 0.0
    for (px in pixels) {
        if ((px ushr 24) < 128) continue
        opaque++
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val b = px and 0xFF
        lightness += (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
        android.graphics.Color.RGBToHSV(r, g, b, hsv)
        if (hsv[1] > 0.25f && hsv[2] > 0.25f) {
            colorful++
            votes[(hsv[0] / 10f).toInt().coerceIn(0, 35)] += hsv[1] * hsv[2]
        }
    }
    if (opaque == 0) return UNKNOWN_ICON_COLOR
    if (colorful < opaque * COLORFUL_SHARE) {
        val dark = 1.0 - lightness / opaque
        return ACHROMATIC_BASE + (dark * 1000).toInt().coerceIn(0, 1000)
    }
    // The strongest bin together with its neighbours, so a color split across a bin edge
    // still wins, then the hue within that window, averaged round the circle.
    var best = 0
    var bestScore = -1f
    for (i in 0 until 36) {
        val score = votes[(i + 35) % 36] + 2f * votes[i] + votes[(i + 1) % 36]
        if (score > bestScore) { bestScore = score; best = i }
    }
    var x = 0.0
    var y = 0.0
    for (offset in -1..1) {
        val bin = (best + offset + 36) % 36
        val angle = Math.toRadians(bin * 10.0 + 5.0)
        x += votes[bin] * cos(angle)
        y += votes[bin] * sin(angle)
    }
    val hue = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    return (hue * 10).toInt().coerceIn(0, 3599)
}
