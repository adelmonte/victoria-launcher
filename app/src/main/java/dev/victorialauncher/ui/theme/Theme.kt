// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import dev.victorialauncher.data.AppFont

fun AppFont.toFontFamily(): FontFamily = when (this) {
    AppFont.SYSTEM -> FontFamily.Default
    AppFont.SANS_SERIF -> FontFamily.SansSerif
    AppFont.SERIF -> FontFamily.Serif
    AppFont.MONOSPACE -> FontFamily.Monospace
    // Without the file this cannot be resolved here; callers that have it use fontFamilyOf.
    AppFont.CUSTOM -> FontFamily.Default
}

/**
 * The typeface to draw with, including one the user supplied.
 *
 * The file is loaded once per path rather than per text: Typeface.createFromFile parses the
 * whole font, and the app list asks for this on every row. A file that will not parse falls
 * back to the default rather than failing — a font picked months ago may since have gone.
 */
fun fontFamilyOf(font: AppFont, fontFile: String?): FontFamily {
    if (font != AppFont.CUSTOM) return font.toFontFamily()
    val path = fontFile ?: return FontFamily.Default
    return customFontCache.getOrPut(path) {
        runCatching { FontFamily(Typeface.createFromFile(File(path))) }.getOrDefault(FontFamily.Default)
    }
}

private val customFontCache = mutableMapOf<String, FontFamily>()

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD1E0),
    onPrimary = Color(0xFF00363F),
    background = Color.Transparent,
    surface = Color(0xFF1B2733),
    onSurface = Color(0xFFE3E8EC),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF10707F),
    background = Color.Transparent,
    surface = Color(0xFFF3F6F8),
    onSurface = Color(0xFF1B2733),
)

/**
 * Corners, so a menu is as round as the things it opens over.
 *
 * Material gives a menu its extraSmall shape, which is 4dp — against rows and cards drawn here
 * at 18 to 22dp it read as a different app's menu. Everything the app draws by hand sits in
 * that range, so the scale is set to meet it rather than each menu being told separately.
 */
private val VictoriaShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(20.dp),
)

@Composable
fun VictoriaTheme(
    font: AppFont = AppFont.SYSTEM,
    fontFile: String? = null,
    content: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    // On Android 12+, use the system's Material You palette (derived from the user's
    // wallpaper) so Settings/dialogs feel like stock Android instead of a bespoke skin;
    // background stays transparent regardless, since the home screen relies on it to let
    // the real wallpaper show through.
    val baseColors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) DarkColors else LightColors
    }
    val colors = baseColors.copy(background = Color.Transparent)
    val baseTypography = MaterialTheme.typography
    val fontFamily = fontFamilyOf(font, fontFile)
    val typography = baseTypography.copy(
        bodyLarge = baseTypography.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = baseTypography.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = baseTypography.bodySmall.copy(fontFamily = fontFamily),
        titleLarge = baseTypography.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = baseTypography.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = baseTypography.titleSmall.copy(fontFamily = fontFamily),
        labelLarge = baseTypography.labelLarge.copy(fontFamily = fontFamily),
    )
    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = VictoriaShapes,
        content = content,
    )
}

/**
 * The shadow to put behind the launcher's own text, or null for none.
 *
 * Only behind light text: a shadow under dark text on a pale wallpaper muddies the letters
 * instead of separating them. The setting asks for contrast, and this is where it is decided
 * whether a shadow would actually gain any.
 */
@Composable
internal fun launcherTextShadow(enabled: Boolean, textColor: Color): Shadow? {
    if (!enabled) return null
    if (textColor.luminance() < 0.5f) return null
    return Shadow(color = Color.Black.copy(alpha = 0.55f), offset = Offset(0f, 1f), blurRadius = 4f)
}
