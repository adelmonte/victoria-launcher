// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import dev.victorialauncher.data.AppFont

fun AppFont.toFontFamily(): FontFamily = when (this) {
    AppFont.SYSTEM -> FontFamily.Default
    AppFont.SANS_SERIF -> FontFamily.SansSerif
    AppFont.SERIF -> FontFamily.Serif
    AppFont.MONOSPACE -> FontFamily.Monospace
}

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

@Composable
fun VictoriaTheme(
    font: AppFont = AppFont.SYSTEM,
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
    val fontFamily = font.toFontFamily()
    val typography = baseTypography.copy(
        bodyLarge = baseTypography.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = baseTypography.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = baseTypography.bodySmall.copy(fontFamily = fontFamily),
        titleLarge = baseTypography.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = baseTypography.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = baseTypography.titleSmall.copy(fontFamily = fontFamily),
        labelLarge = baseTypography.labelLarge.copy(fontFamily = fontFamily),
    )
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}