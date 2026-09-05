// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.data.AppInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Per-app icon overrides are stored either as a gallery `content://` URI or as `pack:<pkg>:<drawable>`. */
const val ICON_PACK_OVERRIDE_PREFIX = "pack:"

fun encodePackOverride(packPackage: String, drawableName: String) =
    "$ICON_PACK_OVERRIDE_PREFIX$packPackage:$drawableName"

/**
 * Icons are rasterised once and cached. They used to be drawn by handing a Drawable to an
 * ImageView through AndroidView, which meant inflating a real Android View per row — far too
 * expensive for a list that rebuilds while scrubbing.
 *
 * The cache is bounded by *bytes*, not entry count. A fixed count is the wrong unit here: the
 * cache key includes the rasterised pixel size, so moving the icon-size slider adds a whole
 * new generation of bitmaps rather than replacing the old one, and at 96dp on a dense screen
 * a single icon is ~450 KB. Sizing by bytes against the heap keeps that bounded no matter how
 * many apps are installed or how large the icons are set.
 */
private object IconCache {
    private val cache = object : LruCache<String, ImageBitmap>(maxSizeKb()) {
        // Reported in KB so the running total cannot overflow an Int.
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            ((value.width.toLong() * value.height.toLong() * 4L) / 1024L).toInt().coerceAtLeast(1)
    }

    private fun maxSizeKb(): Int {
        val heapKb = Runtime.getRuntime().maxMemory() / 1024L
        return (heapKb / 8L).toInt().coerceIn(4 * 1024, 96 * 1024)
    }

    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, value: ImageBitmap) = cache.put(key, value)

    /**
     * Dropped wholesale when a package is added, removed or changed: an app that ships a new
     * icon in an update keeps none of the key's components, so nothing else would invalidate
     * the stale bitmap.
     */
    fun clear() = cache.evictAll()
}

fun clearIconCache() = IconCache.clear()

private fun iconCacheKey(app: AppInfo, iconPack: String?, override: String?, px: Int) =
    "${app.key}|$iconPack|$override|$px"

/**
 * Decode every app's icon ahead of time, off the main thread. Without this the first open of
 * the A-Z list rasterises a screenful of icons synchronously during composition, which is
 * what made it take a beat to appear.
 *
 * Work is fanned out across the dispatcher rather than run one icon at a time, and
 * [priorityKeys] (favorites and folder members) go first so the home screen is covered before
 * the long tail of everything else installed.
 */
suspend fun warmIconCache(
    context: Context,
    apps: List<AppInfo>,
    iconPack: String?,
    overrides: Map<String, String>,
    px: Int,
    priorityKeys: Set<String> = emptySet(),
) {
    if (px <= 0 || apps.isEmpty()) return
    val victoriaApp = context.applicationContext as VictoriaApp

    fun warm(app: AppInfo) {
        val key = iconCacheKey(app, iconPack, overrides[app.key], px)
        if (IconCache.get(key) != null) return
        runCatching {
            val drawable = resolveDrawable(context, victoriaApp, app, iconPack, overrides[app.key])
            IconCache.put(key, drawable.toBitmap(px, px).asImageBitmap())
        }
    }

    val (first, rest) = apps.partition { it.key in priorityKeys }
    coroutineScope {
        for (group in listOf(first, rest)) {
            if (group.isEmpty()) continue
            group.chunked(CHUNK_SIZE)
                .map { chunk -> async { chunk.forEach(::warm) } }
                .awaitAll()
        }
    }
}

/** Big enough that per-task overhead stays negligible against a drawable decode. */
private const val CHUNK_SIZE = 16

/**
 * Icon pack and per-app overrides, provided once for the whole tree. Collecting these flows
 * inside AppIcon meant every visible row started two DataStore collections of its own — with
 * a screenful of rows that is dozens of disk-backed collectors spun up the moment the list
 * appears, each resolving its icon twice (once for the initial value, once for the real one).
 */
@Immutable
data class IconConfig(val pack: String?, val overrides: Map<String, String>)

val LocalIconConfig = staticCompositionLocalOf { IconConfig(null, emptyMap()) }

@Composable
fun AppIcon(app: AppInfo, sizeDp: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val victoriaApp = context.applicationContext as VictoriaApp
    val config = LocalIconConfig.current
    val iconPackPackage = config.pack
    val overrideValue = config.overrides[app.key]
    val px = with(LocalDensity.current) { sizeDp.dp.roundToPx() }.coerceAtLeast(1)

    val cacheKey = iconCacheKey(app, iconPackPackage, overrideValue, px)
    val bitmap: ImageBitmap? = remember(cacheKey) {
        IconCache.get(cacheKey) ?: runCatching {
            val drawable = resolveDrawable(context, victoriaApp, app, iconPackPackage, overrideValue)
            drawable.toBitmap(px, px).asImageBitmap().also { IconCache.put(cacheKey, it) }
        }.getOrNull()
    }

    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = app.label, modifier = modifier.size(sizeDp.dp))
    } else {
        Box(modifier = modifier.size(sizeDp.dp))
    }
}

/**
 * Resolves an icon override — an icon-pack drawable or a picture from the gallery — for
 * either an app or a folder. Both use the same encoding on purpose, so a folder can take any
 * icon an app can.
 */
internal fun decodeIconOverride(
    context: Context,
    victoriaApp: VictoriaApp,
    override: String?,
): Drawable? = when {
    override == null -> null

    override.startsWith(ICON_PACK_OVERRIDE_PREFIX) -> {
        val body = override.removePrefix(ICON_PACK_OVERRIDE_PREFIX)
        val split = body.lastIndexOf(':')
        if (split <= 0) {
            null
        } else {
            victoriaApp.iconPackRepository.loadPackDrawable(
                body.substring(0, split),
                body.substring(split + 1),
            )
        }
    }

    else -> runCatching {
        val uri = Uri.parse(override)
        context.contentResolver.openInputStream(uri)?.use { stream ->
            Drawable.createFromStream(stream, uri.toString())
        }
    }.getOrNull()
}

private fun resolveDrawable(
    context: Context,
    victoriaApp: VictoriaApp,
    app: AppInfo,
    iconPackPackage: String?,
    overrideValue: String?,
): Drawable =
    decodeIconOverride(context, victoriaApp, overrideValue)
        ?: victoriaApp.iconPackRepository.getIcon(iconPackPackage, app.componentName) {
            victoriaApp.appRepository.loadIcon(app.componentName)
        }