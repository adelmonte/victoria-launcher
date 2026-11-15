// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable

/**
 * The space a screen's top bar keeps clear, whether or not the status bar is showing.
 *
 * The default follows the bar, so with "Hide status bar on the home screen" on it dropped to
 * nothing and titles ran under the camera cutout (#166). The cutout is added in as well, for a
 * phone whose cutout reaches below the bar's own height.
 */
@OptIn(ExperimentalLayoutApi::class)
val topBarInsets: WindowInsets
    @Composable get() = WindowInsets.systemBarsIgnoringVisibility
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
